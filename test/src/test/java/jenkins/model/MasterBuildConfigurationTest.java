package jenkins.model;

import static org.junit.Assert.assertTrue;

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
        
        assertTrue("Test is for master with no slave", jenkins.getComputers().length == 1);
        
        // set our own label & mode
        final String myTestLabel = "TestLabelx0123";
        jenkins.setLabelString(myTestLabel);
        jenkins.setMode(Mode.EXCLUSIVE);
        
        // call global config page
        j.configRoundtrip();
        
        // make sure settings were not lost
        assertTrue("Master's label is lost", myTestLabel.equals(jenkins.getLabelString()));
        assertTrue("Master's mode is lost", Mode.EXCLUSIVE.equals(jenkins.getMode()));
    }
}
