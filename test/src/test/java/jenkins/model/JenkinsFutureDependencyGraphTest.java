package jenkins.model;

import static org.mockito.Mockito.spy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import hudson.model.DependencyGraph;

public class JenkinsFutureDependencyGraphTest {
    
    @Rule 
    public JenkinsRule j = new JenkinsRule();
    
    
    @Issue("JENKINS-67237")
    @Test
    public void testGetFutureDependencyGraphWithoutASingleRebuildBeforeHand() throws InterruptedException, ExecutionException  {
        Jenkins jenkins = j.jenkins;
           
        DependencyGraph resultingGraph = jenkins.getFutureDependencyGraph().get();
        assertThat("Completed future dependency graph should be equal to the stored dependency graph, but wasn't.", jenkins.getDependencyGraph(), is(resultingGraph));
    }
    
    

    
    @Issue("JENKINS-67237")
    @Test
    public void testStartRebuildOfDependecyGraphWhileScheduled() throws InterruptedException, ExecutionException  {
        Jenkins jenkins = j.jenkins;
        
        Future<DependencyGraph> firstFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();        
        Future<DependencyGraph> secondFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        
        assertThat("Two future dependency graphs that were scheduled in short succession should be equal, but weren't", firstFutureDependencyGraph, is(secondFutureDependencyGraph));
        assertThat("Last scheduled future dependency graph should have been returned, but wasn't.", secondFutureDependencyGraph, is(jenkins.getFutureDependencyGraph()));
    }
    
    @Issue("JENKINS-67237")
    @Test
    public void testStartRebuildOfDependencyGraphWhileAlreadyRebuilding() throws InterruptedException, ExecutionException  {
        ObservableAndControllableDependencyGraph graph = new ObservableAndControllableDependencyGraph();
        Jenkins jenkins = mockJenkinsWithControllableDependencyGraph(graph);
        
        
        Future<DependencyGraph> firstFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        //Wait until rebuild has started
        while(graph.getNumberOfStartedBuilds() < 1) {
            Thread.sleep(500);
        }
        
        Future<DependencyGraph> secondFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        
        assertThat("Starting a new rebuild of the dependency graph while already rebuilding should result in two distinct future dependency graphs, but didn't.", firstFutureDependencyGraph, is(not(secondFutureDependencyGraph)));
               
        graph.setLetBuildFinish(true);
        //Wait for both builds to complete
        firstFutureDependencyGraph.get();
        secondFutureDependencyGraph.get();
        
        assertThat("Two dependency graphs should have been built, but weren't.", graph.getNumberOfFinishedBuilds(),  is(2));
        
        
    }
    
    
    
    @Issue("JENKINS-67237")
    @Test
    public void testStartRebuildOfDependencyGraphWhileAlreadyRebuildingAndAnotherOneScheduled() throws InterruptedException, ExecutionException  {
        ObservableAndControllableDependencyGraph graph = new ObservableAndControllableDependencyGraph();
        Jenkins jenkins = mockJenkinsWithControllableDependencyGraph(graph);
        
        
        jenkins.rebuildDependencyGraphAsync();
        //Wait until rebuild has started
        while(graph.getNumberOfStartedBuilds() < 1) {
            Thread.sleep(500);
        }
        
        Future<DependencyGraph> secondFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        Future<DependencyGraph> thirdFutureDependencyGraph = jenkins.rebuildDependencyGraphAsync();
        
        assertThat("Two future dependency graphs that were scheduled in short succession should be equal, but weren't", secondFutureDependencyGraph, is(thirdFutureDependencyGraph));
        assertThat("Last scheduled future dependency graph should have been returned, but wasn't.", jenkins.getFutureDependencyGraph(), is(thirdFutureDependencyGraph));
        
        graph.setLetBuildFinish(true);
        //Wait for builds to complete
        thirdFutureDependencyGraph.get();
        
        assertThat("Two dependency graphs should have been built, but weren't.",graph.getNumberOfFinishedBuilds(), is(2));
    }
    
    
    
    
    private Jenkins mockJenkinsWithControllableDependencyGraph(ObservableAndControllableDependencyGraph observableAndControllableDependencyGraph) {
        Jenkins mockedJenkins = spy(j.jenkins);
        Mockito.when(mockedJenkins.createNewDependencyGraph()).thenReturn(observableAndControllableDependencyGraph);
        return mockedJenkins;
    }

    /**
     * The build state of this dependency graph is observable and controllable. 
     */
    class ObservableAndControllableDependencyGraph extends DependencyGraph {
        
        private volatile boolean letBuildFinish = false;
        private volatile int numberOfStartedBuilds = 0;
        private volatile int numberOfFinishedBuilds = 0;
        
        @Override
        public void build() {
            numberOfStartedBuilds++;
            while (!letBuildFinish) {
                //NOOP
            }
            numberOfFinishedBuilds++;
        }
        
        
        
        public void setLetBuildFinish(boolean v) {
            letBuildFinish = v;
        }
        
        public int getNumberOfStartedBuilds() {
            return numberOfStartedBuilds;
        }
        
        public int getNumberOfFinishedBuilds() {
            return numberOfFinishedBuilds;
        }
        
    }
}

