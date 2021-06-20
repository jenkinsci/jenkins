package jenkins.model;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.Objects;

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
    @LocalData
    public void migratedInstanceStartsWithNewTerminology() throws Throwable {
        r.then(j -> {
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
        r.then(j -> { // TODO this is not really useful given @LocalData reloads the home directory between restarts
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
    }

    @Test
    @LocalData
    @Ignore("TODO Figure out how to combine @LocalData and JenkinsSessionRule")
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
        r.then(j -> { // TODO At this point, the migration has been undone by @LocalData
            Assert.assertTrue(j.jenkins.getRenameMigrationDone());
            Assert.assertFalse(j.jenkins.nodeRenameMigrationNeeded);
            Assert.assertFalse(Objects.requireNonNull(j.jenkins.getAdministrativeMonitor(BuiltInNodeMigration.class.getName())).isActivated());
        });
    }
}
