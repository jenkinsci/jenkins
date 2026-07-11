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
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.XmlFile;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.QueueTest;
import hudson.util.OneShotEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class QuietDownCommandTest {

    private CLICommandInvoker command;
    private static final QueueTest.TestFlyweightTask task
            = new QueueTest.TestFlyweightTask(new AtomicInteger(), null);
    private static final String TEST_REASON = "test reason";

    private static final String VIEWER = "viewer";
    private static final String ADMIN = "admin";

    private final LogRecorder logging = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        command = new CLICommandInvoker(j, "quiet-down");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to(ADMIN).
            grant(Jenkins.READ).everywhere().to(VIEWER));
    }

    @Test
    void quietDownShouldFailWithoutAdministerPermission() {
        final CLICommandInvoker.Result result = command
                .asUser(VIEWER)
                .invoke();
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: " + VIEWER + " is missing the Overall/Manage permission"));
    }

    @Test
    void quietDownShouldSuccess() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invoke();
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
    }

    @Test
    void quietDownShouldSuccessWithBlock() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-block");
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
    }

    @Test
    void quietDownShouldSuccessWithTimeout() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-timeout", "0");
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
    }

    @Test
    void quietDownShouldSuccessWithReason() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-reason", TEST_REASON);
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
        assertThat(j.jenkins.getQuietDownReason(), equalTo(TEST_REASON));
    }

    @Test
    void quietDownShouldSuccessWithBlockAndTimeout() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-block", "-timeout", "0");
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
    }

    @Test
    void quietDownShouldSuccessWithBlockAndTimeoutAndReason() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-block", "-timeout", "0", "-reason", TEST_REASON);
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
        assertThat(j.jenkins.getQuietDownReason(), equalTo(TEST_REASON));
    }

    @Test
    void quietDownShouldFailWithEmptyTimeout() {
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-timeout");
        assertThat(result, failedWith(2));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: Option \"-timeout\" takes an operand"));
    }

    @Test
    void quietDownShouldSuccessOnAlreadyQuietDownedJenkins() {
        j.jenkins.doQuietDown();
        assertJenkinsInQuietMode();
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invoke();
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
    }

    @Test
    void quietDownShouldSuccessWithBlockOnAlreadyQuietDownedJenkins() throws Exception {
        j.jenkins.doQuietDown(true, 0, null, false);
        assertJenkinsInQuietMode();
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-block");
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
    }

    @Test
    void quietDownShouldSuccessWithBlockAndTimeoutOnAlreadyQuietDownedJenkins() throws Exception {
        j.jenkins.doQuietDown(true, 0, null, false);
        assertJenkinsInQuietMode();
        final long time_before = System.currentTimeMillis();
        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invokeWithArgs("-block", "-timeout", "20000");
        assertThat(result, succeededSilently());
        assertThat(System.currentTimeMillis() < time_before + 20000, equalTo(true));
        assertJenkinsInQuietMode();
    }

    //
    // Scenario - quiet-down is called without block when executor is running
    // Result - CLI call result is available immediately, execution won't be affected
    //
    @Test
    void quietDownShouldSuccessAndRunningExecutor() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        final CLICommandInvoker.Result result = command
                .asUser(ADMIN)
                .invoke();
        assertThat(result, succeededSilently());
        assertJenkinsInQuietMode();
        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        assertJenkinsInQuietMode();
    }

    //
    // Scenario - quiet-down is called with block when executor is running
    // Expected result - CLI call is blocked indefinitely, execution won't be affected
    //
    @Test
    void quietDownShouldSuccessWithBlockAndRunningExecutor() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();
        final OneShotEvent beforeCli = new OneShotEvent();
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        boolean timeoutOccurred = false;
        final FutureTask exec_task = new FutureTask(() -> {
            assertJenkinsNotInQuietMode();
            beforeCli.signal();
            final CLICommandInvoker.Result result = command
                    .asUser(ADMIN)
                    .invokeWithArgs("-block");
            fail("Should never return from previous CLI call!");
            return null;
        });
        try {
            threadPool.submit(exec_task);
            beforeCli.block();
            assertJenkinsInQuietMode();
            exec_task.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeoutOccurred = true;
        }
        if (!timeoutOccurred)
            fail("Missing timeout for CLI call");

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        exec_task.cancel(true);
        assertJenkinsInQuietMode();
    }

    //
    // Scenario - quiet-down is called with block and zero timeout when executor is running
    // Expected result - CLI call is blocked indefinitely, execution won't be affected
    //
    @Test
    void quietDownShouldSuccessWithBlockAndZeroTimeoutAndRunningExecutor() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();
        final OneShotEvent beforeCli = new OneShotEvent();
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        boolean timeoutOccurred = false;
        final FutureTask exec_task = new FutureTask(() -> {
            assertJenkinsNotInQuietMode();
            beforeCli.signal();
            final CLICommandInvoker.Result result = command
                    .asUser(ADMIN)
                    .invokeWithArgs("-block", "-timeout", "0");
            fail("Should never return from previous CLI call!");
            return null;
        });
        try {
            threadPool.submit(exec_task);
            beforeCli.block();
            assertJenkinsInQuietMode();
            exec_task.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeoutOccurred = true;
        }
        if (!timeoutOccurred)
            fail("Missing timeout for CLI call");

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        exec_task.cancel(true);
        assertJenkinsInQuietMode();
    }

    //
    // Scenario - quiet-down is called with block and a timeout when executor is running
    // Expected result - CLI call return after TIMEOUT seconds, execution won't be affected
    //
    @Test
    void quietDownShouldSuccessWithBlockPlusExpiredTimeoutAndRunningExecutor() throws Exception {
        final int TIMEOUT = 5000;
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();
        final OneShotEvent beforeCli = new OneShotEvent();
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        final FutureTask exec_task = new FutureTask(() -> {
            assertJenkinsNotInQuietMode();
            final long time_before = System.currentTimeMillis();
            beforeCli.signal();
            final CLICommandInvoker.Result result = command
                    .asUser(ADMIN)
                    .invokeWithArgs("-block", "-timeout", Integer.toString(TIMEOUT));
            assertThat(result, succeededSilently());
            assertThat(System.currentTimeMillis() > time_before + TIMEOUT, equalTo(true));
            assertJenkinsInQuietMode();
            return null;
        });
        threadPool.submit(exec_task);
        beforeCli.block();
        assertJenkinsInQuietMode();
        try {
            exec_task.get(2 * TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("Blocking call didn't finish after timeout!", e);
        }
        assertThat(exec_task.isDone(), equalTo(true));
        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        assertJenkinsInQuietMode();
    }

    //
    // Scenario - quiet-down is called with block and a timeout when executor is running
    // Expected result - CLI call shouldn't return (killed by other thread), execution won't be affected
    //
    @Test
    void quietDownShouldSuccessWithBlockPlusNonExpiredTimeoutAndRunningExecutor() throws Exception {
        final int TIMEOUT = 5000;
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();
        final OneShotEvent beforeCli = new OneShotEvent();
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        final FutureTask<Void> exec_task = new FutureTask<>(() -> {
            assertJenkinsNotInQuietMode();
            beforeCli.signal();
            final CLICommandInvoker.Result result = command
                    .asUser(ADMIN)
                    .invokeWithArgs("-block", "-timeout", Integer.toString(2 * TIMEOUT));
            fail("Blocking call shouldn't finish, should be killed by called thread!");
            return null;
        });
        threadPool.submit(exec_task);
        beforeCli.block();
        assertJenkinsInQuietMode();
        boolean timeoutOccurred = false;
        try {
            exec_task.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeoutOccurred = true;
        }
        if (!timeoutOccurred)
            fail("Missing timeout for CLI call");

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        assertJenkinsInQuietMode();
        logging.record(XmlFile.class, Level.FINEST);
    }

    //
    // Scenario - quiet-down is called with block when executor is finishing
    // Expected result - CLI call finish and the execution too
    //
    @Test
    void quietDownShouldSuccessWithBlockAndFinishingExecutor() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();
        final OneShotEvent beforeCli = new OneShotEvent();
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds(), hasSize(1));

        boolean timeoutOccurred = false;
        final FutureTask exec_task = new FutureTask(() -> {
            assertJenkinsNotInQuietMode();
            final long time_before = System.currentTimeMillis();
            beforeCli.signal();
            final CLICommandInvoker.Result result = command
                    .asUser(ADMIN)
                    .invokeWithArgs("-block");
            assertThat(result, succeededSilently());
            assertThat(System.currentTimeMillis() > time_before + 1000, equalTo(true));
            assertJenkinsInQuietMode();
            return null;
        });
        threadPool.submit(exec_task);
        beforeCli.block();
        assertJenkinsInQuietMode();

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        assertJenkinsInQuietMode();

        get(exec_task);

        assertJenkinsInQuietMode();
    }

    //
    // Scenario - quiet-down is called with block and timeout when executor is finishing
    // Expected result - CLI call finish and the execution too
    //
    @Test
    void quietDownShouldSuccessWithBlockAndNonExpiredTimeoutAndFinishingExecutor() throws Exception {
        final int TIMEOUT = 5000;
        final FreeStyleProject project = j.createFreeStyleProject("aProject");
        final ExecutorService threadPool = Executors.newSingleThreadExecutor();
        final OneShotEvent beforeCli = new OneShotEvent();
        final OneShotEvent finish = new OneShotEvent();
        final FreeStyleBuild build = OnlineNodeCommandTest.startBlockingAndFinishingBuild(project, finish);

        final FutureTask exec_task = new FutureTask(() -> {
            assertJenkinsNotInQuietMode();
            final long time_before = System.currentTimeMillis();
            beforeCli.signal();
            final CLICommandInvoker.Result result = command
                    .asUser(ADMIN)
                    .invokeWithArgs("-block", "-timeout", Integer.toString(TIMEOUT));
            assertThat(result, succeededSilently());
            assertThat(System.currentTimeMillis() > time_before + 1000, equalTo(true));
            assertThat(System.currentTimeMillis() < time_before + TIMEOUT, equalTo(true));
            assertJenkinsInQuietMode();
            return null;
        });
        threadPool.submit(exec_task);
        beforeCli.block();
        assertJenkinsInQuietMode();

        finish.signal();
        j.waitForCompletion(build);
        assertThat(build.isBuilding(), equalTo(false));
        j.assertBuildStatusSuccess(build);
        assertJenkinsInQuietMode();
        get(exec_task);
    }

    /**
     * Will try to get the result and retry for some time before failing.
     */
    private static void get(FutureTask exec_task) {
        await().atMost(10, TimeUnit.SECONDS)
                .until(exec_task::isDone);
    }

    /**
     * Asserts if Jenkins is in quiet mode.
     * Will retry for some time before actually failing.
     */
    private void assertJenkinsInQuietMode() {
        assertJenkinsInQuietMode(j);
    }

    /**
     * Asserts if Jenkins is <strong>not</strong> in quiet mode.
     * Will retry for some time before actually failing.
     */
    private void assertJenkinsNotInQuietMode() {
        assertJenkinsNotInQuietMode(j);
    }

    /**
     * Asserts if Jenkins is in quiet mode, retrying for some time before failing.
     */
    public static void assertJenkinsInQuietMode(final JenkinsRule j) {
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> Queue.isBlockedByShutdown(task));
    }

    /**
     * Asserts if Jenkins is <strong>not</strong> in quiet mode, retrying for some time before failing.
     */
    public static void assertJenkinsNotInQuietMode(final JenkinsRule j) {
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> !Queue.isBlockedByShutdown(task));
    }
}
