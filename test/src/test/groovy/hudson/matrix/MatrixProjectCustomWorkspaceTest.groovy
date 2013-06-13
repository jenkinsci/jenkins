package hudson.matrix

import java.util.concurrent.CountDownLatch
import org.jvnet.hudson.test.TestBuilder
import hudson.model.AbstractBuild
import hudson.Launcher
import hudson.model.BuildListener
import org.jvnet.hudson.test.HudsonTestCase

/**
 * Tests the custom workspace support in {@link MatrixProject}.
 *
 * To validate the lease behaviour, use concurrent builds to run two builds and make sure they get
 * same/different workspaces.
 *
 * @author Kohsuke Kawaguchi
 */
class MatrixProjectCustomWorkspaceTest extends HudsonTestCase {
    /**
     * Test the case where both the parent and the child has custom workspace specified.
     */
    void testCustomWorkspace1() {
        def p = createMatrixProject()
        def dir = env.temporaryDirectoryAllocator.allocate()
        p.customWorkspace = dir
        p.childCustomWorkspace = "xyz"

        configRoundtrip(p)
        configureCustomWorkspaceConcurrentBuild(p)

        // all concurrent builds should build on the same one workspace
        runTwoConcurrentBuilds(p).each { b ->
            assertEquals(dir.path, b.workspace.getRemote())
            b.runs.each { r -> assertEquals(new File(dir,"xyz").path, r.workspace.getRemote()) }
        }
    }

    /**
     * Test the case where only the parent has a custom workspace.
     */
    void testCustomWorkspace2() {
        def p = createMatrixProject()
        def dir = env.temporaryDirectoryAllocator.allocate()
        p.customWorkspace = dir
        p.childCustomWorkspace = null

        configRoundtrip(p)
        configureCustomWorkspaceConcurrentBuild(p)

        def bs = runTwoConcurrentBuilds(p)

        // all parent builds share the same workspace
        bs.each { b ->
            assertEquals(dir.path, b.workspace.getRemote())
        }
        // foo=1 #1 and foo=1 #2 shares the same workspace,
        (0..<2).each { i ->
            assertTrue bs[0].runs[i].workspace == bs[1].runs[i].workspace
        }
        // but foo=1 #1 and foo=2 #1 shouldn't.
        (0..<2).each { i ->
            assertTrue bs[i].runs[0].workspace != bs[i].runs[1].workspace
        }
    }

    /**
     * Test the case where only the child has a custom workspace.
     */
    void testCustomWorkspace3() {
        def p = createMatrixProject()
        p.customWorkspace = null
        p.childCustomWorkspace = "."

        configRoundtrip(p)
        configureCustomWorkspaceConcurrentBuild(p)

        def bs = runTwoConcurrentBuilds(p)

        // each parent gets different directory
        assertTrue bs[0].workspace != bs[1].workspace
        // but all #1 builds should get the same workspace
        bs.each { b ->
            (0..<2).each { i-> assertTrue b.workspace == b.runs[i].workspace }
        }
    }

    /**
     * Test the case where neither has custom workspace
     */
    void testCustomWorkspace4() {
        def p = createMatrixProject()
        p.customWorkspace = null
        p.childCustomWorkspace = null

        configRoundtrip(p)
        configureCustomWorkspaceConcurrentBuild(p)

        def bs = runTwoConcurrentBuilds(p)

        // each parent gets different directory
        assertTrue bs[0].workspace != bs[1].workspace
        // and every sub-build gets a different directory
        bs.each { b ->
            def x = b.runs[0].workspace
            def y = b.runs[1].workspace
            def z = b.workspace
            
            assertTrue x!=y
            assertTrue y!=z
            assertTrue z!=x
        }
    }

    /**
     * Configures MatrixProject such that two builds run concurrently.
     */
    def configureCustomWorkspaceConcurrentBuild(MatrixProject p) {
        // needs sufficient parallel execution capability
        jenkins.numExecutors = 10
        jenkins.updateComputerList()

        p.axes = new AxisList(new TextAxis("foo", "1", "2"))
        p.concurrentBuild = true;
        def latch = new CountDownLatch(4)

        p.buildersList.add(new TestBuilder() {
            boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
                latch.countDown()
                latch.await()
                return true
            }
        })
    }

    /**
     * Runs two concurrent builds and return their results.
     */
    List<MatrixBuild> runTwoConcurrentBuilds(MatrixProject p) {
        def f1 = p.scheduleBuild2(0)
        // get one going
        Thread.sleep(1000)
        def f2 = p.scheduleBuild2(0)

        def bs = [f1, f2]*.get().each { assertBuildStatusSuccess(it) }
        return bs
    }
}
