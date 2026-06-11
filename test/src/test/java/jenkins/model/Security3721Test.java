package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.security.LegacySecurityRealm;
import java.util.List;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-3721")
@WithJenkins
public class Security3721Test {

    @Test
    void openRedirectIsBlocked(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(new LegacySecurityRealm());

        List<String> prohibitedPaths = List.of("%5C%5Cexample.org", "%5C/example.org", "/%5Cexample.org", "//example.org", "https://example.org", "\\example.org");
        for (String path : prohibitedPaths) {
            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wc.login("alice");
                wc.setRedirectEnabled(false);
                final FailingHttpStatusCodeException fhsce = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("loginEntry?from=" + path));

                assertThat(fhsce.getStatusCode(), is(302));
                assertThat(fhsce.getResponse().getResponseHeaderValue("Location"), is("/jenkins/"));
            }
        }

        List<String> allowedPaths = List.of("configure", "manage/security");
        for (String path : allowedPaths) {
            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wc.login("alice");
                wc.setRedirectEnabled(false);
                final FailingHttpStatusCodeException fhsce = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("loginEntry?from=" + path));

                assertThat(fhsce.getStatusCode(), is(302));
                assertThat(fhsce.getResponse().getResponseHeaderValue("Location"), is("/jenkins/" + path));
            }
        }
    }
}
