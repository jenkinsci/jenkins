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
package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * {@link ComputerLauncher} filter that can be used as a base class for decorators.
 *
 * <p>
 * Using this class also protects you from method additions in {@link ComputerLauncher},
 * since these two classes are updated in sync.
 *
 * @author Kohsuke Kawaguchi
 * @see SlaveComputer#grabLauncher(Node)
 */
public abstract class ComputerLauncherFilter extends ComputerLauncher {
    protected volatile ComputerLauncher core;

    public ComputerLauncherFilter(ComputerLauncher core) {
        this.core = core;
    }

    /**
     * Returns the delegation target.
     */
    public ComputerLauncher getCore() {
        return core;
    }

    @Override
    public boolean isLaunchSupported() {
        return core.isLaunchSupported();
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        core.launch(computer, listener);
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        core.afterDisconnect(computer, listener);
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        core.beforeDisconnect(computer, listener);
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }
}
