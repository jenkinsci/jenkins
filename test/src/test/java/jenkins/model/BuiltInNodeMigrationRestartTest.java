package jenkins.model;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.jvnet.hudson.test.HudsonHomeLoader;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class BuiltInNodeMigrationRestartTest {
    @Rule
    public JenkinsSessionRule r = new JenkinsSessionRule();

    @Test
    public void testNewInstanceWithoutConfiguration() throws Throwable {
        r.then(j -> {
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
        r.then(j -> {
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
    }

    @Test
    @LocalDataOnce
    public void migratedInstanceStartsWithNewTerminology() throws Throwable {
        r.then(j -> {
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
        r.then(j -> {
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
    }

    @Test
    @LocalDataOnce
    public void oldDataStartsWithOldTerminology() throws Throwable {
        r.then(j -> {
            Assert.assertFalse(j.jenkins.getRenameMigrationDone());
            Assert.assertTrue(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertTrue(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
        r.then(j -> {
            Assert.assertFalse(j.jenkins.getRenameMigrationDone());
            Assert.assertTrue(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertTrue(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());

            j.jenkins.performRenameMigration();

            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
        r.then(j -> {
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
    }

    /**
     * Like {@link LocalData} except it only provides the JENKINS_HOME content if the directory is currently empty.
     */
    @JenkinsRecipe(LocalDataOnce.RuleRunnerImpl.class)
    @Target(METHOD)
    @Retention(RUNTIME)
    private @interface LocalDataOnce {
        class RuleRunnerImpl extends JenkinsRecipe.Runner<LocalDataOnce> {
            public static final Logger LOGGER = Logger.getLogger(RuleRunnerImpl.class.getName());
            private Method method;

            @Override
            public void setup(JenkinsRule jenkinsRule, LocalDataOnce recipe) throws Exception {
                Description desc = jenkinsRule.getTestDescription();
                method = desc.getTestClass().getMethod((desc.getMethodName()));
            }

            @Override
            public void decorateHome(JenkinsRule jenkinsRule, File home) throws Exception {
                // Only copy the home directory if it doesn't have content yet
                final File[] files = home.listFiles();
                if (!home.exists() || !home.isDirectory() || files == null || files.length == 0) {
                    LOGGER.log(Level.INFO, () -> "Copying JENKINS_HOME from test resources to " + home);
                    File source = new HudsonHomeLoader.Local(method, "").allocate();
                    FileUtils.copyDirectory(source, home);
                } else {
                    LOGGER.log(Level.INFO, () -> "JENKINS_HOME " + home + " is not empty, skipping copy from test resources");
                }
            }
        }
    }
}
