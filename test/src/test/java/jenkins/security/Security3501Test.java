package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import jenkins.security.security3501Test.Security3501RootAction;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class Security3501Test {
    @Rule
    public RealJenkinsRule jj = new RealJenkinsRule().addSyntheticPlugin(new RealJenkinsRule.SyntheticPlugin(Security3501RootAction.class.getPackage()).shortName("Security3501RootAction"));

    @Test
    public void testRedirects() throws Throwable {
        jj.then(Security3501Test::_testRedirects);
    }

    public static void _testRedirects(JenkinsRule j) throws Exception {
        List<String> prohibitedPaths = List.of("%5C%5Cexample.org", "%5C/example.org", "/%5Cexample.org", "//example.org", "https://example.org", "\\example.org");
        for (String path : prohibitedPaths) {
            try (JenkinsRule.WebClient wc = j.createWebClient().withRedirectEnabled(false)) {
                final FailingHttpStatusCodeException fhsce = Assert.assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("redirects/content?path=" + path));
                assertThat(fhsce.getStatusCode(), is(404));
            }
        }

        List<String> allowedPaths = List.of("foo", "foo/bar");
        for (String path : allowedPaths) {
            try (JenkinsRule.WebClient wc = j.createWebClient().withRedirectEnabled(false)) {
                final FailingHttpStatusCodeException fhsce = Assert.assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("redirects/content?path=" + path));
                assertThat(fhsce.getStatusCode(), is(302));
                assertThat(fhsce.getResponse().getResponseHeaderValue("Location"), is(j.contextPath + "/redirects/" + path));
            }
        }
    }
}
