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

/**
 * @author pjanouse
 */

package hudson.cli;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.QueueTest;
import hudson.util.OneShotEvent;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.fail;

public class CancelQuietDownCommandTest {

    private CLICommandInvoker command;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, "cancel-quiet-down");
    }

    @Test
    public void cancelQuietDownShouldFailWithoutAdministerPermission() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invoke();
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Overall/Administer permission"));
    }

    @Test
    public void cancelQuietDownShouldSuccessOnNoQuietDownedJenkins() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Jenkins.ADMINISTER)
                .invoke();
        assertThat(result, succeededSilently());
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
    }

    @Test
    public void cancelQuietDownShouldSuccessOnQuietDownedJenkins() throws Exception {
        j.jenkins.getActiveInstance().doQuietDown();
        QuietDownCommandTest.assertJenkinsInQuietMode(j);
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Jenkins.ADMINISTER)
                .invoke();
        assertThat(result, succeededSilently());
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
    }

    //
    // Scenario - cancel-quiet-down is called when executor is running on non-quiet-down Jenkins
    // Result - CLI call result is available immediately, execution won't be affected
    //
    @Test
    public void cancelQuietDownShouldSuccessOnNoQuietDownedJenkinsAndRunningExecutor() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final OneShotEvent finish = new OneShotEvent();
        Future<FreeStyleBuild> build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Jenkins.ADMINISTER)
                .invoke();
        assertThat(result, succeededSilently());
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
        build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));
        assertThat(project.isBuilding(), equalTo(true));
        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
    }

    //
    // Scenario - cancel-quiet-down is called when executor is running on quiet-down Jenkins
    // Result - CLI call result is available immediately, execution won't be affected
    //
    @Test
    public void cancelQuietDownShouldSuccessOnQuietDownedJenkinsAndRunningExecutor() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final OneShotEvent finish = new OneShotEvent();
        Future<FreeStyleBuild> build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(true));
        j.jenkins.getActiveInstance().doQuietDown();
        QuietDownCommandTest.assertJenkinsInQuietMode(j);

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Jenkins.ADMINISTER)
                .invoke();
        assertThat(result, succeededSilently());
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
        build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));
        assertThat(project.isBuilding(), equalTo(true));
        finish.signal();
        build.get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(2));
        assertThat(project.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
    }
}