package jenkins.diagnostics.ooom

import com.gargoylesoftware.htmlunit.html.HtmlPage
import hudson.model.FreeStyleProject
import hudson.model.Job
import hudson.model.TaskListener
import hudson.util.StreamTaskListener
import static org.junit.Assert.*;
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Bug
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.LocalData

import javax.inject.Inject

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class OutOfOrderBuildDetectorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    OutOfOrderBuildDetector oobd;

    @Inject
    OutOfOrderBuildMonitor oobm;

    /**
     * The test case looks like [#3,#1,#2,#4] and we should find #3 to be the problem.
     */
    @Test @LocalData
    public void oneProblem() {
        j.jenkins.injector.injectMembers(this);

        Job dt = j.jenkins.getItem("deletion-test");

        def p = Problem.find(dt);
        assert p.offenders.size()==1;
        def b = (p.offenders as List)[0];

        assert b.id=="2013-07-29_17-09-09"
        assert b.n==3

        def l = StreamTaskListener.fromStdout()

        // find all the problems now
        oobd.execute(l,0);

        // verify that the monitor is activated
        assert oobm.isActivated();
        assert oobm.problems.size()==1;

        def wc = j.createWebClient()

        // at this point there should be a link to start a fix but not the button to dismiss
        def manage = wc.goTo("manage")
        assertNoForm(manage,"dismissOutOfOrderBuilds")
        j.submit(manage.getFormByName("fixOutOfOrderBuilds"));

        // give it a break until the fix is complete
        while (oobm.isFixingActive())
            Thread.sleep(100);

        // there should be a log file now
        oobm.getLogFile().exists()
        wc.goTo("administrativeMonitor/${oobm.class.name}/log")

        // at this point the UI should change to show a dismiss action
        manage = wc.goTo("manage")
        assertNoForm(manage,"fixOutOfOrderBuilds")
        j.submit(manage.getFormByName("dismissOutOfOrderBuilds"));

        // that should stop the alarm and there should be no more forms
        assert !oobm.isActivated();
        manage = wc.goTo("manage")
        assertNoForm(manage,"fixOutOfOrderBuilds")
        assertNoForm(manage,"dismissOutOfOrderBuilds")

        // verify that the problem is actually fixed
        assert dt.getBuildByNumber(1)!=null;
        assert dt.getBuildByNumber(2)!=null;
        assert dt.getBuildByNumber(3)==null;
        assert dt.getBuildByNumber(4)!=null;
        assert Problem.find(dt)==null;

        // and there should be a backup
        assert new File(dt.rootDir,"outOfOrderBuilds/2013-07-29_17-09-09/build.xml").exists()
    }

    def assertNoForm(HtmlPage p, String name) {
        def forms = p.documentElement.getElementsByAttribute("form", "name", name);
        assert forms.size()==0;
    }

    /**
     * If there's no problem, it shouldn't find it.
     */
    @Test
    public void thereShouldBeNoFailure() {
        def f = j.createFreeStyleProject()
        j.assertBuildStatusSuccess(f.scheduleBuild2(0));
        j.assertBuildStatusSuccess(f.scheduleBuild2(0));
        j.assertBuildStatusSuccess(f.scheduleBuild2(0));
        assert Problem.find(f)==null;
    }

    @Bug(22631)
    @LocalData
    @Test public void buildNumberClash() throws Exception {
        j.jenkins.injector.injectMembers(this);
        FreeStyleProject p = j.jenkins.getItemByFullName("problematic", FreeStyleProject.class);
        assertNotNull(p);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TaskListener l = new StreamTaskListener(baos);
        oobd.execute(l, 0);
        String log = baos.toString();
        String idA = "2014-04-15_11-22-11";
        String idB = "2014-04-15_11-22-14";
        assertEquals(idB, p.getBuildByNumber(2).getId());
        assertEquals(3, p.getLastBuild().getNumber());
        File dir = p.getBuildDir();
        File buildDirA = new File(dir, idA);
        File buildDirB = new File(dir, idB);
        BuildPtr b2A = new BuildPtr(p, buildDirA, 2);
        BuildPtr b2B = new BuildPtr(p, buildDirB, 2);
        String expected = "[" + b2A + "]";
        assertTrue("Should see " + expected + " in:\n" + log, log.contains(expected));
        baos = new ByteArrayOutputStream();
        l = new StreamTaskListener(baos);
        oobm.fix(l);
        log = baos.toString();
        assertTrue(buildDirB.isDirectory());
        assertTrue("Should see " + buildDirA + " in:\n" + log, log.contains(buildDirA.toString()));
        File dest = new File(new File(p.getRootDir(), "outOfOrderBuilds"), idA);
        assertTrue("Should see " + dest + " in:\n" + log, log.contains(dest.toString()));
        assertFalse(buildDirA.isDirectory());
        assertTrue(dest.isDirectory());
    }

}
