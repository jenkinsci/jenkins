package org.kohsuke.stapler;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.json.SubmittedForm;

public class Security1097Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testPostWorks() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage htmlPage1 = webClient.goTo("security1097/post1");
        j.submit(htmlPage1.getFormByName("config"));

        final HtmlPage htmlPage2 = webClient.goTo("security1097/post2");
        j.submit(htmlPage2.getFormByName("config"));
    }

    @Test(expected = FailingHttpStatusCodeException.class)
    public void testGet1Fails() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage htmlPage = webClient.goTo("security1097/get1");
        j.submit(htmlPage.getFormByName("config"));
    }

    @Test(expected = FailingHttpStatusCodeException.class)
    public void testGet2Fails() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage htmlPage = webClient.goTo("security1097/get2");
        j.submit(htmlPage.getFormByName("config"));
    }

    @Test
    public void testGetWorksWithEscapeHatch() throws Exception {
        final Field allowed_http_verbs_for_forms = RequestImpl.class.getDeclaredField("ALLOWED_HTTP_VERBS_FOR_FORMS");
        allowed_http_verbs_for_forms.setAccessible(true);
        try {
            allowed_http_verbs_for_forms.set(null, Arrays.asList("GET", "POST"));
            final JenkinsRule.WebClient webClient = j.createWebClient();
            final HtmlPage htmlPage1 = webClient.goTo("security1097/get1");
            j.submit(htmlPage1.getFormByName("config"));

            final HtmlPage htmlPage2 = webClient.goTo("security1097/get2");
            j.submit(htmlPage2.getFormByName("config"));
        } finally {
            allowed_http_verbs_for_forms.set(null, Collections.singletonList("POST"));
        }
    }

    @Extension
    public static class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "security1097";
        }

        /* Deliberate CSRF vulnerability */
        public void doConfigSubmit1(StaplerRequest req) throws ServletException {
            req.getSubmittedForm();
        }

        /* Alternative implementation: */
        public void doConfigSubmit2(@SubmittedForm JSONObject form) {
            /* no-op */
        }
    }
}
