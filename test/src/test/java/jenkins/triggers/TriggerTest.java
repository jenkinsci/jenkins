package jenkins.triggers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.triggers.SlowTriggerAdminMonitor;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class TriggerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule l = new LoggerRule();

    @Before
    public void quicker() {
        Trigger.CRON_THRESHOLD = 3;
    }

    @After
    public void def() {
        Trigger.CRON_THRESHOLD = 30;
    }

    @Test
    public void testTimerSpentTooMuchTime() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test");

        l.record(Logger.getLogger(Trigger.class.getName()), Level.WARNING);
        l.capture(10);

        final BadTimerTrigger trigger = new BadTimerTrigger("* * * * *");
        p.addTrigger(trigger);
        p.doReload();
        while (p.getBuildByNumber(1) == null) {
            Thread.sleep(100);
        }
        j.waitUntilNoActivity();
        assertThat(l.getMessages().toArray(new String[0]) [0],
                containsString("Trigger '" + trigger.getDescriptor().getDisplayName() + "' triggered by '" + p.getFullDisplayName() + "' (" + p.getFullName() + ") spent too much time "));
    }

    public static class BadTimerTrigger extends TimerTrigger {

        private static final Logger LOGGER = Logger.getLogger(BadTimerTrigger.class.getName());

        BadTimerTrigger(@NonNull final String specs) {
            super(specs);
        }


        @Override
        public void run() {
            if (job == null) {
                return;
            }

            try {
                Thread.sleep(Trigger.CRON_THRESHOLD * 1000 + 100);
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Interrupted: ", e);
            }
            job.scheduleBuild(0, new TimerTriggerCause());
        }

        @Extension
        public static class DescriptorImpl extends TriggerDescriptor {
            @Override
            public boolean isApplicable(Item item) {
                return item instanceof BuildableItem;
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "Bad";
            }
        }
    }

    public static class DummyTrigger extends Trigger {
        public static class DummyTriggerDescriptor extends TriggerDescriptor {
            @Override
            public boolean isApplicable(Item item) {
                return true;
            }
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor1 extends DummyTriggerDescriptor {
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor2 extends DummyTriggerDescriptor {
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor3 extends DummyTriggerDescriptor {
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor4 extends DummyTriggerDescriptor {
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor5 extends DummyTriggerDescriptor {
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor6 extends DummyTriggerDescriptor {
        }

        @TestExtension
        public static class BadTimerTriggerDescriptor7 extends DummyTriggerDescriptor {
        }
    }

    @Test
    public void testSlowTriggerAdminMonitorMaxExtries() throws Exception {
        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        SlowTriggerAdminMonitor stam = SlowTriggerAdminMonitor.getInstance();
        SlowTriggerAdminMonitor.MAX_ENTRIES = 5;
        stam.clear();

        stam.report(DummyTrigger.BadTimerTriggerDescriptor1.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        stam.report(DummyTrigger.BadTimerTriggerDescriptor2.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        stam.report(DummyTrigger.BadTimerTriggerDescriptor3.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        stam.report(DummyTrigger.BadTimerTriggerDescriptor4.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        stam.report(DummyTrigger.BadTimerTriggerDescriptor5.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);

        slowTriggerAdminMonitorCheck(stam, 1, SlowTriggerAdminMonitor.MAX_ENTRIES);

        // Replace the oldest entries
        stam.report(DummyTrigger.BadTimerTriggerDescriptor6.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        slowTriggerAdminMonitorCheck(stam, 2, SlowTriggerAdminMonitor.MAX_ENTRIES + 1);
        stam.report(DummyTrigger.BadTimerTriggerDescriptor7.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        slowTriggerAdminMonitorCheck(stam, 3, SlowTriggerAdminMonitor.MAX_ENTRIES + 2);

        // Replace other entry
        stam.report(DummyTrigger.BadTimerTriggerDescriptor5.class, freeStyleProject.getFullName(), 111);
        Thread.sleep(100);
        slowTriggerAdminMonitorCheck(stam, 3, SlowTriggerAdminMonitor.MAX_ENTRIES + 2);
    }

    private void slowTriggerAdminMonitorCheck(final SlowTriggerAdminMonitor stam, final int start, final int number) {
        assertThat(stam.getErrors().size(), equalTo(number - start + 1));
        for (int i = start; i <= number; i++) {
            assertThat("should contain a 'BadTimerTriggerDescriptor" + i + "' entry",
                    stam.getErrors().containsKey("jenkins.triggers.TriggerTest$DummyTrigger$BadTimerTriggerDescriptor" + i), equalTo(true));
        }
    }
}
