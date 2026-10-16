import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger สำหรับบันทึกเหตุการณ์ของ Server ลงหน้าจอและไฟล์ logs/server.log
 * แต่ละบรรทัดมีเวลาจริงและเวลาที่ผ่านไปนับจากเริ่มโปรแกรม
 */
public final class Logger {
    private static final Path LOG_FILE = Path.of("logs", "server.log");
    private static final long START_TIME_NANOS = System.nanoTime();
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Logger() {
        // Utility class: ไม่ต้องสร้าง object
    }

    public static synchronized void info(String message) {
        write("INFO", message, null);
    }

    public static synchronized void warn(String message) {
        write("WARN", message, null);
    }

    public static synchronized void error(String message) {
        write("ERROR", message, null);
    }

    public static synchronized void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    /** เวลาที่ผ่านไปนับจากเริ่มโปรแกรม หน่วยมิลลิวินาที */
    public static long elapsedMs() {
        return (System.nanoTime() - START_TIME_NANOS) / 1_000_000L;
    }

    private static void write(String level, String message, Throwable error) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String threadName = Thread.currentThread().getName();
        if (threadName == null || threadName.isBlank()) {
            threadName = "virtual-thread-" + Thread.currentThread().threadId();
        }
        String line = String.format("[%s] [+%d ms] [%s] [%s] %s",
                timestamp, elapsedMs(), level, threadName, message);

        // แสดงบนหน้าจอด้วย เพื่อให้เห็น log ขณะรัน Server
        if ("ERROR".equals(level)) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }

        try {
            Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // ไม่เรียก Logger ซ้ำตรงนี้ เพื่อป้องกันการวนซ้ำเมื่อเขียน log ไม่สำเร็จ
            System.err.println("[Logger ERROR] Cannot write log file: " + e.getMessage());
        }

        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
