package hudson.init;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class InitMilestoneTest {

    @Rule
    public JenkinsRule r  = new JenkinsRule();

    @Test
    public void testInitMilestones() {

        Queue<InitMilestone> attained = r.jenkins.getExtensionList(Initializers.class).get(0).getAttained();

        assertThat(attained, contains(
                InitMilestone.EXTENSIONS_AUGMENTED,
                InitMilestone.SYSTEM_CONFIG_LOADED,
                InitMilestone.SYSTEM_CONFIG_ADAPTED,
                InitMilestone.JOB_LOADED,
                InitMilestone.JOB_CONFIG_ADAPTED));
    }

    // Using @Initializer in static methods to check all the InitMilestones are loaded in all tests instances and make them fail,
    // so using a TestExtension and checking only the InitMilestone after EXTENSION_AUGMENTED
    @TestExtension("testInitMilestones")
    public static class Initializers {
        private Queue<InitMilestone> attained = new ConcurrentLinkedQueue<>();

        @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
        public void extensionsAugmented() {
            attained.offer(InitMilestone.EXTENSIONS_AUGMENTED);
        }

        @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
        public void pluginsSystemConfigLoaded() {
            attained.offer(InitMilestone.SYSTEM_CONFIG_LOADED);
        }

        @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED)
        public void pluginsSystemConfigAdapted() {
            attained.offer(InitMilestone.SYSTEM_CONFIG_ADAPTED);
        }

        @Initializer(after = InitMilestone.JOB_LOADED)
        public void jobLoaded() {
            attained.offer(InitMilestone.JOB_LOADED);
        }

        @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
        public void jobConfigAdapted() {
            attained.offer(InitMilestone.JOB_CONFIG_ADAPTED);
        }

        public Queue<InitMilestone> getAttained() {
            return attained;
        }
    }

}
