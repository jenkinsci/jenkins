package jenkins.security;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import hudson.model.FreeStyleProject;
import java.net.URL;
import org.htmlunit.Page;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
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
    void buildsPageRendersProgressivelyWithCsrfEnabled() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        /* Ensure there's a build to show */
        FreeStyleProject project = j.createFreeStyleProject("myproject");
        j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");
            HtmlPage page = wc.goTo("view/All/builds");

            // 0 means all background JS tasks (proxy start + news poll) completed without error.
            assertEquals(0, wc.waitForBackgroundJavaScript(5_000));

            // The progress bar element is hidden by progressiveRendering.js when status == "done".
            ScriptResult progressBarDisplay = page.executeJavaScript(
                    "document.getElementById("
                            + "document.querySelector('.progressive-rendering-information-holder').dataset.id"
                            + ").style.display");
            assertEquals("none", progressBarDisplay.getJavaScriptResult().toString());

            // The build table switches from display:none to visible once displayBuilds() runs.
            ScriptResult tableDisplay = page.executeJavaScript(
                    "document.getElementById('projectStatus').style.display");
            assertNotEquals("none", tableDisplay.getJavaScriptResult().toString());

            // At least one row showing the completed build must be present in the table.
            DomElement buildTable = page.getElementById("projectStatus");
            assertThat(buildTable.asNormalizedText(), containsString("myproject"));
        }
    }

    @Test
    void buildsPageRendersProgressivelyWithCsrfDisabled() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);

        /* Ensure there's a build to show */
        FreeStyleProject project = j.createFreeStyleProject("myproject");
        j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");
            HtmlPage page = wc.goTo("view/All/builds");

            assertEquals(0, wc.waitForBackgroundJavaScript(5_000));

            ScriptResult progressBarDisplay = page.executeJavaScript(
                    "document.getElementById("
                            + "document.querySelector('.progressive-rendering-information-holder').dataset.id"
                            + ").style.display");
            assertEquals("none", progressBarDisplay.getJavaScriptResult().toString());

            ScriptResult tableDisplay = page.executeJavaScript(
                    "document.getElementById('projectStatus').style.display");
            assertNotEquals("none", tableDisplay.getJavaScriptResult().toString());

            DomElement buildTable = page.getElementById("projectStatus");
            assertThat(buildTable.asNormalizedText(), containsString("myproject"));
        }
    }
}
