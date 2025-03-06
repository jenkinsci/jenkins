package jenkins.security;



import static org.junit.Assert.assertEquals;

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;


public class AgentSecretActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private DumbSlave agent;
    private String agentUrl;

    @Before
    public void setUp() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        agent = new DumbSlave("test-agent", "/home/jenkins", new hudson.slaves.JNLPLauncher());
        Jenkins jenkins = Jenkins.get();
        jenkins.addNode(agent);
        agentUrl = "computer/test-agent/agent-secret/";
    }

    @Test
    public void testGetSecretWithValidPermissions() throws Exception {
        String expectedSecret = ((SlaveComputer) agent.getComputer()).getJnlpMac();

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("test-user")
                .grant(Computer.CONNECT).everywhere().to("test-user");

        j.jenkins.setAuthorizationStrategy(authStrategy);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");

        String response = webClient.goTo(agentUrl, "text/plain")
                .getWebResponse().getContentAsString();

        assertEquals(expectedSecret, response);

    }

    @Test
    public void testGetSecretWithoutPermissions() throws Exception {
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("test-user");

        j.jenkins.setAuthorizationStrategy(authStrategy);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.login("test-user");

        webClient.assertFails(agentUrl, 403);
    }

}
