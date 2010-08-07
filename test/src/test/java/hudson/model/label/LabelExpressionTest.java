package hudson.model.label;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.DumbSlave;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class LabelExpressionTest extends HudsonTestCase {
    /**
     * Verifies the queueing behavior in the presence of the expression.
     */
    public void testQueueBehavior() throws Exception {
        DumbSlave w32 = createSlave(hudson.getLabel("win 32bit"));
        DumbSlave w64 = createSlave(hudson.getLabel("win 64bit"));
        DumbSlave l32 = createSlave(hudson.getLabel("linux 32bit"));

        final SequenceLock seq = new SequenceLock();

        FreeStyleProject p1 = createFreeStyleProject();
        p1.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                seq.phase(0); // first, make sure the w32 slave is occupied
                seq.phase(2);
                seq.done();
                return true;
            }
        });
        p1.setAssignedLabel(hudson.getLabel("win&&32bit"));

        FreeStyleProject p2 = createFreeStyleProject();
        p2.setAssignedLabel(hudson.getLabel("win&&32bit"));

        FreeStyleProject p3 = createFreeStyleProject();
        p3.setAssignedLabel(hudson.getLabel("win"));

        Future<FreeStyleBuild> f1 = p1.scheduleBuild2(0);

        seq.phase(1); // we schedule p2 build after w32 slave is occupied
        Future<FreeStyleBuild> f2 = p2.scheduleBuild2(0);

        Thread.sleep(1000); // time window to ensure queue has tried to assign f2 build

        // p3 is tied to 'win', so even though p1 is busy, this should still go ahead and complete
        FreeStyleBuild b3 = assertBuildStatusSuccess(p3.scheduleBuild2(0));
        assertSame(w64,b3.getBuiltOn());

        seq.phase(3);   // once we confirm that p3 build is over, we let p1 proceed

        // p1 should have been built on w32
        FreeStyleBuild b1 = assertBuildStatusSuccess(f1);
        assertSame(w32,b1.getBuiltOn());

        // and so is p2
        FreeStyleBuild b2 = assertBuildStatusSuccess(f2);
        assertSame(w32,b2.getBuiltOn());
    }
}
