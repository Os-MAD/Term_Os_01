// วางไฟล์นี้ที่: src/CpuUtil.java
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class CpuUtil {
    private static final OperatingSystemMXBean osBean = 
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    /**
     * ดึง % การใช้งาน CPU ของโปรแกรม Java (0.0% - 100.0%)
     */
    public static double getProcessCpuLoad() {
        double load = osBean.getProcessCpuLoad();
        return (load < 0) ? 0.0 : load * 100.0;
    }
}