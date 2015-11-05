/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import hudson.model.LoadStatistics;
import hudson.slaves.NodeProvisioner.NodeProvisionerInvoker;
import org.jvnet.hudson.test.JenkinsRule;

/** Overrides {@link LoadStatistics#CLOCK}, {@link NodeProvisionerInvoker#INITIALDELAY}, and/or {@link NodeProvisionerInvoker#RECURRENCEPERIOD} during the test. */
public class NodeProvisionerRule extends JenkinsRule {

    private final int clock;
    private int clockOrig;
    private final int initialDelay;
    private int initialDelayOrig;
    private final int recurrencePeriod;
    private int recurrencePeriodOrig;

    public NodeProvisionerRule(int clock, int initialDelay, int recurrencePeriod) {
        this.clock = clock;
        this.initialDelay = initialDelay;
        this.recurrencePeriod = recurrencePeriod;
    }

    @Override public void before() throws Throwable {
        clockOrig = LoadStatistics.CLOCK;
        initialDelayOrig = NodeProvisionerInvoker.INITIALDELAY;
        recurrencePeriodOrig = NodeProvisionerInvoker.RECURRENCEPERIOD;
        if (clock != -1) {
            LoadStatistics.CLOCK = clock;
        }
        if (initialDelay != -1) {
            NodeProvisionerInvoker.INITIALDELAY = initialDelay;
        }
        if (recurrencePeriod != -1) {
            NodeProvisionerInvoker.RECURRENCEPERIOD = recurrencePeriod;
        }
        super.before();
    }

    @Override public void after() throws Exception {
        super.after();
        // TODO should we really restore prior values? That makes tests using this rule not safe to run concurrently. Should rather have Configuration be per-Jenkins.
        LoadStatistics.CLOCK = clockOrig;
        NodeProvisionerInvoker.INITIALDELAY = initialDelayOrig;
        NodeProvisionerInvoker.RECURRENCEPERIOD = recurrencePeriodOrig;
    }

}
