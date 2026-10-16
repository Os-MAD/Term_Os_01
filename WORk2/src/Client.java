import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Client {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int PORT = Integer.getInteger("server.port", 8080);
    private static final Path DOWNLOAD_DIR = Path.of("src");

    // Set to 1 or 10 when collecting measurements for the assignment.
    private static final int NUM_WORKERS = 10;

    private static final AtomicLong TOTAL_WORKER_TIME_NANOS = new AtomicLong(0);

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("=== Multi-threaded File Download Client ===");
            while (true) {
                System.out.println("\n-------------------------------------------");
                System.out.println("Available Commands:");
                System.out.println("1. LIST       (List files on Server)");
                System.out.println("2. GET <file> (Download file, e.g., GET Shiva.jpg)");
                System.out.println("3. EXIT       (Exit program)");
                System.out.print("Enter command: ");

                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                String[] tokens = input.split("\\s+");
                switch (tokens[0].toUpperCase()) {
                    case "EXIT" -> {
                        System.out.println("Exiting client...");
                        return;
                    }
                    case "LIST" -> requestFileList();
                    case "GET" -> {
                        if (tokens.length != 2) {
                            System.out.println("Usage: GET <filename>");
                        } else if (!isSimpleFileName(tokens[1])) {
                            System.out.println("Error: Please provide a filename without a path.");
                        } else {
                            startDownload(tokens[1]);
                        }
                    }
                    default -> System.out.println("Invalid command. Please try again.");
                }
            }
        }
    }

    private static boolean isSimpleFileName(String name) {
        if (name == null || name.isBlank()) return false;
        return Path.of(name).getFileName().toString().equals(name)
                && !name.equals(".") && !name.equals("..");
    }

    private static void requestFileList() {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("LIST");
            System.out.println("\n--- Files on Server ---");
            String response;
            boolean found = false;
            while ((response = reader.readLine()) != null) {
                if (response.startsWith("FILE ")) {
                    int lastSpace = response.lastIndexOf(' ');
                    if (lastSpace > 5) {
                        String name = response.substring(5, lastSpace);
                        String size = response.substring(lastSpace + 1);
                        System.out.println("- " + name + " (" + size + " bytes)");
                        found = true;
                    }
                } else if (response.startsWith("ERROR")) {
                    System.out.println(response);
                }
            }
            if (!found) System.out.println("No files found.");
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
        }
    }

    private static long getFileSize(String fileName) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("INFO " + fileName);
            String response = reader.readLine();
            if (response != null && response.startsWith("SIZE ")) {
                return Long.parseLong(response.substring(5).trim());
            }
            System.out.println("Server responded: " + response);
        } catch (Exception e) {
            System.err.println("Error getting file info: " + e.getMessage());
        }
        return -1;
    }

    private static void startDownload(String fileName) {
        long fileSize = getFileSize(fileName);
        if (fileSize < 0) {
            System.out.println("Download aborted.");
            return;
        }

        try {
            Files.createDirectories(DOWNLOAD_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create download directory: " + e.getMessage());
            return;
        }

        File finalFile = DOWNLOAD_DIR.resolve("DOWNLOADED_" + fileName).toFile();
        System.out.println("Starting download: " + fileName + " (" + fileSize
                + " bytes) using " + NUM_WORKERS + " Workers");
        TOTAL_WORKER_TIME_NANOS.set(0);

        long globalStartNanos = System.nanoTime();
        long chunkSize = fileSize / NUM_WORKERS;
        List<Future<Long>> futures = new ArrayList<>();
        boolean allWorkersSucceeded = true;

        try {
            // Create a fresh output file of the exact expected size.
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(finalFile, "rw")) {
                raf.setLength(fileSize);
            }

            ExecutorService executor = Executors.newFixedThreadPool(NUM_WORKERS);
            try {
                for (int i = 0; i < NUM_WORKERS; i++) {
                    long offset = i * chunkSize;
                    long length = (i == NUM_WORKERS - 1) ? fileSize - offset : chunkSize;
                    int workerId = i + 1;
                    futures.add(executor.submit(
                            () -> downloadPart(fileName, finalFile, offset, length, workerId)));
                }
            } finally {
                executor.shutdown();
            }

            for (Future<Long> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    allWorkersSucceeded = false;
                    Throwable cause = e.getCause();
                    System.err.println("A download worker failed: "
                            + (cause == null ? e.getMessage() : cause.getMessage()));
                }
            }
            if (!executorTerminated(futures)) {
                allWorkersSucceeded = false;
            }
        } catch (Exception e) {
            allWorkersSucceeded = false;
            System.err.println("Download setup failed: " + e.getMessage());
        }

        long totalTimeNanos = System.nanoTime() - globalStartNanos;
        double totalTimeMs = totalTimeNanos / 1_000_000.0;
        double averageWorkerMs = TOTAL_WORKER_TIME_NANOS.get() / (double) NUM_WORKERS / 1_000_000.0;
        double throughputMiBps = totalTimeNanos > 0
                ? (fileSize / (1024.0 * 1024.0)) / (totalTimeNanos / 1_000_000_000.0)
                : 0.0;

        System.out.println("\n=== Download Summary ===");
        System.out.println("File: " + finalFile.getPath());
        System.out.printf("Total Time (Global): %.3f ms%n", totalTimeMs);
        System.out.printf("Average Worker Time: %.3f ms%n", averageWorkerMs);
        System.out.printf("Throughput: %.3f MiB/s%n", throughputMiBps);

        if (!allWorkersSucceeded) {
            System.out.println("[ERROR] One or more workers failed. Hash verification will not be treated as a pass.");
        }

        try {
            long localSize = Files.size(finalFile.toPath());
            boolean sizeMatches = localSize == fileSize;
            System.out.println("File Size Check: " + (sizeMatches ? "PASS" : "FAIL")
                    + " (expected " + fileSize + ", actual " + localSize + " bytes)");

            System.out.println("Verifying file Hash (SHA-256)...");
            String localHash = HashUtil.calculateSHA256(finalFile.toPath());
            String serverHash = getFileHash(fileName);
            System.out.println("Local SHA-256  : " + (localHash == null ? "UNAVAILABLE" : localHash));
            System.out.println("Server SHA-256 : " + (serverHash == null ? "UNAVAILABLE" : serverHash));

            boolean hashMatches = localHash != null && serverHash != null && localHash.equalsIgnoreCase(serverHash);
            if (allWorkersSucceeded && sizeMatches && hashMatches) {
                System.out.println("[SUCCESS] Size and SHA-256 matched. File is complete and intact.");
            } else {
                System.out.println("[ERROR] Verification failed. Do not use this file as a successful download.");
            }
        } catch (IOException e) {
            System.err.println("Could not verify downloaded file: " + e.getMessage());
        }
    }

    private static boolean executorTerminated(List<Future<Long>> futures) {
        for (Future<Long> future : futures) {
            if (!future.isDone() || future.isCancelled()) return false;
        }
        return true;
    }

    private static long downloadPart(String fileName, File finalFile,
                                     long offset, long length, int workerId) throws Exception {
        long workerStartNanos = System.nanoTime();
        long bytesRemaining = length;

        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             InputStream inStream = socket.getInputStream();
             java.io.RandomAccessFile raf = new java.io.RandomAccessFile(finalFile, "rw")) {

            socket.setSoTimeout(30_000);
            raf.seek(offset);
            writer.println("GET " + fileName + " " + offset + " " + length);

            byte[] buffer = new byte[8192];
            while (bytesRemaining > 0) {
                int bytesRead = inStream.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining));
                if (bytesRead == -1) {
                    throw new EOFException("Server closed connection with " + bytesRemaining
                            + " bytes still missing for Worker " + workerId);
                }
                raf.write(buffer, 0, bytesRead);
                bytesRemaining -= bytesRead;
            }
        }

        long durationNanos = System.nanoTime() - workerStartNanos;
        TOTAL_WORKER_TIME_NANOS.addAndGet(durationNanos);
        System.out.printf("Worker %d finished in %.3f ms (Offset: %d, Bytes: %d)%n",
                workerId, durationNanos / 1_000_000.0, offset, length);
        return durationNanos;
    }

    private static String getFileHash(String fileName) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println("HASH " + fileName);
            String response = reader.readLine();
            if (response != null && response.startsWith("HASH ")) {
                return response.substring(5).trim();
            }
            System.err.println("Server responded to HASH: " + response);
        } catch (Exception e) {
            System.err.println("Error getting file hash from server: " + e.getMessage());
        }
        return null;
    }
}
