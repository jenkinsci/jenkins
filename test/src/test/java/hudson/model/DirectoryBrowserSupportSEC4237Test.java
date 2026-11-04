package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DirectoryBrowserSupportSEC4237Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private JenkinsRule.WebClient getWebClient() {
        var wc = j.createWebClient();
        wc.getOptions().setJavaScriptEnabled(false);
        return wc;
    }

    @Issue("SECURITY-4237")
    @Test
    void backslashDotDot_cannotEscapeWorkspaceRoot() throws Exception {
        var pubjob = j.createFreeStyleProject("pubjob");
        var hiddenjob = j.createFreeStyleProject("hiddenjob");
        j.buildAndAssertSuccess(pubjob);
        j.buildAndAssertSuccess(hiddenjob);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("wsuser")
                .grant(Item.READ, Item.WORKSPACE).onItems(pubjob).to("wsuser"));

        try (var wc = getWebClient().withBasicCredentials("wsuser")) {
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            var hiddenJobDirect = wc.goTo(hiddenjob.getUrl(), null);
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, hiddenJobDirect.getWebResponse().getStatusCode());

            var baseline = wc.goTo(pubjob.getUrl() + "ws/", null);
            assertEquals(HttpURLConnection.HTTP_OK, baseline.getWebResponse().getStatusCode());
            assertThat(baseline.getWebResponse().getContentAsString(), not(containsString("hiddenjob")));

            var attack = wc.goTo(pubjob.getUrl() + "ws/%5C../", null);
            assertThat(attack.getWebResponse().getContentAsString(), not(containsString("hiddenjob")));
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, attack.getWebResponse().getStatusCode());
        }
    }

    @Issue("SECURITY-4237")
    @Test
    void backslashDotDot_cannotEscapeUserContentRoot() throws Exception {
        var canary = "canary-" + System.nanoTime() + ".txt";
        Files.writeString(new File(j.jenkins.getRootDir(), canary).toPath(), "root-level file, not under userContent", StandardCharsets.UTF_8);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("reader"));

        try (var wc = getWebClient().withBasicCredentials("reader")) {
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            var baseline = wc.goTo("userContent/", null);
            assertEquals(HttpURLConnection.HTTP_OK, baseline.getWebResponse().getStatusCode());
            assertThat(baseline.getWebResponse().getContentAsString(), not(containsString(canary)));

            var attack = wc.goTo("userContent/%5C../", null);
            assertThat(attack.getWebResponse().getContentAsString(), not(containsString(canary)));
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, attack.getWebResponse().getStatusCode());
        }
    }
}
