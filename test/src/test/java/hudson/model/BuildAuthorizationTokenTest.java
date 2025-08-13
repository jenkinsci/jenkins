package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

// TODO : Using the @Nested class might be cleaner for SECURITY-2558 tests
@WithJenkins
class BuildAuthorizationTokenTest {

    private static final String token = "whatever";

    private JenkinsRule jr;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jr = rule;
        jr.jenkins.setSecurityRealm(jr.createDummySecurityRealm());
        jr.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                    .grant(Jenkins.READ).everywhere().toEveryone()
                                                    .grant(Item.READ).everywhere().toEveryone());
    }

    @Test
    @Issue("SECURITY-2558")
    void triggerJobWithoutSecurityRealm_ShouldSucceed_WithPost() throws Exception {
        jr.jenkins.setSecurityRealm(null);
        jr.jenkins.setAuthorizationStrategy(null);
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        wc.getPage(wc.addCrumb(new WebRequest(new URL(jr.getURL(), project.getUrl() +
                "build?delay=0"),
                HttpMethod.POST)));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }

    @Test
    @Issue("SECURITY-2558")
    void triggerJobWithoutSecurityRealm_ShouldFail_WithGet() throws Exception {
        jr.jenkins.setSecurityRealm(null);
        jr.jenkins.setAuthorizationStrategy(null);
        FreeStyleProject project = jr.createFreeStyleProject();
        JenkinsRule.WebClient wc = jr.createWebClient();
        FailingHttpStatusCodeException fex = assertThrows(
                FailingHttpStatusCodeException.class,
                () -> wc.getPage(new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0"), HttpMethod.GET)),
                "should not reach here since only POST request can");
        assertThat("Should fail with method not allowed", fex.getStatusCode(), is(405));
    }

    @Test
    @Issue("SECURITY-2558")
    void triggerJobWithoutSecurityRealm_ButWithToken_ShouldSucceed_WithGet() throws Exception {
        jr.jenkins.setSecurityRealm(null);
        jr.jenkins.setAuthorizationStrategy(null);
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        wc.getPage(new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0&token=" + token),
                HttpMethod.GET));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }

    @Test
    void triggerJobWithTokenShouldSucceedWithPost() throws Exception {
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        HtmlPage page = wc.getPage(wc.addCrumb(new WebRequest(
                new URL(jr.getURL(), project.getUrl() + "build?delay=0&token=" + token),
                HttpMethod.POST)));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }

    @Test
    void triggerJobWithTokenShouldSucceedWithGet() throws Exception {
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        HtmlPage page = wc.getPage(new WebRequest(
                new URL(jr.getURL(), project.getUrl() + "build?delay=0&token=" + token),
                HttpMethod.GET));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }


    @Test
    void triggerJobsWithoutTokenShouldFail() throws Exception {
        FreeStyleProject project = jr.createFreeStyleProject();
        JenkinsRule.WebClient wc = jr.createWebClient();
        FailingHttpStatusCodeException fex = assertThrows(
                FailingHttpStatusCodeException.class,
                () -> wc.getPage(wc.addCrumb(new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0"), HttpMethod.POST))),
                "should not reach here as anonymous does not have Item.BUILD and token is not set");
        assertThat("Should fail with access denied", fex.getStatusCode(), is(403));
    }

    private FreeStyleProject createFreestyleProjectWithToken() throws Exception {
        FreeStyleProject fsp = jr.createFreeStyleProject();
        Field f = AbstractProject.class.getDeclaredField("authToken");
        f.setAccessible(true);
        f.set(fsp, new BuildAuthorizationToken(token));
        return fsp;
    }
}
