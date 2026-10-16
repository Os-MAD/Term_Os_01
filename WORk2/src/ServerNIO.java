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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ServerNIO {
    private static final String FILE_DIRECTORY = "src/server_files/";
    private static final int PORT = Integer.getInteger("server.port", 8080);
    private static final AtomicLong CONNECTION_COUNTER = new AtomicLong(0);

    public static void main(String[] args) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             ServerSocket serverSocket = new ServerSocket(PORT)) {
            Logger.info("Server (NIO FileChannel Mode) running on port " + PORT + " using Virtual Threads.");
            Logger.info("File directory: " + Paths.get(FILE_DIRECTORY).toAbsolutePath().normalize());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                long connectionId = CONNECTION_COUNTER.incrementAndGet();
                executor.submit(() -> handleClient(clientSocket, connectionId));
            }
        } catch (IOException e) {
            Logger.error("Server stopped because of an I/O error.", e);
        }
    }

    private static void handleClient(Socket socket, long connectionId) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             OutputStream outputStream = socket.getOutputStream()) {
            String request = reader.readLine();
            if (request == null || request.isBlank()) return;

            String[] tokens = request.trim().split("\\s+");
            switch (tokens[0].toUpperCase()) {
                case "LIST" -> handleList(writer);
                case "INFO" -> handleInfo(tokens, writer);
                case "HASH" -> handleHash(tokens, writer);
                case "GET" -> handleGet(tokens, writer, outputStream, connectionId);
                default -> writer.println("ERROR 400 Unknown command");
            }
        } catch (Exception e) {
            Logger.error("Connection #" + connectionId + " handler failed: " + e.getMessage(), e);
        }
    }

    private static void handleList(PrintWriter writer) {
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
        if (count == 0) writer.println("ERROR 404 No files found in directory");
    }

    private static void handleInfo(String[] tokens, PrintWriter writer) {
        if (tokens.length != 2) {
            writer.println("ERROR 400 Usage: INFO <filename>");
            return;
        }
        Path filePath = resolveSafeFilePath(tokens[1]);
        if (filePath == null || !Files.isRegularFile(filePath)) {
            writer.println("ERROR 404 File not found");
            return;
        }
        try {
            writer.println("SIZE " + Files.size(filePath));
        } catch (IOException e) {
            writer.println("ERROR 500 Could not read file size");
        }
    }

    private static void handleHash(String[] tokens, PrintWriter writer) {
        if (tokens.length != 2) {
            writer.println("ERROR 400 Usage: HASH <filename>");
            return;
        }
        Path filePath = resolveSafeFilePath(tokens[1]);
        if (filePath == null || !Files.isRegularFile(filePath)) {
            writer.println("ERROR 404 File not found");
            return;
        }
        String hash = HashUtil.calculateSHA256(filePath);
        if (hash != null) writer.println("HASH " + hash);
        else writer.println("ERROR 500 SHA-256 calculation failed");
    }

    private static void handleGet(String[] tokens, PrintWriter writer,
                                  OutputStream outputStream, long connectionId) throws IOException {
        if (tokens.length != 4) {
            writer.println("ERROR 400 Usage: GET <filename> <offset> <length>");
            return;
        }

        final long offset;
        final long length;
        try {
            offset = Long.parseLong(tokens[2]);
            length = Long.parseLong(tokens[3]);
        } catch (NumberFormatException e) {
            writer.println("ERROR 400 Invalid offset or length");
            return;
        }

        Path filePath = resolveSafeFilePath(tokens[1]);
        if (filePath == null || !Files.isRegularFile(filePath)) {
            writer.println("ERROR 404 File not found");
            return;
        }

        long fileSize = Files.size(filePath);
        if (offset < 0 || length < 0 || offset > fileSize || length > fileSize - offset) {
            writer.println("ERROR 416 Requested range is outside the file");
            return;
        }

        double cpuBefore = CpuUtil.getProcessCpuLoad();
        Logger.info("Connection #" + connectionId + " GET Started | Offset: " + offset
                + " | Requested: " + length + " bytes | CPU Before: " + String.format("%.2f%%", cpuBefore));
        long startNanos = System.nanoTime();
        long sent = 0;

        // FileChannel is used for file access. The Socket/OutputStream is still blocking I/O;
        // this is not a fully non-blocking SocketChannel server.
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
             WritableByteChannel networkChannel = Channels.newChannel(outputStream)) {
            while (sent < length) {
                long transferred = fileChannel.transferTo(offset + sent, length - sent, networkChannel);
                if (transferred <= 0) {
                    throw new IOException("FileChannel.transferTo stopped after " + sent
                            + " of " + length + " bytes");
                }
                sent += transferred;
            }
        }

        double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        double cpuAfter = CpuUtil.getProcessCpuLoad();
        String status = sent == length ? "complete" : "INCOMPLETE";
        Logger.info("Connection #" + connectionId + " GET (NIO FileChannel) " + status + ": "
                + sent + "/" + length + " bytes in " + String.format("%.3f", durationMs)
                + " ms | CPU After: " + String.format("%.2f%%", cpuAfter));
        if (sent != length) throw new IOException("Sent " + sent + " of " + length + " requested bytes");
    }

    private static Path resolveSafeFilePath(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        Path root = Paths.get(FILE_DIRECTORY).toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        return file.startsWith(root) ? file : null;
    }
}
