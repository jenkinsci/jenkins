package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import hudson.model.DependencyGraph;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.stubbing.Answer;

public class JenkinsFutureDependencyGraphTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-67237")
    @Test
    public void testGetFutureDependencyGraphWithoutASingleRebuildBeforeHand() throws InterruptedException, ExecutionException {
        Jenkins jenkins = j.jenkins;

        DependencyGraph resultingGraph = jenkins.getFutureDependencyGraph().get();
        // If no dependency graph was calculated asynchronously, jenkins should return the synchronously calculated dependency graph.
        assertThat("The asynchronously calculated dependency graph should be equal to the synchronously calculated dependency graph but wasn't.", resultingGraph, is(jenkins.getDependencyGraph()));
    }

    @Issue("JENKINS-67237")
    @Test
    public void testStartRebuildOfDependecyGraphWhileScheduled() throws InterruptedException, ExecutionException {
        Jenkins jenkins = j.jenkins;

        Future<DependencyGraph> firstFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        Future<DependencyGraph> secondFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();

        assertThat("Two future dependency graphs that were scheduled in short succession should be equal, but weren't", firstFutureDependencyGraph, is(secondFutureDependencyGraph));
        assertThat("Last scheduled future dependency graph should have been returned, but wasn't.", secondFutureDependencyGraph, is(jenkins.getFutureDependencyGraph()));
    }

    @Issue("JENKINS-67237")
    @Test
    public void testStartRebuildOfDependencyGraphWhileAlreadyRebuilding() throws InterruptedException, ExecutionException {
        RebuildDependencyGraphController rebuildDependencyGraphController = new RebuildDependencyGraphController();
        Jenkins jenkins = mockJenkinsWithControllableDependencyGraph(rebuildDependencyGraphController);

        Future<DependencyGraph> firstFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        // Wait until rebuild has started
        while (rebuildDependencyGraphController.getNumberOfStartedBuilds() < 1) {
            Thread.sleep(500);
        }

        Future<DependencyGraph> secondFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();

        assertThat("Starting a new rebuild of the dependency graph while already rebuilding should result in two distinct future dependency graphs, but didn't.", firstFutureDependencyGraph, is(not(secondFutureDependencyGraph)));

        rebuildDependencyGraphController.setLetBuildFinish(true);
        // Wait for both builds to complete
        firstFutureDependencyGraph.get();
        secondFutureDependencyGraph.get();

        assertThat("Two dependency graphs should have been built, but weren't.", rebuildDependencyGraphController.getNumberOfFinishedBuilds(), is(2));
    }

    @Issue("JENKINS-67237")
    @Test
    public void testStartRebuildOfDependencyGraphWhileAlreadyRebuildingAndAnotherOneScheduled() throws InterruptedException, ExecutionException {
        RebuildDependencyGraphController rebuildDependencyGraphController = new RebuildDependencyGraphController();
        Jenkins jenkins = mockJenkinsWithControllableDependencyGraph(rebuildDependencyGraphController);

        jenkins.rebuildDependencyGraphAsync();
        // Wait until rebuild has started
        while (rebuildDependencyGraphController.getNumberOfStartedBuilds() < 1) {
            Thread.sleep(500);
        }

        Future<DependencyGraph> secondFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        Future<DependencyGraph> thirdFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();

        assertThat("Two future dependency graphs that were scheduled in short succession should be equal, but weren't", secondFutureDependencyGraph, is(thirdFutureDependencyGraph));
        assertThat("Last scheduled future dependency graph should have been returned, but wasn't.", jenkins.getFutureDependencyGraph(), is(thirdFutureDependencyGraph));

        rebuildDependencyGraphController.setLetBuildFinish(true);
        // Wait for builds to complete
        thirdFutureDependencyGraph.get();

        assertThat("Two dependency graphs should have been built, but weren't.", rebuildDependencyGraphController.getNumberOfFinishedBuilds(), is(2));
    }

    private Jenkins mockJenkinsWithControllableDependencyGraph(RebuildDependencyGraphController rebuildDependencyGraphController) {
        Jenkins mockedJenkins = spy(j.jenkins);
        doAnswer((Answer<Void>) invocation -> {

            rebuildDependencyGraphController.increaseNumberOfStartedBuilds();
            if (!rebuildDependencyGraphController.isLetBuildFinish()) {
                // NOOP
            }
            invocation.callRealMethod();

            rebuildDependencyGraphController.increaseNumberOfFinishedBuilds();
            return null;
        }).when(mockedJenkins).rebuildDependencyGraph();

        return mockedJenkins;
    }

    class RebuildDependencyGraphController {

        private volatile boolean letBuildFinish = false;
        private volatile int numberOfStartedBuilds = 0;
        private volatile int numberOfFinishedBuilds = 0;

        public boolean isLetBuildFinish() {
            return letBuildFinish;
        }

        public void setLetBuildFinish(boolean letBuildFinish) {
            this.letBuildFinish = letBuildFinish;
        }

        public int getNumberOfStartedBuilds() {
            return numberOfStartedBuilds;
        }

        public void increaseNumberOfStartedBuilds() {
            this.numberOfStartedBuilds++;
        }

        public int getNumberOfFinishedBuilds() {
            return numberOfFinishedBuilds;
        }

        public void increaseNumberOfFinishedBuilds() {
            this.numberOfFinishedBuilds++;
        }
    }
}
