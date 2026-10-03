import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs ของแต่ละ Job
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * หน้าที่ (หัวข้อ 3 ของโจทย์):
 *   - รอจนถึงเวลา arrivalMs ของแต่ละงาน แล้วส่งงานต่อไปยัง Scheduler
 *   - บันทึกเวลาที่งานเข้าสู่ระบบ "จริง" ลงใน Job
 *     (อาจไม่ตรงกับ arrivalMs เป๊ะ เพราะ Thread ถูกปลุกช้าได้)
 *   - เรียก logger.jobArrived(job) ทุกครั้งที่ปล่อยงาน
 *
 * ข้อควรคิด:
 *   - รายการงานที่ได้จาก WorkloadLoader เรียงตามลำดับในไฟล์ ไม่ได้เรียงตามเวลา
 *   - เมื่อปล่อยงานครบทุกชิ้นแล้ว ต้องมีวิธีบอกระบบว่า "จะไม่มีงานเข้ามาอีก"
 *     ดู TODO เรื่องการปิดระบบใน Main
 */
public class JobGenerator extends Thread {

    private final List<Job> jobs;
    private final ProjectLogger logger;
    private final List<Job> schedulerQueue; // เปลี่ยนจาก BlockingQueue เป็น List ตามที่ออกแบบใหม่

    public JobGenerator(List<Job> jobs, ProjectLogger logger, List<Job> schedulerQueue) {
        super("generator");
        this.jobs = jobs;
        this.logger = logger;
        this.schedulerQueue = schedulerQueue;

        // จัดเรียงลำดับงานตาม field arrivalMs จากน้อยไปมาก
        Collections.sort(this.jobs, Comparator.comparingLong(j -> j.arrivalMs));
    }

    @Override
    public void run() {
        // ใช้ logger.now() แทนการเรียก static แบบเดิม
        long startTime = logger.now();

        try {
            for (Job job : jobs) {
                long elapsedTime = logger.now() - startTime;
                long timeToWait = job.arrivalMs - elapsedTime;

                if (timeToWait > 0) {
                    Thread.sleep(timeToWait);
                }

                // บันทึกเวลาที่เข้าสู่ระบบจริงลงใน Job
                job.actualArrivalMs = logger.now();

                // เรียก logger.jobArrived(job) ทุกครั้งที่ปล่อยงาน
                logger.jobArrived(job);

                // วิธีเอาใส่ schedulerQueue แบบใหม่ (List + Synchronized)
                synchronized(schedulerQueue) {
                    schedulerQueue.add(job);
                    schedulerQueue.notify(); // ส่งสัญญาณไปปลุก Scheduler ที่ติด wait() ให้ตื่นมารับงาน
                }
            }

            // เมื่อปล่อยงานครบทุกชิ้นแล้ว ส่ง null เป็นสัญญาณ (Poison Pill) บอกว่าไม่มีงานเข้ามาอีกแล้ว
            synchronized(schedulerQueue) {
                schedulerQueue.add(null);
                schedulerQueue.notify(); // ปลุก Scheduler มารับค่า null เพื่อรู้ว่าจบการทำงาน
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
