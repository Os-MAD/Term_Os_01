import java.util.concurrent.CountDownLatch;
/**
 * Thread ที่ดึงงานจาก Ready Queue ไปทำจนเสร็จ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ลำดับการทำงานของ Job หนึ่งชิ้น บังคับตามหัวข้อ 6 ของโจทย์:
 *   1. รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
 *   2. จำลองงานหลักด้วย Thread.sleep(job.workMs)
 *   3. ถ้า job.resource != NONE ให้บันทึกเวลาเริ่มรอ แล้ว acquire
 *   4. จำลองการถือครองด้วย Thread.sleep(job.resourceMs)
 *   5. release แล้วบันทึกเวลาจบ
 *
 * ห้ามสลับขั้นที่ 2 กับ 3 เพราะจะทำให้ผลของทุกกลุ่มเทียบกันไม่ได้
 *
 * จุดที่มักพลาด:
 *   - ถ้า exception หรือ interrupt เกิดขึ้นหลัง acquire แต่ก่อน release
 *     permit จะค้างถาวรและระบบจะแขวน ต้องออกแบบให้คืนได้เสมอ
 *   - Worker ต้องหยุดเองได้เมื่อไม่มีงานเหลือแล้ว ไม่ใช่วนรอตลอดไป
 */
public class Worker extends Thread {

    // TODO: เก็บ ReadyQueue, ResourceManager, Statistics และ logger
    String name;
    ReadyQueue readyQueue;
    ResourceManager resources;
    Statistics statistics;
    ProjectLogger logger;
    CountDownLatch completionLatch;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
                  Statistics statistics, ProjectLogger logger, CountDownLatch completionLatch) {
        super(name);
        // TODO
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
        this.completionLatch = completionLatch;
    }

    @Override
    public void run() {
        // TODO: วนรับงานและเรียก processJob จนกว่าจะได้รับสัญญาณให้หยุด
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Job job = readyQueue.take();
                processJob(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** ทำงานหนึ่งชิ้นให้จบตามลำดับ 5 ขั้นด้านบน */
    private void processJob(Job job) throws InterruptedException {
        // TODO
        // startMs เป็นเวลาเริ่มครั้งแรกเท่านั้น ห้ามเขียนทับตอน Job กลับจาก requeue
        if (job.startMs < 0) {
            job.startMs = logger.now();
            logger.jobStarted(job);
        }

        // นับ Worker ที่กำลังประมวลผลทุกครั้งที่หยิบ Job (รวมการกลับจาก requeue)
        statistics.recordstart();
        boolean runningCountHandled = false;

        try {
            if (readyQueue.isMlfq()) {
                // MLFQ: ทำงานได้ไม่เกิน Quantum ของคิวปัจจุบัน
                long quantumMs = readyQueue.getQuantum(job.queueLevel);
                if (quantumMs <= 0) {
                    throw new IllegalStateException("MLFQ quantum must be positive");
                }

                long runMs = Math.min(job.remainingWorkMs, quantumMs);
                if (runMs > 0) {
                    Thread.sleep(runMs);
                }
                job.remainingWorkMs -= runMs;

                logger.systemEvent("MLFQ_SLICE job=" + job.id
                        + " queue=Q" + job.queueLevel
                        + " ran=" + runMs + "ms"
                        + " remaining=" + job.remainingWorkMs + "ms");

                // ยังทำงานหลักไม่เสร็จ: ลดระดับคิว แล้วคืน Job ให้ ReadyQueue
                if (job.remainingWorkMs > 0) {
                    job.queueLevel = Math.min(job.queueLevel + 1, 2); // Q0 -> Q1 -> Q2

                    // Job นี้ไม่อยู่ระหว่างประมวลผลแล้ว แต่ยังไม่ Completed
                    statistics.recordYield();
                    runningCountHandled = true;

                    logger.systemEvent("MLFQ_REQUEUE job=" + job.id
                            + " next=Q" + job.queueLevel
                            + " remaining=" + job.remainingWorkMs + "ms");
                    readyQueue.requeue(job);
                    return;
                }
            } else {
                // FCFS / Priority: ทำงานหลักจนเสร็จตามเดิม
                Thread.sleep(job.workMs);
            }

        
            logger.workFinished(job);

            if (job.resource != ResourceType.NONE) {
                job.resourceWaitStartMs = logger.now();
                logger.resourceWaitStarted(job);

                resources.acquire(job.resource);
                try {
                    job.resourceWaitMs = logger.now() - job.resourceWaitStartMs;
                    logger.resourceAcquired(job, job.resourceWaitMs);
                    Thread.sleep(job.resourceMs);
                } finally {
                    resources.release(job.resource);
                    logger.resourceReleased(job);
                }
            }

            // finishMs ต้องบันทึกหลังใช้ Resource เสร็จ ไม่ใช่หลัง CPU burstๆ
            job.finishMs = logger.now();
            logger.jobCompleted(job);
            statistics.recordCompletion(job);
            runningCountHandled = true;
            completionLatch.countDown(); // นับเพียงครั้งเดียวต่อ Job ที่เสร็จจริง

        } finally {
            // ป้องกัน running ค้าง ถ้า Thread ถูก interrupt ระหว่างทำงาน
            if (!runningCountHandled) {
                statistics.recordYield();
            }
        }
    }
}
