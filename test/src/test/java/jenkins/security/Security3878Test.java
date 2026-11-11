package jenkins.security;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.net.URL;
import java.util.List;
import jenkins.util.ProgressiveRendering;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import org.htmlunit.Page;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-3878")
@WithJenkins
class Security3878Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void boundScriptEndpointDoesNotContainCrumb() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");

            URL scriptUrl = new URL(j.getURL(), "$stapler/bound/script/whatever?var=varname&methods=methodname");
            Page script = wc.getPage(new WebRequest(scriptUrl));
            String content = script.getWebResponse().getContentAsString();

            /* DefaultCrumbIssuer calls Util#toHexString; assert with conservative length */
            assertThat(content, not(matchesPattern(".*[0-9a-f]{16}.*")));

            /* TestCrumbIssuer */
            assertThat(content, not(containsString("test")));

            /* The response must contain the safe JS expression instead. */
            assertThat(content, containsString("document.head.dataset.crumbValue"));
        }
    }

    @Test
    void boundScriptEndpointFunctionalWithCsrfDisabled() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");

            URL scriptUrl = new URL(j.getURL(), "$stapler/bound/script/whatever?var=varname&methods=methodname");
            Page script = wc.getPage(new WebRequest(scriptUrl));
            String content = script.getWebResponse().getContentAsString();

            assertThat(content, containsString("makeStaplerProxy("));
            assertThat(content, not(containsString("document.head.dataset.crumbValue")));

            /* TestCrumbIssuer */
            assertThat(content, not(containsString("test")));

            /* Stapler's CrumbIssuer via CrumbIssuer#initStaplerCrumbIssuer */
            assertThat(content, containsString("','',["));
        }
    }

    @Test
    void progressiveRenderingWorksWithCsrfEnabled() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        assertProgressiveRenderingCompletes();
    }

    @Test
    void progressiveRenderingWorksWithCsrfDisabled() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);
        assertProgressiveRenderingCompletes();
    }

    private void assertProgressiveRenderingCompletes() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");
            HtmlPage page = wc.goTo("security3878");

            // 0 means all background JS tasks (proxy start + news poll) completed without error.
            assertEquals(0, wc.waitForBackgroundJavaScript(5_000));

            // The progress bar element is hidden by progressiveRendering.js when status == "done".
            ScriptResult progressBarDisplay = page.executeJavaScript(
                    "document.getElementById("
                            + "document.querySelector('.progressive-rendering-information-holder').dataset.id"
                            + ").style.display");
            assertEquals("none", progressBarDisplay.getJavaScriptResult().toString());

            // The callback received the data through the bound proxy.
            DomElement result = page.getElementById("security3878-result");
            assertThat(result.asNormalizedText(), containsString("rendered"));
        }
    }

    @TestExtension
    public static class ProgressiveAction extends InvisibleAction implements UnprotectedRootAction {
        @Override
        public String getUrlName() {
            return "security3878";
        }

        public ProgressiveRendering getHandler() {
            return new ProgressiveRendering() {
                @Override
                protected void compute() {
                    progress(1);
                }

                @Override
                protected JSON data() {
                    return JSONArray.fromObject(List.of("rendered"));
                }
            };
        }
    }
}
