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
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
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
    private final Map<Computer, Long> checkAgainAfterTick = new WeakHashMap<>();
    private final Map<Computer, Boolean> lastOnlineState = new WeakHashMap<>();

    /**
     * Administrators can trade elevated CPU usage from frequent agent checks for responsiveness to capacity needs.
     *
     * Per the docs of {@link RetentionStrategy#check}, it is OK to rechecked earlier or later than requested.
     * Make the startup/teardown more responsive and use the requested minutes as multiples of the configured interval.
     * Setting the interval to 60 seconds (which is the default), retains the original behavior and
     *  one has to wait up-to one minute for an in-demand agent (with inDemandDelay=0min) to start.
     * Setting the interval to 10 seconds provides a more responsive behavior towards load changes and
     *  one has to wait up-to ten seconds for an in-demand agent (with inDemandDelay=0min) to start.
     */
    private static final long checkIntervalSeconds = SystemProperties.getLong(ComputerRetentionWork.class.getName() + ".checkIntervalSeconds", TimeUnit.MINUTES.toSeconds(1));

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(checkIntervalSeconds);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doRun() {
        for (final Computer c : Jenkins.get().getComputers()) {
            Queue.withLock(() -> {
                Node n = c.getNode();
                // Do not check newly created agents until saved.
                if (n != null && n.isHoldOffLaunchUntilSave()) return;

                long remainingTicksUntilNextCheck = checkAgainAfterTick.getOrDefault(c, 0L) - 1;
                final boolean stateExpired = remainingTicksUntilNextCheck <= 0;

                // When a node transitions into running state, some strategies return very high teardown delays.
                // For example RetentionStrategy.Demand returns the remaining idleDelay in O(minutes).
                // When a node disconnects (aka crashes), this loop should not wait until after the specified
                //  teardown delay has passed. Instead, it should bring it back up immediately when in-demand.
                final boolean isOnline = c.isOnline();
                final boolean stateChanged = !lastOnlineState.containsKey(c) || isOnline != lastOnlineState.get(c);

                if (stateExpired || stateChanged) {
                    final long requestedDelayInMinutes = c.getRetentionStrategy().check(c);
                    // Keep the delay within reasonable limits: one minute <= delay <= one hour.
                    remainingTicksUntilNextCheck = Math.max(1, Math.min(60, requestedDelayInMinutes));
                }

                lastOnlineState.put(c, isOnline);
                checkAgainAfterTick.put(c, remainingTicksUntilNextCheck);
            });
        }
    }
}
