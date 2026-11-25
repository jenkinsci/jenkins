package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * How a build page tells who started a build, when the user exists and when there is no such user, for example after
 * the user was deleted.
 */
@WithJenkins
class UserIdCausePageTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User.getById("foo", true);
        User.getById("alice", true).addProperty(new UserExperimentalFlagsProperty(Map.of("new-build-page.flag", "true")));
    }

    @Test
    void buildPageLinksToTheUser() throws Exception {
        FreeStyleBuild build = buildStartedBy("foo");

        try (JenkinsRule.WebClient wc = j.createWebClient().login("foo")) {
            DomElement link = wc.getPage(build).querySelector("a.model-link[href$='/user/foo']");
            assertThat(link, notNullValue());
            assertThat(link.getTextContent(), is("foo"));
        }
    }

    @Test
    void buildPageOfAMissingUserHasNoLink() throws Exception {
        FreeStyleBuild build = buildStartedBy("missing");

        try (JenkinsRule.WebClient wc = j.createWebClient().login("foo")) {
            HtmlPage page = wc.getPage(build);
            assertThat(page.querySelectorAll("a[href$='/null']"), empty());
            assertThat(page.getBody().getTextContent(), containsString("Started by anonymous user"));
        }
    }

    @Test
    void newBuildPageLinksToTheUser() throws Exception {
        FreeStyleBuild build = buildStartedBy("foo");

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            DomElement item = wc.getPage(build).querySelector("a.jenkins-details__item[href$='/user/foo']");
            assertThat(item, notNullValue());
            assertThat(item.getTextContent(), containsString("Started by foo"));
        }
    }

    @Test
    void newBuildPageOfAMissingUserHasNoLinkAndNoBrokenAvatar() throws Exception {
        FreeStyleBuild build = buildStartedBy("missing");

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            HtmlPage page = wc.getPage(build);
            DomNode item = page.querySelectorAll(".jenkins-details__item").stream()
                    .filter(node -> node.getTextContent().contains("Started by"))
                    .findFirst()
                    .orElseThrow();
            assertThat(page.querySelectorAll("img.jenkins-avatar"), empty());
            assertThat(item.getNodeName(), is("div"));
            assertThat(item.getTextContent(), containsString("Started by anonymous user"));
        }
    }

    private FreeStyleBuild buildStartedBy(String userId) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        return j.assertBuildStatusSuccess(p.scheduleBuild2(0, new CauseAction(new Cause.UserIdCause(userId))));
    }
}
