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
    public void retainMasterLabelWhenNoSlaveDefined() throws Exception {
        Jenkins jenkins = j.getInstance();

        assertEquals("Test is for master with no slave", 1, jenkins.getComputers().length);
        
        // set our own label & mode
        final String myTestLabel = "TestLabelx0123";
        jenkins.setLabelString(myTestLabel);
        jenkins.setMode(Mode.EXCLUSIVE);
        
        // call global config page
        j.configRoundtrip();
        
        // make sure settings were not lost
        assertEquals("Master's label is lost", myTestLabel, jenkins.getLabelString());
        assertEquals("Master's mode is lost", Mode.EXCLUSIVE, jenkins.getMode());
    }
}
