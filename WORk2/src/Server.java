import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Server {
    // โฟลเดอร์ที่เก็บไฟล์สำหรับให้ Client ดาวน์โหลด
    private static final String FILE_DIRECTORY = "src/server_files/";
    private static final int PORT = 8080;
    private static final AtomicLong CONNECTION_COUNTER = new AtomicLong(0);

    public static void main(String[] args) {
        // Virtual Threads ต้องใช้ Java 21 ขึ้นไป
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             ServerSocket serverSocket = new ServerSocket(PORT)) {

            Logger.info("Server is running on port " + PORT + " using Virtual Threads.");
            Logger.info("File directory: " + Paths.get(FILE_DIRECTORY).toAbsolutePath().normalize());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                long connectionId = CONNECTION_COUNTER.incrementAndGet();
                Logger.info("Connection #" + connectionId + " accepted from "
                        + clientSocket.getRemoteSocketAddress());
                executor.submit(() -> handleClient(clientSocket, connectionId));
            }
        } catch (IOException e) {
            Logger.error("Server stopped because of an I/O error.", e);
        }
    }

    private static void handleClient(Socket socket, long connectionId) {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            String request = reader.readLine();
            if (request == null || request.isBlank()) {
                Logger.warn("Connection #" + connectionId + " from " + remote + " sent an empty request.");
                return;
            }

            Logger.info("Connection #" + connectionId + " request: " + request);
            String[] tokens = request.trim().split("\\s+");
            String command = tokens[0].toUpperCase();

            switch (command) {
                case "LIST" -> handleList(writer, connectionId);
                case "INFO" -> handleInfo(tokens, writer, connectionId);
                case "GET" -> handleGet(tokens, writer, socket.getOutputStream(), connectionId);
                default -> {
                    writer.println("ERROR 400 Unknown command");
                    Logger.warn("Connection #" + connectionId + " used unknown command: " + command);
                }
            }
        } catch (Exception e) {
            Logger.error("Connection #" + connectionId + " handler failed: " + e.getMessage(), e);
        } finally {
            Logger.info("Connection #" + connectionId + " closed (" + remote + ").");
        }
    }

    private static void handleList(PrintWriter writer, long connectionId) {
        File folder = new File(FILE_DIRECTORY);
        File[] files = folder.listFiles();
        int count = 0;

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    writer.println("FILE " + file.getName() + " " + file.length());
                    count++;
                }
            }
        }

        if (count == 0) {
            writer.println("ERROR 404 No files found in directory");
            Logger.warn("Connection #" + connectionId + " requested LIST, but no files were found.");
        } else {
            Logger.info("Connection #" + connectionId + " LIST completed: " + count + " file(s).");
        }
    }

    private static void handleInfo(String[] tokens, PrintWriter writer, long connectionId) {
        if (tokens.length < 2) {
            writer.println("ERROR 400 Missing filename");
            Logger.warn("Connection #" + connectionId + " sent INFO without a filename.");
            return;
        }

        Path filePath = resolveSafeFilePath(tokens[1]);
        if (filePath == null || !java.nio.file.Files.isRegularFile(filePath)) {
            writer.println("ERROR 404 File not found");
            Logger.warn("Connection #" + connectionId + " requested missing/invalid file INFO: " + tokens[1]);
            return;
        }

        try {
            long fileSize = java.nio.file.Files.size(filePath);
            writer.println("SIZE " + fileSize);
            Logger.info("Connection #" + connectionId + " INFO " + tokens[1]
                    + " -> " + fileSize + " bytes.");
        } catch (IOException e) {
            writer.println("ERROR 500 Could not read file size");
            Logger.error("Connection #" + connectionId + " could not read file size: " + tokens[1], e);
        }
    }

    private static void handleGet(String[] tokens, PrintWriter writer,
                                  OutputStream outputStream, long connectionId) throws IOException {
        if (tokens.length < 4) {
            writer.println("ERROR 400 Missing GET arguments");
            Logger.warn("Connection #" + connectionId + " sent GET with missing arguments.");
            return;
        }

        final long offset;
        final long length;
        try {
            offset = Long.parseLong(tokens[2]);
            length = Long.parseLong(tokens[3]);
        } catch (NumberFormatException e) {
            writer.println("ERROR 400 Invalid offset or length");
            Logger.warn("Connection #" + connectionId + " sent invalid GET range: " + tokens[2] + " " + tokens[3]);
            return;
        }

        if (offset < 0 || length < 0) {
            writer.println("ERROR 400 Offset and length must be non-negative");
            Logger.warn("Connection #" + connectionId + " sent negative GET range.");
            return;
        }

        Path filePath = resolveSafeFilePath(tokens[1]);
        if (filePath == null || !java.nio.file.Files.isRegularFile(filePath)) {
            writer.println("ERROR 404 File not found");
            Logger.warn("Connection #" + connectionId + " requested missing/invalid file GET: " + tokens[1]);
            return;
        }

        long fileSize = java.nio.file.Files.size(filePath);
        if (offset > fileSize || length > fileSize - offset) {
            writer.println("ERROR 416 Requested range is outside the file");
            Logger.warn("Connection #" + connectionId + " requested out-of-range bytes for " + tokens[1]
                    + " (offset=" + offset + ", length=" + length + ", fileSize=" + fileSize + ").");
            return;
        }

        Logger.info("Connection #" + connectionId + " GET started: file=" + tokens[1]
                + ", offset=" + offset + ", length=" + length + " bytes.");
        long startNanos = System.nanoTime();
        long sent = sendFileRegion(filePath, offset, length, outputStream);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        Logger.info("Connection #" + connectionId + " GET finished: file=" + tokens[1]
                + ", sent=" + sent + "/" + length + " bytes, duration=" + durationMs + " ms.");
    }

    /** ป้องกันการระบุชื่อไฟล์เพื่อออกนอกโฟลเดอร์ server_files */
    private static Path resolveSafeFilePath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path root = Paths.get(FILE_DIRECTORY).toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root)) {
            return null;
        }
        return file;
    }

    /** ส่งข้อมูลช่วงไฟล์ด้วย NIO transferTo และวนส่งจนกว่าจะครบช่วงที่ร้องขอ */
    private static long sendFileRegion(Path filePath, long offset, long length,
                                       OutputStream outputStream) throws IOException {
        long totalTransferred = 0;
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
             WritableByteChannel networkChannel = Channels.newChannel(outputStream)) {

            while (totalTransferred < length) {
                long transferred = fileChannel.transferTo(
                        offset + totalTransferred,
                        length - totalTransferred,
                        networkChannel);

                if (transferred > 0) {
                    totalTransferred += transferred;
                } else {
                    // Fallback หาก transferTo ส่งข้อมูลไม่ได้ในรอบนั้น
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(
                            (int) Math.min(8192L, length - totalTransferred));
                    int read = fileChannel.read(buffer, offset + totalTransferred);
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        networkChannel.write(buffer);
                    }
                    totalTransferred += read;
                }
            }
        }
        return totalTransferred;
    }
}
