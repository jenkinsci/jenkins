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
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.ExecutorTest;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class DeleteBuildsCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, "delete-builds");
    }

    @Test
    void deleteBuildsShouldFailWithoutJobReadPermission() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test
    void deleteBuildsShouldFailWithoutRunDeletePermission() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Run/Delete permission"));
    }

    @Test
    void deleteBuildsShouldFailIfJobDoesNotExist() {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("never_created", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test
    void deleteBuildsShouldFailIfJobNameIsEmpty() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job ''; perhaps you meant 'aProject'?"));
    }

    @Test
    void deleteBuildsShouldSuccess() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }

    @Test
    void deleteBuildsShouldSuccessIfBuildDoesNotExist() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 0 builds"));
    }

    @Test
    void deleteBuildsShouldSuccessIfBuildNumberZeroSpecified() throws Exception {
        j.buildAndAssertSuccess(j.createFreeStyleProject("aProject"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 0 builds"));
    }

    @Issue("JENKINS-73835")
    @Test
    void deleteBuildsShouldFailIfTheBuildIsRunning() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        var build = ExecutorTest.startBlockingBuild(project);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1");
        assertThat(result, failedWith(1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("Unable to delete aProject #1 because it is still running"));

        build.doStop();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(build));
    }

    @Test
    void deleteBuildsShouldSuccessEvenTheBuildIsStuckInTheQueue() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1"));
        project.setAssignedLabel(new LabelAtom("never_created"));
        assertNotNull(project.scheduleBuild2(0));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it isn't in the queue", project.isInQueue(), equalTo(true));
        assertThat("Job wasn't scheduled properly - it is running on non-exist node", project.isBuilding(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 0 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isBuilding(), equalTo(false));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isInQueue(), equalTo(true));

        Jenkins.get().getQueue().cancel(project);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isBuilding(), equalTo(false));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isInQueue(), equalTo(false));
    }

    @Test
    void deleteBuildsManyShouldSuccess() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(5));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 2 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(3));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "3-5");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 3 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }

    @Test
    void deleteBuildsManyShouldSuccessEvenABuildIsSpecifiedTwice() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1-1,1-2,2-2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }

    @Test
    void deleteBuildsManyShouldSuccessEvenLastBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "2-3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }

    @Test
    void deleteBuildsManyShouldSuccessEvenMiddleBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        project.getBuildByNumber(2).delete();
        project.getBuildByNumber(5).delete();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(4));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2,3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 2 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "4-6");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 2 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }

    @Test
    void deleteBuildsManyShouldSuccessEvenFirstBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        project.getBuildByNumber(1).delete();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "2-3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }

    @Test
    void deleteBuildsManyShouldSuccessEvenTheFirstAndLastBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        project.getBuildByNumber(1).delete();
        project.getBuildByNumber(3).delete();
        project.getBuildByNumber(5).delete();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2,3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        result = command
                .authorizedTo(Jenkins.READ, Item.READ, Run.DELETE)
                .invokeWithArgs("aProject", "3-5");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(0));
    }
}
