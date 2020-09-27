package jenkins.util;

import groovy.lang.GroovyClassLoader;
import hudson.triggers.SafeTimerTask;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
            protected void doRun() {
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

    /**
     * Launch two tasks which can only complete
     * by running doRun() concurrently.
     */
    @Test
    @Issue("JENKINS-49206")
    public void timerBogusClassloader() throws Exception {
        final int threadCount = 10;  // Twice Timer pool size to ensure we end up creating at least one new thread
        final CountDownLatch startLatch = new CountDownLatch(threadCount);

        final ClassLoader[] contextClassloaders = new ClassLoader[threadCount];
        ScheduledFuture<?>[] futures = new ScheduledFuture[threadCount];
        final ClassLoader bogusClassloader = new GroovyClassLoader();

        Runnable timerTest = new Runnable() {
            @Override
            public void run() {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(bogusClassloader);
                ScheduledExecutorService exec = Timer.get();
                for (int i=0; i<threadCount; i++) {
                    final int j = i;
                    futures[j] = exec.schedule(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                startLatch.countDown();
                                contextClassloaders[j] = Thread.currentThread().getContextClassLoader();
                            } catch (Exception ex) {
                                throw  new RuntimeException(ex);
                            }
                        }
                    }, 0, TimeUnit.SECONDS);
                }
                Thread.currentThread().setContextClassLoader(cl);
            }
        };

        Thread t = new Thread(timerTest);
        t.start();
        t.join(1000L);

        for (int i=0; i<threadCount; i++) {
            futures[i].get();
            assertEquals(Timer.class.getClassLoader(), contextClassloaders[i]);
        }
    }
}
