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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Computer;
import hudson.model.Descriptor.FormException;
import hudson.model.Agent;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Partial implementation of {@link Agent} to be used by {@link AbstractCloudImpl}.
 * You may want to implement {@link EphemeralNode} too.
 * @author Kohsuke Kawaguchi
 * @since 1.382
 */
public abstract class AbstractCloudAgent extends Agent {

    protected AbstractCloudAgent(@NonNull String name, String remoteFS, ComputerLauncher launcher)
            throws FormException, IOException {
        super(name, remoteFS, launcher);
    }

    /**
     * Use {@link #AbstractCloudAgent(java.lang.String, java.lang.String, hudson.agents.ComputerLauncher)}
     * @deprecated since 2.184
     */
    @Deprecated
    protected AbstractCloudAgent(String name, String nodeDescription, String remoteFS, String numExecutors,
                              Mode mode, String labelString, ComputerLauncher launcher,
                              RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties)
            throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    /**
     * Use {@link #AbstractCloudAgent(java.lang.String, java.lang.String, hudson.agents.ComputerLauncher)}
     * @deprecated since 2.184
     */
    @Deprecated
    protected AbstractCloudAgent(String name, String nodeDescription, String remoteFS, int numExecutors,
                              Mode mode, String labelString, ComputerLauncher launcher,
                              RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties)
            throws FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    @Override
    public abstract AbstractCloudComputer createComputer();

    /**
     * Releases and removes this agent.
     */
    public void terminate() throws InterruptedException, IOException {
        final Computer computer = toComputer();
        if (computer != null) {
            computer.recordTermination();
        }
        try {
            _terminate(computer instanceof AgentComputer ? ((AgentComputer) computer).getListener() : new LogTaskListener(LOGGER, Level.INFO));
        } finally {
            try {
                Jenkins.get().removeNode(this);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to remove " + name, e);
            }
        }
    }

    /**
     * Performs the removal of the underlying resource from the cloud.
     */
    protected abstract void _terminate(TaskListener listener) throws IOException, InterruptedException;

    private static final Logger LOGGER = Logger.getLogger(AbstractCloudAgent.class.getName());
}
