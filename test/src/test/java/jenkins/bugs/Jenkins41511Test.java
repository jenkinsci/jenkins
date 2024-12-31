package jenkins.bugs;

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class Jenkins41511Test {

    @BeforeClass
    public static void setUpClass() {
        System.setProperty(Jenkins.class.getName() + ".agentAgentPort", "10000");
        System.setProperty(Jenkins.class.getName() + ".agentAgentPortEnforce", "true");
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        Jenkins.get().setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null));
        j.submit(j.createWebClient().goTo("configureSecurity").getFormByName("config"));
    }
}
