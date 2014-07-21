/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import static org.junit.Assert.*;
import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import jenkins.model.Jenkins;
import hudson.cli.CLICommandInvoker.Result;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.model.User;
import hudson.security.ACL;
import hudson.slaves.OfflineCause.UserCause;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author ogondza
 */
public class ComputerStateTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void connect() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, "connect-node");

        Slave slave = j.createSlave();
        assertTrue(slave.toComputer().isOffline());

        Result result = command.authorizedTo(Jenkins.READ, Computer.CONNECT)
                .invokeWithArgs(slave.getNodeName())
        ;

        assertThat(result, succeededSilently());
        slave.toComputer().waitUntilOnline();
        assertTrue(slave.toComputer().isOnline());
    }

    @Test
    public void online() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, "online-node");

        Slave slave = j.createSlave();
        assertTrue(slave.toComputer().isOffline());

        Result result = command.authorizedTo(Jenkins.READ, Computer.CONNECT)
                .invokeWithArgs(slave.getNodeName())
        ;

        assertThat(result, succeededSilently());
        slave.toComputer().waitUntilOnline();
        assertTrue(slave.toComputer().isOnline());
    }

    @Test
    public void disconnect() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, "disconnect-node");

        Slave slave = j.createOnlineSlave();
        assertTrue(slave.toComputer().isOnline());

        Result result = command.authorizedTo(Jenkins.READ, Computer.DISCONNECT)
                .invokeWithArgs(slave.getNodeName(), "-m", "Custom cause message")
        ;

        assertThat(result, succeededSilently());
        assertTrue(slave.toComputer().isOffline());

        UserCause cause = (UserCause) slave.toComputer().getOfflineCause();
        assertThat(cause.toString(), endsWith("Custom cause message"));
        assertThat(cause.getUser(), equalTo(command.user()));
    }

    @Test
    public void offline() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, "offline-node");

        Slave slave = j.createOnlineSlave();
        assertTrue(slave.toComputer().isOnline());

        Result result = command.authorizedTo(Jenkins.READ, Computer.DISCONNECT)
                .invokeWithArgs(slave.getNodeName(), "-m", "Custom cause message")
        ;

        assertThat(result, succeededSilently());
        assertTrue(slave.toComputer().isOffline());

        UserCause cause = (UserCause) slave.toComputer().getOfflineCause();
        assertThat(cause.toString(), endsWith("Custom cause message"));
        assertThat(cause.getUser(), equalTo(command.user()));
    }
}
