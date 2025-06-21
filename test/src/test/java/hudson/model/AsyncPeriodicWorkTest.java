package hudson.model;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import hudson.ExtensionList;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class AsyncPeriodicWorkTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void extraCallGetsIgnored() {
        var instance = ExtensionList.lookupSingleton(AsyncPeriodicWorkTestImpl.class);
        assertThat(instance.getCount(), is(0));
        instance.run();
        // Gets ignored
        instance.run();
        await().until(instance::getCount, is(1));
        await("Should never reach 2 as the second call should be ignored").during(Duration.ofSeconds(2)).until(instance::getCount, lessThan(2));
    }

    @Test
    public void extraCallGetsQueued() {
        var instance = ExtensionList.lookupSingleton(AsyncPeriodicWorkTestImpl.class);
        instance.setQueueIfAlreadyRunning(true);
        assertThat(instance.getCount(), is(0));
        instance.run();
        // Gets queued
        instance.run();
        await("The second call has been queued and executed later").until(instance::getCount, is(2));
    }

    @TestExtension({"extraCallGetsIgnored", "extraCallGetsQueued"})
    public static class AsyncPeriodicWorkTestImpl extends AsyncPeriodicWork {
        private boolean queueIfAlreadyRunning;
        private int count = 0;

        public AsyncPeriodicWorkTestImpl() {
            super(AsyncPeriodicWorkTestImpl.class.getSimpleName());
        }

        @Override
        protected void execute(TaskListener listener) throws InterruptedException {
            count++;
            Thread.sleep(100);
        }

        public int getCount() {
            return count;
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.DAYS.toMillis(1);
        }

        @Override
        public long getInitialDelay() {
            return TimeUnit.DAYS.toMillis(1);
        }

        public void setQueueIfAlreadyRunning(boolean queueIfAlreadyRunning) {
            this.queueIfAlreadyRunning = queueIfAlreadyRunning;
        }

        @Override
        protected boolean queueIfAlreadyRunning() {
            return queueIfAlreadyRunning;
        }
    }
}
