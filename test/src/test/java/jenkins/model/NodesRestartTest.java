package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import java.io.IOException;
import java.io.Serial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class NodesRestartTest {

    @RegisterExtension
    private final JenkinsSessionExtension s = new JenkinsSessionExtension();

    // The point of this implementation is to override readResolve so that Slave#readResolve doesn't get called.
    public static class DummyAgent extends Slave {
        @SuppressWarnings(value = "checkstyle:redundantmodifier")
        public DummyAgent(@NonNull String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
            super(name, remoteFS, launcher);
        }

        @Serial
        @Override
        protected Object readResolve() {
            return this;
        }
    }

    @Test
    void checkNodeRestart() throws Throwable {
        s.then(r -> {
            assertThat(r.jenkins.getNodes(), hasSize(0));
            var node = new DummyAgent("my-node", "temp", r.createComputerLauncher(null));
            r.jenkins.addNode(node);
            assertThat(r.jenkins.getNodes(), hasSize(1));
        });
        s.then(r -> {
            assertThat(r.jenkins.getNodes(), hasSize(1));
            var node = r.jenkins.getNode("my-node");
            assertNotNull(node.getNodeProperties());
            assertNotNull(node.getAssignedLabels());
        });
    }
}
