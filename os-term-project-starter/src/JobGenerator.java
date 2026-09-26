import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

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

    // TODO: เก็บรายการงาน, ช่องทางส่งงานไปยัง Scheduler และ logger
    //
    // หมายเหตุ: constructor ด้านล่างยังไม่มี parameter สำหรับ "ช่องทางส่งงาน"
    // เพราะเป็นสิ่งที่กลุ่มต้องออกแบบเอง (หัวข้อ 2 ห้ามให้ JobGenerator
    // ใส่งานลง ReadyQueue โดยตรง ต้องผ่าน Scheduler เสมอ)
    // ให้เพิ่ม parameter เข้าไปตามที่ออกแบบ เช่น BlockingQueue<Job>
    // หรือคลาสของกลุ่มเอง — เพิ่ม parameter ได้ แต่อย่าเปลี่ยนชื่อคลาส

    private final List<Job> jobs;
    private final ProjectLogger logger;
    private final BlockingQueue<Job> schedulerQueue;

    public JobGenerator(List<Job> jobs, ProjectLogger logger, BlockingQueue<Job> schedulerQueue) {
        super("generator");
        // TODO
        this.jobs = jobs;
        this.logger = logger;
        this.schedulerQueue = schedulerQueue;

        // จัดเรียงลำดับงานตาม field arrivalMs จากน้อยไปมาก
        Collections.sort(this.jobs, Comparator.comparingLong(j -> j.arrivalMs));
    }

    @Override
    public void run() {
        // TODO: วนปล่อยงานตามเวลา แล้วแจ้งเมื่อปล่อยครบ
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

                // ส่งงานต่อไปยัง Scheduler ผ่าน Queue ที่ออกแบบไว้
                schedulerQueue.put(job);
            }

            // เมื่อปล่อยงานครบทุกชิ้นแล้ว ส่ง null เป็นสัญญาณ (Poison Pill) บอกว่าไม่มีงานเข้ามาอีกแล้ว
            schedulerQueue.put(null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
