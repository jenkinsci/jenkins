package jenkins.agents;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class InboundAgentTlsCliArgumentTest {

    @Rule
    public final RealJenkinsRule rjr = new RealJenkinsRule().https();

    @Rule
    public InboundAgentRule iar = new InboundAgentRule();

    @Before
    public void setUp() throws Throwable {
        rjr.startJenkins();
    }

    @Test
    public void shouldConnectOverWebSocketWithoutCertificateCheck() throws Throwable {
        var options = InboundAgentRule.Options
            .newBuilder()
            .webSocket()
            .noCertificateCheck();
        iar.createAgent(rjr, options.build());
    }

    @Test
    public void shouldConnectOverWebSocketWithCertificateFromCLI() throws Throwable {
        var options = InboundAgentRule.Options
            .newBuilder()
            .webSocket()
            .cert(rjr.getRootCAPem());
        iar.createAgent(rjr, options.build());
    }

    @Test
    public void shouldConnectOverTcpWithoutCertificateCheck() throws Throwable {
        var options = InboundAgentRule.Options
            .newBuilder()
            .noCertificateCheck();
        iar.createAgent(rjr, options.build());
    }

    @Test
    public void shouldConnectOverTcpWithCertificateFromCLI() throws Throwable {
        var options = InboundAgentRule.Options
            .newBuilder()
            .cert(rjr.getRootCAPem());
        iar.createAgent(rjr, options.build());
    }
}
