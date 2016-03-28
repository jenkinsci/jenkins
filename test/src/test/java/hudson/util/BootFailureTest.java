package hudson.util;

import hudson.WebAppMain;
import hudson.model.Hudson;
import hudson.model.listeners.ItemListener;
import static hudson.util.BootFailureTest.makeBootFail;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.WebApp;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class BootFailureTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    static boolean makeBootFail = true;
    static WebAppMain wa;

    static class CustomRule extends JenkinsRule {
        @Override
        public void before() throws Throwable {
            env = new TestEnvironment(testDescription);
            env.pin();
            // don't let Jenkins start automatically
        }

        @Override
        public Hudson newHudson() throws Exception {
            ServletContext ws = createWebServer();
            wa = new WebAppMain() {
                @Override
                public WebAppMain.FileAndDescription getHomeDir(ServletContextEvent event) {
                    try {
                        return new WebAppMain.FileAndDescription(homeLoader.allocate(), "test");
                    } catch (Exception x) {
                        throw new AssertionError(x);
                    }
                }
            };
            wa.contextInitialized(new ServletContextEvent(ws));
            wa.joinInit();

            Object a = WebApp.get(ws).getApp();
            if (a instanceof Hudson) {
                return (Hudson) a;
            }
            return null;    // didn't boot
        }
    }
    @Rule
    public CustomRule j = new CustomRule();

    @After
    public void tearDown() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) {
            j.cleanUp();
        }
    }

    public static class SeriousError extends Error {}

    @TestExtension("runBootFailureScript")
    public static class InduceBootFailure extends ItemListener {
        @Override
        public void onLoaded() {
            if (makeBootFail)
                throw new SeriousError();
        }
    }

    @Test
    public void runBootFailureScript() throws Exception {
        final File home = tmpDir.newFolder();
        j.with(new HudsonHomeLoader() {
            @Override
            public File allocate() throws Exception {
                return home;
            }
        });

        // creates a script
        FileUtils.write(new File(home, "boot-failure.groovy"), "hudson.util.BootFailureTest.problem = exception");
        File d = new File(home, "boot-failure.groovy.d");
        d.mkdirs();
        FileUtils.write(new File(d, "1.groovy"), "hudson.util.BootFailureTest.runRecord << '1'");
        FileUtils.write(new File(d, "2.groovy"), "hudson.util.BootFailureTest.runRecord << '2'");

        // first failed boot
        makeBootFail = true;
        assertNull(j.newHudson());
        assertEquals(1, bootFailures(home));

        // second failed boot
        problem = null;
        runRecord = new ArrayList<String>();
        assertNull(j.newHudson());
        assertEquals(2, bootFailures(home));
        assertEquals(Arrays.asList("1", "2"), runRecord);

        // make sure the script has actually run
        assertEquals(SeriousError.class, problem.getCause().getClass());

        // if it boots well, the failure record should be gone
        makeBootFail = false;
        assertNotNull(j.newHudson());
        assertFalse(BootFailure.getBootFailureFile(home).exists());
    }

    private static int bootFailures(File home) throws IOException {
        return FileUtils.readLines(BootFailure.getBootFailureFile(home)).size();
    }

    @Issue("JENKINS-24696")
    @Test
    public void interruptedStartup() throws Exception {
        final File home = tmpDir.newFolder();
        j.with(new HudsonHomeLoader() {
            @Override
            public File allocate() throws Exception {
                return home;
            }
        });
        File d = new File(home, "boot-failure.groovy.d");
        d.mkdirs();
        FileUtils.write(new File(d, "1.groovy"), "hudson.util.BootFailureTest.runRecord << '1'");
        j.newHudson();
        assertEquals(Collections.singletonList("1"), runRecord);
    }
    @TestExtension("interruptedStartup")
    public static class PauseBoot extends ItemListener {
        @Override
        public void onLoaded() {
            wa.contextDestroyed(null);
            // make the Jenkins.<init> thread abort
            throw new Error();
        }
    }

    // to be set by the script
    public static Exception problem;
    public static List<String> runRecord = new ArrayList<String>();

}
