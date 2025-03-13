package jenkins.agents;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class TLSCustomCertificateTest {

    @Rule
    public final RealJenkinsRule rjr = new RealJenkinsRule().https();

    @Rule
    public InboundAgentRule iar = new InboundAgentRule();

    @Before
    public void setUp() throws Throwable {
        rjr.startJenkins();
    }

    @Test
    public void test_noCertificateCheck_worksInWebSocketAgent() throws Throwable {
        // TODO need to configure `-noCertificateCheck` option
        var options = InboundAgentRule.Options
            .newBuilder()
            .webSocket();

        iar.createAgent(rjr, options.build());
        // try to run a build to ensure agent is connected.
    }

    @Test
    public void test_cert_worksInWebSocketAgent() throws Throwable {
        // TODO need to configure `-cert` option with passing the rootCA as a string variable
        var options = InboundAgentRule.Options
            .newBuilder()
            .webSocket();

        iar.createAgent(rjr, options.build());
        // try to run a build to ensure agent is connected.
    }
}
