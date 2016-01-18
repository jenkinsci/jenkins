package hudson.slaves;

import hudson.Extension;
import hudson.remoting.Channel;
import jenkins.slaves.PingFailureAnalyzer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * When the ping thread times out perform a thread dump on the slave machine
 * to diagnose what operations were happening on the slave machine when communication
 * between the slave and master stopped.
 *
 * @author christ66
 */
@Extension
public class ThreadDumpPingTimeout extends PingFailureAnalyzer {
    @Override
    public void onPingFailure(Channel c, Throwable cause) throws IOException {
        if (THREAD_DUMP_PING_TIMEOUT) return;

        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(), mbean.isSynchronizerUsageSupported());
        long[] deadLocks = mbean.findDeadlockedThreads();
        ThreadInfo[] deadlockThreads = mbean.getThreadInfo(deadLocks);

        LOGGER.log(Level.SEVERE, "Thread dump:\n" + Arrays.toString(threads));
        LOGGER.log(Level.SEVERE, "Deadlocked threads: " + Arrays.toString(deadlockThreads));
    }

    public static boolean THREAD_DUMP_PING_TIMEOUT = Boolean.getBoolean(ThreadDumpPingTimeout.class.getName());

    private static final Logger LOGGER = Logger.getLogger(ThreadDumpPingTimeout.class.getName());
}
