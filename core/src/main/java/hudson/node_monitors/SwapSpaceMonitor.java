/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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

import hudson.Util;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Computer;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jvnet.hudson.MemoryMonitor;
import org.jvnet.hudson.MemoryUsage;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * Checks the swap space availability.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.233
 */
public class SwapSpaceMonitor extends NodeMonitor {
    /**
     * Returns the HTML representation of the space.
     */
    public String toHtml(MemoryUsage usage) {
        if(usage.availableSwapSpace==-1)
            return "N/A";

       String humanReadableSpace = Functions.humanReadableByteSize(usage.availableSwapSpace);
       
        long free = usage.availableSwapSpace;
        free/=1024L;   // convert to KB
        free/=1024L;   // convert to MB
        if(free>256 || usage.totalSwapSpace<usage.availableSwapSpace*5)
            return humanReadableSpace; // if we have more than 256MB free or less than 80% filled up, it's OK

        // Otherwise considered dangerously low.
        return Util.wrapToErrorSpan(humanReadableSpace);
    }

    public long toMB(MemoryUsage usage) {
        if(usage.availableSwapSpace==-1)
            return -1;

        long free = usage.availableSwapSpace;
        free/=1024L;   // convert to KB
        free/=1024L;   // convert to MB
        return free;
    }

    @Override
    public String getColumnCaption() {
        // Hide this column from non-admins
        return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER) ? super.getColumnCaption() : null;
    }

    /**
     * @deprecated as of 2.0
     *      use injection
     */
    public static /*almost final*/ AbstractNodeMonitorDescriptor<MemoryUsage> DESCRIPTOR;

    @Extension @Symbol("swapSpace")
    public static class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<MemoryUsage> {
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        @Override
        protected MonitorTask createCallable(Computer c) {
            return new MonitorTask();
        }

        public String getDisplayName() {
            return Messages.SwapSpaceMonitor_DisplayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new SwapSpaceMonitor();
        }
    };

    /**
     * Obtains the string that represents the architecture.
     */
    private static class MonitorTask extends MasterToSlaveCallable<MemoryUsage,IOException> {
        public MemoryUsage call() throws IOException {
            MemoryMonitor mm;
            try {
                mm = MemoryMonitor.get();
            } catch (IOException e) {
                return report(e);
            } catch (LinkageError e) { // JENKINS-15796
                return report(e);
            }
            return new MemoryUsage2(mm.monitor());
        }

        private <T extends Throwable> MemoryUsage report(T e) throws T {
            if (!warned) {
                warned = true;
                throw e;
            } else { // JENKINS-2194
                return null;
            }
        }

        private static final long serialVersionUID = 1L;

        private static boolean warned = false;
    }

    /**
     * Memory Usage.
     *
     * <p>
     * {@link MemoryUsage} + stapler annotations.
     */
    @ExportedBean
    public static class MemoryUsage2 extends MemoryUsage {
        private static final long serialVersionUID = 2216994637932270352L;

        public MemoryUsage2(MemoryUsage mem) {
            super(mem.totalPhysicalMemory, mem.availablePhysicalMemory, mem.totalSwapSpace, mem.availableSwapSpace);
        }

        /**
         * Total physical memory of the system, in bytes.
         */
        @Exported
        public long getTotalPhysicalMemory() {
            return totalPhysicalMemory;
        }

        /**
         * Of the total physical memory of the system, available bytes.
         */
        @Exported
        public long getAvailablePhysicalMemory() {
            return availablePhysicalMemory;
        }

        /**
         * Total number of swap space in bytes.
         */
        @Exported
        public long getTotalSwapSpace() {
            return totalSwapSpace;
        }

        /**
         * Available swap space in bytes.
         */
        @Exported
        public long getAvailableSwapSpace() {
            return availableSwapSpace;
        }
    }
}
