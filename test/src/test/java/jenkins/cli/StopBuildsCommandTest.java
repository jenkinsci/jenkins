/*
 * The MIT License
 *
 * Copyright (c) 2018, Ilia Zasimov
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

package jenkins.cli;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class StopBuildsCommandTest {

    private static final String TEST_JOB_NAME = "jobName";
    private static final String TEST_JOB_NAME_2 = "jobName2";
    private static final String LN = System.lineSeparator();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void shouldStopLastBuild() throws Exception {
        final FreeStyleProject project = createLongRunningProject(TEST_JOB_NAME);
        FreeStyleBuild build = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", build);
        final String stdout = runWith(List.of(TEST_JOB_NAME)).stdout();

        assertThat(stdout, equalTo("Build '#1' stopped for job 'jobName'" + LN));

        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(build));
    }

    @Test
    void shouldNotStopEndedBuild() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject(TEST_JOB_NAME);
        project.getBuildersList().add(new SleepBuilder(TimeUnit.SECONDS.toMillis(1)));
        j.buildAndAssertSuccess(project);

        final String out = runWith(List.of(TEST_JOB_NAME)).stdout();

        assertThat(out, equalTo("No builds stopped" + LN));
    }

    @Test
    void shouldStopSeveralWorkingBuilds() throws Exception {
        final FreeStyleProject project = createLongRunningProject(TEST_JOB_NAME);
        project.setConcurrentBuild(true);

        FreeStyleBuild b1 = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b1);
        FreeStyleBuild b2 = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b2);

        final String stdout = runWith(List.of(TEST_JOB_NAME)).stdout();

        assertThat(stdout, equalTo("Build '#2' stopped for job 'jobName'" + LN +
                "Build '#1' stopped for job 'jobName'" + LN));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b1));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b2));
    }

    @Test
    void shouldReportNotSupportedType() throws Exception {
        final String testFolderName = "folder";
        j.createFolder(testFolderName);

        final String stderr = runWith(List.of(testFolderName)).stderr();

        assertThat(stderr, equalTo(LN + "ERROR: Job not found: 'folder'" + LN));
    }

    @Test
    void shouldDoNothingIfJobNotFound() {
        final String stderr = runWith(List.of(TEST_JOB_NAME)).stderr();

        assertThat(stderr, equalTo(LN + "ERROR: Job not found: 'jobName'" + LN));
    }

    @Test
    void shouldStopWorkingBuildsInSeveralJobs() throws Exception {
        final List<String> inputJobNames = asList(TEST_JOB_NAME, TEST_JOB_NAME_2);
        setupAndAssertTwoBuildsStop(inputJobNames);
    }

    @Test
    void shouldFilterJobDuplicatesInInput() throws Exception {
        final List<String> inputNames = asList(TEST_JOB_NAME, TEST_JOB_NAME, TEST_JOB_NAME_2);
        setupAndAssertTwoBuildsStop(inputNames);
    }

    @Test
    void shouldReportBuildStopError() throws Exception {
        final FreeStyleProject project = createLongRunningProject(TEST_JOB_NAME);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().toEveryone().
                grant(Item.READ).onItems(project).toEveryone().
                grant(Item.CANCEL).onItems(project).toAuthenticated());
        FreeStyleBuild build = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", build);

        final String stdout = runWith(List.of(TEST_JOB_NAME)).stdout();

        assertThat(stdout,
                equalTo("Exception occurred while trying to stop build '#1' for job 'jobName'. " +
                        "Exception class: AccessDeniedException3, message: anonymous is missing the Job/Cancel permission" + LN +
                        "No builds stopped" + LN));

        build.doStop();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(build));
    }

    @Test
    void shouldStopSecondJobEvenIfFirstStopFailed() throws Exception {
        final FreeStyleProject project = createLongRunningProject(TEST_JOB_NAME_2);

        final FreeStyleProject restrictedProject = createLongRunningProject(TEST_JOB_NAME);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().toEveryone().
                grant(Item.READ).onItems(restrictedProject, project).toEveryone().
                grant(Item.CANCEL).onItems(restrictedProject).toAuthenticated().
                grant(Item.CANCEL).onItems(project).toEveryone());

        FreeStyleBuild b1 = restrictedProject.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b1);
        FreeStyleBuild b2 = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b2);

        final String stdout = runWith(asList(TEST_JOB_NAME, TEST_JOB_NAME_2)).stdout();

        assertThat(stdout,
                equalTo("Exception occurred while trying to stop build '#1' for job 'jobName'. " +
                        "Exception class: AccessDeniedException3, message: anonymous is missing the Job/Cancel permission" + LN +
                        "Build '#1' stopped for job 'jobName2'" + LN));

        b1.doStop();
        b2.doStop();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b1));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b2));
    }

    @Test
    void shouldStopEarlierBuildsEvenIfLatestComplete() throws Exception {
        final FreeStyleProject project = createLongRunningProject(TEST_JOB_NAME);
        project.setConcurrentBuild(true);
        j.jenkins.setNumExecutors(3);

        FreeStyleBuild b1 = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b1);
        FreeStyleBuild b2 = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b2);

        project.getBuildersList().clear();
        FreeStyleBuild b3 = j.buildAndAssertSuccess(project);

        final String stdout = runWith(List.of(TEST_JOB_NAME)).stdout();

        assertThat(stdout, equalTo("Build '#2' stopped for job 'jobName'" + LN +
                "Build '#1' stopped for job 'jobName'" + LN));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b1));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b2));
    }

    private CLICommandInvoker.Result runWith(final List<String> jobNames) {
        CLICommand cmd = new StopBuildsCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(j, cmd);
        return invoker.invokeWithArgs(jobNames.toArray(new String[0]));
    }

    private void setupAndAssertTwoBuildsStop(final List<String> inputNames) throws Exception {
        final FreeStyleProject project = createLongRunningProject(TEST_JOB_NAME);
        final FreeStyleProject project2 = createLongRunningProject(TEST_JOB_NAME_2);

        FreeStyleBuild b1 = project.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b1);
        FreeStyleBuild b2 = project2.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Sleeping", b2);

        final String stdout = runWith(inputNames).stdout();

        assertThat(stdout, equalTo("Build '#1' stopped for job 'jobName'" + LN +
                "Build '#1' stopped for job 'jobName2'" + LN));

        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b1));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b2));
    }

    private FreeStyleProject createLongRunningProject(final String jobName) throws IOException {
        final FreeStyleProject project = j.createFreeStyleProject(jobName);
        project.getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        return project;
    }
}
