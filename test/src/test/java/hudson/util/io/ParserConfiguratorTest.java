package hudson.util.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Extension;
import hudson.remoting.Channel;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for {@link ParserConfigurator}.
 */
@WithJenkins
class ParserConfiguratorTest {

    /**
     * Verifies that when running on the controller (no active {@link Channel}),
     * {@link ParserConfigurator#applyConfiguration} dispatches to the local
     * extension list and actually invokes registered configurators.
     *
     * <p>Before the fix, the code checked {@code Jenkins.getInstanceOrNull() == null}
     * to detect the agent context. That caused the Jenkins class to be loaded on agents,
     * triggering an {@link ExceptionInInitializerError} / classloading timeout.
     * The fix replaces that check with {@code Channel.current() != null}.
     */
    @Issue("JENKINS-26471")
    @Test
    void controllerPathInvokesRegisteredConfigurators() throws IOException, InterruptedException {
        // Confirm we are on the controller — no remoting Channel is active.
        assertTrue(Channel.current() == null, "Expected no active Channel on the controller");

        // Reset the flag before the test.
        TrackingConfigurator.invoked.set(false);

        SAXReader reader = new SAXReader();
        ParserConfigurator.applyConfiguration(reader, this);

        assertTrue(
                TrackingConfigurator.invoked.get(),
                "ParserConfigurator registered as an extension should have been invoked on the controller path");
    }

    /**
     * A Jenkins extension that records whether it was called.
     * Registered automatically by {@link Extension}.
     */
    @Extension
    public static class TrackingConfigurator extends ParserConfigurator {
        static final AtomicBoolean invoked = new AtomicBoolean(false);

        @Override
        public void configure(SAXReader reader, Object context) {
            invoked.set(true);
        }
    }
}
