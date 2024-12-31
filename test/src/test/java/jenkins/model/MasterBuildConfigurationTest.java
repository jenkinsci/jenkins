package jenkins.model;

import static org.junit.Assert.assertEquals;

import hudson.model.Node.Mode;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class MasterBuildConfigurationTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-23966")
    public void retainMasterLabelWhenNoAgentDefined() throws Exception {
        Jenkins jenkins = j.getInstance();

        assertEquals("Test is for controller with no agent", 1, jenkins.getComputers().length);

        // set our own label & mode
        final String myTestLabel = "TestLabelx0123";
        jenkins.setLabelString(myTestLabel);
        jenkins.setMode(Mode.EXCLUSIVE);

        // call global config page
        j.configRoundtrip();

        // make sure settings were not lost
        assertEquals("Built in node's label is lost", myTestLabel, jenkins.getLabelString());
        assertEquals("Built in node's mode is lost", Mode.EXCLUSIVE, jenkins.getMode());
    }
}
