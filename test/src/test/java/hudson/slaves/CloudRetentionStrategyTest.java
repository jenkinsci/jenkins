/*
 * The MIT License
 *
 * Copyright 2026 Preetham.
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

package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CloudRetentionStrategyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-27215")
    void checkDoesNotHoldQueueLockDuringTermination() throws Exception {
        CountDownLatch terminateStartedLatch = new CountDownLatch(1);
        CountDownLatch unblockTerminateLatch = new CountDownLatch(1);
        CountDownLatch terminateCompletedLatch = new CountDownLatch(1);
        AtomicInteger terminateCallCount = new AtomicInteger(0);

        SlowCloudSlave slave = new SlowCloudSlave(
                "slow-cloud-agent",
                j.createTmpDir().getPath(),
                j.createComputerLauncher(null),
                terminateStartedLatch,
                unblockTerminateLatch,
                terminateCompletedLatch,
                terminateCallCount,
                false
        );

        j.jenkins.addNode(slave);
        AbstractCloudComputer<?> computer = (AbstractCloudComputer<?>) slave.toComputer();
        assertNotNull(computer);

        CloudRetentionStrategy strategy = (CloudRetentionStrategy) slave.getRetentionStrategy();

        // Trigger retention check under Queue lock
        Queue.runWithLock(() -> strategy.check(computer));

        // Wait until background termination task enters _terminate()
        assertTrue(terminateStartedLatch.await(10, TimeUnit.SECONDS), "Termination should start in background");

        // Verify computer stops accepting tasks
        assertFalse(computer.isAcceptingTasks(), "Computer should no longer accept tasks during termination");

        // While _terminate() is STILL blocked on unblockTerminateLatch, verify another thread can acquire Queue.lock
        AtomicBoolean lockAcquiredByOtherThread = new AtomicBoolean(false);
        Thread otherThread = new Thread(() -> Queue.runWithLock(() -> lockAcquiredByOtherThread.set(true)));
        otherThread.start();
        otherThread.join(5000);

        assertTrue(lockAcquiredByOtherThread.get(), "Queue.lock must NOT be held while _terminate() is blocked");

        // Verify duplicate check calls do not trigger second termination
        Queue.runWithLock(() -> strategy.check(computer));
        assertEquals(1, terminateCallCount.get(), "Duplicate termination should not be triggered");

        // Unblock termination and verify cleanup completes
        unblockTerminateLatch.countDown();
        assertTrue(terminateCompletedLatch.await(10, TimeUnit.SECONDS), "Termination task should complete");
    }

    @Test
    @Issue("JENKINS-27215")
    void terminationHandlesExceptionGracefully() throws Exception {
        LoggerRule loggerRule = new LoggerRule().record(CloudRetentionStrategy.class, Level.WARNING).capture(100);

        CountDownLatch terminateStartedLatch = new CountDownLatch(1);
        CountDownLatch unblockTerminateLatch = new CountDownLatch(1);
        CountDownLatch terminateCompletedLatch = new CountDownLatch(1);
        AtomicInteger terminateCallCount = new AtomicInteger(0);

        SlowCloudSlave slave = new SlowCloudSlave(
                "failing-cloud-agent",
                j.createTmpDir().getPath(),
                j.createComputerLauncher(null),
                terminateStartedLatch,
                unblockTerminateLatch,
                terminateCompletedLatch,
                terminateCallCount,
                true
        );

        j.jenkins.addNode(slave);
        AbstractCloudComputer<?> computer = (AbstractCloudComputer<?>) slave.toComputer();
        assertNotNull(computer);

        CloudRetentionStrategy strategy = (CloudRetentionStrategy) slave.getRetentionStrategy();

        Queue.runWithLock(() -> strategy.check(computer));

        assertTrue(terminateStartedLatch.await(10, TimeUnit.SECONDS), "Termination should start in background");

        // Unblock termination which throws IOException
        unblockTerminateLatch.countDown();

        // Verify the async task completes its execution
        assertTrue(terminateCompletedLatch.await(10, TimeUnit.SECONDS), "Termination task should complete despite exception");
        assertEquals(1, terminateCallCount.get(), "Termination should have been attempted exactly once");

        // Verify that the warning log was produced by CloudRetentionStrategy's catch block
        assertThat(loggerRule, recorded(Level.WARNING, containsString("Failed to terminate failing-cloud-agent")));
    }

    private static class SlowCloudSlave extends AbstractCloudSlave {
        private final transient CountDownLatch terminateStartedLatch;
        private final transient CountDownLatch unblockTerminateLatch;
        private final transient CountDownLatch terminateCompletedLatch;
        private final transient AtomicInteger terminateCallCount;
        private final boolean throwException;

        SlowCloudSlave(
                String name,
                String remoteFS,
                ComputerLauncher launcher,
                CountDownLatch terminateStartedLatch,
                CountDownLatch unblockTerminateLatch,
                CountDownLatch terminateCompletedLatch,
                AtomicInteger terminateCallCount,
                boolean throwException
        ) throws Descriptor.FormException, IOException {
            super(name, remoteFS, launcher);
            setRetentionStrategy(new CloudRetentionStrategy(0));
            this.terminateStartedLatch = terminateStartedLatch;
            this.unblockTerminateLatch = unblockTerminateLatch;
            this.terminateCompletedLatch = terminateCompletedLatch;
            this.terminateCallCount = terminateCallCount;
            this.throwException = throwException;
        }

        @Override
        public AbstractCloudComputer<?> createComputer() {
            return new AbstractCloudComputer<>(this);
        }

        @Override
        protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
            try {
                terminateCallCount.incrementAndGet();
                terminateStartedLatch.countDown();
                unblockTerminateLatch.await();
                if (throwException) {
                    throw new IOException("Simulated cloud API termination failure");
                }
            } finally {
                terminateCompletedLatch.countDown();
            }
        }
    }
}
