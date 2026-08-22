package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.net.URL;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests that links on the AllView empty state page work correctly even when
 * accessed via /view/all/ (where relative links would incorrectly resolve
 * under /view/all/ instead of the Jenkins root).
 */
@WithJenkins
class AllViewEmptyStateLinksTest {

    @Test
    void linksWorkFromViewAll(JenkinsRule j) throws Exception {
        j.jenkins.setNumExecutors(0);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo("view/all/");
            for (String linkText : List.of("Create a job", "Set up an agent", "Configure a cloud")) {
                HtmlAnchor link = page.getAnchorByText(linkText);
                String resolved = link.click().getUrl().toString();
                assertThat(linkText + " resolves under /view/all/", resolved, not(containsString("/view/all/")));
            }
        }
    }

    @Test
    void loginLinkWorksFromViewAllForAnonymous(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone());

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo("view/all/");
            HtmlAnchor link = page.getAnchorByText("Log in to Jenkins");
            URL resolved = link.click().getUrl();
            assertThat(resolved.getPath(), startsWith(j.contextPath + "/login"));
        }
    }
}
