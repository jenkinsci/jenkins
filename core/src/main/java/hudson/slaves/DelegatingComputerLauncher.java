/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenient base implementation of {@link ComputerLauncher} that allows
 * subtypes to perform some initialization (typically something cloud/v12n related
 * to power up the machine), then to delegate to another {@link ComputerLauncher}
 * to connect.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.382
 */
public abstract class DelegatingComputerLauncher extends ComputerLauncher {
    protected ComputerLauncher launcher;

    protected DelegatingComputerLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        getLauncher().launch(computer, listener);
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        getLauncher().afterDisconnect(computer, listener);
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        getLauncher().beforeDisconnect(computer, listener);
    }

    public static abstract class DescriptorImpl extends Descriptor<ComputerLauncher> {
        /**
         * Returns the applicable nested computer launcher types.
         * The default implementation avoids all delegating descriptors, as that creates infinite recursion.
         */
        public List<Descriptor<ComputerLauncher>> getApplicableDescriptors() {
            List<Descriptor<ComputerLauncher>> r = new ArrayList<Descriptor<ComputerLauncher>>();
            for (Descriptor<ComputerLauncher> d : Functions.getComputerLauncherDescriptors()) {
                if (DelegatingComputerLauncher.class.isInstance(d))  continue;
                r.add(d);
            }
            return r;
        }
    }

}
