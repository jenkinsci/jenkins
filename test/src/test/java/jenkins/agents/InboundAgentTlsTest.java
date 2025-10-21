package jenkins.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class InboundAgentTlsTest {

    @RegisterExtension
    private final RealJenkinsExtension rjr = new RealJenkinsExtension().https();

    @RegisterExtension
    private final InboundAgentExtension iar = new InboundAgentExtension();

    @BeforeEach
    void setUp() throws Throwable {
        rjr.startJenkins();
    }

    @Test
    void webSocketNoCertificateCheck() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .webSocket()
            .noCertificateCheck();
        iar.createAgent(rjr, options.build());
    }

    @Test
    void webSocketWithCertByValue() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .webSocket()
            .cert(rjr.getRootCAPem());
        iar.createAgent(rjr, options.build());
    }

    @Test
    void tcpWithNoCertificateCheck() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .noCertificateCheck();
        iar.createAgent(rjr, options.build());
    }

    @Test
    void tcpWithCertByValue() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .cert(rjr.getRootCAPem());
        iar.createAgent(rjr, options.build());
    }
}
