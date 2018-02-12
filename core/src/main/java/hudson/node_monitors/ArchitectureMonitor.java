/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.Extension;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Discovers the architecture of the system to display in the agent list page.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArchitectureMonitor extends NodeMonitor {
    @Extension @Symbol("architecture")
    public static final class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<String> {
        @Override
        protected Callable<String, IOException> createCallable(Computer c) {
            return new GetArchTask();
        }

        public String getDisplayName() {
            return Messages.ArchitectureMonitor_DisplayName();
        }

        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ArchitectureMonitor();
        }
    }

    /**
     * Obtains the string that represents the architecture.
     */
    private static class GetArchTask extends MasterToSlaveCallable<String,IOException> {
        public String call() {
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            return os+" ("+arch+')';
        }

        private static final long serialVersionUID = 1L;
    }
}
