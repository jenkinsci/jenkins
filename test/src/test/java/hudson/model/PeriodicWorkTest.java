package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PeriodicWorkTest {

    private JenkinsRule jr;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jr = rule;
    }

    @Test
    void newExtensionsAreScheduled() throws Exception {
        TestPeriodicWork tpw = new TestPeriodicWork();

        int size = PeriodicWork.all().size();
        ExtensionList.lookup(PeriodicWork.class).add(tpw);

        assertThat("we have one new PeriodicWork", PeriodicWork.all(), hasSize(size + 1));
        assertThat("The task was run within 15 seconds", tpw.doneSignal.await(15, TimeUnit.SECONDS), is(true));
    }

    private static class TestPeriodicWork extends PeriodicWork {

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
        protected void doRun() {
            doneSignal.countDown();
        }
    }
}
