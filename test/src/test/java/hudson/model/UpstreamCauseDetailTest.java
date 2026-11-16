package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Tab;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UpstreamCauseDetailTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void upstreamLinkKeepsTheCurrentTab() throws Exception {
        FreeStyleBuild downstream = buildDownstreamOf(j.createFreeStyleProject("upstream"));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            List<String> links = detailLinks(wc.getPage(downstream, "sample/"));
            assertThat(links, hasItem(j.contextPath + "/job/upstream/1/sample/"));
        }
    }

    @Test
    void upstreamLinkGoesToTheRunWhenItLacksTheTab() throws Exception {
        FreeStyleBuild downstream = buildDownstreamOf(j.createFreeStyleProject("upstream-without-tab"));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            List<String> links = detailLinks(wc.getPage(downstream, "sample/"));
            assertThat(links, hasItem(j.contextPath + "/job/upstream-without-tab/1/"));
            assertThat(links, not(hasItem(j.contextPath + "/job/upstream-without-tab/1/sample/")));
        }
    }

    @Test
    void upstreamLinkGoesToTheRunFromTheRunPage() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User.getById("alice", true)
                .addProperty(new UserExperimentalFlagsProperty(Map.of("new-build-page.flag", "true")));
        FreeStyleBuild downstream = buildDownstreamOf(j.createFreeStyleProject("upstream"));

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            List<String> links = detailLinks(wc.getPage(downstream));
            assertThat(links, hasItem(j.contextPath + "/job/upstream/1/"));
            assertThat(links, not(hasItem(j.contextPath + "/job/upstream/1/sample/")));
        }
    }

    @Test
    void deletedUpstreamBuildLinksToTheJob() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject("upstream");
        FreeStyleBuild downstream = buildDownstreamOf(upstream);
        upstream.getBuildByNumber(1).delete();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            assertThat(detailLinks(wc.getPage(downstream, "sample/")), hasItem(j.contextPath + "/job/upstream/"));
        }
    }

    private FreeStyleBuild buildDownstreamOf(FreeStyleProject upstream) throws Exception {
        FreeStyleBuild upstreamBuild = j.buildAndAssertSuccess(upstream);
        FreeStyleProject downstream = j.createFreeStyleProject("downstream");
        return j.assertBuildStatusSuccess(
                downstream.scheduleBuild2(0, new CauseAction(new Cause.UpstreamCause(upstreamBuild))));
    }

    private static List<String> detailLinks(HtmlPage page) {
        return page.querySelectorAll(".jenkins-details a.jenkins-details__item").stream()
                .filter(HtmlAnchor.class::isInstance)
                .map(node -> ((HtmlAnchor) node).getHrefAttribute())
                .toList();
    }

    public static final class SampleTab extends Tab {

        SampleTab(Actionable object) {
            super(object);
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Sample";
        }

        @Override
        public String getUrlName() {
            return "sample";
        }
    }

    @TestExtension
    public static final class SampleTabFactory extends TransientActionFactory<FreeStyleBuild> {

        @Override
        public Class<FreeStyleBuild> type() {
            return FreeStyleBuild.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull FreeStyleBuild target) {
            return target.getParent().getName().endsWith("-without-tab") ? Set.of() : Set.of(new SampleTab(target));
        }
    }
}
