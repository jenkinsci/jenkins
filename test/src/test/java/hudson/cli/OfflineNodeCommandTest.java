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

package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Agent;
import hudson.agents.DumbAgent;
import hudson.agents.OfflineCause;
import hudson.util.OneShotEvent;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author pjanouse
 */
public class OfflineNodeCommandTest {

    private CLICommandInvoker command;

    @ClassRule
    public static final BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Rule
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, "offline-node");
    }

    @Test
    public void offlineNodeShouldFailWithoutComputerDisconnectPermission() throws Exception {
        j.createAgent("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Disconnect permission"));
        assertThat(result.stderr(), not(containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT)));
    }

    @Test
    public void offlineNodeShouldFailIfNodeDoesNotExist() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such agent \"never_created\" exists."));
        assertThat(result.stderr(), not(containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT)));
    }

    @Test
    public void offlineNodeShouldSucceedOnOnlineNode() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        agent.toComputer().waitUntilOnline();
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(false));
        assertThat(agent.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnOfflineNode() throws Exception {
        Agent agent = inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().name("aNode").skipStart().build());
        agent.toComputer().setTemporarilyOffline(true, null);
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.LegacyOfflineCause.class));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnDisconnectedNode() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        agent.toComputer().waitUntilOnline();
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), equalTo(null));
        agent.toComputer().disconnect();
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOnline(), equalTo(false));
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOnline(), equalTo(false));
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeShouldSucceedOnOnlineNodeWithCause() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        agent.toComputer().waitUntilOnline();
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(false));
        assertThat(agent.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnOfflineNodeWithCause() throws Exception {
        Agent agent = inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().name("aNode").skipStart().build());
        agent.toComputer().setTemporarilyOffline(true, null);
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.LegacyOfflineCause.class));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnDisconnectedNodeWithCause() throws Exception {
        DumbAgent agent = j.createAgent("aNode", "", null);
        agent.toComputer().waitUntilOnline();
        assertThat(agent.toComputer().isOnline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), equalTo(null));
        agent.toComputer().disconnect();
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOnline(), equalTo(false));
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(agent.toComputer().isOnline(), equalTo(false));
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnBuildingNode() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        DumbAgent agent = j.createAgent("aNode", "", null);
        agent.toComputer().waitUntilOnline();
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedNode(agent);
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(build.isBuilding(), equalTo(true));

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
    }

    @Test
    public void offlineNodeShouldSucceedOnBuildingNodeWithCause() throws Exception {
        final OneShotEvent finish = new OneShotEvent();
        DumbAgent agent = j.createAgent("aNode", "", null);
        agent.toComputer().waitUntilOnline();
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.setAssignedNode(agent);
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        agent.toComputer().waitUntilOffline();
        assertThat(agent.toComputer().isOffline(), equalTo(true));
        assertThat(agent.toComputer().isTemporarilyOffline(), equalTo(true));
        assertThat(agent.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(build.isBuilding(), equalTo(true));

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
    }

    @Test
    public void offlineNodeManyShouldSucceed() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        DumbAgent agent3 = j.createAgent("aNode3", "", null);
        agent1.toComputer().waitUntilOnline();
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), equalTo(null));
        agent2.toComputer().waitUntilOnline();
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), equalTo(null));
        agent3.toComputer().waitUntilOnline();
        assertThat(agent3.toComputer().isOnline(), equalTo(true));
        assertThat(agent3.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3");
        assertThat(result, succeededSilently());
        assertThat(agent1.toComputer().isOffline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(agent2.toComputer().isOffline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent2.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(agent3.toComputer().isOffline(), equalTo(true));
        assertThat(agent3.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent3.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeManyShouldSucceedWithCause() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        DumbAgent agent3 = j.createAgent("aNode3", "", null);
        agent1.toComputer().waitUntilOnline();
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), equalTo(null));
        agent2.toComputer().waitUntilOnline();
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), equalTo(null));
        agent3.toComputer().waitUntilOnline();
        assertThat(agent3.toComputer().isOnline(), equalTo(true));
        assertThat(agent3.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(agent1.toComputer().isOffline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(agent2.toComputer().isOffline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent2.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(agent3.toComputer().isOffline(), equalTo(true));
        assertThat(agent3.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent3.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeManyShouldFailIfANodeDoesNotExist() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        agent1.toComputer().waitUntilOnline();
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), equalTo(null));
        agent2.toComputer().waitUntilOnline();
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));
        assertThat(agent1.toComputer().isOffline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(agent2.toComputer().isOffline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent2.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeManyShouldFailIfANodeDoesNotExistWithCause() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        agent1.toComputer().waitUntilOnline();
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), equalTo(null));
        agent2.toComputer().waitUntilOnline();
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created", "-m", "aCause");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));
        assertThat(agent1.toComputer().isOffline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(agent2.toComputer().isOffline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent2.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        agent1.toComputer().waitUntilOnline();
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), equalTo(null));
        agent2.toComputer().waitUntilOnline();
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1");
        assertThat(result, succeededSilently());
        assertThat(agent1.toComputer().isOffline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(agent2.toComputer().isOffline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent2.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void offlineNodeManyShouldSucceedEvenANodeIsSpecifiedTwiceWithCause() throws Exception {
        DumbAgent agent1 = j.createAgent("aNode1", "", null);
        DumbAgent agent2 = j.createAgent("aNode2", "", null);
        agent1.toComputer().waitUntilOnline();
        assertThat(agent1.toComputer().isOnline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), equalTo(null));
        agent2.toComputer().waitUntilOnline();
        assertThat(agent2.toComputer().isOnline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), equalTo(null));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(agent1.toComputer().isOffline(), equalTo(true));
        assertThat(agent1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(agent2.toComputer().isOffline(), equalTo(true));
        assertThat(agent2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) agent2.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void offlineNodeShouldSucceedOnMaster() {
        final Computer masterComputer = Jenkins.get().getComputer("");

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
    public void offlineNodeShouldSucceedOnMasterWithCause() {
        final Computer masterComputer = Jenkins.get().getComputer("");

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
