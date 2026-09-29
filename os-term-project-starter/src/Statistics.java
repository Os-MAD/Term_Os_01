import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * รวบรวมและคำนวณค่าที่ใช้วัดผลของการรันหนึ่งครั้ง
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้ (หัวข้อ 8):
 *   - Waiting Time, Turnaround Time, Throughput, Resource Wait Time
 *   - ต้องถูกอัปเดตจากหลาย Worker พร้อมกันได้อย่างปลอดภัย
 *   - ผลต้องสอดคล้องกับสมการตรวจสอบ:
 *       Turnaround = Waiting + workMs + Resource Wait + resourceMs
 *     ใช้สมการนี้ตรวจงานทีละชิ้นได้ว่าค่าไหนคำนวณผิด
 *
 * ข้อควรระวัง: ค่าเฉลี่ยของ Resource Wait ให้คิดเฉพาะงานที่ใช้ resource
 * ส่วนงานที่ resource = NONE ให้ถือว่า Resource Wait เป็น 0
 */
public class Statistics {

    // TODO: เก็บข้อมูลของงานที่เสร็จแล้ว หรือเก็บผลรวมไว้คำนวณทีหลัง
    //จำนวนงานที่ทำเสร็จแล้ว
    private final AtomicInteger completed = new AtomicInteger(0);
    /** บันทึกว่างานชิ้นหนึ่งเสร็จแล้ว เรียกโดย Worker หลายตัวพร้อมกันได้ */
    // จำนวนงานที่ worker กำลังทำ
    private final AtomicInteger running = new AtomicInteger(0);
    //เรียกตอน worker เริ่มทำ job เสร็จ
    public void recordstart() {
        running.incrementAndGet();
    }

    public void recordCompletion(Job job) {
        /// อย่าลืมใช้ SYNCHROZATION ไม่งั้นจะเกิด RACE CONDITIONNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN
        running.decrementAndGet();
        completed.incrementAndGet();    
        
    }
        //Monitor ใช้ดูว่ากำลังทำกี่งาน
        public int runningCount() {
        return running.get();
    }
    

    /** จำนวนงานที่เสร็จแล้ว ใช้โดย Monitor และใช้ตรวจว่างานครบหรือยัง */
    public int completedCount() {
        return completed.get();
        // TODO
    
    }

    /**
     * พิมพ์ตารางสรุปผลตอนจบโปรแกรม
     * อย่างน้อยต้องมี avg Waiting Time, avg Turnaround Time,
     * Throughput และ avg Resource Wait Time
     *
     * ตามหัวข้อ 14 ให้รายงานเวลาเป็นจำนวนเต็มหน่วย ms
     * และ Throughput อย่างน้อย 2 ตำแหน่งทศนิยม
     */
    public void printSummary(List<Job> allJobs, long makespanMs) {
        // TODO
        throw new UnsupportedOperationException("TODO: Statistics.printSummary");
    }
}
