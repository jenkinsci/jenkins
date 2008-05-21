package hudson.slaves;

import java.util.Map;
import java.util.WeakHashMap;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.triggers.SafeTimerTask;

/**
 * Periodically checks the slaves and try to reconnect dead slaves.
 *
 * @author Kohsuke Kawaguchi
 * @author Stephen Connolly
 */
public class ComputerRetentionWork extends SafeTimerTask {

    /**
     * Use weak hash map to avoid leaking {@link Computer}.
     */
    private final Map<Computer, Long> nextCheck = new WeakHashMap<Computer, Long>();

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    protected void doRun() {
        final long startRun = System.currentTimeMillis();
        for (Computer c : Hudson.getInstance().getComputers()) {
            if (!nextCheck.containsKey(c) || startRun > nextCheck.get(c)) {
                // at the moment I don't trust strategies to wait more than 60 minutes
                // strategies need to wait at least one minute
                final long waitInMins = Math.min(1, Math.max(60, c.getRetentionStrategy().check(c)));
                nextCheck.put(c, startRun + waitInMins*1000*60 /*MINS->MILLIS*/);
            }
        }
    }
}
