package hudson.model;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.util.OneShotEvent;
import jenkins.model.CauseOfInterruption.UserInterruption;
import jenkins.model.InterruptedBuildAction;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExecutorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void yank() throws Exception {
        j.jenkins.setNumExecutors(1);
        Computer c = j.jenkins.toComputer();
        final Executor e = c.getExecutors().get(0);

        // kill an executor
        kill(e);

        // make sure it's dead
        assertTrue(c.getExecutors().contains(e));
        assertTrue(e.getCauseOfDeath()!=null);

        // test the UI
        HtmlPage p = j.createWebClient().goTo("");
        p = p.getAnchorByText("Dead (!)").click();
        assertTrue(p.getWebResponse().getContentAsString().contains(ThreadDeath.class.getName()));
        j.submit(p.getFormByName("yank"));

        assertFalse(c.getExecutors().contains(e));
        waitUntilExecutorSizeIs(c, 1);
    }

    @Test
    @Issue("JENKINS-4756")
    public void whenAnExecutorIsYankedANewExecutorTakesItsPlace() throws Exception {
        j.jenkins.setNumExecutors(1);

        Computer c = j.jenkins.toComputer();
        Executor e = getExecutorByNumber(c, 0);

        kill(e);
        e.doYank();

        waitUntilExecutorSizeIs(c, 1);

        assertNotNull(getExecutorByNumber(c, 0));
    }

    private void waitUntilExecutorSizeIs(Computer c, int executorCollectionSize) throws InterruptedException {
        int timeOut = 10;
        while (c.getExecutors().size() != executorCollectionSize) {
            Thread.sleep(10);
            if (timeOut-- == 0) fail("executor collection size was not " + executorCollectionSize);
        }
    }

    private void kill(Executor e) throws InterruptedException, IOException {
        e.killHard();
        // trigger a new build which causes the forced death of the executor
        j.createFreeStyleProject().scheduleBuild2(0);
        while (e.isActive())
            Thread.sleep(10);
    }

    private Executor getExecutorByNumber(Computer c, int executorNumber) {
        for (Executor executor : c.getExecutors()) {
            if (executor.getNumber() == executorNumber) {
                return executor;
            }
        }
        return null;
    }

    /**
     * Makes sure that the cause of interruption is properly recorded.
     */
    @Test
    public void abortCause() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        Future<FreeStyleBuild> r = startBlockingBuild(p);

        User johnny = User.get("Johnny");
        p.getLastBuild().getExecutor().interrupt(Result.FAILURE,
                new UserInterruption(johnny),   // test the merge semantics
                new UserInterruption(johnny));

        FreeStyleBuild b = r.get();

        // make sure this information is recorded
        assertEquals(b.getResult(), Result.FAILURE);
        InterruptedBuildAction iba = b.getAction(InterruptedBuildAction.class);
        assertEquals(1,iba.getCauses().size());
        assertEquals(((UserInterruption) iba.getCauses().get(0)).getUser(), johnny);

        // make sure it shows up in the log
        assertTrue(b.getLog().contains(johnny.getId()));
    }

    @Test
    public void disconnectCause() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(slave);

        Future<FreeStyleBuild> r = startBlockingBuild(p);
        User johnny = User.get("Johnny");

        p.getLastBuild().getBuiltOn().toComputer().disconnect(
                new OfflineCause.UserCause(johnny, "Taking offline to break your build")
        );

        FreeStyleBuild b = r.get();

        String log = b.getLog();
        assertEquals(b.getResult(), Result.FAILURE);
        assertThat(log, containsString("Finished: FAILURE"));
        assertThat(log, containsString("Build step 'Bogus' marked build as failure"));
        assertThat(log, containsString("Slave went offline during the build"));
        assertThat(log, containsString("Disconnected by Johnny : Taking offline to break your buil"));
    }

    private Future<FreeStyleBuild> startBlockingBuild(FreeStyleProject p) throws Exception {
        final OneShotEvent e = new OneShotEvent();

        p.getBuildersList().add(new BlockingBuilder(e));

        Future<FreeStyleBuild> r = p.scheduleBuild2(0);
        e.block();  // wait until we are safe to interrupt
        assertTrue(p.getLastBuild().isBuilding());

        return r;
    }

    private static final class BlockingBuilder extends TestBuilder {
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
    }
}
