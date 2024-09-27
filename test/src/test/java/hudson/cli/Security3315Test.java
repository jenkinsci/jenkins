package hudson.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;

@RunWith(Parameterized.class)
public class Security3315Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public FlagRule<Boolean> escapeHatch;

    private final Boolean allowWs;

    @Parameterized.Parameters
    public static List<String> escapeHatchValues() {
        return Arrays.asList(null, "true", "false");
    }

    public Security3315Test(String allowWs) {
        this.allowWs = allowWs == null ? null : Boolean.valueOf(allowWs);
        this.escapeHatch = new FlagRule<>(() -> CLIAction.ALLOW_WEBSOCKET, v -> CLIAction.ALLOW_WEBSOCKET = v, this.allowWs);
    }

    @Test
    public void test() throws IOException {
        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
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
        }
    }
}
