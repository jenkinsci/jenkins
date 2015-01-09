package hudson.util

import hudson.WebAppMain
import hudson.model.Hudson
import hudson.model.listeners.ItemListener
import jenkins.model.Jenkins
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.jvnet.hudson.test.HudsonHomeLoader
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestEnvironment
import org.jvnet.hudson.test.TestExtension
import org.kohsuke.stapler.WebApp

import javax.servlet.ServletContextEvent

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class BootFailureTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    static boolean makeBootFail = true;

    @Rule
    public JenkinsRule j = new JenkinsRule() {
        @Override
        void before() throws Throwable {
            env = new TestEnvironment(testDescription);
            env.pin();
            // don't let Jenkins start automatically
        }

        @Override
        public Hudson newHudson() throws Exception {
            def ws = createWebServer()
            def wa = new WebAppMain() {
                @Override
                WebAppMain.FileAndDescription getHomeDir(ServletContextEvent event) {
                    return new WebAppMain.FileAndDescription(homeLoader.allocate(),"test");
                }
            }
            wa.contextInitialized(new ServletContextEvent(ws));
            wa.joinInit();

            def a = WebApp.get(ws).app;
            if (a instanceof Jenkins)
                return a;
            return null;    // didn't boot
        }
    }

    @After
    public void tearDown() {
        Jenkins.getInstance()?.cleanUp()
    }

    public static class SeriousError extends Error {}

    @TestExtension()
    public static class InduceBootFailure extends ItemListener {
        @Override
        void onLoaded() {
            if (makeBootFail)
                throw new SeriousError();
        }
    }

    @Test
    void runBootFailureScript() {
        final def home = tmpDir.newFolder()
        j.with({ -> home} as HudsonHomeLoader)

        // creates a script
        new File(home,"boot-failure.groovy").text = "hudson.util.BootFailureTest.problem = exception";
        def d = new File(home, "boot-failure.groovy.d")
        d.mkdirs();
        new File(d,"1.groovy").text = "hudson.util.BootFailureTest.runRecord << '1'";
        new File(d,"2.groovy").text = "hudson.util.BootFailureTest.runRecord << '2'";

        // first failed boot
        makeBootFail = true;
        assert j.newHudson()==null;
        assert bootFailures(home)==1;

        // second failed boot
        problem = null;
        runRecord = [];
        assert j.newHudson()==null;
        assert bootFailures(home)==2;
        assert runRecord==["1","2"]

        // make sure the script has actually run
        assert problem.cause instanceof SeriousError

        // if it boots well, the failure record should be gone
        makeBootFail = false;
        assert j.newHudson()!=null;
        assert !BootFailure.getBootFailureFile(home).exists()
    }

    private static int bootFailures(File home) {
        return BootFailure.getBootFailureFile(home).readLines().size()
    }

    // to be set by the script
    public static Exception problem;
    public static def runRecord = [];

}
