package jenkins.security;

import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class Security2777Test {
    public static final String ACTION_URL = "security2777";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testView() throws IOException {
        final JenkinsRule.WebClient wc = j.createWebClient();

        // no exception on action index page
        wc.getPage(wc.getContextPath() + ACTION_URL);

        final FailingHttpStatusCodeException ex2 = Assert.assertThrows("no icon, no response", FailingHttpStatusCodeException.class, () -> wc.getPage(wc.getContextPath() + ACTION_URL + "/fragmentWithoutIcon"));
        Assert.assertEquals("it's 404", 404, ex2.getStatusCode());

        final FailingHttpStatusCodeException ex3 = Assert.assertThrows("icon, still no response", FailingHttpStatusCodeException.class, () -> wc.getPage(wc.getContextPath() + ACTION_URL + "/fragmentWithIcon"));
        Assert.assertEquals("it's 404", 404, ex3.getStatusCode());
    }

    @TestExtension
    public static class ViewHolder implements UnprotectedRootAction {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return ACTION_URL;
        }
    }
}
