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

    private int completed = 0;
    private int running = 0;

    private long totalWaitingTime = 0;
    private long totalTurnaroundTime = 0;
    private long totalResourceWaitTime = 0;
    private int resourceJobCount = 0;
    //เรียกตอน worker เริ่มทำ job เสร็จ
    public synchronized void recordStart() {
        running++;
    }

    public synchronized void recordCompletion(Job job) {
        long waitingTime = job.startMs - job.actualArrivalMs;

        long resourceWaitTime = 0;
        long resourceTime = 0;

        if (job.resource != ResourceType.NONE) {
            resourceWaitTime = job.resourceWaitMs;
            resourceTime = job.resourceMs;
            resourceJobCount++;
        }

        long turnaroundTime = waitingTime + job.workMs + resourceWaitTime + resourceTime;

        totalWaitingTime += waitingTime;
        totalTurnaroundTime += turnaroundTime;
        totalResourceWaitTime += resourceWaitTime;

        running--;
        completed++;
    }

    //Monitor ใช้ดูว่ากำลังทำกี่งาน
    public synchronized int runningCount() {
        return running;
    }

    public synchronized int completedCount() {
        return completed;
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

        int totalJobs = allJobs.size();
        long avgWaitingTime = (totalJobs > 0) ? totalWaitingTime / totalJobs : 0;
        long avgTurnaroundTime = (totalJobs > 0) ? totalTurnaroundTime / totalJobs : 0;
        long avgResourceWaitTime = (resourceJobCount > 0) ? totalResourceWaitTime / resourceJobCount : 0;
        double makespanSec = makespanMs / 1000.0;
        double throughput =(makespanSec > 0) ? totalJobs / makespanSec : 0.0;

        System.out.println("\n==================================================");
        System.out.println("                 SUMMARY STATISTICS               ");
        System.out.println("==================================================");
        System.out.println("Average Waiting Time    : " + avgWaitingTime + " ms");
        System.out.println("Average Turnaround Time : " + avgTurnaroundTime + " ms");
        System.out.println("Average Resource Wait   : " + avgResourceWaitTime + " ms");
        System.out.printf("Throughput              : %.2f jobs/second%n", throughput);
        System.out.println("==================================================");
    }
}