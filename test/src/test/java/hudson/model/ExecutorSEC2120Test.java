package hudson.model;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import hudson.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.tasks.Builder;
import hudson.util.OneShotEvent;
import java.io.IOException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class ExecutorSEC2120Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void disconnectCause_WithoutTrace() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(slave);

        Future<FreeStyleBuild> r = startBlockingBuild(p);

        String message = "It went away";
        p.getLastBuild().getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        OfflineCause offlineCause = p.getLastBuild().getBuiltOn().toComputer().getOfflineCause();
        assertThat(offlineCause.toString(), not(containsString(message)));
    }

    /**
     * Start a project with an infinite build step
     *
     * @param project {@link FreeStyleProject} to start
     * @return A {@link Future} object represents the started build
     * @throws Exception if somethink wrong happened
     */
    public static Future<FreeStyleBuild> startBlockingBuild(FreeStyleProject project) throws Exception {
        final OneShotEvent e = new OneShotEvent();

        project.getBuildersList().add(new BlockingBuilder(e));

        Future<FreeStyleBuild> r = project.scheduleBuild2(0);
        e.block();  // wait until we are safe to interrupt
        assertTrue(project.getLastBuild().isBuilding());

        return r;
    }

    private static final class BlockingBuilder extends Builder {
        private final OneShotEvent e;

        private BlockingBuilder(OneShotEvent e) {
            this.e = e;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            VirtualChannel channel = launcher.getChannel();
            Node node = build.getBuiltOn();

            e.signal(); // we are safe to be interrupted
            for (;;) {
                // Keep using the channel
                channel.call(node.getClockDifferenceCallable());
                Thread.sleep(100);
            }
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Builder> {}
    }

}
