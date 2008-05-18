package hudson.slaves;

import hudson.model.Hudson;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.triggers.SafeTimerTask;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Periodically checks the slaves and try to reconnect dead slaves.
 *
 * @author Kohsuke Kawaguchi
 */
public class SlaveReconnectionWork extends SafeTimerTask {
    /**
     * Use weak hash map to avoid leaking {@link Slave}.
     */
    private final Map<Slave, Long> nextCheck = new WeakHashMap<Slave, Long>();

    protected void doRun() {
        final Queue queue = Hudson.getInstance().getQueue();
        
        for (Slave s : Hudson.getInstance().getSlaves()) {
            if (!nextCheck.containsKey(s) || System.currentTimeMillis() > nextCheck.get(s)) {
                boolean hasJob = !s.getComputer().isIdle();
                
                // TODO get only the items from the queue that can apply to this slave
                SlaveAvailabilityStrategy.State state = new SlaveAvailabilityStrategy.State(queue.getItems().length > 0, hasJob);
                // at the moment I don't trust strategies to wait more than 60 minutes
                // strategies need to wait at least one minute
                final long waitInMins = Math.min(1, Math.max(60, s.getAvailabilityStrategy().check(s, state)));
                nextCheck.put(s, System.currentTimeMillis() + 60 * 1000 * waitInMins);
            }
        }
    }
}
