package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.google.inject.Key;
import hudson.ExtensionFinder;
import hudson.ExtensionList;
import hudson.cli.declarative.CLIRegisterer;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
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
        ExtensionList<ExtensionFinder> extensionFinders = j.jenkins.getExtensionList(ExtensionFinder.class);
        assertThat(extensionFinders, hasSize(2));
        assertThat(extensionFinders, containsInAnyOrder(instanceOf(ExtensionFinder.GuiceFinder.class), instanceOf(CLIRegisterer.class)));
        for (ExtensionFinder f : extensionFinders) {
            if (f instanceof ExtensionFinder.GuiceFinder) {
                ExtensionFinder.GuiceFinder finder = (ExtensionFinder.GuiceFinder) f;
                List<String> sezpozNames = finder.getSezpozIndices().stream().map(IndexItem::className).collect(Collectors.toList());
                assertThat("jenkins.slaves.JnlpSlaveAgentProtocol4", in(sezpozNames));
                List<String> bindingTypes = finder.getContainer().getBindings().keySet().stream().map(Key::getTypeLiteral).map(Object::toString).collect(Collectors.toList());
                assertThat("jenkins.slaves.JnlpSlaveAgentProtocol4", in(bindingTypes));
                Object o = finder.getContainer().getBindings().entrySet().stream().filter(e -> e.getKey().getTypeLiteral().toString().equals("jenkins.slaves.JnlpSlaveAgentProtocol4")).map(e -> e.getValue().getProvider().get());
                assertNotNull(o);
            }
        }
        Set<String> agentProtocols = j.jenkins.getAgentProtocols();
        assertThat(agentProtocols, containsInAnyOrder("JNLP4-connect", "Ping"));
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
