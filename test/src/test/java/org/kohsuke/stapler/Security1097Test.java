package org.kohsuke.stapler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import jakarta.servlet.ServletException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.json.SubmittedForm;

@WithJenkins
class Security1097Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testPostWorks() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage htmlPage1 = webClient.goTo("security1097/post1");
        j.submit(htmlPage1.getFormByName("config"));

        final HtmlPage htmlPage2 = webClient.goTo("security1097/post2");
        j.submit(htmlPage2.getFormByName("config"));
    }

    @Test
    void testGet1Fails() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage htmlPage = webClient.goTo("security1097/get1");
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(htmlPage.getFormByName("config")));
        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void testGet2Fails() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage htmlPage = webClient.goTo("security1097/get2");
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(htmlPage.getFormByName("config")));
        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, e.getStatusCode());
    }

    @Test
    void testGetWorksWithEscapeHatch() throws Exception {
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
            allowed_http_verbs_for_forms.set(null, List.of("POST"));
        }
    }

    @Extension
    public static class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "security1097";
        }

        /* Deliberate CSRF vulnerability */
        public void doConfigSubmit1(StaplerRequest2 req) throws ServletException {
            req.getSubmittedForm();
        }

        /* Alternative implementation: */
        public void doConfigSubmit2(@SubmittedForm JSONObject form) {
            /* no-op */
        }
    }
}
