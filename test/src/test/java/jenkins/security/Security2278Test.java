package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Queue;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security2278Test {

    private static JenkinsRule j;
    private static FreeStyleProject project;

    @BeforeAll
    static void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        final MockFolder folder = j.createFolder("foo");
        project = folder.createProject(FreeStyleProject.class, "bar");
        project.getBuildersList().add(new SleepBuilder(TimeUnit.SECONDS.toMillis(600)));
        project.save();

        // can cancel but not read the project, plus it's in a subfolder (unsure whether that is needed)
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.CANCEL).onItems(project).toEveryone()
                .grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());

        // one build in progress
        project.scheduleBuild2(0, new Cause.UserIdCause("alice")).waitForStart();

        // one waiting in queue
        project.scheduleBuild2(0, new Cause.UserIdCause("alice"));
    }

    @Test
    void testUi() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage topPage = webClient.goTo("");
        final String contentAsString = topPage.getWebResponse().getContentAsString();
        assertThat(contentAsString, containsString("Build Executor Status"));
        assertThat(contentAsString, containsString("Unknown Task"));
        assertThat(contentAsString, not(containsString("job/foo/job/bar")));
        assertThat(contentAsString, not(containsString("stop-button-link")));
    }

    @Test
    void testUiWithPermission() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient().login("alice");
        final HtmlPage topPage = webClient.goTo("");
        final String contentAsString = topPage.getWebResponse().getContentAsString();
        assertThat(contentAsString, containsString("Build Executor Status"));
        assertThat(contentAsString, not(containsString("Unknown Task")));
        assertThat(contentAsString, containsString("job/foo/job/bar"));
        assertThat(contentAsString, containsString("stop-button-link"));
    }

    @Test
    void testQueueCancelWithoutPermission() throws Exception {
        final Queue.Item[] items = j.jenkins.getQueue().getItems();
        assertEquals(1, items.length);
        final long id = items[0].getId();

        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(false);

        final Page stopResponse = webClient.getPage(addReferer(webClient.addCrumb(new WebRequest(new URL(j.jenkins.getRootUrl() + "/queue/cancelItem?id=" + id), HttpMethod.POST)), j.jenkins.getRootUrl()));
        assertEquals(404, stopResponse.getWebResponse().getStatusCode());
        assertEquals(1, j.jenkins.getQueue().getItems().length);
    }

    @Test
    void testWebMethodWithoutPermission() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(false);

        final Executor busyExecutor = project.getBuilds().stream().findFirst().orElseThrow(() -> new IllegalStateException("expected build")).getExecutor();
        final Computer computer = j.jenkins.toComputer();
        assertNotNull(computer);
        final List<Executor> executors = computer.getExecutors();
        int found = -1;
        for (int i = 0; i < computer.getNumExecutors(); i++) {
            if (executors.get(i) == busyExecutor) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            throw new IllegalStateException("didn't find executor");
        }
        final Page stopResponse = webClient.getPage(addReferer(webClient.addCrumb(new WebRequest(new URL(j.jenkins.getRootUrl() + "/computer/(master)/executors/" + found + "/stop/"), HttpMethod.POST)), j.jenkins.getRootUrl()));
        assertEquals(302, stopResponse.getWebResponse().getStatusCode());

        final FreeStyleBuild build = project.getBuildByNumber(1);
        assertTrue(build.isBuilding());
        assertFalse(Objects.requireNonNull(build.getExecutor()).isInterrupted());
    }

    private WebRequest addReferer(WebRequest request, String referer) {
        request.setAdditionalHeader("Referer", referer);
        return request;
    }

    @AfterAll
    static void tearDown() throws Exception {
        // clear out queue
        j.jenkins.getQueue().clear();

        // wait for build to finish aborting
        final FreeStyleBuild freeStyleBuild = project.getBuilds().stream().findFirst().orElseThrow(() -> new IllegalStateException("Didn't find build"));
        freeStyleBuild.doStop();
        j.jenkins.getQueue().getItem(freeStyleBuild.getQueueId()).getFuture().get();
    }
}
