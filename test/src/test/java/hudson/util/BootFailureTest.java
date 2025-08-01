package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.WebAppMain;
import hudson.model.Hudson;
import hudson.model.listeners.ItemListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.kohsuke.stapler.WebApp;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class BootFailureTest {

    @TempDir
    private File tmpDir;

    static boolean makeBootFail = true;
    static WebAppMain wa;

    private static String forceSessionTrackingByCookiePreviousValue;

    // to be set by the script
    private static Exception problem;
    private static List<String> runRecord = new ArrayList<>();

    @RegisterExtension
    private final JenkinsSessionExtension session = new CustomJenkinsSessionExtension();

    /*
     * TODO use RealJenkinsRule
     *
     * The system property change workaround is needed because wa.contextInitialized is called while the context is
     * already started. The JavaDoc is explicit: it must be starting, not started. When this test has been rewritten to
     * use RealJenkinsRule, this workaround should be deleted.
     */

    @BeforeAll
    static void disableSessionTrackingSetting() {
        forceSessionTrackingByCookiePreviousValue = SystemProperties.getString(WebAppMain.FORCE_SESSION_TRACKING_BY_COOKIE_PROP);
        System.setProperty(WebAppMain.FORCE_SESSION_TRACKING_BY_COOKIE_PROP, "false");
    }

    @AfterAll
    static void resetSessionTrackingSetting() {
        if (forceSessionTrackingByCookiePreviousValue == null) {
            System.clearProperty(WebAppMain.FORCE_SESSION_TRACKING_BY_COOKIE_PROP);
        } else {
            System.setProperty(WebAppMain.FORCE_SESSION_TRACKING_BY_COOKIE_PROP, forceSessionTrackingByCookiePreviousValue);
        }
    }

    @AfterEach
    void tearDown() {
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
    void runBootFailureScript() throws Throwable {
        session.then(j -> {
            final File home = newFolder(tmpDir, "junit");
            j.with(() -> home);

            // creates a script
            Files.writeString(home.toPath().resolve("boot-failure.groovy"), "hudson.util.BootFailureTest.problem = exception", StandardCharsets.UTF_8);
            Path d = home.toPath().resolve("boot-failure.groovy.d");
            Files.createDirectory(d);
            Files.writeString(d.resolve("1.groovy"), "hudson.util.BootFailureTest.runRecord << '1'", StandardCharsets.UTF_8);
            Files.writeString(d.resolve("2.groovy"), "hudson.util.BootFailureTest.runRecord << '2'", StandardCharsets.UTF_8);

            // first failed boot
            makeBootFail = true;
            assertNull(((CustomJenkinsSessionExtension.CustomJenkinsRule) j).newHudson());
            assertEquals(1, bootFailures(home));

            // second failed boot
            problem = null;
            runRecord = new ArrayList<>();
            assertNull(((CustomJenkinsSessionExtension.CustomJenkinsRule) j).newHudson());
            assertEquals(2, bootFailures(home));
            assertEquals(Arrays.asList("1", "2"), runRecord);

            // make sure the script has actually run
            assertEquals(SeriousError.class, problem.getCause().getClass());

            // if it boots well, the failure record should be gone
            makeBootFail = false;
            assertNotNull(((CustomJenkinsSessionExtension.CustomJenkinsRule) j).newHudson());
            assertFalse(BootFailure.getBootFailureFile(home).exists());
        });
    }

    private static int bootFailures(File home) {
        return new BootFailure() { }.loadAttempts(home).size();
    }

    @Issue("JENKINS-24696")
    @Test
    void interruptedStartup() throws Throwable {
        session.then(j -> {
            final File home = newFolder(tmpDir, "junit");
            j.with(() -> home);
            Path d = home.toPath().resolve("boot-failure.groovy.d");
            Files.createDirectory(d);
            Files.writeString(d.resolve("1.groovy"), "hudson.util.BootFailureTest.runRecord << '1'", StandardCharsets.UTF_8);
            ((CustomJenkinsSessionExtension.CustomJenkinsRule) j).newHudson();
            assertEquals(List.of("1"), runRecord);
        });
    }

    @TestExtension("interruptedStartup")
    public static class PauseBoot extends ItemListener {
        @Override
        public void onLoaded() {
            wa.contextDestroyed(null);
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

    private static class CustomJenkinsSessionExtension extends JenkinsSessionExtension {

        private int port;
        private Description description;

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(getHome(), port);
            r.apply(
                    new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            port = r.getPort();
                            s.run(r);
                        }
                    },
                    description
            ).evaluate();
        }

        private static final class CustomJenkinsRule extends JenkinsRule {

            CustomJenkinsRule(File home, int port) {
                with(() -> home);
                localPort = port;
            }

            int getPort() {
                return localPort;
            }

            @Override
            public void before() {
                env = new TestEnvironment(testDescription);
                env.pin();
                // don't let Jenkins start automatically
            }

            @Override
            public Hudson newHudson() throws Exception {
                localPort = 0;
                ServletContext ws = createWebServer2();
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
    }
}
