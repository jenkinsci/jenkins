package hudson.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.WebAppMain;
import hudson.model.Hudson;
import hudson.model.listeners.ItemListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.NoListenerConfiguration2;
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
        public void before() {
            env = new TestEnvironment(testDescription);
            env.pin();
            // don't let Jenkins start automatically
        }

        @Override
        public Hudson newHudson() throws Exception {
            localPort = 0;
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
            // Without this gymnastic, the jenkins-test-harness adds a NoListenerConfiguration
            // that prevents us to inject our own custom WebAppMain
            // With this approach we can make the server calls the regular contextInitialized
            ServletContext ws = createWebServer2((context, server) -> {
                NoListenerConfiguration2 noListenerConfiguration = context.getBean(NoListenerConfiguration2.class);
                // future-proof
                assertNotNull(noListenerConfiguration);
                if (noListenerConfiguration != null) {
                    context.removeBean(noListenerConfiguration);
                    context.addBean(new NoListenerConfiguration2(context) {
                        @Override
                        protected void doStart() {
                            // default behavior of noListenerConfiguration
                            super.doStart();
                            // ensuring our custom context will received the contextInitialized event
                            context.addEventListener(wa);
                        }
                    });
                }
            });
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
        j.with(() -> home);

        // creates a script
        Files.writeString(home.toPath().resolve("boot-failure.groovy"), "hudson.util.BootFailureTest.problem = exception", StandardCharsets.UTF_8);
        Path d = home.toPath().resolve("boot-failure.groovy.d");
        Files.createDirectory(d);
        Files.writeString(d.resolve("1.groovy"), "hudson.util.BootFailureTest.runRecord << '1'", StandardCharsets.UTF_8);
        Files.writeString(d.resolve("2.groovy"), "hudson.util.BootFailureTest.runRecord << '2'", StandardCharsets.UTF_8);

        // first failed boot
        makeBootFail = true;
        assertNull(j.newHudson());
        assertEquals(1, bootFailures(home));

        // second failed boot
        problem = null;
        runRecord = new ArrayList<>();
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
        return new BootFailure() { }.loadAttempts(home).size();
    }

    @Issue("JENKINS-24696")
    @Test
    public void interruptedStartup() throws Exception {
        final File home = tmpDir.newFolder();
        j.with(() -> home);
        Path d = home.toPath().resolve("boot-failure.groovy.d");
        Files.createDirectory(d);
        Files.writeString(d.resolve("1.groovy"), "hudson.util.BootFailureTest.runRecord << '1'", StandardCharsets.UTF_8);
        j.newHudson();
        assertEquals(List.of("1"), runRecord);
    }

    @TestExtension("interruptedStartup")
    public static class PauseBoot extends ItemListener {
        @Override
        public void onLoaded() {
            wa.contextDestroyed(null);
        }
    }

    // to be set by the script
    public static Exception problem;
    public static List<String> runRecord = new ArrayList<>();

}
