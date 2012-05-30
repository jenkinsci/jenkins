package hudson.matrix;

import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildTrigger;
import hudson.util.RunList;

/**
 * @author Stefan Wolf
 */
public class MatrixProjectDependencyTest extends HudsonTestCase {
	
	/**
	 * Checks if the MatrixProject adds and Triggers downstream Projects via
	 * the DependencyGraph 
	 */
	public void testMatrixProjectTriggersDependencies() throws Exception {
		MatrixProject matrixProject = createMatrixProject();
		FreeStyleProject freestyleProject = createFreeStyleProject();
		matrixProject.getPublishersList().add(new BuildTrigger(freestyleProject.getName(), false));
		
		jenkins.rebuildDependencyGraph();
		
		buildAndAssertSuccess(matrixProject);
		waitUntilNoActivity();
		
		RunList<FreeStyleBuild> builds = freestyleProject.getBuilds();
		assertEquals("There should only be one FreestyleBuild", 1, builds.size());
		FreeStyleBuild build = builds.iterator().next();
		assertEquals(Result.SUCCESS, build.getResult());
		List<AbstractProject> downstream = jenkins.getDependencyGraph().getDownstream(matrixProject);
		assertTrue(downstream.contains(freestyleProject));		
	}
	
}
