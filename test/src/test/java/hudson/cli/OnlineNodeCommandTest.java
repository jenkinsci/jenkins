/*
 * The MIT License
 *
 * Copyright 2015 Red Hat, Inc.
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

package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.agents.DumbAgent;
import hudson.util.OneShotEvent;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * @author pjanouse
 */
public class OnlineNodeCommandTest {

    private CLICommandInvoker command;

    @ClassRule
    public static final BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, "online-node");
    }

    @Test public void onlineNodeShouldFailWithoutComputerConnectPermission() throws Exception {
        j.createAgent("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Connect permission"));
    }

    @Test public void onlineNodeShouldFailIfNodeDoesNotExist() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such agent \"never_created\" exists."));
    }

    @Test public void onlineNodeShouldSucceed() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnOnlineNode() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnOfflineNode() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        agent.toComputer().setTemporarilyOffline(true);
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOffline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnDisconnectedNode() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        agent.toComputer().disconnect();
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOffline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(false));
    }

    @Test public void onlineNodeShouldSucceedOnDisconnectingNode() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        agent.toComputer().disconnect();

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(false));
    }

    @Test public void onlineNodeShouldSucceedOnBuildingOfflineNode() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        DumbAgent agent = j.createAgent("aNode", "", null);
        if (!agent.toComputer().isOnline()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedNode(agent);
        final FreeStyleBuild build = startBlockingAndFinishingBuild(project, finish);

        agent.toComputer().setTemporarilyOffline(true);
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOffline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (agent.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            agent.toComputer().waitUntilOnline();
        }
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        assertThat(build.isBuilding(), equalTo(true));

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
    }

    @Test public void onlineNodeManyShouldSucceed() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        DumbAgent agent3 = j.createAgent("aNode3", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3");
        assertThat(result, succeededSilently());
        if (agent1.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode1 going online is in progress...");
            agent1.toComputer().waitUntilOnline();
        }
        if (agent2.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode2 going online is in progress...");
            agent2.toComputer().waitUntilOnline();
        }
        if (agent3.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode3 going online is in progress...");
            agent3.toComputer().waitUntilOnline();
        }
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent3.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeManyShouldFailIfANodeDoesNotExist() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));
        if (agent1.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode1 going online is in progress...");
            agent1.toComputer().waitUntilOnline();
        }
        if (agent2.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode2 going online is in progress...");
            agent2.toComputer().waitUntilOnline();
        }
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1");
        assertThat(result, succeededSilently());
        if (agent1.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode1 going online is in progress...");
            agent1.toComputer().waitUntilOnline();
        }
        if (agent2.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode2 going online is in progress...");
            agent2.toComputer().waitUntilOnline();
        }
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnMaster() {
        final Computer masterComputer = j.jenkins.getComputer("");

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("");
        assertThat(result, succeededSilently());
        assertThat(masterComputer.isOnline(), equalTo(true));

        result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("");
        assertThat(result, succeededSilently());
        assertThat(masterComputer.isOnline(), equalTo(true));
    }

    /**
     * Start a project with an infinite build step and wait until signal to finish
     *
     * @param project {@link FreeStyleProject} to start
     * @param finish {@link OneShotEvent} to signal to finish a build
     * @return the started build (the caller should wait for its completion)
     * @throws Exception if somethink wrong happened
     */
    public static FreeStyleBuild startBlockingAndFinishingBuild(FreeStyleProject project, OneShotEvent finish) throws Exception {
        assertFalse(finish.isSignaled());

        final OneShotEvent block = new OneShotEvent();

        project.getBuildersList().add(new BlockingAndFinishingBuilder(block, finish));

        FreeStyleBuild b = project.scheduleBuild2(0).waitForStart();
        block.block();  // wait until we are safe to interrupt
        assertTrue(b.isBuilding());

        return b;
    }

    private static final class BlockingAndFinishingBuilder extends TestBuilder {
        private final OneShotEvent block;
        private final OneShotEvent finish;

        private BlockingAndFinishingBuilder(OneShotEvent block, OneShotEvent finish) {
            this.block = block;
            this.finish = finish;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            VirtualChannel channel = launcher.getChannel();
            Node node = build.getBuiltOn();

            block.signal(); // we are safe to be interrupted
            while (!finish.isSignaled()) {
                // Keep using the channel
                channel.call(node.getClockDifferenceCallable());
                Thread.sleep(100);
            }
            return true;
        }
    }
}
