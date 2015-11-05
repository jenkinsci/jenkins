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
package hudson.slaves;

import hudson.model.Slave;
import hudson.model.Descriptor.FormException;
import hudson.Extension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default {@link Slave} implementation for computers that do not belong to a higher level structure,
 * like grid or cloud.
 *
 * @author Kohsuke Kawaguchi
 */
public final class DumbSlave extends Slave {
    /**
     * @deprecated as of 1.286.
     *      Use {@link #DumbSlave(String, String, String, String, Node.Mode, String, ComputerLauncher, RetentionStrategy, List)}
     */
    @Deprecated
    public DumbSlave(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws FormException, IOException {
        this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }
    
    @DataBoundConstructor
    public DumbSlave(String name, String nodeDescription, String remoteFS, String numExecutors, Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws IOException, FormException {
    	super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        public String getDisplayName() {
            return Messages.DumbSlave_displayName();
        }
    }
}
