public class AgingThread extends Thread {

    private final ReadyQueue readyQueue;
    private final ProjectLogger logger;

    private final long agingIntervalMs;

    public AgingThread(
            ReadyQueue readyQueue,
            ProjectLogger logger,
            long agingIntervalMs) {

        super("aging");

        this.readyQueue = readyQueue;
        this.logger = logger;
        this.agingIntervalMs = agingIntervalMs;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(agingIntervalMs);
                readyQueue.applyAging(agingIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown() {
        interrupt();
    }
}
