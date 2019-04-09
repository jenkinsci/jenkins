package hudson;

import hudson.model.UpdateCenter;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.Ignore;

//TODO merge it within PluginTest after the security release
public class PluginSEC925Test {
    
    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @Ignore("TODO observed to fail in CI with 404 due to external UC issues")
    @Test
    @Issue("SECURITY-925")
    public void preventTimestamp2_toBeServed() throws Exception {
        // impossible to use installDetachedPlugin("credentials") since we want to have it exploded like with WAR
        Jenkins.getInstance().getUpdateCenter().getSites().get(0).updateDirectlyNow(false);
        List<Future<UpdateCenter.UpdateCenterJob>> pluginInstalled = r.jenkins.pluginManager.install(Arrays.asList("credentials"), true);
    
        for (Future<UpdateCenter.UpdateCenterJob> job : pluginInstalled) {
            job.get();
        }
        r.createWebClient().assertFails("plugin/credentials/.timestamp2", HttpServletResponse.SC_BAD_REQUEST);
    }
}
