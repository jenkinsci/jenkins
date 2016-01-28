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

        assertThat(result, failedWith(1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("user is missing the Agent/Connect permission"));
    }

    @Test public void onlineNodeShouldFailIfNodeDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No such agent \"never_created\" exists."));

    }

    @Test public void onlineNodeShouldSucceed() throws Exception {

        DumbSlave slave = j.createSlave("aNode", "", null);

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");

        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnOnlineNode() throws Exception {

        DumbSlave slave = j.createSlave("aNode", "", null);

        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(true));

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");

        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnOfflieNode() throws Exception {

        DumbSlave slave = j.createSlave("aNode", "", null);

        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(true));

        slave.toComputer().setTemporarilyOffline(true);
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");

        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test public void onlineNodeShouldSucceedOnDisconnectedNode() throws Exception {

        DumbSlave slave = j.createSlave("aNode", "", null);

        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(true));
        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();
        assertThat(slave.toComputer().isOffline(), equalTo(true));

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");

        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(false));
    }

    @Test public void onlineNodeShouldSucceedOnDisconnectingNode() throws Exception {

        DumbSlave slave = j.createSlave("aNode", "", null);

        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(true));
        slave.toComputer().disconnect();

        CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONNECT, Jenkins.READ)
                .invokeWithArgs("aNode");

        assertThat(result, succeededSilently());
        if (slave.toComputer().isConnecting()) {
            System.out.println("Waiting until online in progress...");
            slave.toComputer().waitUntilOnline();
        }

        assertThat(slave.toComputer().isOnline(), equalTo(false));
    }

}
