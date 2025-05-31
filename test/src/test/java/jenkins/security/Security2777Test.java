package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security2777Test {
    private static final String ACTION_URL = "security2777";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testView() throws IOException {
        final JenkinsRule.WebClient wc = j.createWebClient();

        // no exception on action index page
        wc.getPage(wc.getContextPath() + ACTION_URL);

        final FailingHttpStatusCodeException ex2 = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(wc.getContextPath() + ACTION_URL + "/fragmentWithoutIcon"), "no icon, no response");
        assertEquals(404, ex2.getStatusCode(), "it's 404");

        final FailingHttpStatusCodeException ex3 = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(wc.getContextPath() + ACTION_URL + "/fragmentWithIcon"), "icon, still no response");
        assertEquals(404, ex3.getStatusCode(), "it's 404");
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
