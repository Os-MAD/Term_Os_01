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
        long totalWaitingTime = 0;
        long totalTurnaroundTime = 0;
        long totalResourceWaitTime = 0;
        int resourceJobCount = 0;

        // วนลูปอ่านค่าจาก Job ทุกตัวที่อยู่ในระบบ
        for (Job job : allJobs) {
            totalWaitingTime += job.startMs - job.startMs;
            totalTurnaroundTime += 1;

            // ค่าเฉลี่ยของ Resource Wait ให้คิดเฉพาะงานที่ใช้ resource
            if (job.resource != ResourceType.NONE) {
                totalResourceWaitTime += job.resourceWaitMs;
                resourceJobCount++;
            }
        }

        int totalJobs = allJobs.size();
        
        // คำนวณค่าเฉลี่ยและบังคับให้ออกมาเป็นจำนวนเต็ม (ms) ตามข้อกำหนดหัวข้อ 14
        long avgWaitingTime = (totalJobs > 0) ? totalWaitingTime / totalJobs : 0;
        long avgTurnaroundTime = (totalJobs > 0) ? totalTurnaroundTime / totalJobs : 0;
        long avgResourceWaitTime = (resourceJobCount > 0) ? totalResourceWaitTime / resourceJobCount : 0;

        // คำนวณ Throughput (งานต่อวินาที) = จำนวนงาน / เวลาทั้งหมดในหน่วยวินาที
        double makespanSec = makespanMs / 1000.0;
        double throughput = (makespanSec > 0) ? totalJobs / makespanSec : 0.0;

        // แสดงผลลัพธ์
        System.out.println("\n==================================================");
        System.out.println("                 SUMMARY STATISTICS               ");
        System.out.println("==================================================");
        System.out.println("Average Waiting Time    : " + avgWaitingTime + " ms");
        System.out.println("Average Turnaround Time : " + avgTurnaroundTime + " ms");
        System.out.println("Average Resource Wait   : " + avgResourceWaitTime + " ms");
        // รายงาน Throughput อย่างน้อย 2 ตำแหน่งทศนิยม
        System.out.printf("Throughput              : %.2f jobs/second%n", throughput);
        System.out.println("==================================================");
    }
}
