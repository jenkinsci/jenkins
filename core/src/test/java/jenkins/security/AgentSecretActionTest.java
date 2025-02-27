package jenkins.security;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import hudson.model.Computer;
import hudson.slaves.SlaveComputer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import hudson.security.Permission;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.PrintWriter;
import java.io.StringWriter;

public class AgentSecretActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private AgentSecretAction action;
    private StaplerRequest req;
    private StaplerResponse rsp;
    private StringWriter stringWriter;
    private PrintWriter writer;

    @Before
    public void setUp() throws Exception {
        action = new AgentSecretAction();
        req = mock(StaplerRequest.class);
        rsp = mock(StaplerResponse.class);
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(rsp.getWriter()).thenReturn(writer);
    }

    @Test
    public void testGetSecretWithValidPermissions() throws Exception {
        DumbSlave agent = j.createSlave("test-agent", null);
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.AGENT_SECRET).everywhere().to("test-user"));

        action.doGet(req, rsp, "test-agent");
        writer.flush();
        verify(rsp).setContentType("text/plain");
        assertEquals(expectedSecret, stringWriter.toString());
    }

    @Test
    public void testGetSecretWithoutPermissions() throws Exception {
        j.createSlave("test-agent", null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());

        thrown.expect(org.acegisecurity.AccessDeniedException.class);
        action.doGet(req, rsp, "test-agent");
    }

    @Test
    public void testGetSecretWithInvalidNodeName() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.AGENT_SECRET).everywhere().to("test-user"));

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Node not found: non-existent-agent");
        action.doGet(req, rsp, "non-existent-agent");
    }

    @Test
    public void testGetSecretWithNullNodeName() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.AGENT_SECRET).everywhere().to("test-user"));


        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Node name is required");
        action.doGet(req, rsp, null);
    }

    @Test
    public void testGetSecretWithMasterNode() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Computer.AGENT_SECRET).everywhere().to("test-user"));

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The specified node is not an agent/slave node: master");
        action.doGet(req, rsp, "master");
    }
}
