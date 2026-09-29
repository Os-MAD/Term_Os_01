import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วมของทั้งระบบ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 5: ใช้ Semaphore ควบคุม PRINTER และ DATABASE
 *     จำนวน permit มาจาก command line (Config)
 *     ในส่วนบังคับให้สร้าง Semaphore แบบ fair = true
 *   - Worker ทุกตัวต้องใช้ ResourceManager object เดียวกัน
 *   - หัวข้อ 7: permit ต้องไม่สูญหายหรือค้าง แม้เกิด exception
 *     หรือถูก interrupt ระหว่างถือ resource
 *
 * คำถามที่จะถูกถามใน Demo:
 *   - ทำไมต้อง fair = true และถ้าเปลี่ยนเป็น false จะเกิดอะไรขึ้น
 *   - ถ้า Thread ถูก interrupt หลัง acquire สำเร็จแต่ก่อน release
 *     โค้ดของกลุ่มยังคืน permit ได้หรือไม่
 */
public class ResourceManager {

    // เก็บ Semaphore ของ PRINTER และ DATABASE โดยใช้ final เพื่อความเสถียร (Thread-safe)
    private final Semaphore printerSemaphore;
    private final Semaphore databaseSemaphore;
    
    // เก็บจำนวน permit รวมทั้งหมดไว้สำหรับคำนวณสถานะ (status)
    private final int totalPrinterPermits;
    private final int totalDatabasePermits;

    public ResourceManager(int printerPermits, int databasePermits) {
        // สร้าง Semaphore แบบ fair = true ตามที่ส่วนบังคับของโจทย์กำหนด
        this.printerSemaphore = new Semaphore(printerPermits, true);
        this.databaseSemaphore = new Semaphore(databasePermits, true);
        
        this.totalPrinterPermits = printerPermits;
        this.totalDatabasePermits = databasePermits;
    }

    /** ขอสิทธิ์ใช้ทรัพยากร จะรอจนกว่าจะได้ */
    public void acquire(ResourceType type) throws InterruptedException {
        if (type == ResourceType.PRINTER) {
            printerSemaphore.acquire();
        } else if (type == ResourceType.DATABASE) {
            databaseSemaphore.acquire();
        }
        // หากเป็น ResourceType.NONE ไม่ต้องทำการขอ Semaphore ใดๆ
    }

    /** คืนสิทธิ์ใช้ทรัพยากร */
    public void release(ResourceType type) {
        if (type == ResourceType.PRINTER) {
            printerSemaphore.release();
        } else if (type == ResourceType.DATABASE) {
            databaseSemaphore.release();
        }
        // หากเป็น ResourceType.NONE ไม่ต้องทำการคืน Semaphore ใดๆ
    }

    /**
     * ข้อความสั้น ๆ บอกสถานะการใช้ทรัพยากร สำหรับส่งให้ ProjectLogger.monitor()
     * เช่น "printer=1/1 database=0/2"
     */
    public String status() {
        // คำนวณจำนวนที่ถูกใช้งาน = จำนวนทั้งหมด - จำนวนที่ว่างอยู่ (available)
        int printerUsed = totalPrinterPermits - printerSemaphore.availablePermits();
        int databaseUsed = totalDatabasePermits - databaseSemaphore.availablePermits();
        
        return "printer=" + printerUsed + "/" + totalPrinterPermits + 
               " database=" + databaseUsed + "/" + totalDatabasePermits;
    }
}
