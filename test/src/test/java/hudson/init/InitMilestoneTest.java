package hudson.init;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.ArrayList;
import java.util.List;
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

        List<InitMilestone> attained = r.jenkins.getExtensionList(Initializers.class).get(0).getAttained();

        // TODO assert that they are contained in order, currently it generally works but flakes after some time
        assertThat(attained, containsInAnyOrder(
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

        public List<InitMilestone> getAttained() {
            return new ArrayList<>(attained);
        }
    }

}
