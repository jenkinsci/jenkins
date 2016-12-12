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

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

/**
 * Tests for CLI command {@link hudson.cli.ConsoleCommand console}
 *
 * @author pjanouse
 */
public class ConsoleCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {

        command = new CLICommandInvoker(j, "console");
    }

    @Test public void consoleShouldFailWithoutJobReadPermission() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test public void consoleShouldFailWithoutItemBuildPermission() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Job/Build permission"));
    }

    @Test public void consoleShouldFailWhenProjectDoesNotExist() throws Exception {

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("never_created");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test public void consoleShouldFailWhenLastBuildDoesNotdExist() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject");

        assertThat(result, failedWith(4));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Permalink lastBuild produced no build"));
    }

    @Test public void consoleShouldFailWhenRequestedBuildDoesNotExist() throws Exception {

        j.createFreeStyleProject("aProject");

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such build #1"));
    }

    @Test public void consoleShouldFailWhenRequestedInvalidBuildNumber() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1a");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Not sure what you meant by \"1a\""));

        project.getBuildersList().add(new Shell("echo 1"));
        project.scheduleBuild2(0).get();

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1a");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Not sure what you meant by \"1a\". Did you mean \"lastBuild\"?"));
    }

    @Test public void consoleShouldSuccessWithLastBuild() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 1"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("echo 1"));
    }

    @Test public void consoleShouldSuccessWithSpecifiedBuildNumber() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo ${BUILD_NUMBER}"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 1"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 2"));
        assertThat(project.scheduleBuild2(0).get().getLog(), containsString("echo 3"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "2");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("echo 2"));
    }

    @Test public void consoleShouldSuccessWithFollow() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo start - ${BUILD_NUMBER}\nsleep 10s\n"
                + "echo after sleep - ${BUILD_NUMBER}"));
        if (!project.scheduleBuild(0)) {
            fail("Job wasn't scheduled properly");
        }

        // Wait until project is started (at least 1s)
        while(!project.isBuilding()) {
            System.out.println("Waiting for build to start and sleep 1s...");
            Thread.sleep(1000);
        }

        // Wait for the first message
        if(!project.getBuildByNumber(1).getLog().contains("start - 1")) {
            Thread.sleep(1000);
        }

        assertThat(project.getBuildByNumber(1).getLog(), containsString("start - 1"));
        assertThat(project.getBuildByNumber(1).getLog(), not(containsString("after sleep - 1")));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("start - 1"));
        assertThat(result.stdout(), not(containsString("after sleep - 1")));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1", "-f");

        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("after sleep - 1"));

        assertThat(project.getBuildByNumber(1).isBuilding(), equalTo(false));
        assertThat(project.getBuildByNumber(1).getResult(), equalTo(Result.SUCCESS));
        assertThat(project.getBuildByNumber(1).getLog(), containsString("after sleep - 1"));
    }

    @Test public void consoleShouldSuccessWithLastNLines() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\necho 2\necho 3\necho 4\necho 5"));
        project.scheduleBuild2(0).get();
        assertThat(project.getBuildByNumber(1).getLog(), containsString("echo 1"));
        assertThat(project.getBuildByNumber(1).getLog(), containsString("echo 5"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1", "-n", "4");

        assertThat(result, succeeded());
        assertThat(result.stdout(), not(containsString("echo 1")));
        assertThat(result.stdout(), containsString("echo 5"));
    }

    @Test public void consoleShouldSuccessWithLastNLinesAndFollow() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\necho 2\necho 3\necho 4\necho 5\n"
            + "sleep 10s\n"
            + "echo 6\necho 7\necho 8\necho 9"));

        if (!project.scheduleBuild(0)) {
            fail("Job wasn't scheduled properly");
        }

        // Wait until project is started (at least 1s)
        while(!project.isBuilding()) {
            System.out.println("Waiting for build to start and sleep 1s...");
            Thread.sleep(1000);
        }

        // Wait for the first sleep
        if(!project.getBuildByNumber(1).getLog().contains("echo 5")) {
            Thread.sleep(1000);
        }

        assertThat(project.getBuildByNumber(1).getLog(), containsString("echo 5"));
        assertThat(project.getBuildByNumber(1).getLog(), not(containsString("echo 6")));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1", "-f", "-n", "4");

        assertThat(result, succeeded());
        assertThat(result.stdout(), not(containsString("echo 1")));
        assertThat(result.stdout(), containsString("echo 5"));
        assertThat(result.stdout(), containsString("echo 6"));
        assertThat(result.stdout(), containsString("echo 9"));

        assertThat(project.getBuildByNumber(1).isBuilding(), equalTo(false));
        assertThat(project.getBuildByNumber(1).getResult(), equalTo(Result.SUCCESS));
        assertThat(project.getBuildByNumber(1).getLog(), containsString("echo 9"));
    }

    @Test public void consoleShouldFailIfTheBuildIsStuckInTheQueue() throws Exception {

        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10s"));
        project.setAssignedLabel(new LabelAtom("never_created"));

        assertThat("Job wasn't scheduled properly", project.scheduleBuild(0), equalTo(true));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it isn't in the queue", project.isInQueue(), equalTo(true));
        assertThat("Job wasn't scheduled properly - it is running on non-exist node", project.isBuilding(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Item.BUILD)
                .invokeWithArgs("aProject", "1");

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such build #1"));
    }

}
