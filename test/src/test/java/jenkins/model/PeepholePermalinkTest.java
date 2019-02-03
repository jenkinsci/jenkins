package jenkins.model;

import hudson.Functions;
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import java.io.File;
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class PeepholePermalinkTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Basic operation of the permalink generation.
     */
    @Test
    public void basics() throws Exception {
        Assume.assumeFalse("can't run on windows because we rely on symlinks", Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b1 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        File lsb = new File(p.getBuildDir(), "lastSuccessfulBuild");
        File lfb = new File(p.getBuildDir(), "lastFailedBuild");

        assertLink(lsb, b1);

        // now another build that fails
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b2 = p.scheduleBuild2(0).get();

        assertLink(lsb, b1);
        assertLink(lfb, b2);

        // one more build and this time it succeeds
        p.getBuildersList().clear();
        FreeStyleBuild b3 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertLink(lsb, b3);
        assertLink(lfb, b2);

        // delete b3 and symlinks should update properly
        b3.delete();
        assertLink(lsb, b1);
        assertLink(lfb, b2);

        b1.delete();
        assertLink(lsb, null);
        assertLink(lfb, b2);

        b2.delete();
        assertLink(lsb, null);
        assertLink(lfb, null);
    }

    private void assertLink(File symlink, Run build) throws Exception {
        assertEquals(build == null ? "-1" : Integer.toString(build.getNumber()), Util.resolveSymlink(symlink));
    }

    /**
     * job/JOBNAME/lastStable and job/JOBNAME/lastSuccessful symlinks that we
     * used to generate should still work
     */
    @Test
    public void legacyCompatibility() throws Exception {
        Assume.assumeFalse("can't run on windows because we rely on symlinks", Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b1 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        for (String n : new String[] {"lastStable", "lastSuccessful"}) {
            // test if they both point to b1
            assertEquals(new File(p.getRootDir(), n + "/build.xml").length(), new File(b1.getRootDir(), "build.xml").length());
        }
    }

    @Test
    @Issue("JENKINS-19034")
    public void rebuildBuildNumberPermalinks() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        File f = new File(p.getBuildDir(), "1");
        // assertTrue(Util.isSymlink(f))
        f.delete();
        PeepholePermalink link = (PeepholePermalink) p.getPermalinks().stream().filter(l -> l instanceof PeepholePermalink).findAny().get();
        link.updateCache(p, b);
        assertTrue("build symlink hasn't been restored", f.exists());
    }

}
