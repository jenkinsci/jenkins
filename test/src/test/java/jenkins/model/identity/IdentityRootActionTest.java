package jenkins.model.identity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class IdentityRootActionTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void ui() throws Exception {
        HtmlPage p = r.createWebClient().goTo("instance-identity");
        assertThat(p.getElementById("fingerprint").getTextContent(),
                containsString(ExtensionList.lookup(UnprotectedRootAction.class).get(IdentityRootAction.class).getFingerprint()));
    }
}
