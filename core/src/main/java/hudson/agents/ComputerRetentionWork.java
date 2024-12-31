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

package hudson.agents;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AperiodicWork;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Queue;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import jenkins.model.GlobalComputerRetentionCheckIntervalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * Periodically checks the agents and try to reconnect dead agents.
 *
 * @author Kohsuke Kawaguchi
 * @author Stephen Connolly
 */
@Extension @Symbol("computerRetention")
public class ComputerRetentionWork extends AperiodicWork {

    /**
     * Use weak hash map to avoid leaking {@link Computer}.
     */
    private final Map<Computer, Long> nextCheck = new WeakHashMap<>();

    @Override
    public long getRecurrencePeriod() {
        return ExtensionList.lookupSingleton(GlobalComputerRetentionCheckIntervalConfiguration.class).getComputerRetentionCheckInterval() * 1000L;
    }

    @Override
    public AperiodicWork getNewInstance() {
        // ComputerRetentionWork is a singleton.
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doAperiodicRun() {
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
