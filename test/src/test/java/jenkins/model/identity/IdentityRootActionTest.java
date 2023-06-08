package jenkins.model.identity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
