package jenkins.security;

import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class SecurityContextExecutorServiceTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    protected void first() throws Exception {
        j.createDummySecurityRealm();
        System.out.println(j.jenkins.getSecurityRealm());
    }
    
    public void testSecurity() throws Exception {

      
    }

}
