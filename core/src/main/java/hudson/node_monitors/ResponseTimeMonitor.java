/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc.
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

package hudson.node_monitors;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Callable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.logging.Logger;
import jenkins.security.MasterToAgentCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Monitors the round-trip response time to this agent.
 *
 * @author Kohsuke Kawaguchi
 */
public class ResponseTimeMonitor extends NodeMonitor {

    @DataBoundConstructor
    public ResponseTimeMonitor() {
    }

    @SuppressFBWarnings(value = "MS_PKGPROTECT", justification = "for backward compatibility")
    public static /*almost final*/ AbstractNodeMonitorDescriptor<Data> DESCRIPTOR;

    @Extension
    @Symbol("responseTime")
    public static class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<Data> {

        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "for backward compatibility")
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        @Override
        protected Callable<Data, IOException> createCallable(Computer c) {
            return new Step1(get(c));
        }

        @Override
        protected Map<Computer, Data> monitor() throws InterruptedException {
            Result<Data> base = monitorDetailed();
            Map<Computer, Data> monitoringData = base.getMonitoringData();
            for (Map.Entry<Computer, Data> e : monitoringData.entrySet()) {
                Computer c = e.getKey();
                Data d = e.getValue();
                if (base.getSkipped().contains(c)) {
                    assert d == null;
                    continue;
                }

                if (d == null) {
                    // if we failed to monitor, put in the special value that indicates a failure
                    e.setValue(d = new Data(get(c), -1L));
                }

                if (d.hasTooManyTimeouts() && !isIgnored()) {
                    // unlike other monitors whose failure still allow us to communicate with the agent,
                    // the failure in this monitor indicates that we are just unable to make any requests
                    // to this agent. So we should severe the connection, as opposed to marking it temporarily
                    // off line, which still keeps the underlying channel open.
                    c.disconnect(d);
                    LOGGER.warning(Messages.ResponseTimeMonitor_MarkedOffline(c.getName()));
                }
            }
            return monitoringData;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ResponseTimeMonitor_DisplayName();
        }
    }

    private static final class Step1 extends MasterToAgentCallable<Data, IOException> {
        private Data cur;

        private Step1(Data cur) {
            this.cur = cur;
        }

        @Override
        public Data call() {
            // this method must be being invoked locally, which means the roundtrip time is zero and zero forever
            return new Data(cur, 0);
        }

        private Object writeReplace() {
            return new Step2(cur);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class Step2 extends MasterToAgentCallable<Step3, IOException> {
        private final Data cur;
        private final long start = System.currentTimeMillis();

        Step2(Data cur) {
            this.cur = cur;
        }

        @Override
        public Step3 call() {
            // this method must be being invoked locally, which means the roundtrip time is zero and zero forever
            return new Step3(cur, start);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class Step3 implements Serializable {
        private final Data cur;
        private final long start;

        private Step3(Data cur, long start) {
            this.cur = cur;
            this.start = start;
        }

        private Object readResolve() {
            long end = System.currentTimeMillis();
            return new Data(cur, end - start);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Immutable representation of the monitoring data.
     */
    @ExportedBean
    public static final class Data extends MonitorOfflineCause implements Serializable {
        /**
         * Record of the past 5 times. -1 if time out. Otherwise in milliseconds.
         * Old ones first.
         */
        private final long[] past5;

        private Data(Data old, long newDataPoint) {
            if (old == null)
                past5 = new long[] {newDataPoint};
            else {
                past5 = new long[Math.min(5, old.past5.length + 1)];
                int copyLen = past5.length - 1;
                System.arraycopy(old.past5, old.past5.length - copyLen, this.past5, 0, copyLen);
                past5[past5.length - 1] = newDataPoint;
            }
        }

        /**
         * Computes the recurrence of the time out
         */
        private int failureCount() {
            int cnt = 0;
            //noinspection StatementWithEmptyBody
            for (int i = past5.length - 1; i >= 0 && past5[i] < 0; i--, cnt++)
                ;
            return cnt;
        }

        /**
         * Computes the average response time, by taking the time out into account.
         */
        @Exported
        public long getAverage() {
            long total = 0;
            for (long l : past5) {
                if (l < 0)     total += TIMEOUT;
                else        total += l;
            }
            return total / past5.length;
        }

        public boolean hasTooManyTimeouts() {
            return failureCount() >= 5;
        }

        /**
         * String rendering of the data
         */
        @Override
        public String toString() {
            int fc = failureCount();
            if (fc > 0)
                return Messages.ResponseTimeMonitor_TimeOut(fc);
            return getAverage() + "ms";
        }

        @Override
        public Class<? extends NodeMonitor> getTrigger() {
            return ResponseTimeMonitor.class;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Time out interval in milliseconds.
     */
    private static final long TIMEOUT = 5000;

    private static final Logger LOGGER = Logger.getLogger(ResponseTimeMonitor.class.getName());
}
