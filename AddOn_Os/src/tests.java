import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class tests {
    public static void main(String[] args) throws Exception {

        // 1) เตรียมระบบ: คิว MLFQ, ตัวนับสถิติ, Resource และ Logger
        ProjectLogger logger = new ProjectLogger();
        ReadyQueue queue = new ReadyQueue(Config.Policy.MLFQ, logger);
        Statistics stats = new Statistics();
        ResourceManager resources = new ResourceManager(1, 1);

        // 2) อ่าน Time Quantum ของ Q0, Q1, Q2
        long q0 = queue.getQuantum(0), q1 = queue.getQuantum(1), q2 = queue.getQuantum(2);

        // 3) สร้าง Job ทดสอบ 3 แบบ: สั้น / ยาว / ใช้ PRINTER
        Job[] jobs = {
            new Job("SHORT", 0, 1, Math.min(80, q0), ResourceType.NONE, 0, 0),
            new Job("LONG", 0, 2, q0 + q1 + q2 + 1, ResourceType.NONE, 0, 1),
            new Job("PRINTER", 0, 3, q0 + 1, ResourceType.PRINTER, 120, 2)
        };

        // 4) สร้าง Worker และตัวรอให้ทั้ง 3 Job เสร็จ
        CountDownLatch done = new CountDownLatch(jobs.length);
        Worker worker = new Worker("worker-1", queue, resources, stats, logger, done);

        // 5) ส่ง Job ทุกตัวเข้า ReadyQueue
        for (Job job : jobs) {
            job.actualArrivalMs = logger.now();
            queue.add(job);
            logger.jobArrived(job);
        }

        // 6) เริ่ม Worker และรอผล (มี Timeout ป้องกันรอไม่สิ้นสุด)
        worker.start();
        boolean finished;
        try {
            finished = done.await(Math.max(10000, q0 + q1 + q2 + q0 + 5000),
                    TimeUnit.MILLISECONDS);
        } finally {
            // 7) หยุด Worker เมื่อทดสอบจบหรือหมดเวลารอ
            worker.interrupt();
            worker.join();
        }

        // 8) อ่านสถานะสุดท้าย: งานรอ . งานกำลังทำ. งานเสร็จ
        int ready = queue.size();
        int running = stats.runningCount();
        int completed = stats.completedCount();
        System.out.printf("RESULT ready=%d running=%d completed=%d%n",
                ready, running, completed);

        // 9) ตรวจผล: ทุกงานเสร็จ, คิวว่าง, ไม่มี Running ค้าง
        // SHORT ต้องจบใน Q0; LONG ต้องลงถึง Q2; PRINTER ต้องใช้ Resource
        if (!finished || ready != 0 || running != 0 || completed != 3
                || jobs[0].queueLevel != 0 || jobs[1].queueLevel != 2
                || jobs[2].queueLevel < 1 || jobs[2].resourceWaitStartMs < 0) {
            throw new AssertionError("MLFQ test failed");
        }

        // 10) ทุกเงื่อนไขผ่าน
        System.out.println("PASS: short / long / resource");
    }
}
