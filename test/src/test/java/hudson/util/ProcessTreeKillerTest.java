package hudson.util;

import hudson.model.FreeStyleProject;
import hudson.tasks.Maven;

import org.easymock.EasyMock;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class ProcessTreeKillerTest extends HudsonTestCase {

	public void testManualAbortProcess() throws Exception {
		ProcessTreeKiller.enabled = true;
		FreeStyleProject project = createFreeStyleProject();
		
		// this contains a maven project with a single test that sleeps 5s.
		project.setScm(new ExtractResourceSCM(getClass().getResource(
				"ProcessTreeKiller-test-project.jar")));
		project.getBuildersList().add(new Maven("install", "maven"));

		// build the project, wait until tests are running, then cancel.
		project.scheduleBuild(0);
		Thread.sleep(2000);

		project.getLastBuild().doStop(
				EasyMock.createNiceMock(StaplerRequest.class),
				EasyMock.createNiceMock(StaplerResponse.class));

		Thread.sleep(1000);
		
		// will fail (at least on windows) if test process is still running
		project.getWorkspace().deleteRecursive();

	}

}
