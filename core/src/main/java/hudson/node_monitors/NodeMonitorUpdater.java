package hudson.node_monitors;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.util.DaemonThreadFactory;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * When a slave is connected, redo the node monitoring.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NodeMonitorUpdater extends ComputerListener {
    // TODO: shutdown hook to kill off this timer
    private final ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(1,new DaemonThreadFactory());

    private volatile long timestamp;

    /**
     * Triggers the update with 5 seconds quiet period, to avoid triggering data check too often
     * when multiple slaves become online at about the same time.
     */
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        timestamp = System.currentTimeMillis();
        timer.schedule(new Runnable() {
            public void run() {
                if (System.currentTimeMillis()-timestamp<4000)
                    return;

                for (NodeMonitor nm : Jenkins.getInstance().getComputer().getMonitors()) {
                    nm.triggerUpdate();
                }
            }
        }, 5, TimeUnit.SECONDS);
    }
}
