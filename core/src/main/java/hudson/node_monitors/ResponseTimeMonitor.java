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


import hudson.Extension;
import hudson.model.Computer;
import hudson.remoting.Callable;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Monitors the round-trip response time to this slave.
 *
 * @author Kohsuke Kawaguchi
 */
public class ResponseTimeMonitor extends NodeMonitor {
    @Extension
    public static final AbstractNodeMonitorDescriptor<Data> DESCRIPTOR = new AbstractAsyncNodeMonitorDescriptor<Data>() {
        @Override
        protected Callable<Data,IOException> createCallable(Computer c) {
            return new Step1(get(c));
        }

        @Override
        protected Map<Computer, Data> monitor() throws InterruptedException {
            Map<Computer, Data> base = super.monitor();
            for (Entry<Computer, Data> e : base.entrySet()) {
                Computer c = e.getKey();
                Data d = e.getValue();
                if (d ==null) {
                    // if we failed to monitor, put in the special value that indicates a failure
                    e.setValue(d=new Data(get(c),-1L));
                }

                if(d.hasTooManyTimeouts() && !isIgnored()) {
                    // unlike other monitors whose failure still allow us to communicate with the slave,
                    // the failure in this monitor indicates that we are just unable to make any requests
                    // to this slave. So we should severe the connection, as opposed to marking it temporarily
                    // off line, which still keeps the underlying channel open.
                    c.disconnect(d);
                    LOGGER.warning(Messages.ResponseTimeMonitor_MarkedOffline(c.getName()));
                }
            }
            return base;
        }

        public String getDisplayName() {
            return Messages.ResponseTimeMonitor_DisplayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ResponseTimeMonitor();
        }
    };

    private static final class Step1 extends MasterToSlaveCallable<Data,IOException> {
        private Data cur;

        private Step1(Data cur) {
            this.cur = cur;
        }

        public Data call() {
            // this method must be being invoked locally, which means the roundtrip time is zero and zero forever
            return new Data(cur,0);
        }

        private Object writeReplace() {
            return new Step2(cur);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class Step2 extends MasterToSlaveCallable<Step3,IOException> {
        private final Data cur;
        private final long start = System.currentTimeMillis();

        public Step2(Data cur) {
            this.cur = cur;
        }

        public Step3 call() {
            // this method must be being invoked locally, which means the roundtrip time is zero and zero forever
            return new Step3(cur,start);
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
            return new Data(cur,(end-start));
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
            if(old==null)
                past5 = new long[] {newDataPoint};
            else {
                past5 = new long[Math.min(5,old.past5.length+1)];
                int copyLen = past5.length - 1;
                System.arraycopy(old.past5, old.past5.length-copyLen, this.past5, 0, copyLen);
                past5[past5.length-1] = newDataPoint;
            }
        }

        /**
         * Computes the recurrence of the time out
         */
        private int failureCount() {
            int cnt=0;
            for(int i=past5.length-1; i>=0 && past5[i]<0; i--, cnt++)
                ;
            return cnt;
        }

        /**
         * Computes the average response time, by taking the time out into account.
         */
        @Exported
        public long getAverage() {
            long total=0;
            for (long l : past5) {
                if(l<0)     total += TIMEOUT;
                else        total += l;
            }
            return total/past5.length;
        }

        public boolean hasTooManyTimeouts() {
            return failureCount()>=5;
        }

        /**
         * String rendering of the data
         */
        @Override
        public String toString() {
//            StringBuilder buf = new StringBuilder();
//            for (long l : past5) {
//                if(buf.length()>0)  buf.append(',');
//                buf.append(l);
//            }
//            return buf.toString();
            int fc = failureCount();
            if(fc>0)
                return Messages.ResponseTimeMonitor_TimeOut(fc);
            return getAverage()+"ms";
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
