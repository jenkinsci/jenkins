package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URI;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.htmlunit.TextPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

public class TcpAgentAgentListenerTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void headers() throws Exception {
        WebClient wc = r.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        r.getInstance().setAgentAgentPort(-1);
        wc.assertFails("tcpAgentAgentListener", HttpURLConnection.HTTP_NOT_FOUND);

        r.getInstance().setAgentAgentPort(0);
        Page p = wc.goTo("tcpAgentAgentListener", "text/plain");
        assertEquals(HttpURLConnection.HTTP_OK, p.getWebResponse().getStatusCode());
        assertThat(p.getWebResponse().getResponseHeaderValue("X-Instance-Identity"), notNullValue());
    }

    @Test
    public void diagnostics() throws Exception {
        r.getInstance().setAgentAgentPort(0);
        int p = r.jenkins.getTcpAgentAgentListener().getPort();
        WebClient wc = r.createWebClient();

        TextPage text = wc.getPage(new URI("http://localhost:" + p + "/").toURL());
        String c = text.getContent();
        assertThat(c, containsString(Jenkins.VERSION));

        wc.setThrowExceptionOnFailingStatusCode(false);
        Page page = wc.getPage(new URI("http://localhost:" + p + "/xxx").toURL());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, page.getWebResponse().getStatusCode());
    }
}
