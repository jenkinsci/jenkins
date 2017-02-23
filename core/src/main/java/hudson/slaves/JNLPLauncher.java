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
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.TaskListener;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ComputerLauncher} via JNLP.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class JNLPLauncher extends ComputerLauncher {
    /**
     * If the agent needs to tunnel the connection to the master,
     * specify the "host:port" here. This can include the special
     * syntax "host:" and ":port" to indicate the default host/port
     * shall be used.
     *
     * <p>
     * Null if no tunneling is necessary.
     *
     * @since 1.250
     */
    public final String tunnel;

    /**
     * Additional JVM arguments. Can be null.
     * @since 1.297
     */
    public final String vmargs;

    @DataBoundConstructor
    public JNLPLauncher(String tunnel, String vmargs) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
        this.vmargs = Util.fixEmptyAndTrim(vmargs);
    }

    public JNLPLauncher() {
        this(null,null);
    }

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        // do nothing as we cannot self start
    }

    /**
     * @deprecated as of 1.XXX
     *      Use {@link Jenkins#getDescriptor(Class)}
     */
    public static /*almost final*/ Descriptor<ComputerLauncher> DESCRIPTOR;

    @Extension @Symbol("jnlp")
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        public String getDisplayName() {
            return Messages.JNLPLauncher_displayName();
        }
    };

    /**
     * Hides the JNLP launcher when the JNLP agent port is not enabled.
     *
     * @since 2.16
     */
    @Extension
    public static class DescriptorVisibilityFilterImpl extends DescriptorVisibilityFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(@CheckForNull Object context, @Nonnull Descriptor descriptor) {
            return descriptor.clazz != JNLPLauncher.class || Jenkins.getInstance().getTcpSlaveAgentListener() != null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filterType(@Nonnull Class<?> contextClass, @Nonnull Descriptor descriptor) {
            return descriptor.clazz != JNLPLauncher.class || Jenkins.getInstance().getTcpSlaveAgentListener() != null;
        }
    }

}
