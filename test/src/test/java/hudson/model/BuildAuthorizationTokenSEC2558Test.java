package hudson.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThrows;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import java.lang.reflect.Field;
import java.net.URL;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

// The existing @Before is annoying so this class is independent
// TODO : Using @Nested class could be more clean than using separate class
// TODO : Merge back into BuildAuthorizationTokenTest
public class BuildAuthorizationTokenSEC2558Test {

    @Rule
    public JenkinsRule jr = new JenkinsRule();

    private static final String token = "whatever";

    @Test
    @Issue("SECURITY-2558")
    public void triggerJobWithoutSecurityRealm_ShouldSucceed_WithPost() throws Exception {
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
    public void triggerJobWithoutSecurityRealm_ShouldFail_WithGet() throws Exception {
        FreeStyleProject project = jr.createFreeStyleProject();
        JenkinsRule.WebClient wc = jr.createWebClient();
        FailingHttpStatusCodeException fex = assertThrows(
                "should not reach here since only POST request can",
                FailingHttpStatusCodeException.class,
                () -> wc.getPage(new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0"), HttpMethod.GET)));
        assertThat("Should fail with method not allowed", fex.getStatusCode(), is(405));
    }

    @Test
    @Issue("SECURITY-2558")
    public void triggerJobWithoutSecurityRealm_ButWithToken_ShouldSucceed_WithGet() throws Exception {
        FreeStyleProject project = createFreestyleProjectWithToken();
        JenkinsRule.WebClient wc = jr.createWebClient();
        wc.getPage(new WebRequest(new URL(jr.getURL(), project.getUrl() + "build?delay=0&token=" + token),
                HttpMethod.GET));
        jr.waitUntilNoActivity();
        assertThat("the project should have been built", project.getBuilds(), hasSize(1));
    }

    private FreeStyleProject createFreestyleProjectWithToken() throws Exception {
        FreeStyleProject fsp = jr.createFreeStyleProject();
        Field f = AbstractProject.class.getDeclaredField("authToken");
        f.setAccessible(true);
        f.set(fsp, new BuildAuthorizationToken(token));
        return fsp;
    }
}
