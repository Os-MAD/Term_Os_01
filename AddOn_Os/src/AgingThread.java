
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
        while (!isInterrupted()) {
            try {
                Thread.sleep(agingIntervalMs);

                readyQueue.applyAging();

                logger.systemEvent("AGING applied");

            } catch (InterruptedException e) {
                interrupt();
                break;
            }
        }
    }

    public void shutdown() {
        interrupt();
    }
}
