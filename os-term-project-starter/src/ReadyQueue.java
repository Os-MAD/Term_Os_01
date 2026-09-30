import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * คิวงานที่พร้อมถูกหยิบไปทำ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * สิ่งที่คลาสนี้ต้องทำได้:
 *   - เก็บงานที่รอ Worker อยู่
 *   - หยิบงานถัดไปตามนโยบายที่เลือก (FCFS หรือ Priority)
 *   - ถูกเรียกจากหลาย Thread พร้อมกันได้อย่างปลอดภัย
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 4: priority = 1 สูงสุด เมื่อเท่ากันต้องมีกติกาตัดสินลำดับ (tie-break)
 *     ที่ตัดสินจากข้อมูลของ Job ไม่ขึ้นกับว่า Thread ใดเข้าถึงคิวก่อน
 *   - หัวข้อ 7: ห้ามวนลูปเช็กแบบกิน CPU (busy waiting) — Worker ที่ไม่มีงานทำ
 *     ต้องถูกพักไว้ ไม่ใช่วนถามซ้ำ ๆ
 *
 * จะออกแบบเป็นคลาสเดียวที่รับนโยบายเข้ามา หรือแยกเป็นสองคลาส
 * หรือใช้โครงสร้างข้อมูลสำเร็จรูปของ Java ก็ได้ ขอให้อธิบายเหตุผลได้ใน Demo
 */
public class ReadyQueue {

    // TODO: เก็บนโยบาย (Config.Policy) และโครงสร้างข้อมูลที่ใช้เก็บงาน
    // ใช้ LinkedBlockingQueue สำหรับการทำงานแบบ FCFS (Thread-safe โดยธรรมชาติ)
    private final Config.Policy policy;
    private final BlockingQueue<Job> queue;

    public ReadyQueue(Config.Policy policy) {
        // TODO
        // สร้างคิวแบบ LinkedBlockingQueue ซึ่งเป็น FIFO ตรงกับ FCFS 
        // (ละเว้นการตรวจสอบ Config.Policy ไปก่อนเนื่องจากรองรับแค่ FCFS ตามความต้องการ)
        this.policy = policy;
        this.queue = new LinkedBlockingQueue<>();

        if(policy == Config.Policy.FCFS){
            this.queue = new LinkedBlockingQueue<>();
            // รอสร้าง PriorityBlockingQueue -------------------------
        }
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public void add(Job job) {
        // TODO
        // เพิ่มงานเข้าไปต่อท้ายคิวอย่างปลอดภัย
        this.queue.add(job);
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     *
     * ถ้ายังไม่มีงาน ต้องรอโดยไม่กิน CPU
     * ต้องคิดด้วยว่าจะบอก Worker อย่างไรเมื่อไม่มีงานเหลือแล้วและควรหยุดทำงาน
     */
    public Job take() throws InterruptedException {
        // TODO
        // take() จะหยุดรอ (block) อัตโนมัติหากคิวว่าง โดยไม่ใช้ loop กิน CPU (ไม่มี busy waiting)
        return this.queue.take();
    }

    /** จำนวนงานที่รออยู่ตอนนี้ ใช้โดย Monitor — ต้องอ่านได้อย่างปลอดภัย */
    public int size() {
        // TODO
        // คืนค่าจำนวนงานที่อยู่ในคิว
        return this.queue.size();
    }
}
