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
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

public class DeleteNodeCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, "delete-node");
    }

    @Test public void deleteNodeShouldFailWithoutNodeDeletePermission() throws Exception {

        j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aNode")
        ;

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Agent/Delete permission"));
    }

    @Test public void deleteNodeShouldSucceed() throws Exception {

        j.createSlave("aNode", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("aNode")
        ;

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getNode("aNode"), nullValue());
    }

    @Test public void deleteNodeShouldFailIfNodeDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("never_created")
        ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such node 'never_created'"));
    }

    @Test public void deleteNodeManyShouldSucceed() throws Exception {

        j.createSlave("aNode1", "", null);
        j.createSlave("aNode2", "", null);
        j.createSlave("aNode3", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode3");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aNode1"), nullValue());
        assertThat(j.jenkins.getView("aNode2"), nullValue());
        assertThat(j.jenkins.getView("aNode3"), nullValue());
    }

    @Test public void deleteNodeManyShouldFailIfFirstNodeDoesNotExist() throws Exception {

        j.createSlave("aNode1", "", null);
        j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("never_created", "aNode1", "aNode2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such node 'never_created'"));
        assertThat(result.stderr(), containsString("ERROR: Error occured while performing this command, see previous stderr output."));

        assertThat(j.jenkins.getView("aNode1"), nullValue());
        assertThat(j.jenkins.getView("aNode2"), nullValue());
        assertThat(j.jenkins.getView("never_created"), nullValue());
    }

    @Test public void deleteNodeManyShouldFailIfMiddleNodeDoesNotExist() throws Exception {

        j.createSlave("aNode1", "", null);
        j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("aNode1", "never_created", "aNode2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such node 'never_created'"));
        assertThat(result.stderr(), containsString("ERROR: Error occured while performing this command, see previous stderr output."));

        assertThat(j.jenkins.getView("aNode1"), nullValue());
        assertThat(j.jenkins.getView("aNode2"), nullValue());
        assertThat(j.jenkins.getView("never_created"), nullValue());
    }

    @Test public void deleteNodeManyShouldFailIfLastNodeDoesNotExist() throws Exception {

        j.createSlave("aNode1", "", null);
        j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "never_created");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created: No such node 'never_created'"));
        assertThat(result.stderr(), containsString("ERROR: Error occured while performing this command, see previous stderr output."));

        assertThat(j.jenkins.getView("aNode1"), nullValue());
        assertThat(j.jenkins.getView("aNode2"), nullValue());
        assertThat(j.jenkins.getView("never_created"), nullValue());
    }

    @Test public void deleteNodeManyShouldFailIfMoreNodesDoNotExist() throws Exception {

        j.createSlave("aNode1", "", null);
        j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("aNode1", "never_created1", "never_created2", "aNode2");

        assertThat(result, failedWith(5));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("never_created1: No such node 'never_created1'"));
        assertThat(result.stderr(), containsString("never_created2: No such node 'never_created2'"));
        assertThat(result.stderr(), containsString("ERROR: Error occured while performing this command, see previous stderr output."));

        assertThat(j.jenkins.getView("aNode1"), nullValue());
        assertThat(j.jenkins.getView("aNode2"), nullValue());
        assertThat(j.jenkins.getView("never_created1"), nullValue());
        assertThat(j.jenkins.getView("never_created2"), nullValue());
    }

    @Test public void deleteNodeManyShouldSucceedEvenANodeIsSpecifiedTwice() throws Exception {

        j.createSlave("aNode1", "", null);
        j.createSlave("aNode2", "", null);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Computer.DELETE, Jenkins.READ)
                .invokeWithArgs("aNode1", "aNode2", "aNode1");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aNode1"), nullValue());
        assertThat(j.jenkins.getView("aNode2"), nullValue());
    }
}
