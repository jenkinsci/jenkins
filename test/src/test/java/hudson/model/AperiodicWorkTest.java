package hudson.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.ExtensionList;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AperiodicWorkTest {

    @Rule
    public JenkinsRule jr = new JenkinsRule();


    @Test
    public void newExtensionsAreScheduled() throws Exception {
        TestAperiodicWork tapw = new TestAperiodicWork();

        int size = AperiodicWork.all().size();
        ExtensionList.lookup(AperiodicWork.class).add(tapw);

        assertThat("we have one new AperiodicWork", AperiodicWork.all(), hasSize(size+1));
        assertThat("The task was run within 15 seconds", tapw.doneSignal.await(15, TimeUnit.SECONDS), is(true));
    }

    private static class TestAperiodicWork extends AperiodicWork {

        CountDownLatch doneSignal = new CountDownLatch(1);

        @Override
        public long getRecurrencePeriod() {
            // should make this only ever run once initially for testing.
            return Long.MAX_VALUE;
        }

        @Override
        public long getInitialDelay() {
            // Don't delay just run it!
            return 0L;
        }

        @Override
        public AperiodicWork getNewInstance() {
            return this;
        }

        @Override
        protected void doAperiodicRun() {
            doneSignal.countDown();
        }
    }
}