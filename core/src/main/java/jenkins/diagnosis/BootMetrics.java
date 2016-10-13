package jenkins.diagnosis;

import hudson.init.InitMilestone;
import hudson.init.InitReactorListener;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Task;
import org.kohsuke.MetaInfServices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Record system metrics during jenkins boot, to help diagnose performance issues.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@MetaInfServices
public class BootMetrics implements InitReactorListener {

    @Override
    public void onTaskStarted(Task t) {
    }

    @Override
    public void onTaskCompleted(Task t) {
    }

    @Override
    public void onTaskFailed(Task t, Throwable err, boolean fatal) {
    }

    @Override
    public void onAttained(Milestone milestone) {
        final String name = milestone.toString();
        if (name != null) out.println(System.currentTimeMillis() + " > " + name);
        if (milestone == InitMilestone.COMPLETED) {
            timer.cancel();
            out.close();
        }
    }


    private SystemMetrics metrics;
    private final PrintStream out;
    private final Timer timer;

    public BootMetrics() throws FileNotFoundException {

        final File log = new File(Jenkins.getInstance().getRootDir(), "logs/boot-metrics.log");
        log.getParentFile().mkdirs();
        out = new PrintStream(log);
        timer = new Timer("Boot metrics", true);

        // Collect system metrics every second
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                final SystemMetrics m = getSystemMetrics();
                if (metrics != null) {
                    SystemMetrics prev = metrics;

                    float period = (m.time - prev.time) / 1000f;
                    float cpupercent = (m.cputime - prev.cputime) / CPUS * period;
                    float iowait = m.iowait - prev.iowait;
                    long read_bytes = m.read_bytes - prev.read_bytes;
                    out.println(MessageFormat.format("{0,number,#} cpu:{1,number,#.##%} iowait:{2,number,#.###}% read_bytes:{3}",
                                                     m.time, cpupercent, iowait, read_bytes));
                }
                metrics = m;
            }
        }, 0, 1000);
    }

    public static final int CPUS = Runtime.getRuntime().availableProcessors();
    public static final File SELF_STAT = new File("/proc/self/stat");
    public static final File SELF_IO = new File("/proc/self/io");
    public static final File STAT = new File("/proc/stat");
    public static final float Hertz = 100f;

    /**
     * TODO Hertz=100 on standard Linux but might be another value. Can estimate using
     * see https://gitlab.com/procps-ng/procps/blob/master/proc/sysinfo.c#L210
     */

    /**
     * Collect system metrics from /proc
     * see http://man7.org/linux/man-pages/man5/proc.5.html
     */
    private static SystemMetrics getSystemMetrics() {
        if (!System.getProperty("os.name").toLowerCase().startsWith("linux")) {
            // Not running on a Linux system
            return null;
        }
        try {
            SystemMetrics m = new SystemMetrics();

            final String stat = FileUtils.readFileToString(SELF_STAT);
            String[] s = stat.split(" ");
            long utime = Long.parseLong(s[13]) + Long.parseLong(s[15]); // user-space time in jiffies
            long stime = Long.parseLong(s[14]) + Long.parseLong(s[16]); // kernel-space time in jiffies
            m.cputime = (utime + stime) / Hertz;
            m.time = System.currentTimeMillis();
            s = FileUtils.readFileToString(STAT).split(" ");
            m.iowait = Long.parseLong(s[5]) / Hertz;

            if (SELF_IO.exists() && SELF_IO.canRead()) {
                final LineIterator lineIterator = FileUtils.lineIterator(SELF_IO);
                while (lineIterator.hasNext()) {
                    final String line = lineIterator.next();
                    if (line.startsWith("read_bytes: ")) {
                        m.read_bytes = Long.parseLong(line.substring(12));
                    }
                }
            }
            return m;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private static class SystemMetrics {

        /** timestamp (in milliseconds) */
        long time;

        /** CPU time (in seconds) */
        float cputime;

        /** Time waiting for I/O to complete (in seconds) */
        float iowait;

        /** Bytes read from the storage layer */
        public long read_bytes;
    }

}
