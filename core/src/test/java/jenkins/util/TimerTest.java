package jenkins.util;

import static org.junit.Assert.fail;

import hudson.triggers.SafeTimerTask;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TimerTest {

    /**
     * Launch two tasks which can only complete
     * by running doRun() concurrently.
     */
    @Test
    @Issue("JENKINS-19622")
    public void timersArentBlocked() throws InterruptedException {
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch stopLatch = new CountDownLatch(1);

        SafeTimerTask task1 = new SafeTimerTask() {
            @Override
            protected void doRun() throws Exception {
                startLatch.countDown();
                stopLatch.await();
            }
        };

        SafeTimerTask task2 = new SafeTimerTask() {
            @Override
            protected void doRun() throws Exception {
                stopLatch.countDown();
            }
        };

        Timer.get().schedule(task1, 1, TimeUnit.MILLISECONDS);
        startLatch.await();
        Timer.get().schedule(task2, 2, TimeUnit.MILLISECONDS);
        if (! stopLatch.await(10000, TimeUnit.MILLISECONDS)) {
            fail("Failed to run the two tasks simultaneously");
        }

    }
}
