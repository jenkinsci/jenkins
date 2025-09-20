package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import jenkins.security.security3501Test.Security3501RootAction;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

@ParameterizedClass
@ValueSource(strings = { "/jenkins", "" })
class Security3501Test {

    @Parameter
    private String contextPath;

    @RegisterExtension
    private final RealJenkinsExtension jj = new RealJenkinsExtension().addSyntheticPlugin(new RealJenkinsExtension.SyntheticPlugin(Security3501RootAction.class.getPackage()).shortName("Security3501RootAction"));

    @BeforeEach
    void setUp() {
        jj.withPrefix(contextPath);
    }

    @Test
    void testRedirects() throws Throwable {
        jj.then(new TestRedirectsStep(contextPath));
    }

    private record TestRedirectsStep(String context) implements RealJenkinsExtension.Step {
        public void run(JenkinsRule j) throws Exception {
            List<String> prohibitedPaths = List.of("%5C%5Cexample.org", "%5C/example.org", "/%5Cexample.org", "//example.org", "https://example.org", "\\example.org");
            for (String path : prohibitedPaths) {
                try (JenkinsRule.WebClient wc = j.createWebClient().withRedirectEnabled(false)) {
                    final FailingHttpStatusCodeException fhsce = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("redirects/content?path=" + path));
                    assertThat(fhsce.getStatusCode(), is(404));
                }
            }

            List<String> allowedPaths = List.of("foo", "foo/bar");
            for (String path : allowedPaths) {
                try (JenkinsRule.WebClient wc = j.createWebClient().withRedirectEnabled(false)) {
                    final FailingHttpStatusCodeException fhsce = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("redirects/content?path=" + path));
                    assertThat(fhsce.getStatusCode(), is(302));
                    assertThat(fhsce.getResponse().getResponseHeaderValue("Location"), is(context + "/redirects/" + path));
                }
            }
        }
    }
}
