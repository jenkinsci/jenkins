package hudson.node_monitors;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.util.Futures;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jenkins.util.Timer;

/**
 * When a slave is connected, redo the node monitoring.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NodeMonitorUpdater extends ComputerListener {

    private static final Runnable MONITOR_UPDATER = new Runnable() {
        @Override
        public void run() {
            for (NodeMonitor nm : Jenkins.getInstance().getComputer().getMonitors()) {
                nm.triggerUpdate();
            }
        }
    };

    private Future<?> future = Futures.precomputed(null);

    /**
     * Triggers the update with 5 seconds quiet period, to avoid triggering data check too often
     * when multiple slaves become online at about the same time.
     */
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        synchronized(this) {
            future.cancel(false);
            future = Timer.get().schedule(MONITOR_UPDATER, 5, TimeUnit.SECONDS);
        }
    }
}
