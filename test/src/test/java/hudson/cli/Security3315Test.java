package hudson.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security3315Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    static List<Boolean> escapeHatchValues() {
        return Arrays.asList(null, true, false);
    }

    @ParameterizedTest
    @MethodSource("escapeHatchValues")
    void test(Boolean allowWs) throws IOException {
        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            CLIAction.ALLOW_WEBSOCKET = allowWs;

            // HTTP 400 is WebSocket "success" (HTMLUnit doesn't support it)
            final URL jenkinsUrl = j.getURL();
            WebRequest request = new WebRequest(new URL(jenkinsUrl.toString() + "cli/ws"), HttpMethod.GET);
            Page page = wc.getPage(request);
            assertThat(page.getWebResponse().getStatusCode(), is(allowWs == Boolean.TRUE ? 400 : 403)); // no Origin header

            request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://example.org:" + jenkinsUrl.getPort());
            page = wc.getPage(request);
            assertThat(page.getWebResponse().getStatusCode(), is(allowWs == Boolean.TRUE ? 400 : 403)); // Wrong Origin host

            request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + jenkinsUrl.getHost());
            page = wc.getPage(request);
            assertThat(page.getWebResponse().getStatusCode(), is(allowWs == Boolean.TRUE ? 400 : 403)); // Wrong Origin port

            request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + jenkinsUrl.getHost() + ":" + jenkinsUrl.getPort());
            page = wc.getPage(request);
            assertThat(page.getWebResponse().getStatusCode(), is(allowWs == Boolean.FALSE ? 403 : 400)); // Reject correct Origin if ALLOW_WS is explicitly false
        } finally {
            CLIAction.ALLOW_WEBSOCKET = null;
        }
    }

    @Issue("SECURITY-3674")
    @Test
    void testDifferentDomain() throws IOException {
        final URL jenkinsUrl = j.getURL();
        // safety assertions
        final String jenkinsUrlHost = "localhost";
        assertThat(jenkinsUrl.getHost(), is(jenkinsUrlHost));
        assertThat(JenkinsLocationConfiguration.get().getUrl(), is(jenkinsUrl.toString()));

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            {
                // control: Regular request to the configured domain
                final WebRequest request = new WebRequest(new URL(jenkinsUrl.toString() + "cli/ws"), HttpMethod.GET);
                // correct Origin
                request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + jenkinsUrl.getHost() + ":" + jenkinsUrl.getPort());
                final Page page = wc.getPage(request);
                assertThat(page.getWebResponse().getStatusCode(), is(400)); // 400 is WebSocket "success" (HTMLUnit doesn't support it)
            }

            final String alternativeUrlHost = "127.0.0.1";
            final URL alternativeUrl = new URL(jenkinsUrl.toString().replace(jenkinsUrlHost, alternativeUrlHost));
            {
                // wrong but matching request domain and origin, prohibited by default
                final WebRequest request = new WebRequest(new URL(alternativeUrl.toString() + "cli/ws"), HttpMethod.GET);
                request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + alternativeUrlHost + ":" + jenkinsUrl.getPort());
                final Page page = wc.getPage(request);
                assertThat(page.getWebResponse().getStatusCode(), is(403));
            }

            CLIAction.ACCEPT_URL_FROM_REQUEST = true;
            try {
                // wrong but matching request domain and origin, allowed by escape hatch
                final WebRequest request = new WebRequest(new URL(alternativeUrl.toString() + "cli/ws"), HttpMethod.GET);
                request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + alternativeUrlHost + ":" + jenkinsUrl.getPort());
                final Page page = wc.getPage(request);
                assertThat(page.getWebResponse().getStatusCode(), is(400));
            } finally {
                CLIAction.ACCEPT_URL_FROM_REQUEST = false;
            }

            JenkinsLocationConfiguration.get().setUrl(null);
            {
                // correct request domain and origin, but no configured URL, prohibited
                final WebRequest request = new WebRequest(new URL(jenkinsUrl.toString() + "cli/ws"), HttpMethod.GET);
                request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + jenkinsUrl.getHost() + ":" + jenkinsUrl.getPort());
                final Page page = wc.getPage(request);
                assertThat(page.getWebResponse().getStatusCode(), is(403));
            }

            CLIAction.ACCEPT_URL_FROM_REQUEST = true;
            try {
                // wrong but matching request domain and origin, allowed by escape hatch even without URL
                final WebRequest request = new WebRequest(new URL(alternativeUrl.toString() + "cli/ws"), HttpMethod.GET);
                request.setAdditionalHeader("Origin", jenkinsUrl.getProtocol() + "://" + alternativeUrlHost + ":" + jenkinsUrl.getPort());
                final Page page = wc.getPage(request);
                assertThat(page.getWebResponse().getStatusCode(), is(400));
            } finally {
                CLIAction.ACCEPT_URL_FROM_REQUEST = false;
            }
        }
    }
}
