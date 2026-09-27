package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.htmlunit.TextPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TcpSlaveAgentListenerTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void headers() throws Exception {
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
    void diagnostics() throws Exception {
        r.getInstance().setSlaveAgentPort(0);
        int p = r.jenkins.getTcpSlaveAgentListener().getPort();
        WebClient wc = r.createWebClient();

        TextPage text = wc.getPage(new URI("http://localhost:" + p + "/").toURL());
        String c = text.getContent();
        assertThat(c, containsString(Jenkins.VERSION));

        wc.setThrowExceptionOnFailingStatusCode(false);
        Page page = wc.getPage(new URI("http://localhost:" + p + "/xxx").toURL());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, page.getWebResponse().getStatusCode());
    }

    @Test
    void handshakeTimeout() throws Exception {
        int originalTimeout = TcpSlaveAgentListener.HANDSHAKE_TIMEOUT;
        try {
            TcpSlaveAgentListener.HANDSHAKE_TIMEOUT = 1000;
            r.getInstance().setSlaveAgentPort(0);
            int port = r.jenkins.getTcpSlaveAgentListener().getPort();

            try (Socket s = new Socket("localhost", port)) {
                OutputStream out = s.getOutputStream();
                out.write("GET".getBytes());
                out.flush();

                InputStream in = s.getInputStream();
                assertEquals(-1, in.read());
            }
        } finally {
            TcpSlaveAgentListener.HANDSHAKE_TIMEOUT = originalTimeout;
        }
    }

    @Test
    void connectionCap() throws Exception {
        int originalCap = TcpSlaveAgentListener.MAX_CONNECTION_HANDLERS;
        try {
            TcpSlaveAgentListener.MAX_CONNECTION_HANDLERS = 2;
            r.getInstance().setSlaveAgentPort(0);
            int port = r.jenkins.getTcpSlaveAgentListener().getPort();

            try (Socket s1 = new Socket("localhost", port);
                 Socket s2 = new Socket("localhost", port)) {

                s1.getOutputStream().write("GET".getBytes());
                s1.getOutputStream().flush();
                s2.getOutputStream().write("GET".getBytes());
                s2.getOutputStream().flush();

                try (Socket s3 = new Socket("localhost", port)) {
                    assertEquals(-1, s3.getInputStream().read());
                }
            }
        } finally {
            TcpSlaveAgentListener.MAX_CONNECTION_HANDLERS = originalCap;
        }
    }
}
