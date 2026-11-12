package jenkins.security;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateNodeCommand;
import hudson.model.Computer;
import hudson.model.Failure;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("SECURITY-4025")
class Security4025Test {

    private static final String ATTACKER = "attacker";
    private static final String SOURCE_AGENT = "source-agent";
    private static final String VICTIM_AGENT = "victim-agent";

    private static String agentXmlNamed(String name, String remoteFS) {
        return "<?xml version=\"1.1\" encoding=\"UTF-8\"?>"
                + "<slave>"
                + "<name>" + name + "</name>"
                + "<remoteFS>" + remoteFS + "</remoteFS>"
                + "<numExecutors>1</numExecutors>"
                + "<mode>NORMAL</mode>"
                + "<launcher class=\"hudson.slaves.JNLPLauncher\">"
                + "<workDirSettings><disabled>false</disabled><internalDir>remoting</internalDir>"
                + "<failIfWorkDirIsMissing>false</failIfWorkDirIsMissing></workDirSettings>"
                + "<webSocket>false</webSocket>"
                + "</launcher>"
                + "<label></label>"
                + "<nodeProperties/>"
                + "</slave>";
    }

    @Test
    void postConfigXmlCannotOverwriteAnotherAgent(JenkinsRule j) throws Exception {
        j.createSlave(SOURCE_AGENT, null, null);
        DumbSlave victim = j.createSlave(VICTIM_AGENT, null, null);
        String victimOriginalFS = victim.getRemoteFS();

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Computer.CONFIGURE).everywhere().to(ATTACKER));

        try (JenkinsRule.WebClient wc = j.createWebClient().login(ATTACKER).withThrowExceptionOnFailingStatusCode(false)) {
            WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("computer/%s/config.xml", SOURCE_AGENT)), HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody(agentXmlNamed(VICTIM_AGENT, "/OVERWRITTEN"));

            Page response = wc.getPage(req);
            assertThat(response.getWebResponse().getStatusCode(), not(equalTo(HttpURLConnection.HTTP_OK)));
        }

        // source-agent must still exist
        assertNotNull(j.jenkins.getNode(SOURCE_AGENT), "source-agent must still exist");

        // victim-agent must be completely untouched
        Node victimAfter = j.jenkins.getNode(VICTIM_AGENT);
        assertNotNull(victimAfter);
        assertThat(((Slave) victimAfter).getRemoteFS(), equalTo(victimOriginalFS));
    }

    /**
     * POST config.xml with a name that does not collide with any existing agent succeeds,
     * renaming the node. See also UpdateNodeCommandTest#updateNodeShouldModifyNodeConfiguration.
     */
    @Test
    void postConfigXmlRenameToFreshNameSucceeds(JenkinsRule j) throws Exception {
        j.createSlave(SOURCE_AGENT, null, null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Computer.CONFIGURE).everywhere().to(ATTACKER));

        try (JenkinsRule.WebClient wc = j.createWebClient().login(ATTACKER).withThrowExceptionOnFailingStatusCode(false)) {
            WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("computer/%s/config.xml", SOURCE_AGENT)), HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody(agentXmlNamed("brand-new-name", "/some/path"));

            Page response = wc.getPage(req);
            assertThat(response.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
        }

        assertNotNull(j.jenkins.getNode("brand-new-name"));
        assertThat(j.jenkins.getNode(SOURCE_AGENT), nullValue());
    }

    @Test
    void cliUpdateNodeCannotOverwriteAnotherAgent(JenkinsRule j) throws Exception {
        j.createSlave(SOURCE_AGENT, null, null);
        DumbSlave victim = j.createSlave(VICTIM_AGENT, null, null);
        String victimOriginalFS = victim.getRemoteFS();

        CLICommandInvoker command = new CLICommandInvoker(j, new UpdateNodeCommand()).authorizedTo(Computer.CONFIGURE, Jenkins.READ);
        byte[] payload = agentXmlNamed(VICTIM_AGENT, "/OVERWRITTEN").getBytes(StandardCharsets.UTF_8);
        CLICommandInvoker.Result result = command.withStdin(new ByteArrayInputStream(payload)).invokeWithArgs(SOURCE_AGENT);

        assertThat(result, failedWith(8));
        assertThat(result.stderr(), containsString("Node already exists: victim-agent"));
        assertThat(result.stderr(), not(containsString("Unexpected exception occurred while performing")));

        assertNotNull(j.jenkins.getNode(SOURCE_AGENT));

        // victim-agent must be completely untouched
        Node victimAfter = j.jenkins.getNode(VICTIM_AGENT);
        assertNotNull(victimAfter);
        assertThat(((Slave) victimAfter).getRemoteFS(), equalTo(victimOriginalFS));
    }

    @Test
    void replaceNodeRejectsCollision(JenkinsRule j) throws Exception {
        DumbSlave source = j.createSlave(SOURCE_AGENT, null, null);
        DumbSlave victim = j.createSlave(VICTIM_AGENT, null, null);
        String victimOriginalFS = victim.getRemoteFS();

        DumbSlave colliding = new DumbSlave(VICTIM_AGENT, "/OVERWRITTEN", new JNLPLauncher(false));

        assertThrows(Failure.class, () -> j.jenkins.getNodesObject().replaceNode(source, colliding));

        // Both nodes must survive intact
        assertNotNull(j.jenkins.getNode(SOURCE_AGENT));
        Node victimAfter = j.jenkins.getNode(VICTIM_AGENT);
        assertNotNull(victimAfter);
        assertThat(((Slave) victimAfter).getRemoteFS(), equalTo(victimOriginalFS));
    }
}
