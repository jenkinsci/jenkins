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

/**
 * @author pjanouse
 */

package hudson.cli;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.tasks.Builder;
import hudson.util.OneShotEvent;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.concurrent.Future;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertTrue;

public class OnlineNodeCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, "online-node");
    }

    @Test public void onlineNodeShouldFailWithoutComputerConnectPermission() throws Exception {
        j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Connect permission"));
    }

    @Test public void onlineNodeShouldFailIfNodeDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such agent \"never_created\" exists."));
    }

    @Test public void onlineNodeShouldSucceed() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnOnlineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnOfflineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        slave.toComputer().setTemporarilyOffline(true);
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnDisconnectedNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(false));
    }

    @Test public void onlineNodeShouldSucceedOnDisconnectingNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        slave.toComputer().disconnect();

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(false));
    }

    @Test public void onlineNodeShouldSucceedOnBuildingOfflineNode() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        DumbSlave slave = j.createSlave("aNode", "", null);
        if (!slave.toComputer().isOnline()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedNode(slave);
        final Future<FreeStyleBuild> build = startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        slave.toComputer().setTemporarilyOffline(true);
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until going online is in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(true));

        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
    }

    @Test public void onlineNodeManyShouldSucceed() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        DumbSlave slave3 = j.createSlave("aNode3", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3");
        assertThat(result, succeededSilently());
        if (slave1.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode1 going online is in progress...");
            slave1.toComputer().waitUntilOnline();
        }
        if (slave2.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode2 going online is in progress...");
            slave2.toComputer().waitUntilOnline();
        }
        if (slave3.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode3 going online is in progress...");
            slave3.toComputer().waitUntilOnline();
        }
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave3.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeManyShouldFailIfANodeDoesNotExist() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_ERROR_TEXT));
        if (slave1.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode1 going online is in progress...");
            slave1.toComputer().waitUntilOnline();
        }
        if (slave2.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode2 going online is in progress...");
            slave2.toComputer().waitUntilOnline();
        }
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1");
        assertThat(result, succeededSilently());
        if (slave1.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode1 going online is in progress...");
            slave1.toComputer().waitUntilOnline();
        }
        if (slave2.toComputer().isConnecting()) {
            System.out.println("Waiting until aNode2 going online is in progress...");
            slave2.toComputer().waitUntilOnline();
        }
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnMaster() throws Exception {
        final Computer masterComputer = j.jenkins.getActiveInstance().getComputer("");

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
     * @return A {@link Future} object represents the started build
     * @throws Exception if somethink wrong happened
     */
    public static Future<FreeStyleBuild> startBlockingAndFinishingBuild(FreeStyleProject project, OneShotEvent finish) throws Exception {
        final OneShotEvent block = new OneShotEvent();

        project.getBuildersList().add(new BlockingAndFinishingBuilder(block, finish));

        Future<FreeStyleBuild> r = project.scheduleBuild2(0);
        block.block();  // wait until we are safe to interrupt
        assertTrue(project.getLastBuild().isBuilding());

        return r;
    }

    private static final class BlockingAndFinishingBuilder extends Builder {
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
            for (;;) {
                // Go out if we should finish
                if (finish.isSignaled())
                    break;

                // Keep using the channel
                channel.call(node.getClockDifferenceCallable());
                Thread.sleep(100);
            }
            return true;
        }
        @TestExtension("disconnectCause")
        public static class DescriptorImpl extends Descriptor<Builder> {}
    }
}
