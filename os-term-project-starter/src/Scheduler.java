import java.util.List;

/**
 * รับงานจาก JobGenerator แล้วจัดเข้า Ready Queue
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ (หัวข้อ 2 และ 4):
 *   - Scheduler เป็น Thread บังคับ ห้ามให้ JobGenerator ใส่งานลง Ready Queue โดยตรง
 *   - รับผิดชอบการจัดลำดับตามนโยบาย FCFS หรือ Priority
 *
 * ข้อควรคิด:
 *   - Scheduler รับงานจาก JobGenerator ผ่านอะไร และรอโดยไม่กิน CPU อย่างไร
 *   - เมื่อ JobGenerator ปล่อยงานครบแล้ว Scheduler รู้ได้อย่างไรว่าควรหยุด
 */
public class Scheduler extends Thread {

    // เปลี่ยนจาก BlockingQueue เป็น List
    private final List<Job> schedulerQueue;
    private final ReadyQueue readyQueue;
    private final ProjectLogger logger;

    // TODO: เก็บช่องทางรับงานจาก JobGenerator, ReadyQueue ปลายทาง และ logger
    //
    // หมายเหตุ: constructor ด้านล่างยังไม่มี parameter สำหรับ "ช่องทางรับงาน"
    // ให้เพิ่มเข้าไปให้ตรงกับที่ออกแบบไว้ใน JobGenerator
    // เพิ่ม parameter ได้ แต่อย่าเปลี่ยนชื่อคลาส

    public Scheduler(List<Job> schedulerQueue, ReadyQueue readyQueue, ProjectLogger logger) {
        super("scheduler");
        // TODO
        this.schedulerQueue = schedulerQueue;
        this.readyQueue = readyQueue;
        this.logger = logger;
        
    }

    @Override
    public void run() {
        // TODO: วนรับงานเข้ามาแล้วใส่ ReadyQueue จนกว่าจะได้รับสัญญาณให้หยุด
        try {
            while(true){
                Job job = null;

                // 1. จำเป็นต้องทำการล็อค (Lock) List ก่อนใช้งานทุกครั้งเมื่อทำงานแบบ Multi-threading
                synchronized (schedulerQueue) {
                    // 2. ใช้ while เพื่อเช็คว่า List ว่างหรือไม่ (ป้องกัน Spurious wakeup)
                    while (schedulerQueue.isEmpty()) {
                        // 3. สั่งให้ Thread นี้หยุดพักการทำงาน (Release lock และรอโดยไม่กิน CPU) 
                        // จนกว่า JobGenerator จะเรียก schedulerQueue.notify()
                        schedulerQueue.wait(); 
                    }
                    // 4. เมื่อหลุดจาก wait() แสดงว่ามีงานเข้ามาแล้ว ให้ดึงงานตัวแรกออก (ลบตำแหน่งที่ 0)
                    job = schedulerQueue.remove(0);
                }

                // จบแล้วให้ schedular หยุด
                if(job == JobGenerator.POISON_PILL){
                    break;
                }

                // ส่ง job เข้า Rdy queue
                readyQueue.add(job);
            }

        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
}
