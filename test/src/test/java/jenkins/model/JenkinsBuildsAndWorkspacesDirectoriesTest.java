package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.init.InitMilestone;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Since JENKINS-50164, Jenkins#workspacesDir and Jenkins#buildsDir had their associated UI deleted.
 * So instead of configuring through the UI, we now have to use sysprops for this.
 * <p>
 * So this test class uses a {@link JenkinsSessionExtension} to check the behaviour of this sysprop being
 * present or not between two restarts.
 */
class JenkinsBuildsAndWorkspacesDirectoriesTest {

    private static final String LOG_WHEN_CHANGING_BUILDS_DIR = "Changing builds directories from ";
    private static final String LOG_WHEN_CHANGING_WORKSPACES_DIR = "Changing workspaces directories from ";

    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();

    private final LogRecorder loggerRule = new LogRecorder();

    @TempDir
    private static File tmp;

    @BeforeEach
    void before() {
        clearSystemProperties();
    }

    @AfterEach
    void after() {
        clearSystemProperties();
    }

    private void clearSystemProperties() {
        Stream.of(Jenkins.BUILDS_DIR_PROP, Jenkins.WORKSPACES_DIR_PROP)
                .forEach(System::clearProperty);
    }

    @Issue("JENKINS-53284")
    @Test
    void changeWorkspacesDirLog() throws Throwable {
        loggerRule.record(Jenkins.class, Level.WARNING)
                .record(Jenkins.class, Level.INFO).capture(1000);

        story.then(step -> {
            assertFalse(logWasFound(LOG_WHEN_CHANGING_WORKSPACES_DIR));
            setWorkspacesDirProperty("testdir1");
        });

        story.then(step -> {
            assertTrue(logWasFoundAtLevel(LOG_WHEN_CHANGING_WORKSPACES_DIR,
                                          Level.WARNING));
            setWorkspacesDirProperty("testdir2");
        });

        story.then(step -> assertTrue(logWasFoundAtLevel(LOG_WHEN_CHANGING_WORKSPACES_DIR, Level.WARNING)));
    }

    @Issue("JENKINS-50164")
    @Test
    void badValueForBuildsDir() throws Throwable {
        story.then(rule -> {
            final List<String> badValues = new ArrayList<>(Arrays.asList(
                    "blah",
                    "$JENKINS_HOME",
                    "$JENKINS_HOME/builds",
                    "$ITEM_FULL_NAME",
                    "/path/to/builds",
                    "/invalid/$JENKINS_HOME",
                    "relative/ITEM_FULL_NAME"));
            if (!new File("/").canWrite()) {
                badValues.add("/foo/$ITEM_FULL_NAME");
                badValues.add("/$ITEM_FULLNAME");
            } // else perhaps running as root

            for (String badValue : badValues) {
                assertThrows(InvalidBuildsDir.class, () -> Jenkins.checkRawBuildsDir(badValue), badValue + " should have been rejected");
            }
        });
    }

    @Issue("JENKINS-50164")
    @Test
    void goodValueForBuildsDir() throws Throwable {
        story.then(rule -> {
            final List<String> badValues = Arrays.asList(
                    "$JENKINS_HOME/foo/$ITEM_FULL_NAME",
                    "${ITEM_ROOTDIR}/builds");

            for (String goodValue : badValues) {
                Jenkins.checkRawBuildsDir(goodValue);
            }
        });
    }

    @Issue("JENKINS-50164")
    @Test
    void jenkinsDoesNotStartWithBadSysProp() throws Throwable {
        loggerRule.record(Jenkins.class, Level.WARNING)
                .record(Jenkins.class, Level.INFO)
                .capture(100);

        story.then(rule -> {
            assertTrue(rule.getInstance().isDefaultBuildDir());
            setBuildsDirProperty("/bluh");
        });

        assertThrows(ReactorException.class, () -> story.then(step -> fail("should have failed before reaching here.")));
    }

    @Issue("JENKINS-50164")
    @Test
    void jenkinsDoesNotStartWithScrewedUpConfigXml() throws Throwable {
        loggerRule.record(Jenkins.class, Level.WARNING)
                .record(Jenkins.class, Level.INFO)
                .capture(100);

        story.then(rule -> {

            assertTrue(rule.getInstance().isDefaultBuildDir());

            // Now screw up the value by writing into the file directly, like one could do using external XML manipulation tools
            final Path configFile = rule.jenkins.getRootDir().toPath().resolve("config.xml");
            final String screwedUp = Files.readString(configFile).
                    replaceFirst("<buildsDir>.*</buildsDir>", "<buildsDir>eeeeeeeeek</buildsDir>");
            Files.writeString(configFile, screwedUp, StandardCharsets.UTF_8);
        });

        assertThrows(ReactorException.class, () -> story.then(step -> fail("should have failed before reaching here.")));
    }

    @Issue("JENKINS-50164")
    @Test
    void buildsDir() throws Throwable {
        loggerRule.record(Jenkins.class, Level.WARNING)
                .record(Jenkins.class, Level.INFO)
                .capture(100);

        story.then(step -> assertFalse(logWasFound("Using non default builds directories")));

        story.then(steps -> {
            assertTrue(steps.getInstance().isDefaultBuildDir());
            setBuildsDirProperty("$JENKINS_HOME/plouf/$ITEM_FULL_NAME/bluh");
            assertFalse(JenkinsBuildsAndWorkspacesDirectoriesTest.this.logWasFound(LOG_WHEN_CHANGING_BUILDS_DIR));
        });

        story.then(step -> {
                       assertFalse(step.getInstance().isDefaultBuildDir());
                       assertEquals("$JENKINS_HOME/plouf/$ITEM_FULL_NAME/bluh", step.getInstance().getRawBuildsDir());
                       assertTrue(logWasFound("Changing builds directories from "));
                   }
        );

        story.then(step -> assertTrue(logWasFound("Using non default builds directories"))
        );
    }

    @Issue("JENKINS-50164")
    @Test
    void workspacesDir() throws Throwable {
        loggerRule.record(Jenkins.class, Level.WARNING)
                .record(Jenkins.class, Level.INFO)
                .capture(1000);

        story.then(step -> assertFalse(logWasFound("Using non default workspaces directories")));

        story.then(step -> {
            assertTrue(step.getInstance().isDefaultWorkspaceDir());
            final String workspacesDir = "bluh";
            setWorkspacesDirProperty(workspacesDir);
            assertFalse(logWasFound("Changing workspaces directories from "));
        });

        story.then(step -> {
            assertFalse(step.getInstance().isDefaultWorkspaceDir());
            assertEquals("bluh", step.getInstance().getRawWorkspaceDir());
            assertTrue(logWasFound("Changing workspaces directories from "));
        });


        story.then(step -> {
                       assertFalse(step.getInstance().isDefaultWorkspaceDir());
                       assertTrue(logWasFound("Using non default workspaces directories"));
                   }
        );
    }

    @Disabled("TODO calling restart seems to break Surefire")
    @Issue("JENKINS-50164")
    @LocalData
    @Test
    void fromPreviousCustomSetup() throws Throwable {
        assumeFalse(Functions.isWindows(), "Default Windows lifecycle does not support restart.");

        // check starting point and change config for next run
        final String newBuildsDirValueBySysprop = "/tmp/${ITEM_ROOTDIR}/bluh";
        story.then(j -> {
            assertEquals("${ITEM_ROOTDIR}/ze-previous-custom-builds", j.jenkins.getRawBuildsDir());
            setBuildsDirProperty(newBuildsDirValueBySysprop);
        });

        // Check the sysprop setting was taken in account
        story.then(j -> {
            assertEquals(newBuildsDirValueBySysprop, j.jenkins.getRawBuildsDir());

            // ** HACK AROUND JENKINS-50422: manually restarting ** //
            // Check the disk (cannot just restart normally with the rule, )
            assertThat(Files.readString(j.jenkins.getRootDir().toPath().resolve("config.xml")),
                       containsString("<buildsDir>" + newBuildsDirValueBySysprop + "</buildsDir>"));

            String rootDirBeforeRestart = j.jenkins.getRootDir().toString();
            clearSystemProperties();
            j.jenkins.restart();

            int maxLoops = 50;
            while (j.jenkins.getInitLevel() != InitMilestone.COMPLETED && maxLoops-- > 0) {
                Thread.sleep(300);
            }

            assertEquals(rootDirBeforeRestart, j.jenkins.getRootDir().toString());
            assertThat(Files.readString(j.jenkins.getRootDir().toPath().resolve("config.xml")),
                       containsString("<buildsDir>" + newBuildsDirValueBySysprop + "</buildsDir>"));
            assertEquals(newBuildsDirValueBySysprop, j.jenkins.getRawBuildsDir());
            // ** END HACK ** //
        });

    }

    private void setWorkspacesDirProperty(String workspacesDir) {
        System.setProperty(Jenkins.WORKSPACES_DIR_PROP, workspacesDir);
    }

    private void setBuildsDirProperty(String buildsDir) {
        System.setProperty(Jenkins.BUILDS_DIR_PROP, buildsDir);
    }

    private boolean logWasFound(String searched) {
        return loggerRule.getRecords().stream()
                .anyMatch(record -> record.getMessage().contains(searched));
    }

    private boolean logWasFoundAtLevel(String searched, Level level) {
        return loggerRule.getRecords().stream()
                .filter(record -> record.getMessage().contains(searched)).anyMatch(record -> record.getLevel().equals(level));
    }

    @Test
    @Issue("JENKINS-17138")
    void externalBuildDirectoryRenameDelete() throws Throwable {
        // Hack to get String builds usable in lambda below
        final List<String> builds = new ArrayList<>();

        story.then(steps -> {
            builds.add(newFolder(tmp, "junit").toString());
            assertTrue(steps.getInstance().isDefaultBuildDir());
            setBuildsDirProperty(builds.get(0) + "/${ITEM_FULL_NAME}");
        });

        story.then(steps -> {
            assertEquals(builds.get(0) + "/${ITEM_FULL_NAME}", steps.jenkins.getRawBuildsDir());
            FreeStyleProject p = steps.jenkins.createProject(MockFolder.class, "d").createProject(FreeStyleProject.class, "prj");
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            File oldBuildDir = new File(builds.get(0), "d/prj");
            assertEquals(new File(oldBuildDir, b.getId()), b.getRootDir());
            assertTrue(b.getRootDir().isDirectory());
            p.renameTo("proj");
            File newBuildDir = new File(builds.get(0), "d/proj");
            assertEquals(new File(newBuildDir, b.getId()), b.getRootDir());
            assertTrue(b.getRootDir().isDirectory());
            p.delete();
            assertFalse(b.getRootDir().isDirectory());
        });
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
