package jenkins.model.identity;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class IdentityRootActionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void ui() throws Exception {
        HtmlPage p = r.createWebClient().goTo("instance-identity");
        assertThat(p.getElementById("fingerprint").getTextContent(),
                containsString(ExtensionList.lookup(UnprotectedRootAction.class).get(IdentityRootAction.class).getFingerprint()));
    }
}
