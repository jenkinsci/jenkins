package hudson.model;

import java.lang.reflect.Field;
import java.net.URL;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import jenkins.model.Jenkins;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class BuildAuthorizationTokenTest {

    @Rule
    public JenkinsRule jr = new JenkinsRule();

    private static final String token = "whatever";

    @Before
    public void setupSecurity() {
        jr.jenkins.setSecurityRealm(jr.createDummySecurityRealm());
        jr.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                    .grant(Jenkins.READ).everywhere().toEveryone()
                                                    .grant(Item.READ).everywhere().toEveryone());
    }

    @Test
    public void triggerJobWithTokenShouldSucceedWithPost() throws Exception {
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        HtmlPage page = wc.getPage(wc.addCrumb(new WebRequest(new URL(jr.getURL(), project.getUrl() +
                                                                                   "build?delay=0&token="+token)
                                                             ,HttpMethod.POST)));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }

    @Test
    public void triggerJobWithTokenShouldSucceedWithGet() throws Exception {
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        HtmlPage page = wc.getPage(new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0&token=" + token)
                                                  ,HttpMethod.GET));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }


    @Test
    public void triggerJobsWithoutTokenShouldFail() throws Exception {
        FreeStyleProject project = jr.createFreeStyleProject();
        JenkinsRule.WebClient wc = jr.createWebClient();
        try {
            HtmlPage page = wc.getPage(wc.addCrumb(
                    new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0"), HttpMethod.POST)));
            fail("should not reach here as anonymous does not have Item.BUILD and token is not set");
        }
        catch (FailingHttpStatusCodeException fex) {
            assertThat("Should fail with access denied", fex.getStatusCode(), is(403));
        }
    }

    private FreeStyleProject createFreestyleProjectWithToken() throws Exception {
        FreeStyleProject fsp = jr.createFreeStyleProject();
        Field f = AbstractProject.class.getDeclaredField("authToken");
        f.setAccessible(true);
        f.set(fsp, new BuildAuthorizationToken(token));
        return fsp;
    }
}
