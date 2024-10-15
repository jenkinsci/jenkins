package hudson.node_monitors;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.util.Futures;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.Timer;

/**
 * When an agent is connected, redo the node monitoring.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NodeMonitorUpdater extends ComputerListener {

    private static final Runnable MONITOR_UPDATER = new Runnable() {
        @Override
        public void run() {
            for (NodeMonitor nm : ComputerSet.getMonitors()) {
                nm.triggerUpdate();
            }
        }
    };

    private static final Runnable MARKEDOFFLINE_UPDATER = new Runnable() {
        @Override
        public void run() {
            MonitorMarkedNodeOffline no = AdministrativeMonitor.all().get(MonitorMarkedNodeOffline.class);
            if (no != null) {
                boolean markedOffline = false;
                for (Computer c : Jenkins.get().getComputers()) {
                    if (c.getChannel() != null && c.getOfflineCause() instanceof MonitorOfflineCause) {
                        markedOffline = true;
                        break;
                    }
                }
                no.active = markedOffline;
            }
        }
    };

    private Future<?> future = Futures.precomputed(null);

    /**
     * Triggers the update with 5 seconds quiet period, to avoid triggering data check too often
     * when multiple agents become online at about the same time.
     */
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        synchronized (this) {
            future.cancel(false);
            future = Timer.get().schedule(MONITOR_UPDATER, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onTemporarilyOnline(Computer c) {
        synchronized (this) {
            future.cancel(false);
            future = Timer.get().schedule(MARKEDOFFLINE_UPDATER, 5, TimeUnit.SECONDS);
        }
    }
}
