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

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class ConnectNodeCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, "connect-node");
    }

    @Test public void connectNodeShouldFailWithoutComputerConnectPermission() throws Exception {
        j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Connect permission"));
        assertThat(result.stderr(), not(containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT)));
    }

    @Test public void connectNodeShouldFailIfNodeDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such agent \"never_created\" exists."));
        assertThat(result.stderr(), not(containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT)));
    }

    @Test public void connectNodeShouldSucceed() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));

        result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));

        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOnline(), equalTo(false));
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void connectNodeShouldSucceedWithForce() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("-f", "aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));

        result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("-f", "aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));

        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOnline(), equalTo(false));
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("-f", "aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void connectNodeManyShouldSucceed() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);
        DumbSlave slave3 = j.createSlave("aNode3", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3");
        assertThat(result, succeededSilently());
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
        assertThat(slave3.toComputer().isOnline(), equalTo(true));
    }


    @Test public void connectNodeManyShouldFailIfANodeDoesNotExist() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created");
        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such agent \"never_created\" exists. Did you mean \"aNode1\"?"));
        assertThat(result.stderr(), containsString("ERROR: " + CLICommand.CLI_LISTPARAM_SUMMARY_ERROR_TEXT));
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
    }

    @Test public void connectNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {
        DumbSlave slave1 = j.createSlave("aNode1", "", null);
        DumbSlave slave2 = j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1");
        assertThat(result, succeededSilently());
        assertThat(slave1.toComputer().isOnline(), equalTo(true));
        assertThat(slave2.toComputer().isOnline(), equalTo(true));
    }

    @Test public void connectNodeShouldSucceedOnMaster() throws Exception {
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
}
