package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Computer;
import hudson.slaves.DumbSlave;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security3512Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-3512")
    void copyAgentTest() throws Exception {
        Computer.EXTENDED_READ.setEnabled(true);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mockAuthorizationStrategy = new MockAuthorizationStrategy();
        mockAuthorizationStrategy.grant(Jenkins.READ, Computer.CREATE, Computer.EXTENDED_READ).everywhere().to("alice");
        mockAuthorizationStrategy.grant(Jenkins.READ, Computer.CREATE).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(mockAuthorizationStrategy);

        DumbSlave agent = j.createOnlineSlave();

        assertEquals(2, j.getInstance().getComputers().length);

        String agentCopyURL = j.getURL() + "/computer/createItem?mode=copy&from=" + agent.getNodeName() + "&name=";

        { // with ExtendedRead permission you can copy a node
            try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false).login("alice")) {
                WebResponse rsp = wc.getPage(wc.addCrumb(new WebRequest(new URL(agentCopyURL + "aliceAgent"),
                        HttpMethod.POST))).getWebResponse();

                assertEquals(200, rsp.getStatusCode());
                assertEquals(3, j.getInstance().getComputers().length);
            }
        }

        { // without ExtendedRead permission you cannot copy a node
            try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false).login("bob")) {
                WebResponse rsp = wc.getPage(wc.addCrumb(new WebRequest(new URL(agentCopyURL + "bobAgent"),
                        HttpMethod.POST))).getWebResponse();

                assertEquals(403, rsp.getStatusCode());
                assertThat(rsp.getContentAsString(), containsString("bob is missing the Agent/ExtendedRead permission"));
                assertEquals(3, j.getInstance().getComputers().length);
            }
        }
    }
}
