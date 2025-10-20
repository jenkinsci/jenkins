package jenkins.bugs;

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Jenkins41511Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeAll
    static void setUpClass() {
        System.setProperty(Jenkins.class.getName() + ".slaveAgentPort", "10000");
        System.setProperty(Jenkins.class.getName() + ".slaveAgentPortEnforce", "true");
    }

    @Test
    void configRoundTrip() throws Exception {
        Jenkins.get().setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null));
        j.submit(j.createWebClient().goTo("configureSecurity").getFormByName("config"));
    }
}
