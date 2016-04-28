/*
 * The MIT License
 *
 * Copyright 2016 Red Hat, Inc.
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

import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.util.OneShotEvent;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.concurrent.Future;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

public class OfflineNodeCommandTest {

    private CLICommandInvoker command;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, "offline-node");
    }

    @Test
    public void offlineNodeShouldFailWithoutComputerDisconnectPermission() throws Exception {
        j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Disconnect permission"));
        assertThat(result.stderr(), not(containsString("ERROR: Error occured while performing this command, see previous stderr output.")));
    }

    @Test
    public void offlineNodeShouldFailIfNodeDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such agent \"never_created\" exists."));
        assertThat(result.stderr(), not(containsString("ERROR: Error occured while performing this command, see previous stderr output.")));
    }

    @Test
    public void offlineNodeShouldSucceed() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnOnlineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(false));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnOfflineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().setTemporarilyOffline(true, null);
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnDisconnectedNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));
        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOnline(), equalTo(false));
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(false));
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedWithCause() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnOnlineNodeWithCause() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(false));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnOfflineNodeWithCause() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().setTemporarilyOffline(true, null);
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnDisconnectedNodeWithCause() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));
        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOnline(), equalTo(false));
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(false));
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnBuildingNode() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedNode(slave);
        final Future<FreeStyleBuild> build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(true));

        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
    }

    @Test
    public void offlineNodeShouldSucceedOnBuildingNodeWithCause() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedNode(slave);
        final Future<FreeStyleBuild> build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(true));

        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
    }

    @Test
    public void offlineNodeManyShouldSucceed() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        DumbSlave slave3 = j.createSlave("aNode3", "", null);
        slave1.toComputer().waitUntilOnline();
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), equalTo(null));
        slave2.toComputer().waitUntilOnline();
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), equalTo(null));
        slave3.toComputer().waitUntilOnline();
        assertThat(slave3.toComputer().isOnline(), equalTo(true));
        assertThat(slave3.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3");
        assertThat(result, succeededSilently());
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(slave3.toComputer().isOffline(), equalTo(true));
        assertThat(slave3.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave3.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeManyShouldSucceedWithCause() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        DumbSlave slave3 = j.createSlave("aNode3", "", null);
        slave1.toComputer().waitUntilOnline();
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), equalTo(null));
        slave2.toComputer().waitUntilOnline();
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), equalTo(null));
        slave3.toComputer().waitUntilOnline();
        assertThat(slave3.toComputer().isOnline(), equalTo(true));
        assertThat(slave3.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(slave3.toComputer().isOffline(), equalTo(true));
        assertThat(slave3.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave3.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeManyShouldFailIfANodeDoesNotExist() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        slave1.toComputer().waitUntilOnline();
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), equalTo(null));
        slave2.toComputer().waitUntilOnline();
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: Error occured while performing this command, see previous stderr output."));
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeManyShouldFailIfANodeDoesNotExistWithCause() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        slave1.toComputer().waitUntilOnline();
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), equalTo(null));
        slave2.toComputer().waitUntilOnline();
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created", "-m", "aCause");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: Error occured while performing this command, see previous stderr output."));
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        slave1.toComputer().waitUntilOnline();
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), equalTo(null));
        slave2.toComputer().waitUntilOnline();
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1");
        assertThat(result, succeededSilently());
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeManyShouldSucceedEvenANodeIsSpecifiedTwiceWithCause() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        slave1.toComputer().waitUntilOnline();
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), equalTo(null));
        slave2.toComputer().waitUntilOnline();
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause() instanceof OfflineCause.ByCLI, equalTo(true));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnMaster() throws Exception {
        final Computer masterComputer = Jenkins.getActiveInstance().getComputer("");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("");
        assertThat(result, succeededSilently());
        assertThat(masterComputer.isOffline(), equalTo(true));
        assertThat(masterComputer.isTemporarilyOffline(), equalTo(true));
        assertThat(masterComputer.getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) masterComputer.getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnMasterWithCause() throws Exception {
        final Computer masterComputer = Jenkins.getActiveInstance().getComputer("");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(masterComputer.isOffline(), equalTo(true));
        assertThat(masterComputer.isTemporarilyOffline(), equalTo(true));
        assertThat(masterComputer.getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) masterComputer.getOfflineCause()).message, equalTo("aCause"));
    }
}
