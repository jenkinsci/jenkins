package hudson.init;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class InitMilestoneTest {

    private static int order = 0;
    private static InitMilestone[] attained = new InitMilestone[InitMilestone.values().length];

    @Rule
    public JenkinsRule r  = new JenkinsRule();

    @Test
    public void testInitMilestones() {
        assertEquals(attained[0], InitMilestone.STARTED);
        assertEquals(attained[1], InitMilestone.PLUGINS_LISTED);
        assertEquals(attained[2], InitMilestone.PLUGINS_PREPARED);
        assertEquals(attained[3], InitMilestone.PLUGINS_STARTED);
        assertEquals(attained[4], InitMilestone.EXTENSIONS_AUGMENTED);
        assertEquals(attained[5], InitMilestone.SYSTEM_CONFIG_LOADED);
        assertEquals(attained[6], InitMilestone.SYSTEM_CONFIG_ADAPTED);
        assertEquals(attained[7], InitMilestone.JOB_LOADED);
        assertEquals(attained[8], InitMilestone.JOB_CONFIG_ADAPTED);
    }

    @Initializer(after = InitMilestone.STARTED)
    public static void started() {
        attained[order++] = InitMilestone.STARTED;
    }

    @Initializer(after = InitMilestone.PLUGINS_LISTED)
    public static void pluginsListed() {
        attained[order++] = InitMilestone.PLUGINS_LISTED;
    }

    @Initializer(after = InitMilestone.PLUGINS_PREPARED)
    public static void pluginsPrepared() {
        attained[order++] = InitMilestone.PLUGINS_PREPARED;
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void pluginsStarted() {
        attained[order++] = InitMilestone.PLUGINS_STARTED;;
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void extensionsAugmented() {
        attained[order++] = InitMilestone.EXTENSIONS_AUGMENTED;;
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void pluginsSystemConfigLoaded() {
        attained[order++] = InitMilestone.SYSTEM_CONFIG_LOADED;;
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public static void pluginsSystemConfigAdapted() {
        attained[order++] = InitMilestone.SYSTEM_CONFIG_ADAPTED;;
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void jobLoaded() {
        attained[order++] = InitMilestone.JOB_LOADED;;
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public static void jobConfigAdapted() {
        attained[order++] = InitMilestone.JOB_CONFIG_ADAPTED;;
    }

}