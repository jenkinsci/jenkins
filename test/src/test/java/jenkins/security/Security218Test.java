package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;

import hudson.agents.DumbAgent;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import org.codehaus.groovy.runtime.MethodClosure;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("SECURITY-218")
public class Security218Test implements Serializable {
    @Rule
    public transient JenkinsRule j = new JenkinsRule();

    @Rule
    public transient InboundAgentRule inboundAgents = new InboundAgentRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClassFilterImpl.class, Level.FINE);

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for regular static agent
     */
    @Test
    public void dumbAgent() throws Exception {
        check(j.createOnlineAgent());
    }

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for JNLP agent
     */
    @Test
    public void jnlpAgent() throws Exception {
        DumbAgent a = (DumbAgent) inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().secret().build());
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
    private void check(DumbAgent s) {
        IOException e = assertThrows(
                "Expected the connection to die",
                IOException.class,
                () -> s.getComputer().getChannel().call(new EvilReturnValue()));
        assertThat(e.getMessage(), containsString(MethodClosure.class.getName()));
    }

    private static class EvilReturnValue extends MasterToAgentCallable<Object, RuntimeException> {
        @Override
        public Object call() {
            return new MethodClosure("oops", "trim");
        }
    }
}
