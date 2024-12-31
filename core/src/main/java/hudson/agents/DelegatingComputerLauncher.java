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

package hudson.agents;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.RestrictedSince;
import hudson.model.Descriptor;
import hudson.model.Agent;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Base implementation of {@link ComputerLauncher} that to be used by launchers that
 * perform some initialization (typically something cloud/v12n related
 * to power up the machine), and then delegate to another {@link ComputerLauncher}
 * to connect.
 *
 * <strong>If you are delegating to another {@link ComputerLauncher} you must extend this base class</strong>
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
    public void launch(AgentComputer computer, TaskListener listener) throws IOException, InterruptedException {
        getLauncher().launch(computer, listener);
    }

    @Override
    public void afterDisconnect(AgentComputer computer, TaskListener listener) {
        getLauncher().afterDisconnect(computer, listener);
    }

    @Override
    public void beforeDisconnect(AgentComputer computer, TaskListener listener) {
        getLauncher().beforeDisconnect(computer, listener);
    }

    public abstract static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        /**
         * Returns the applicable nested computer launcher types.
         * The default implementation avoids all delegating descriptors, as that creates infinite recursion.
         * @since 2.12
         */
        public List<Descriptor<ComputerLauncher>> applicableDescriptors(@CheckForNull Agent it,
                                                                        @NonNull Agent.AgentDescriptor itDescriptor) {
            List<Descriptor<ComputerLauncher>> r = new ArrayList<>();
            for (Descriptor<ComputerLauncher> d : itDescriptor.computerLauncherDescriptors(it)) {
                if (DelegatingComputerLauncher.class.isAssignableFrom(d.getKlass().toJavaClass()))  continue;
                r.add(d);
            }
            return r;
        }
        /**
         * Returns the applicable nested computer launcher types.
         * The default implementation avoids all delegating descriptors, as that creates infinite recursion.
         * @deprecated use {@link #applicableDescriptors(Agent, Agent.AgentDescriptor)}
         */

        @Deprecated
        @Restricted(DoNotUse.class)
        @RestrictedSince("2.12")
        public List<Descriptor<ComputerLauncher>> getApplicableDescriptors() {
            List<Descriptor<ComputerLauncher>> r = new ArrayList<>();
            for (Descriptor<ComputerLauncher> d :
                    Jenkins.get().getDescriptorList(ComputerLauncher.class)) {
                if (DelegatingComputerLauncher.class.isAssignableFrom(d.getKlass().toJavaClass()))  continue;
                r.add(d);
            }
            return r;
        }
    }

}
