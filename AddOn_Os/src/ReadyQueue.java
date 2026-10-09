
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ReadyQueue {

    private final Config.Policy policy;
    private final ProjectLogger logger;

    private final List<Job> queue = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    // เรียง Priority น้อยไปมาก เพราะ Priority 1 สำคัญที่สุด
    private final Comparator<Job> priorityComparator =
            Comparator.comparingInt((Job job) -> job.adjustedPriority)
                    .thenComparingLong(job -> job.arrivalMs)
                    .thenComparingInt(job -> job.sequence);

    public ReadyQueue(Config.Policy policy) {
        this(policy, null);
    }

    public ReadyQueue(Config.Policy policy,ProjectLogger logger) {
        this.policy = policy;
        this.logger = logger;
    }

    // เพิ่ม Job เข้าคิว
    public void add(Job job) {
        lock.lock();

        try {
            // บันทึกเวลาที่ Job เข้าสู่ ReadyQueue
            job.readyQueueEntryMs = nowMs();
            job.adjustedPriority = job.priority;

            queue.add(job);
            notEmpty.signal();

        } finally {
            lock.unlock();
        }
    }

    // ดึง Job ถัดไป โดย Worker จะรอหากคิวว่าง
    public Job take() throws InterruptedException {
        lock.lockInterruptibly();

        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }

            if (policy == Config.Policy.FCFS) {
                // FCFS: งานที่เข้าคิวก่อนจะได้ทำก่อน
                return queue.remove(0);
            }

            // Priority: เลือก Job ที่มี Priority สูงสุด
            int bestIndex = 0;

            for (int i = 1; i < queue.size(); i++) {
                if (priorityComparator.compare(
                        queue.get(i), queue.get(bestIndex)) < 0) {
                    bestIndex = i;
                }
            }

            return queue.remove(bestIndex);

        } finally {
            lock.unlock();
        }
    }

    // AgingThread เรียกเมธอดนี้ตามช่วงเวลาที่กำหนด
    public void applyAging(long agingIntervalMs) {
        if (policy != Config.Policy.PRIORITY) {
            return;
        }

        if (agingIntervalMs <= 0) {
            throw new IllegalArgumentException("agingIntervalMs must be positive");
        }

        lock.lock();

        try {
            long now = nowMs();

            for (Job job : queue) {
                long waitingMs = Math.max(0, now - job.readyQueueEntryMs);

                int agingLevels = (int) Math.min(waitingMs / agingIntervalMs,(long) job.priority - 1);

                int newPriority = job.priority - agingLevels;

                if (job.adjustedPriority != newPriority) {
                    int oldPriority = job.adjustedPriority;
                    job.adjustedPriority = newPriority;

                    logger.systemEvent("AGING applied: " + job.id + " priority " + oldPriority + " -> " + newPriority);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();

        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    private long nowMs() {
        return logger != null
                ? logger.now()
                : System.currentTimeMillis();
    }
}
