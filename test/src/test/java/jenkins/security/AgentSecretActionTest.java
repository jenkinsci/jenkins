package jenkins.security;



import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.Computer;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    private AgentSecretAction action;
    private StaplerRequest2 req;
    private StaplerResponse2 rsp;
    private StringWriter stringWriter;
    private PrintWriter writer;

    @Before
    public void setUp() throws Exception {
        SlaveComputer computer = mock(SlaveComputer.class);
        action = new AgentSecretAction(computer);
        req = mock(StaplerRequest2.class);
        rsp = mock(StaplerResponse2.class);
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(rsp.getWriter()).thenReturn(writer);
    }

    @Test
    public void testGetSecretWithValidPermissions() throws Exception {
        DumbSlave agent = j.createSlave("test-agent", null);
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy()
                .grant(Computer.CONNECT).everywhere().to("test-user");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        User user = User.getById("test-user", true);
            try (ACLContext ignored = ACL.as(user)) {
                action.doIndex(req, rsp);
                writer.flush();
                verify(rsp).setContentType("text/plain");
                assertEquals(expectedSecret, stringWriter.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

    }

    @Test
    public void testGetSecretViaWebClient() throws Exception {
        DumbSlave agent = j.createSlave("test-agent", null);
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.CONNECT).everywhere().to("test-user"));

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");
        String response = webClient.goTo("computer/test-agent/agentSecret/", "text/plain")
                .getWebResponse().getContentAsString();

        assertEquals(expectedSecret, response);
    }

    @Test
    public void testGetSecretWithoutPermissions() throws Exception {
        j.createSlave("test-agent", null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");
        thrown.expect(org.acegisecurity.AccessDeniedException.class);
        action.doIndex(req, rsp);
    }

    @Test
    public void testGetSecretWithInvalidNodeName() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.CONNECT).everywhere().to("test-user"));

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Node not found: non-existent-agent");
        action.doIndex(req, rsp);
    }

    @Test
    public void testGetSecretWithNullNodeName() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.CONNECT).everywhere().to("test-user"));


        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Node name is required");
        action.doIndex(req, rsp);
    }

}
