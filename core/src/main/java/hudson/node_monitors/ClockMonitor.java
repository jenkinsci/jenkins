/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Thomas J. Black
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
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.util.ClockDifference;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link NodeMonitor} that checks clock of {@link Node} to
 * detect out of sync clocks.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public class ClockMonitor extends NodeMonitor {

    @DataBoundConstructor
    public ClockMonitor() {
    }

    public ClockDifference getDifferenceFor(Computer c) {
        return DESCRIPTOR.get(c);
    }

    /**
     * @deprecated as of 2.0
     *      Don't use this field, use injection.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_PKGPROTECT", justification = "for backward compatibility")
    public static /*almost final*/ AbstractNodeMonitorDescriptor<ClockDifference> DESCRIPTOR;

    @Extension @Symbol("clock")
    public static class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<ClockDifference> {
        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "for backward compatibility")
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        @Override
        public boolean canTakeOffline() {
            return false;
        }

        @Override
        protected Callable<ClockDifference, IOException> createCallable(Computer c) {
            Node n = c.getNode();
            if (n == null) return null;
            return n.getClockDifferenceCallable();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ClockMonitor_DisplayName();
        }
    }
}
