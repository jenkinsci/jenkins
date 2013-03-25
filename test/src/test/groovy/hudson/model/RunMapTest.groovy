package hudson.model

import org.jvnet.hudson.test.Bug
import org.jvnet.hudson.test.HudsonTestCase
import org.jvnet.hudson.test.SleepBuilder
import org.jvnet.hudson.test.TestEnvironment

import javax.xml.transform.stream.StreamSource

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class RunMapTest extends HudsonTestCase {
    /**
     * Makes sure that reloading the project while a build is in progress won't clobber that in-progress build.
     */
    @Bug(12318)
    public void testReloadWhileBuildIsInProgress() {
        def p = createFreeStyleProject()

        // want some completed build records
        def b1 = assertBuildStatusSuccess(p.scheduleBuild2(0))

        // now create a build that hangs until we signal the OneShotEvent
        p.buildersList.add(new SleepBuilder(9999999));
        def b2 = p.scheduleBuild2(0).waitForStart()
        assertEquals 2,b2.number

        // now reload
        p.updateByXml(new StreamSource(p.configFile.file))

        // we should still see the same object for #2 because that's in progress
        assertSame p.getBuildByNumber(b2.number),b2
        // build #1 should be reloaded
        assertNotSame b1,p.getBuildByNumber(1)

        // and reference gets fixed up
        b1 = p.getBuildByNumber(1)
        assertSame b1.nextBuild,b2
        assertSame b2.previousBuild,b1
    }

    /**
     * Testing if the lazy loading can gracefully tolerate a RuntimeException during unmarshalling.
     */
    @Bug(15533)
    public void testRuntimeExceptionInUnmarshalling() {
        def p = createFreeStyleProject()
        def b = assertBuildStatusSuccess(p.scheduleBuild2(0))
        b.addAction(new BombAction());
        b.save()

        p._getRuns().purgeCache()
        assert p.getBuildByNumber(b.number)==null
        assert bombed
    }

    public static class BombAction extends InvisibleAction {
        public Object readResolve() {
            TestEnvironment.get().testCase.bombed = true
            throw new NullPointerException();
        }
    }

    boolean bombed;


}
