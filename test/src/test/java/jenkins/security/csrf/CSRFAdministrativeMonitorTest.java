package jenkins.security.csrf;

import hudson.model.AdministrativeMonitor;
import hudson.security.csrf.DefaultCrumbIssuer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CSRFAdministrativeMonitorTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @Issue("JENKINS-47372")
    public void testWithoutIssuer() {
        j.jenkins.setCrumbIssuer(null);
        
        CSRFAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(CSRFAdministrativeMonitor.class);
        assertTrue("Monitor must not be activated", monitor.isActivated());
    }

    @Test
    @Issue("JENKINS-47372")
    public void testWithIssuer() {
        j.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
        
        CSRFAdministrativeMonitor monitor = j.jenkins.getExtensionList(AdministrativeMonitor.class).get(CSRFAdministrativeMonitor.class);
        assertFalse("Monitor must be activated", monitor.isActivated());
    }
    
}
