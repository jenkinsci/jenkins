/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.slaves;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.Symbol;

/**
 * Periodically checks the agents and try to reconnect dead agents.
 *
 * @author Kohsuke Kawaguchi
 * @author Stephen Connolly
 */
@Extension @Symbol("computerRetention")
public class ComputerRetentionWork extends PeriodicWork {

    /**
     * Use weak hash map to avoid leaking {@link Computer}.
     */
    private final Map<Computer, Long> nextCheck = new WeakHashMap<>();

    private ScheduledFuture<?> pending;

    @Override
    protected void schedulePeriodicWork() {
        pending = Timer.get().scheduleAtFixedRate(this, this.getInitialDelay(), this.getRecurrencePeriod(), TimeUnit.MILLISECONDS);
    }

    /**
     * Restart the periodic check.
     * The previous run may still be pending. We do not need to wait for its completion as all calls of
     * {@link RetentionStrategy#check} inside the runner are performed in a queue with a lock.
     * Per the docs on {@link RetentionStrategy#check}, it is OK to recheck earlier or later than requested.
     *
     * @since TODO
     */
    public synchronized void restart() {
        if (pending != null) {
            pending.cancel(false);
        }
        schedulePeriodicWork();
    }

    @Override
    public long getRecurrencePeriod() {
        return Jenkins.get().getComputerRetentionCheckInterval() * 1000L;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doRun() {
        final long startRun = System.currentTimeMillis();
        for (final Computer c : Jenkins.get().getComputers()) {
            Queue.withLock(new Runnable() {
                @Override
                public void run() {
                    Node n = c.getNode();
                    if (n != null && n.isHoldOffLaunchUntilSave())
                        return;
                    if (!nextCheck.containsKey(c) || startRun > nextCheck.get(c)) {
                        // at the moment I don't trust strategies to wait more than 60 minutes
                        final long waitInMins = Math.max(0, Math.min(60, c.getRetentionStrategy().check(c)));
                        nextCheck.put(c, startRun + TimeUnit.MINUTES.toMillis(waitInMins));
                    }
                }
            });
        }
    }
}
