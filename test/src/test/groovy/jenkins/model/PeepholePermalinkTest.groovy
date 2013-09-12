package jenkins.model

import hudson.Functions
import hudson.Util
import hudson.model.Run
import org.jvnet.hudson.test.Bug
import org.jvnet.hudson.test.FailureBuilder
import org.jvnet.hudson.test.HudsonTestCase

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class PeepholePermalinkTest extends HudsonTestCase {
    /**
     * Basic operation of the permalink generation.
     */
    void testBasics() {
        if (Functions.isWindows())  return; // can't run on windows because we rely on symlinks

        def p = createFreeStyleProject()
        def b1 = assertBuildStatusSuccess(p.scheduleBuild2(0))

        def lsb = new File(p.buildDir, "lastSuccessfulBuild")
        def lfb = new File(p.buildDir, "lastFailedBuild")

        assertLink(lsb,b1)

        // now another build that fails
        p.buildersList.add(new FailureBuilder())
        def b2 = p.scheduleBuild2(0).get()

        assertLink(lsb,b1)
        assertLink(lfb,b2)

        // one more build and this time it succeeds
        p.buildersList.clear()
        def b3 = assertBuildStatusSuccess(p.scheduleBuild2(0))

        assertLink(lsb,b3)
        assertLink(lfb,b2)

        // delete b3 and symlinks should update properly
        b3.delete()
        assertLink(lsb,b1)
        assertLink(lfb,b2)

        b1.delete()
        assertLink(lsb,null)
        assertLink(lfb,b2)

        b2.delete()
        assertLink(lsb,null)
        assertLink(lfb,null)
    }

    def assertLink(File symlink, Run build) {
        assert Util.resolveSymlink(symlink)==(build==null ? "-1" : build.number as String);
    }

    /**
     * job/JOBNAME/lastStable and job/JOBNAME/lastSuccessful symlinks that we used to generate should still work
     */
    void testLegacyCompatibility() {
        if (Functions.isWindows())  return; // can't run on windows because we rely on symlinks

        def p = createFreeStyleProject()
        def b1 = assertBuildStatusSuccess(p.scheduleBuild2(0))

        ["lastStable","lastSuccessful"].each { n ->
            // test if they both point to b1
            assert new File(p.rootDir,"$n/build.xml").length() == new File(b1.rootDir,"build.xml").length()
        }
    }

    @Bug(19034)
    void testRebuildBuildNumberPermalinks() {
        def p = createFreeStyleProject()
        def b = assertBuildStatusSuccess(p.scheduleBuild2(0))
        File f = new File(p.getBuildDir(), "1")
        // assertTrue(Util.isSymlink(f))
        f.delete()
        PeepholePermalink link = p.getPermalinks().find({l -> l instanceof PeepholePermalink})
        println(link)
        link.updateCache(p, b)
        assertTrue("build symlink hasn't been restored", f.exists())
    }
}
