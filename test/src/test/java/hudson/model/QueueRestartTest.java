/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;
import org.jvnet.hudson.test.recipes.LocalData;

class QueueRestartTest {

    @RegisterExtension
    private final RealJenkinsExtension rr = new RealJenkinsExtension();

    @Disabled("Pending JENKINS-68319 sometimes fails, in CI & locally")
    @Issue("JENKINS-68319")
    @LocalData("quietDown")
    @Test
    void persistQueueOnRestart() throws Throwable {
        // Avoid periodic save in order to test that the cleanup process saves the queue.
        rr.javaOptions("-Dhudson.model.Queue.Saver.DELAY_SECONDS=" + TimeUnit.DAYS.toSeconds(1));

        rr.then(QueueRestartTest::queueBuild);
        rr.then(QueueRestartTest::assertBuildFinishes);
    }

    @Disabled("Pending JENKINS-68319 sometimes fails, in CI & locally")
    @Issue("JENKINS-68319")
    @LocalData("quietDown")
    @Test
    void persistQueueOnConsecutiveRestarts() throws Throwable {
        // Avoid periodic save in order to test that the cleanup process saves the queue.
        rr.javaOptions("-Dhudson.model.Queue.Saver.DELAY_SECONDS=" + TimeUnit.DAYS.toSeconds(1));

        rr.then(QueueRestartTest::queueBuild);
        rr.then(QueueRestartTest::assertBuildIsScheduled);
        rr.then(QueueRestartTest::assertBuildFinishes);
    }

    private static void queueBuild(JenkinsRule j) throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.scheduleBuild2(0);
        assertBuildIsScheduled(j);

        // Ensure the queue has not been saved in order to test that the cleanup process saves
        // the queue.
        assertFalse(new File(j.jenkins.getRootDir(), "queue.xml").exists());
    }

    private static void assertBuildFinishes(JenkinsRule j) throws Exception {
        assertBuildIsScheduled(j);
        j.jenkins.doCancelQuietDown();
        FreeStyleProject p = j.jenkins.getItemByFullName("p", FreeStyleProject.class);
        FreeStyleBuild b;
        while ((b = p.getLastBuild()) == null) {
            Thread.sleep(100);
        }
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }

    private static void assertBuildIsScheduled(JenkinsRule j) {
        j.jenkins.getQueue().maintain();
        assertFalse(j.jenkins.getQueue().isEmpty());
    }
}
