package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class NodesRestartTest {
    @Rule
    public JenkinsSessionRule s = new JenkinsSessionRule();

    // The point of this implementation is to override readResolve so that Slave#readResolve doesn't get called.
    public static class DummyAgent extends Slave {
        public DummyAgent(@NonNull String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
            super(name, remoteFS, launcher);
        }

        @Override
        protected Object readResolve() {
            return this;
        }
    }

    @Test
    public void checkNodeRestart() throws Throwable {
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
