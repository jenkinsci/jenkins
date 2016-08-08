package hudson;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.remoting.Base64;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.annotation.Nullable;

import jenkins.model.Jenkins;
import jenkins.model.identity.InstanceIdentityProvider;
import jenkins.security.security218.ysoserial.ExecBlockingSecurityManager.ExecException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class TcpSlaveAgentListenerTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void headers() throws Exception {
        r.getInstance().setSlaveAgentPort(-1);
        try {
            r.createWebClient().goTo("tcpSlaveAgentListener");
            fail("Should get 404");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(), is(404));
        }
        r.getInstance().setSlaveAgentPort(0);
        Page p = r.createWebClient().goTo("tcpSlaveAgentListener", "text/plain");
        assertThat(p.getWebResponse().getResponseHeaderValue("X-Instance-Identity"), notNullValue());
    }

    @Test
    public void diagnostics() throws Exception {
        r.getInstance().setSlaveAgentPort(0);
        int p = r.jenkins.getTcpSlaveAgentListener().getPort();
        WebClient wc = r.createWebClient();
        TextPage text = (TextPage) wc.getPage("http://localhost:"+p+"/");
        String c = text.getContent();
        assertThat(c,containsString(Jenkins.VERSION));

        try {
            wc.getPage("http://localhost:"+p+"/xxx");
            fail("Expected 404");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(),equalTo(404));
        }
    }
}
