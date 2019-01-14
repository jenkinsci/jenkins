package jenkins.triggers;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.BuildableItem;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.triggers.Messages;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerAdminMonitor;
import hudson.triggers.TriggerDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.containsString;
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
                containsString("cron trigger " + BadTimerTrigger.class.getName()
                        + ".run() triggered by " + p.toString() + " spent too  much time "));
        j.interactiveBreak();
    }

    @Test
    public void myTest() throws Exception {
        TriggerAdminMonitor.getInstance().report("Test", "Test message");
        j.interactiveBreak();
    }

    public static class BadTimerTrigger extends TimerTrigger {

        private static final Logger LOGGER = Logger.getLogger(BadTimerTrigger.class.getName());

        BadTimerTrigger(@Nonnull final String specs) throws ANTLRException {
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
}