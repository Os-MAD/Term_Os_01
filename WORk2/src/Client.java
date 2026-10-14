import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Client {
    // กำหนดรายละเอียด Server
    private static final String SERVER_ADDRESS = "127.0.0.1"; // โหลดจากเครื่องตัวเอง (localhost)
    private static final int PORT = 8080;
    
    // ตั้งค่าไฟล์ที่ต้องการโหลด และ โฟลเดอร์ปลายทาง
    private static final String TARGET_FILE = "Faputa_1.jpg"; // ชื่อไฟล์ใน server_files ที่คุณมี
    private static final String DOWNLOAD_DIR = "src/"; // จะเซฟไว้ที่ src/Faputa (1).jpg
    
    private static final int NUM_WORKERS = 10; // บังคับ 10 Workers ตามข้อกำหนด

    public static void main(String[] args) {
        System.out.println("Starting Multi-threaded File Download Client...");
        
        long startTime = System.currentTimeMillis();

        // 1. ขอขนาดไฟล์เป้าหมายจาก Server (คำสั่ง INFO)
        long fileSize = getFileSize(TARGET_FILE);
        if (fileSize <= 0) {
            System.err.println("Failed to get file size. Download aborted.");
            return;
        }
        
        System.out.println("File size: " + fileSize + " bytes. Splitting to " + NUM_WORKERS + " workers.");

        // 2. เตรียมไฟล์เปล่าขนาดเท่าของจริงรอไว้ก่อน (ใช้ RandomAccessFile)
        File finalFile = new File(DOWNLOAD_DIR + "DOWNLOADED_" + TARGET_FILE);
        try (RandomAccessFile raf = new RandomAccessFile(finalFile, "rw")) {
            raf.setLength(fileSize); // จองพื้นที่ไฟล์
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 3. แบ่งช่วงไฟล์ให้ 10 Workers (คำนวณ offset และ length)
        long chunkSize = fileSize / NUM_WORKERS;
        ExecutorService executor = Executors.newFixedThreadPool(NUM_WORKERS);

        for (int i = 0; i < NUM_WORKERS; i++) {
            long offset = i * chunkSize;
            // Worker ตัวสุดท้ายให้รับเศษข้อมูลที่เหลือทั้งหมด
            long length = (i == NUM_WORKERS - 1) ? (fileSize - offset) : chunkSize;
            
            final int workerId = i + 1; // ลำดับของ Worker (1-10)

            // สั่งให้ Worker แต่ละตัวไปทำงาน
            executor.submit(() -> {
                downloadPart(TARGET_FILE, finalFile, offset, length, workerId);
            });
        }

        // 4. รอให้ทุก Worker โหลดเสร็จ
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 5. สรุปผลการโหลด
        long endTime = System.currentTimeMillis();
        long timeTaken = endTime - startTime;
        double throughputMBps = (fileSize / 1024.0 / 1024.0) / (timeTaken / 1000.0);

        System.out.println("\nDownload Complete!");
        System.out.println("Saved as: " + finalFile.getPath());
        System.out.println("Total Time: " + timeTaken + " ms");
        System.out.printf("Throughput: %.2f MB/s\n", throughputMBps);
    }

    // ฟังก์ชันสำหรับส่งคำสั่ง INFO เพื่อขอขนาดไฟล์ก่อนเริ่มโหลด
    private static long getFileSize(String fileName) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // ส่งคำสั่ง INFO
            writer.println("INFO " + fileName);
            
            // อ่านค่าที่ตอบกลับมา (คาดหวัง SIZE <bytes>)
            String response = reader.readLine();
            if (response != null && response.startsWith("SIZE ")) {
                return Long.parseLong(response.split(" ")[1]);
            } else {
                System.out.println("Server responded: " + response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    // ฟังก์ชันหลักที่ให้ Worker แต่ละตัวโหลดไฟล์เฉพาะส่วนของตัวเอง
    private static void downloadPart(String fileName, File finalFile, long offset, long length, int workerId) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             InputStream inStream = socket.getInputStream();
             // เปิด RandomAccessFile เพื่อเขียนข้อมูลลงตำแหน่งที่กำหนด
             RandomAccessFile raf = new RandomAccessFile(finalFile, "rw")) {

            // เลื่อนเคอร์เซอร์ไปรอเขียนที่ตำแหน่ง offset ของตัวเอง
            raf.seek(offset);

            // ส่งคำสั่ง GET พร้อมระบุช่วงไฟล์
            writer.println("GET " + fileName + " " + offset + " " + length);
            
            System.out.println("Worker " + workerId + " downloading offset " + offset + " (length: " + length + ")");

            // อ่านข้อมูลดิบที่ส่งกลับมาแล้วเขียนลงไฟล์
            byte[] buffer = new byte[8192];
            long bytesRemaining = length;
            int bytesRead;

            while (bytesRemaining > 0 && (bytesRead = inStream.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining))) != -1) {
                raf.write(buffer, 0, bytesRead);
                bytesRemaining -= bytesRead;
            }

            System.out.println("Worker " + workerId + " finished.");

        } catch (Exception e) {
            System.err.println("Worker " + workerId + " failed: " + e.getMessage());
        }
    }
}
