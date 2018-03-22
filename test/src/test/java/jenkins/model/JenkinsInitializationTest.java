package jenkins.model;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Issue("JENKINS-50164")
public class JenkinsInitializationTest {

    private static final Map<String, String> PROPS_TO_RESTORE = new LinkedHashMap<>();
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    @BeforeClass
    public static void beforeClass() {
        Stream.of(Jenkins.BUILDS_DIR_PROP, Jenkins.WORKSPACES_DIR_PROP)
                .forEach(property -> PROPS_TO_RESTORE.put(property, System.getProperty(property)));
    }

    @AfterClass
    public static void afterClass() {
        PROPS_TO_RESTORE.forEach((k, v) -> {
            if (v == null) {
                System.clearProperty(k);
            } else {
                System.setProperty(k, v);
            }
        });
    }

    @Test
    public void buildsDir() throws Exception {
        loggerRule.record(Jenkins.class, Level.WARNING).capture(10);
        story.then(steps -> {
            assertTrue(Jenkins.get().isDefaultBuildDir());
            System.setProperty("jenkins.model.Jenkins.BUILDS_DIR", "blah");
            assertFalse(JenkinsInitializationTest.this.logWasFound("Changing builds directories from "));
        });

        story.then(step -> {
                       assertFalse(Jenkins.get().isDefaultBuildDir());
                       assertEquals("blah", story.j.getInstance().getRawBuildsDir());
                       assertTrue(logWasFound("Changing builds directories from "));
                   }
        );
    }

    @Test
    public void workspacesDir() throws Exception {
        loggerRule.record(Jenkins.class, Level.WARNING).capture(10);
        story.then(step -> {
            assertTrue(Jenkins.get().isDefaultWorkspaceDir());
            System.setProperty("jenkins.model.Jenkins.WORKSPACES_DIR", "bluh");
            assertFalse(logWasFound("Changing workspaces directories from "));
        });

        story.then(step -> {
            assertFalse(Jenkins.get().isDefaultBuildDir());
            assertEquals("bluh", story.j.getInstance().getRawWorkspaceDir());
            assertTrue(logWasFound("Changing workspaces directories from "));
        });
    }

    private boolean logWasFound(String searched) {
        return loggerRule.getRecords().stream()
                .anyMatch(record -> record.getMessage().contains(searched));
    }


}
