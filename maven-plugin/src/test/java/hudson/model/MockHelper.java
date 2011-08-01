package hudson.model;

import java.util.Collection;

import org.mockito.Mockito;

public class MockHelper {
    public static DependencyGraph mockDependencyGraph(Collection<AbstractProject<?,?>> allProjects) {
        DependencyGraph graph = new DependencyGraph();
        graph = Mockito.spy(graph);
        Mockito.doReturn(allProjects).when(graph).getAllProjects();
        return graph;
    }
}
