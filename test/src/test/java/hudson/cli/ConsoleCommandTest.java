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
import static org.hamcrest.Matchers.not;
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
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for CLI command {@link hudson.cli.ConsoleCommand console}
 *
 * @author pjanouse
 */
@WithJenkins
class ConsoleCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, "console");
    }

    @Test
    void consoleShouldFailWithoutJobReadPermission() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Issue("JENKINS-52181")
    @Test
    void consoleShouldBeAccessibleForUserWithRead() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        if (Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo 1"));
        } else {
            project.getBuildersList().add(new Shell("echo 1"));
        }
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("aProject");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("echo 1"));
    }

    @Test
    void consoleShouldFailWhenProjectDoesNotExist() {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("never_created");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test
    void consoleShouldFailWhenLastBuildDoesNotExist() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(4));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Permalink lastBuild produced no build"));
    }

    @Test
    void consoleShouldFailWhenRequestedBuildDoesNotExist() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such build #1"));
    }

    @Test
    void consoleShouldFailWhenRequestedInvalidBuildNumber() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1a");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Not sure what you meant by \"1a\""));

        project.getBuildersList().add(Functions.isWindows() ? new BatchFile("echo 1") : new Shell("echo 1"));
        j.buildAndAssertSuccess(project);

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1a");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Not sure what you meant by \"1a\". Did you mean \"lastBuild\"?"));
    }

    @Test
    void consoleShouldSuccessWithLastBuild() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        if (Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo 1"));
        } else {
            project.getBuildersList().add(new Shell("echo 1"));
        }
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("echo 1"));
    }

    @Test
    void consoleShouldSuccessWithSpecifiedBuildNumber() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        if (Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo %BUILD_NUMBER%"));
        } else {
            project.getBuildersList().add(new Shell("echo ${BUILD_NUMBER}"));
        }
        j.assertLogContains("echo 1", j.buildAndAssertSuccess(project));
        j.assertLogContains("echo 2", j.buildAndAssertSuccess(project));
        j.assertLogContains("echo 3", j.buildAndAssertSuccess(project));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "2");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("echo 2"));
    }

    @Test
    void consoleShouldSuccessWithFollow() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        //TODO: do we really want to sleep for 10 seconds?
        if (Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("""
                    echo start - %BUILD_NUMBER%\r
                    ping -n 10 127.0.0.1 >nul\r
                    echo after sleep - %BUILD_NUMBER%"""));
        } else {
            project.getBuildersList().add(new Shell("""
                    echo start - ${BUILD_NUMBER}
                    sleep 10
                    echo after sleep - ${BUILD_NUMBER}"""));
        }
        FreeStyleBuild build = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("start - 1", build);
        j.assertLogContains("start - 1", build);
        j.assertLogNotContains("after sleep - 1", build);

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("start - 1"));
        assertThat(result.stdout(), not(containsString("after sleep - 1")));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1", "-f");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("after sleep - 1"));

        j.assertBuildStatusSuccess(j.waitForCompletion(build));
        j.assertLogContains("after sleep - 1", build);
    }

    @Test
    void consoleShouldSuccessWithLastNLines() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        if (Functions.isWindows()) {
            project.getBuildersList().add(new BatchFile("echo 1\r\necho 2\r\necho 3\r\necho 4\r\necho 5"));
        } else {
            project.getBuildersList().add(new Shell("echo 1\necho 2\necho 3\necho 4\necho 5"));
        }
        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        j.assertLogContains("echo 1", build);
        j.assertLogContains("echo 5", build);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1", "-n", Functions.isWindows() ? "5" : "4");

        assertThat(result, succeeded());
        assertThat(result.stdout(), not(containsString("echo 1")));
        assertThat(result.stdout(), containsString("echo 5"));
    }

    @Test
    void consoleShouldSuccessWithLastNLinesAndFollow() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        //TODO: do we really want to sleep for 10 seconds?
        if (Functions.isWindows()) {
            // the ver >NUL is to reset ERRORLEVEL so we don't fail (ping causes the error)
            project.getBuildersList().add(new BatchFile("""
                    echo 1
                    echo 2
                    echo 3
                    echo 4
                    echo 5
                    ping -n 10 127.0.0.1 >nul
                    echo 6
                    echo 7
                    echo 8
                    echo 9"""));
        } else {
            project.getBuildersList().add(new Shell("""
                    echo 1
                    echo 2
                    echo 3
                    echo 4
                    echo 5
                    sleep 10
                    echo 6
                    echo 7
                    echo 8
                    echo 9"""));
        }

        FreeStyleBuild build = project.scheduleBuild2(0).waitForStart();

        j.waitForMessage("echo 5", build);
        j.assertLogContains("echo 5", build);
        j.assertLogNotContains("echo 6", build);

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1", "-f", "-n", Functions.isWindows() ? "5" : "4");

        assertThat(result, succeeded());
        assertThat(result.stdout(), not(containsString("echo 1")));
        assertThat(result.stdout(), containsString("echo 5"));
        assertThat(result.stdout(), containsString("echo 6"));
        assertThat(result.stdout(), containsString("echo 9"));

        j.assertBuildStatusSuccess(j.waitForCompletion(build));
        j.assertLogContains("echo 9", build);
    }

    @Test
    void consoleShouldFailIfTheBuildIsStuckInTheQueue() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10"));
        project.setAssignedLabel(new LabelAtom("never_created"));

        assertNotNull(project.scheduleBuild2(0));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it isn't in the queue", project.isInQueue(), equalTo(true));
        assertThat("Job wasn't scheduled properly - it is running on non-exist node", project.isBuilding(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such build #1"));
        assertTrue(j.jenkins.getQueue().cancel(project));
    }

}
