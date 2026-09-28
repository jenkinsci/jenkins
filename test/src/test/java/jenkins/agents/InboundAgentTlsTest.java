package jenkins.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class InboundAgentTlsTest {

    @RegisterExtension
    private final RealJenkinsExtension rjr = new RealJenkinsExtension().https();

    @RegisterExtension
    private final InboundAgentExtension iar = new InboundAgentExtension();

    @BeforeEach
    @DisabledIfEnvironmentVariable(named = "WORKSPACE",   // Defined on CI agents
                                   matches = "^[C-Z]:.*", // Windows CI workspace path
                                   disabledReason = "Expensive to run and not Windows specific")
    void setUp() throws Throwable {
        rjr.startJenkins();
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "WORKSPACE",   // Defined on CI agents
                                   matches = "^[C-Z]:.*", // Windows CI workspace path
                                   disabledReason = "Expensive to run and not Windows specific")
    void webSocketNoCertificateCheck() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .webSocket()
            .noCertificateCheck();
        iar.createAgent(rjr, options.build());
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "WORKSPACE",   // Defined on CI agents
                                   matches = "^[C-Z]:.*", // Windows CI workspace path
                                   disabledReason = "Expensive to run and not Windows specific")
    void webSocketWithCertByValue() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .webSocket()
            .cert(rjr.getRootCAPem());
        iar.createAgent(rjr, options.build());
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "WORKSPACE",   // Defined on CI agents
                                   matches = "^[C-Z]:.*", // Windows CI workspace path
                                   disabledReason = "Expensive to run and not Windows specific")
    void tcpWithNoCertificateCheck() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .noCertificateCheck();
        iar.createAgent(rjr, options.build());
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "WORKSPACE",   // Defined on CI agents
                                   matches = "^[C-Z]:.*", // Windows CI workspace path
                                   disabledReason = "Expensive to run and not Windows specific")
    void tcpWithCertByValue() throws Throwable {
        var options = InboundAgentExtension.Options
            .newBuilder()
            .cert(rjr.getRootCAPem());
        iar.createAgent(rjr, options.build());
    }
}
