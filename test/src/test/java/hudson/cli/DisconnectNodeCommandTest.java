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
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author pjanouse
 */
public class DisconnectNodeCommandTest {

    private CLICommandInvoker command;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, "disconnect-node");
    }

    @Test
    public void disconnectNodeShouldFailWithoutComputerDisconnectPermission() throws Exception {
        j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Disconnect permission"));
        assertThat(result.stderr(), not(containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT)));
    }

    @Test
    public void disconnectNodeShouldFailIfNodeDoesNotExist() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such agent \"never_created\" exists."));
        assertThat(result.stderr(), not(containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT)));
    }

    @Test
    public void disconnectNodeShouldSucceed() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));

        slave.toComputer().connect(true);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));

        result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void disconnectNodeShouldSucceedWithCause() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "aCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("aCause"));

        slave.toComputer().connect(true);
        slave.toComputer().waitUntilOnline();
        assertThat(slave.toComputer().isOnline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), equalTo(null));

        result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "anotherCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("anotherCause"));

        result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs("aNode", "-m", "yetAnotherCause");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOffline(), equalTo(true));
        assertThat(slave.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave.toComputer().getOfflineCause()).message, equalTo("yetAnotherCause"));
    }

    @Test
    public void disconnectNodeManyShouldSucceed() throws Exception {
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
        assertThat(slave1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo(null));
        assertThat(slave3.toComputer().isOffline(), equalTo(true));
        assertThat(slave3.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave3.toComputer().getOfflineCause()).message, equalTo(null));
    }

    @Test
    public void disconnectNodeManyShouldSucceedWithCause() throws Exception {
        int n = 3;
        var agents = new ArrayList<DumbSlave>();
        for (int i = 1; i <= n; i++) {
            agents.add(j.createSlave("aNode" + i, "", null));
        }
        for (var agent : agents) {
            var computer = agent.toComputer();
            computer.waitUntilOnline();
            assertThat(computer.isOnline(), equalTo(true));
            assertThat(computer.getOfflineCause(), equalTo(null));
        }

        var args = new ArrayList<String>();
        for (var agent : agents) {
            args.add(agent.getNodeName());
        }
        args.add("-m");
        args.add("aCause");
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DISCONNECT, Jenkins.READ)
                .invokeWithArgs(args.toArray(String[]::new));
        assertThat(result, succeededSilently());
        for (var agent : agents) {
            var computer = agent.toComputer();
            assertThat(computer.isOffline(), equalTo(true));
            var cause = computer.getOfflineCause();
            if (cause instanceof OfflineCause.ByCLI cliCause) {
                assertThat(cliCause.message, equalTo("aCause"));
            } else {
                assertThat("seen occasionally in CI", cause, instanceOf(OfflineCause.ChannelTermination.class));
            }
        }
    }

    @Test
    public void disconnectNodeManyShouldFailIfANodeDoesNotExist() throws Exception {
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
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));
        assertThat(slave1.toComputer().isOffline(), equalTo(true));
        assertThat(slave1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }

    @Test
    public void disconnectNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {
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
        assertThat(slave1.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave1.toComputer().getOfflineCause()).message, equalTo("aCause"));
        assertThat(slave2.toComputer().isOffline(), equalTo(true));
        assertThat(slave2.toComputer().getOfflineCause(), instanceOf(OfflineCause.ByCLI.class));
        assertThat(((OfflineCause.ByCLI) slave2.toComputer().getOfflineCause()).message, equalTo("aCause"));
    }
}
