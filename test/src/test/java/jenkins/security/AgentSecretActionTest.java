package jenkins.security;



import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

public class AgentSecretActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DumbSlave agent;

    private AgentSecretAction action;
    private StaplerRequest2 req;
    private StaplerResponse2 rsp;
    private StringWriter stringWriter;
    private PrintWriter writer;

    private String agentUrl;

    @Before
    public void setUp() throws Exception {
        agent = new DumbSlave("test-agent", "/home/jenkins", new hudson.slaves.JNLPLauncher());
        j.jenkins.addNode(agent);
        SlaveComputer computer = (SlaveComputer) agent.getComputer();
        action = new AgentSecretAction(computer);
        req = mock(StaplerRequest2.class);
        rsp = mock(StaplerResponse2.class);
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(rsp.getWriter()).thenReturn(writer);
        agentUrl = "computer/test-agent/agentSecret/";
    }

    @Test
    public void testGetSecretWithValidPermissions() throws Exception {
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy()
                .grant(Computer.CONNECT).everywhere().to("test-user");
        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");
        String response = webClient.goTo(agentUrl, "text/plain")
                .getWebResponse().getContentAsString();

        assertEquals(expectedSecret, response);

    }

    @Test
    public void testGetSecretViaWebClient() throws Exception {
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.CONNECT).everywhere().to("test-user"));

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");
        String response = webClient.goTo(agentUrl, "text/plain")
                .getWebResponse().getContentAsString();

        assertEquals(expectedSecret, response);
    }

    @Test
    public void testGetSecretWithoutPermissions() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");
        try {
            webClient.goTo(agentUrl, "text/plain");
            fail("Expected AccessDeniedException was not thrown.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

}
