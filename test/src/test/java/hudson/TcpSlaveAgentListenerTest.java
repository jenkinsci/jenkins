package hudson;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;

import java.net.HttpURLConnection;
import java.net.URL;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

public class TcpSlaveAgentListenerTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void headers() throws Exception {
        WebClient wc = r.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        r.getInstance().setSlaveAgentPort(-1);
        wc.assertFails("tcpSlaveAgentListener", HttpURLConnection.HTTP_NOT_FOUND);

        r.getInstance().setSlaveAgentPort(0);
        Page p = wc.goTo("tcpSlaveAgentListener", "text/plain");
        assertEquals(HttpURLConnection.HTTP_OK, p.getWebResponse().getStatusCode());
        assertThat(p.getWebResponse().getResponseHeaderValue("X-Instance-Identity"), notNullValue());
    }

    @Test
    public void diagnostics() throws Exception {
        r.getInstance().setSlaveAgentPort(0);
        int p = r.jenkins.getTcpSlaveAgentListener().getPort();
        WebClient wc = r.createWebClient();

        TextPage text = wc.getPage(new URL("http://localhost:" + p + "/"));
        String c = text.getContent();
        assertThat(c, containsString(Jenkins.VERSION));

        wc.setThrowExceptionOnFailingStatusCode(false);
        Page page = wc.getPage(new URL("http://localhost:" + p + "/xxx"));
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, page.getWebResponse().getStatusCode());
    }
}
