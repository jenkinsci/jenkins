package hudson.model;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestEnvironment;

import javax.xml.transform.stream.StreamSource;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class RunMapTest extends HudsonTestCase {
    /**
     * Makes sure that reloading the project while a build is in progress won't clobber that in-progress build.
     */
    @Bug(12318)
    public void testReloadWhileBuildIsInProgress() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        // want some completed build records
        FreeStyleBuild b1 = assertBuildStatusSuccess(p.scheduleBuild2(0));

        // now create a build that hangs until we signal the OneShotEvent
        p.getBuildersList().add(new SleepBuilder(9999999));
        FreeStyleBuild b2 = p.scheduleBuild2(0).waitForStart();
        assertEquals(2, b2.number);

        // now reload
        p.updateByXml(new StreamSource(p.getConfigFile().getFile()));

        // we should still see the same object for #2 because that's in progress
        assertSame(p.getBuildByNumber(b2.number), b2);
        // build #1 should be reloaded
        assertNotSame(b1, p.getBuildByNumber(1));

        // and reference gets fixed up
        b1 = p.getBuildByNumber(1);
        assertSame(b1.getNextBuild(), b2);
        assertSame(b2.getPreviousBuild(), b1);
    }

    /**
     * Testing if the lazy loading can gracefully tolerate a RuntimeException during unmarshalling.
     */
    @Bug(15533)
    public void testRuntimeExceptionInUnmarshalling() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0));
        b.addAction(new BombAction());
        b.save();

        p._getRuns().purgeCache();
        b = p.getBuildByNumber(b.number);
        // Original test assumed that b == null, but after JENKINS-21024 this is no longer true,
        // so this may not really be testing anything interesting:
        assertNotNull(b);
        assertNull(b.getAction(BombAction.class));
        assertTrue(bombed);
    }

    public static class BombAction extends InvisibleAction {
        public Object readResolve() {
            ((RunMapTest) TestEnvironment.get().testCase).bombed = true;
            throw new NullPointerException();
        }
    }

    private boolean bombed;


}
