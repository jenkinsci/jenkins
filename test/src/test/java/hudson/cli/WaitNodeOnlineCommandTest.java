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

import hudson.slaves.DumbSlave;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

public class WaitNodeOnlineCommandTest {

    private CLICommandInvoker command;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, "wait-node-online");
    }

    @Test
    public void waitNodeOnlineShouldFailIfNodeDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("never_created");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such node 'never_created'"));
    }

    @Test
    public void waitNodeOnlineShouldSucceedOnGoingOnlineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }

    @Test
    public void waitNodeOnlineShouldTimeoutOnGoingOfflineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().setTemporarilyOffline(true);

        boolean timeoutOccurred = false;
        FutureTask task = new FutureTask(new Callable() {
            public Object call() {
                final CLICommandInvoker.Result result = command
                        .authorizedTo(Jenkins.READ)
                        .invokeWithArgs("aNode");
                fail("Never should return from previous CLI call!");
                return null;
            }
        });
        try {
            task.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeoutOccurred = true;
        } finally {
            task.cancel(true);
        }

        if(!timeoutOccurred)
            fail("Missing timeout for CLI call");
    }

    @Test
    public void waitNodeOnlineShouldTimeoutOnDisconnectedNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().disconnect();
        slave.toComputer().waitUntilOffline();

        boolean timeoutOccurred = false;
        FutureTask task = new FutureTask(new Callable() {
            public Object call() {
                final CLICommandInvoker.Result result = command
                        .authorizedTo(Jenkins.READ)
                        .invokeWithArgs("aNode");
                fail("Never should return from previous CLI call!");
                return null;
            }
        });
        try {
            task.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeoutOccurred = true;
        } finally {
            task.cancel(true);
        }

        if(!timeoutOccurred)
            fail("Missing timeout for CLI call");
    }

    @Test
    public void waitNodeOnlineShouldTimeoutOnDisconnectingNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().disconnect();

        boolean timeoutOccurred = false;
        FutureTask task = new FutureTask(new Callable() {
            public Object call() {
                final CLICommandInvoker.Result result = command
                        .authorizedTo(Jenkins.READ)
                        .invokeWithArgs("aNode");
                fail("Never should return from previous CLI call!");
                return null;
            }
        });
        try {
            task.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeoutOccurred = true;
        } finally {
            task.cancel(true);
        }

        if(!timeoutOccurred)
            fail("Missing timeout for CLI call");
    }

    @Test
    public void waitNodeOnlineShouldSuccessOnOnlineNode() throws Exception {
        DumbSlave slave = j.createSlave("aNode", "", null);
        slave.toComputer().waitUntilOnline();
        while (true) {
            if(slave.toComputer().isOnline())
                break;
            Thread.sleep(100);
        }

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode");
        assertThat(result, succeededSilently());
        assertThat(slave.toComputer().isOnline(), equalTo(true));
    }
}
