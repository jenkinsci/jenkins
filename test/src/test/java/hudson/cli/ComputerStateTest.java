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

import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.cli.CLICommandInvoker.Result;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import jenkins.model.Jenkins;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author ogondza
 */
@WithJenkins
class ComputerStateTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void connect() throws Exception {
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
    void online() throws Exception {
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
    void disconnect() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, "disconnect-node");

        Slave slave = j.createOnlineSlave();
        assertTrue(slave.toComputer().isOnline());

        Result result = command.authorizedTo(Jenkins.READ, Computer.DISCONNECT)
                .invokeWithArgs(slave.getNodeName(), "-m", "Custom cause message");

        assertThat(result, succeededSilently());
        assertTrue(slave.toComputer().isOffline());

        OfflineCause cause = slave.toComputer().getOfflineCause();

        if (cause instanceof OfflineCause.UserCause userCause) {
            assertThat(userCause.toString(), endsWith("Custom cause message"));
            assertThat(userCause.getUser(), equalTo(command.user()));
        } else {
            assertThat("seen occasionally in CI", cause, instanceOf(OfflineCause.ChannelTermination.class));
        }
    }

    @Test
    void offline() throws Exception {
        CLICommandInvoker command = new CLICommandInvoker(j, "offline-node");

        Slave slave = j.createOnlineSlave();
        assertTrue(slave.toComputer().isOnline());

        Result result = command.authorizedTo(Jenkins.READ, Computer.DISCONNECT)
                .invokeWithArgs(slave.getNodeName(), "-m", "Custom cause message");

        assertThat(result, succeededSilently());
        assertTrue(slave.toComputer().isOffline());

        OfflineCause cause = slave.toComputer().getOfflineCause();

        if (cause instanceof OfflineCause.UserCause userCause) {
            assertThat(userCause.toString(), endsWith("Custom cause message"));
            assertThat(userCause.getUser(), equalTo(command.user()));
        } else {
            assertThat("seen occasionally in CI", cause, instanceOf(OfflineCause.ChannelTermination.class));
        }
    }

    @Test
    void testUiForConnected() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        Computer computer = slave.toComputer();

        WebClient wc = j.createWebClient();
        assertConnected(wc, slave);

        computer.setTemporarilyOffline(true, null);
        assertTrue(computer.isTemporarilyOffline());
        assertConnected(wc, slave);

        slave.toComputer().disconnect(null);

        HtmlPage page = wc.getPage(slave);

        assertLinkDoesNotExist(page, "Disconnect");

        assertLinkDoesNotExist(page, "Script Console");
        HtmlPage script = wc.getPage(slave, "script");
        assertThat(script.getByXPath("//form[@action='script']"), empty());

        assertLinkDoesNotExist(page, "System Information");
        HtmlPage info = wc.getPage(slave, "systemInfo");
        assertThat(info.asNormalizedText(), not(containsString("Environment Variables")));
    }

    private void assertConnected(WebClient wc, DumbSlave slave) throws Exception {
        HtmlPage main = wc.getPage(slave);
        main.getAnchorByText("Disconnect");

        main.getAnchorByText("Script Console");
        HtmlPage script = wc.getPage(slave, "script");
        assertThat(script.getByXPath("//form[@action='script']"), not(empty()));

        main.getAnchorByText("System Information");
        HtmlPage info = wc.getPage(slave, "systemInfo");
        assertThat(info.asNormalizedText(), containsString("Environment Variables"));
    }

    private void assertLinkDoesNotExist(HtmlPage page, String text) {
        assertThrows(
                ElementNotFoundException.class,
                () -> page.getAnchorByText(text),
                text + " link should not exist");
    }
}
