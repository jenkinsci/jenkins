package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.LogRotator;
import hudson.util.DescribableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class GlobalBuildDiscarderTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @LocalData
    @Issue("JENKINS-61688")
    void testLoading() {
        assertEquals(0, GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().size());
    }

    @Test
    @LocalData
    @Issue("JENKINS-61688")
    void testLoadingWithDiscarders() {
        final DescribableList<GlobalBuildDiscarderStrategy, GlobalBuildDiscarderStrategyDescriptor> configuredBuildDiscarders = GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders();
        assertEquals(2, configuredBuildDiscarders.size());
        assertNotNull(configuredBuildDiscarders.get(JobGlobalBuildDiscarderStrategy.class));
        assertEquals(5, ((LogRotator) configuredBuildDiscarders.get(SimpleGlobalBuildDiscarderStrategy.class).getDiscarder()).getNumToKeep());
    }

    @Test
    void testJobBuildDiscarder() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        { // no discarder
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            GlobalBuildDiscarderListener.await();
            assertArrayEquals(new int[]{5, 4, 3, 2, 1}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "all 5 builds exist");
        }

        { // job build discarder
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new JobGlobalBuildDiscarderStrategy());
            p.setBuildDiscarder(new LogRotator(null, "3", null, null));
            assertArrayEquals(new int[]{5, 4, 3, 2, 1}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "all 5 builds exist");

            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            assertArrayEquals(new int[]{5, 4, 3}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "only 3 builds left");

            j.buildAndAssertSuccess(p);
            GlobalBuildDiscarderListener.await();
            assertArrayEquals(new int[]{6, 5, 4}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "still only 3 builds");

            p.setBuildDiscarder(null);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            GlobalBuildDiscarderListener.await();
            assertArrayEquals(new int[]{8, 7, 6, 5, 4}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "5 builds again");

            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            assertArrayEquals(new int[]{8, 7, 6, 5, 4}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "still 5 builds");
        }

        { // global build discarder
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new SimpleGlobalBuildDiscarderStrategy(new LogRotator(null, "2", null, null)));
            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            assertArrayEquals(new int[]{8, 7}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "newest 2 builds");
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            GlobalBuildDiscarderListener.await();

            // run global discarders once a build finishes
            assertArrayEquals(new int[]{10, 9}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "2 builds because of GlobalBuildDiscarderListener");

            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().clear();
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new SimpleGlobalBuildDiscarderStrategy(new LogRotator(null, "1", null, null)));

            // apply global config changes periodically
            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            assertArrayEquals(new int[]{10}, p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "2 builds again");
        }

        // reset global config
        GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().clear();

        { // job and global build discarder
            FreeStyleProject p1 = j.createFreeStyleProject();
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            GlobalBuildDiscarderListener.await();
            assertArrayEquals(new int[]{7, 6, 5, 4, 3, 2, 1}, p1.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "job with 5 builds");
            p1.setBuildDiscarder(new LogRotator(null, "5", null, null));

            FreeStyleProject p2 = j.createFreeStyleProject();
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            GlobalBuildDiscarderListener.await();
            assertArrayEquals(new int[]{6, 5, 4, 3, 2, 1}, p2.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "job with 3 builds");
            p2.setBuildDiscarder(new LogRotator(null, "3", null, null));

            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new SimpleGlobalBuildDiscarderStrategy(new LogRotator(null, "4", null, null)));
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new JobGlobalBuildDiscarderStrategy());

            { // job 1 with builds more aggressively deleted by global strategy
                j.buildAndAssertSuccess(p1);
                j.buildAndAssertSuccess(p1);
                GlobalBuildDiscarderListener.await();
                assertArrayEquals(new int[]{9, 8, 7, 6}, p1.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "job 1 discards down to 5, but global override is for 4");
                ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
                assertArrayEquals(new int[]{9, 8, 7, 6}, p1.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "job 1 discards down to 5, but global override is for 4");
            }

            { // job 2 with more aggressive local build discarder
                j.buildAndAssertSuccess(p2);
                GlobalBuildDiscarderListener.await();
                assertArrayEquals(new int[]{7, 6, 5}, p2.getBuilds().stream().mapToInt(Run::getNumber).toArray(), "job 1 discards down to 3");
            }
        }
    }
}
