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
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.labels.LabelAtom;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class RunRangeCommand2Test {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, new RunRangeCommandTest.DummyRangeCommand());
    }

    @Test
    void dummyRangeShouldFailIfJobNameIsEmptyOnEmptyJenkins() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job ''"));
    }

    @Test
    void dummyRangeShouldFailIfJobNameIsSpaceOnEmptyJenkins() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs(" ", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job ' '"));
    }

    @Test
    void dummyRangeShouldSuccessEvenTheBuildIsRunning() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo 1\r\nping -n 10 127.0.0.1 >nul") : new Shell("echo 1\nsleep 10"));
        FreeStyleBuild build = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("echo 1", build);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: 1" + System.lineSeparator()));
        j.assertBuildStatusSuccess(j.waitForCompletion(build));
    }

    @Test
    void dummyRangeShouldSuccessEvenTheBuildIsStuckInTheQueue() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10"));
        project.setAssignedLabel(new LabelAtom("never_created"));
        assertNotNull(project.scheduleBuild2(0));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it isn't in the queue",
                project.isInQueue(), equalTo(true));
        assertThat("Job wasn't scheduled properly - it is running on non-exist node",
                project.isBuilding(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Builds: " + System.lineSeparator()));
        assertTrue(j.jenkins.getQueue().cancel(project));
    }

}
