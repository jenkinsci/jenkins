package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.in;
import static org.junit.Assert.assertThrows;

import com.google.inject.Injector;
import com.google.inject.Key;
import hudson.ExtensionFinder;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.java.sezpoz.IndexItem;
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
    public void dumbSlave() throws Exception {
        check(j.createOnlineSlave());
    }

    /**
     * Makes sure SECURITY-218 fix also applies to agents.
     *
     * This test is for JNLP agent
     */
    @Test
    public void jnlpSlave() throws Exception {
        for (ExtensionFinder finder : j.jenkins.getExtensionList(ExtensionFinder.class)) {
            if (finder instanceof ExtensionFinder.GuiceFinder) {
                List<IndexItem<?, Object>> sezpozIndices = ((ExtensionFinder.GuiceFinder) finder).getSezpozIndices();
                List<String> sezpozNames = sezpozIndices.stream().map(IndexItem::className).collect(Collectors.toList());
                assertThat("jenkins.slaves.JnlpSlaveAgentProtocol4", in(sezpozNames));
                Injector container = ((ExtensionFinder.GuiceFinder) finder).getContainer();
                List<String> bindingTypes = container.getBindings().keySet().stream().map(Key::getTypeLiteral).map(Object::toString).collect(Collectors.toList());
                assertThat("jenkins.slaves.JnlpSlaveAgentProtocol4", in(bindingTypes));
            }
        }
        DumbSlave a = (DumbSlave) inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().secret().build());
        j.waitOnline(a);
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
                "Expected the connection to die",
                IOException.class,
                () -> s.getComputer().getChannel().call(new EvilReturnValue()));
        assertThat(e.getMessage(), containsString(MethodClosure.class.getName()));
    }

    private static class EvilReturnValue extends MasterToSlaveCallable<Object, RuntimeException> {
        @Override
        public Object call() {
            return new MethodClosure("oops", "trim");
        }
    }
}
