package hudson.util.io;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.remoting.Channel;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for {@link ParserConfigurator}.
 */
@WithJenkins
class ParserConfiguratorTest {

    /**
     * Verifies that when running on the controller (no active {@link Channel}),
     * {@link ParserConfigurator#applyConfiguration} uses {@code JenkinsJVM.isJenkinsJVM()}
     * to dispatch to the local extension list and invokes registered configurators.
     *
     * <p>Before the fix, the code called {@code Jenkins.getInstanceOrNull()} on agents,
     * which triggered the Jenkins class static initialiser and caused an
     * {@link ExceptionInInitializerError} / classloading timeout on the agent JVM.
     * The fix uses {@code JenkinsJVM.isJenkinsJVM()} as the authoritative
     * controller/agent discriminator.
     */
    @Issue("JENKINS-26473")
    @Test
    void controllerPathInvokesRegisteredConfigurators(JenkinsRule j) throws IOException, InterruptedException {
        // Confirm we are on the controller — no remoting Channel is active.
        assertNull(Channel.current(), "Expected no active Channel on the controller");

        AtomicBoolean invoked = new AtomicBoolean(false);

        // Register a configurator directly into the extension list so the test
        // does not rely on the annotation processor indexing a @Deprecated extension point.
        j.jenkins.getExtensionList(ParserConfigurator.class).add(new ParserConfigurator() {
            @Override
            public void configure(SAXReader reader, Object context) {
                invoked.set(true);
            }
        });

        ParserConfigurator.applyConfiguration(new SAXReader(), this);

        assertTrue(
                invoked.get(),
                "ParserConfigurator registered as an extension should have been invoked on the controller path");
    }
}
