package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.slaves.DumbSlave;
import java.io.IOException;
import org.codehaus.groovy.runtime.MethodClosure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("SECURITY-218")
@WithJenkins
class Security218Test {

    @RegisterExtension
    private final InboundAgentExtension inboundAgents = new InboundAgentExtension();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for regular static agent
     */
    @Test
    void dumbSlave() throws Exception {
        check(j.createOnlineSlave());
    }

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for JNLP agent
     */
    @Test
    void jnlpSlave() throws Exception {
        DumbSlave a = (DumbSlave) inboundAgents.createAgent(j, InboundAgentExtension.Options.newBuilder().build());
        try {
            j.createWebClient().goTo("computer/" + a.getNodeName() + "/jenkins-agent.jnlp?encrypt=true", "application/octet-stream");
            check(a);
        } finally {
            inboundAgents.stop(j, a.getNodeName());
        }
    }

    /**
     * The attack scenario here is that the controller sends a normal command to an agent and it
     * returns a malicious response.
     */
    @SuppressWarnings("ConstantConditions")
    private void check(DumbSlave s) {
        IOException e = assertThrows(
                IOException.class,
                () -> s.getComputer().getChannel().call(new EvilReturnValue()),
                "Expected the connection to die");
        assertThat(e.getMessage(), containsString(MethodClosure.class.getName()));
    }

    private static class EvilReturnValue extends MasterToSlaveCallable<Object, RuntimeException> {
        @Override
        public Object call() {
            return new MethodClosure("oops", "trim");
        }
    }
}
