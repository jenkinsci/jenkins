package jenkins.triggers;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.triggers.Messages;
import hudson.triggers.SlowTriggerAdminMonitor;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class TriggerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule l = new LoggerRule();

    @Test
    public void testTimerSpentToMuchTime() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test");

        l.record(Logger.getLogger(Trigger.class.getName()), Level.WARNING);
        l.capture(10);

        p.addTrigger(new BadTimerTrigger("* * * * *"));
        p.doReload();
        while (p.getBuildByNumber(1) == null) {
            Thread.sleep(100);
        }
        j.waitUntilNoActivity();
        assertThat(l.getMessages().toArray(new String[0]) [0],
                containsString("Trigger " + BadTimerTrigger.class.getName()
                        + ".run() triggered by " + p.toString() + " spent too much time "));
    }

    public static class BadTimerTrigger extends TimerTrigger {

        private static final Logger LOGGER = Logger.getLogger(BadTimerTrigger.class.getName());

        BadTimerTrigger(@NonNull final String specs) throws ANTLRException {
            super(specs);
        }

        @Override
        public void run() {
            if (job == null) {
                return;
            }

            try {
                Thread.sleep(Trigger.CRON_THRESHOLD + 100);
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Interrupted: ", e);
            }
            job.scheduleBuild(0, new TimerTriggerCause());
        }

        @Extension
        public static class DescriptorImpl extends TriggerDescriptor {
            public boolean isApplicable(Item item) {
                return item instanceof BuildableItem;
            }

            public String getDisplayName() {
                return Messages.TimerTrigger_DisplayName();
            }
        }
    }

    @Test
    public void testSlowTriggerAdminMonitorMaxExtries() throws Exception {
        SlowTriggerAdminMonitor stam = SlowTriggerAdminMonitor.getInstance();
        SlowTriggerAdminMonitor.MAX_ENTRIES = 5;
        stam.clear();
        for (int i = 1; i <= SlowTriggerAdminMonitor.MAX_ENTRIES; i++) {
            stam.report("Test"+i, "Test"+i);
            Thread.sleep(1000);
        }
        slowTriggerAdminMonitorCheck(stam, 1, SlowTriggerAdminMonitor.MAX_ENTRIES);

        // Replace the oldest entries
        stam.report("Test" + (SlowTriggerAdminMonitor.MAX_ENTRIES + 1),
                "Test" + (SlowTriggerAdminMonitor.MAX_ENTRIES + 1));
        slowTriggerAdminMonitorCheck(stam, 2, SlowTriggerAdminMonitor.MAX_ENTRIES + 1);
        stam.report("Test" + (SlowTriggerAdminMonitor.MAX_ENTRIES + 2),
                "Test" + (SlowTriggerAdminMonitor.MAX_ENTRIES + 2));
        slowTriggerAdminMonitorCheck(stam, 3, SlowTriggerAdminMonitor.MAX_ENTRIES + 2);

        // Replace other entry
        stam.report("Test5", "Test5");
        slowTriggerAdminMonitorCheck(stam, 3, SlowTriggerAdminMonitor.MAX_ENTRIES + 2);
    }

    private void slowTriggerAdminMonitorCheck(final SlowTriggerAdminMonitor stam, final int start, final int number) {
        assertThat(stam.getErrors().size(), equalTo(number - start + 1));
        for (int i = start; i <= number; i++) {
            assertThat("should contain a 'Test" + i + "' entry",
                    stam.getErrors().containsKey("Test" + i), equalTo(true));
        }
    }
}
