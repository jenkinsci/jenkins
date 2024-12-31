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
import static org.junit.Assert.assertEquals;

import hudson.model.Computer;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Agent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CreateNodeCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, new CreateNodeCommand());
    }

    @Test public void createNodeShouldFailWithoutComputerCreatePermission() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invoke()
        ;

        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Create permission"));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(6));
    }

    @Test public void createNode() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CREATE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invoke()
        ;

        assertThat(result, succeededSilently());

        final Agent updated = (Agent) j.jenkins.getNode("AgentFromXML");
        assertThat(updated.getNodeName(), equalTo("AgentFromXML"));
        assertThat(updated.getNumExecutors(), equalTo(42));
    }

    @Test public void createNodeSpecifyingNameExplicitly() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CREATE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invokeWithArgs("CustomAgentName")
        ;

        assertThat(result, succeededSilently());

        assertThat("An agent with original name should not exist", j.jenkins.getNode("AgentFromXml"), nullValue());

        final Agent updated = (Agent) j.jenkins.getNode("CustomAgentName");
        assertThat(updated.getNodeName(), equalTo("CustomAgentName"));
        assertThat(updated.getNumExecutors(), equalTo(42));
    }

    @Test public void createNodeSpecifyingDifferentNameExplicitly() throws Exception {

        final Node original = j.createAgent("AgentFromXml", null, null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CREATE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invokeWithArgs("CustomAgentName")
        ;

        assertThat(result, succeededSilently());

        assertThat("An agent with original name should be left untouched", j.jenkins.getNode("AgentFromXml"), equalTo(original));

        final Agent updated = (Agent) j.jenkins.getNode("CustomAgentName");
        assertThat(updated.getNodeName(), equalTo("CustomAgentName"));
        assertThat(updated.getNumExecutors(), equalTo(42));
    }

    @Test public void createNodeShouldFailIfNodeAlreadyExist() throws Exception {

        j.createAgent("AgentFromXML", null, null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CREATE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invoke()
        ;

        assertThat(result.stderr(), containsString("ERROR: Node 'AgentFromXML' already exists"));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(4));
    }

    @Test public void createNodeShouldFailIfNodeAlreadyExistWhenNameSpecifiedExplicitly() throws Exception {

        j.createAgent("ExistingAgent", null, null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CREATE, Jenkins.READ)
                .withStdin(Computer.class.getResourceAsStream("node.xml"))
                .invokeWithArgs("ExistingAgent")
        ;

        assertThat(result.stderr(), containsString("ERROR: Node 'ExistingAgent' already exists"));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(4));
    }

    @Test
    @Issue("SECURITY-2021")
    public void createNodeShouldFailIfNodeIsNotGood() {
        int nodeListSizeBefore = j.jenkins.getNodes().size();

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.CREATE, Jenkins.READ)
                .withStdin(CreateNodeCommandTest.class.getResourceAsStream("node_sec2021.xml"))
                .invoke()
                ;

        assertThat(result.stderr(), containsString(Messages.Hudson_UnsafeChar('/')));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(1));

        // ensure not side effects
        assertEquals(nodeListSizeBefore, j.jenkins.getNodes().size());
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_withoutOtherNode() {
        int nodeListSizeBefore = j.jenkins.getNodes().size();

        CLICommandInvoker.Result result = command
                .withStdin(new ByteArrayInputStream("<agent/>".getBytes(StandardCharsets.UTF_8)))
                .invokeWithArgs("nodeA.")
                ;

        assertThat(result.stderr(), containsString(Messages.Hudson_TrailingDot()));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(1));

        // ensure not side effects
        assertEquals(nodeListSizeBefore, j.jenkins.getNodes().size());
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_withExistingNode() {
        int nodeListSizeBefore = j.jenkins.getNodes().size();

        assertThat(command.withStdin(new ByteArrayInputStream("<agent/>".getBytes(StandardCharsets.UTF_8))).invokeWithArgs("nodeA"), succeededSilently());
        assertEquals(nodeListSizeBefore + 1, j.jenkins.getNodes().size());

        CLICommandInvoker.Result result = command
                .withStdin(new ByteArrayInputStream("<agent/>".getBytes(StandardCharsets.UTF_8)))
                .invokeWithArgs("nodeA.")
                ;

        assertThat(result.stderr(), containsString(Messages.Hudson_TrailingDot()));
        assertThat(result, hasNoStandardOutput());
        assertThat(result, failedWith(1));

        // ensure not side effects
        assertEquals(nodeListSizeBefore + 1, j.jenkins.getNodes().size());
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_exceptIfEscapeHatchIsSet() {
        String propName = Jenkins.NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
        String initialValue = System.getProperty(propName);
        System.setProperty(propName, "false");
        try {
            int nodeListSizeBefore = j.jenkins.getNodes().size();

            assertThat(command.withStdin(new ByteArrayInputStream("<agent/>".getBytes(StandardCharsets.UTF_8))).invokeWithArgs("nodeA"), succeededSilently());
            assertEquals(nodeListSizeBefore + 1, j.jenkins.getNodes().size());

            CLICommandInvoker.Result result = command
                    .withStdin(new ByteArrayInputStream("<agent/>".getBytes(StandardCharsets.UTF_8)))
                    .invokeWithArgs("nodeA.")
                    ;

            assertThat(result, succeededSilently());

            assertEquals(nodeListSizeBefore + 2, j.jenkins.getNodes().size());
        }
        finally {
            if (initialValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, initialValue);
            }
        }
    }
}
