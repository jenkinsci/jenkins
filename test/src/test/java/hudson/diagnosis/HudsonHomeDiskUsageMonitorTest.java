package hudson.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.net.HttpURLConnection;
import java.net.URI;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class HudsonHomeDiskUsageMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("SECURITY-371")
    @Test
    void noAccessForNonAdmin() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        // TODO: Use MockAuthorizationStrategy in later versions
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        realm.addGroups("bob", "users");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admins");
        auth.add(Jenkins.READ, "users");
        j.jenkins.setAuthorizationStrategy(auth);

        User bob = User.getById("bob", true);
        User administrator = User.getById("administrator", true);

        WebRequest request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/disable").toURL(), HttpMethod.POST);

        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();

        wc.withBasicApiToken(bob);
        Page p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        assertTrue(mon.isEnabled());

        WebRequest requestReadOnly = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull").toURL(), HttpMethod.GET);
        p = wc.getPage(requestReadOnly);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, p.getWebResponse().getStatusCode());

        wc.withBasicApiToken(administrator);
        request = new WebRequest(new URI(wc.getContextPath() + "administrativeMonitor/hudsonHomeIsFull/disable").toURL(), HttpMethod.POST);
        p = wc.getPage(request);
        assertEquals(HttpURLConnection.HTTP_OK, p.getWebResponse().getStatusCode());
        assertFalse(mon.isEnabled());
    }
}
