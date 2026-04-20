package jenkins.model;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;

/**
 * Keeps track of the uptime of Jenkins.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.538
 */
@Extension
public class Uptime {
    private long startTime;
    private long startNanos;

    /**
     * Timestamp in which Jenkins became fully up and running.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Duration in milliseconds since Jenkins became available
     * @return uptime in milliseconds
     */
    public long getUptime() {
        return (System.nanoTime() - startNanos) / 1000000L;
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void init() {
        startTime = System.currentTimeMillis();
        startNanos = System.nanoTime();
    }
}
