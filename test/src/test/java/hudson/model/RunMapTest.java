package hudson.model;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

public class RunMapTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    /**
     * Makes sure that reloading the project while a build is in progress won't clobber that in-progress build.
     */
    @Issue("JENKNS-12318")
    @Test public void reloadWhileBuildIsInProgress() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        // want some completed build records
        FreeStyleBuild b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // now create a build that hangs until we signal the OneShotEvent
        p.getBuildersList().add(new SleepBuilder(9999999));
        FreeStyleBuild b2 = p.scheduleBuild2(0).waitForStart();
        assertEquals(2, b2.number);

        // now reload
        p.updateByXml((Source) new StreamSource(p.getConfigFile().getFile()));

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
    @Issue("JENKINS-15533")
    @Test public void runtimeExceptionInUnmarshalling() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
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
            bombed = true;
            throw new NullPointerException();
        }
    }

    private static boolean bombed;

    @Issue("JENKINS-25788")
    @Test public void remove() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        RunMap<FreeStyleBuild> runs = p._getRuns();
        assertEquals(2, runs.size());
        assertTrue(runs.remove(b1));
        assertEquals(1, runs.size());
        assertFalse(runs.remove(b1));
        assertEquals(1, runs.size());
        assertTrue(runs.remove(b2));
        assertEquals(0, runs.size());
        assertFalse(runs.remove(b2));
        assertEquals(0, runs.size());
    }

}
