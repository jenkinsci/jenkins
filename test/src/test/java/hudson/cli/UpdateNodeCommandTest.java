/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Computer;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Slave;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UpdateNodeCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, new UpdateNodeCommand());
    }

    @Test
    void updateNodeShouldFailWithoutComputerConfigurePermission() throws Exception {

        j.createSlave("MyAgent", null, null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("MyAgent")
        ;

        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Configure permission"));
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
    }

    @Test
    void updateNodeShouldModifyNodeConfiguration() throws Exception {

        j.createSlave("MyAgent", null, null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONFIGURE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invokeWithArgs("MyAgent")
        ;

        assertThat(result, succeededSilently());

        assertThat("An agent with old name should not exist", j.jenkins.getNode("MyAgent"), nullValue());

        final Node updatedSlave = j.jenkins.getNode("AgentFromXML");
        assertThat(updatedSlave.getNodeName(), equalTo("AgentFromXML"));
        assertThat(updatedSlave.getNumExecutors(), equalTo(42));
    }

    @Test
    void updateNodeShouldFailIfNodeDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONFIGURE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invokeWithArgs("MyAgent")
        ;

        assertThat(result.stderr(), containsString("ERROR: No such node 'MyAgent'"));
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
    }

    @Issue("SECURITY-281")
    @Test
    void updateNodeShouldFailForMaster() {
        CLICommandInvoker.Result result = command.authorizedTo(Computer.CONFIGURE, Jenkins.READ).withStdin(Computer.class.getResourceAsStream("node.xml")).invokeWithArgs("");
        assertThat(result.stderr(), containsString("No such node ''"));
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());

        // old name
        result = command.authorizedTo(Computer.EXTENDED_READ, Jenkins.READ).withStdin(Computer.class.getResourceAsStream("node.xml")).invokeWithArgs("(master)");
        assertThat(result.stderr(), containsString("No such node '(master)'"));
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());

        // new name
        result = command.authorizedTo(Computer.EXTENDED_READ, Jenkins.READ).withStdin(Computer.class.getResourceAsStream("node.xml")).invokeWithArgs("(built-in)");
        assertThat(result.stderr(), containsString("No such node '(built-in)'"));
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
    }

    @Test
    @Issue("SECURITY-2021")
    void updateNodeShouldFailForDotDot() throws Exception {
        String okName = "MyNode";
        Slave node = j.createSlave(okName, null, null);
        // currently <dummy>, but doing so will be a bit more future-proof
        String defaultDescription = node.getNodeDescription();

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CONFIGURE, Jenkins.READ)
                .withStdin(UpdateNodeCommandTest.class.getResourceAsStream("node_sec2021.xml"))
                .invokeWithArgs(okName)
                ;

        assertThat(result.stderr(), containsString(Messages.Hudson_UnsafeChar('/')));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(1));

        assertEquals(okName, node.getNodeName());
        // ensure the other data were not saved
        assertEquals(defaultDescription, node.getNodeDescription());
    }
}
