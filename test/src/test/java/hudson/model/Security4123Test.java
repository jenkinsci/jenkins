package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.slaves.JNLPLauncher;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security4123Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-4123")
    void legacyAgentCommandIsNotMigratedToCommandLauncher() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        var mockAuthorizationStrategy = new MockAuthorizationStrategy();
        mockAuthorizationStrategy.grant(Jenkins.READ, Computer.CREATE).everywhere().to("attacker");
        j.jenkins.setAuthorizationStrategy(mockAuthorizationStrategy);

        var xml = """
                <slave>
                    <name>evil-agent</name>
                    <remoteFS>/tmp/evil-agent</remoteFS>
                    <numExecutors>1</numExecutors>
                    <mode>NORMAL</mode>
                    <agentCommand>touch /tmp/poc-ran</agentCommand>
                </slave>
                """;

        try (var logging = new LogRecorder().record(Slave.class, Level.WARNING).capture(10);
             var wc = j.createWebClient().login("attacker")) {
            var request = new WebRequest(wc.createCrumbedUrl("computer/createItem?name=evil-agent"), HttpMethod.POST);
            request.setAdditionalHeader("Content-Type", "application/xml");
            request.setRequestBody(xml);
            WebResponse rsp = wc.getPage(request).getWebResponse();
            assertEquals(200, rsp.getStatusCode());
            assertThat(logging.getMessages(), not(hasItem(containsString("agentCommand"))));
        }

        var agent = (Slave) j.jenkins.getNode("evil-agent");
        assertThat(agent.getLauncher(), instanceOf(JNLPLauncher.class));
    }
}
