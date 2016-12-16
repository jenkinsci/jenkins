package hudson.diagnosis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.model.User;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonHomeDiskUsageMonitorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void flow() throws Exception {
        // manually activate this
        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();
        mon.activated = true;

        // clicking yes should take us to somewhere
        j.submit(getForm(mon), "yes");
        assertTrue(mon.isEnabled());

        // now dismiss
        // submit(getForm(mon),"no"); TODO: figure out why this test is fragile
        mon.doAct("no");
        assertFalse(mon.isEnabled());

        // and make sure it's gone
        try {
            fail(getForm(mon)+" shouldn't be there");
        } catch (ElementNotFoundException e) {
            // as expected
        }
    }

    @Issue("SECURITY-371")
    @Test
    public void noAccessForNonAdmin() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        // TODO: Use MockAuthorizationStrategy in later versions
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        realm.addGroups("bob", "users");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admins");
        auth.add(Jenkins.READ, "users");
        j.jenkins.setAuthorizationStrategy(auth);

        WebRequest request = new WebRequest(wc.createCrumbedUrl("administrativeMonitor/hudsonHomeIsFull/act"), HttpMethod.POST);
        NameValuePair param = new NameValuePair("no", "true");
        request.setRequestParameters(Collections.singletonList(param));

        HudsonHomeDiskUsageMonitor mon = HudsonHomeDiskUsageMonitor.get();

        try {
            wc.login("bob");
            wc.getPage(request);
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
        assertTrue(mon.isEnabled());

        try {
            WebRequest getIndex = new WebRequest(wc.createCrumbedUrl("administrativeMonitor/hudsonHomeIsFull"), HttpMethod.GET);
            wc.getPage(getIndex);
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }

        wc.login("administrator");
        wc.getPage(request);
        assertFalse(mon.isEnabled());

    }

    /**
     * Gets the warning form.
     */
    private HtmlForm getForm(HudsonHomeDiskUsageMonitor mon) throws IOException, SAXException {
        HtmlPage p = j.createWebClient().goTo("manage");
        return p.getFormByName(mon.id);
    }
}
