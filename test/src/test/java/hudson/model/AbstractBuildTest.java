package hudson.model;

import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

public class AbstractBuildTest extends HudsonTestCase {

	public void testVariablesResolved() throws Exception {
		FreeStyleProject project = createFreeStyleProject();
		Hudson.getInstance().getNodeProperties().replaceBy(
				Collections.singleton(new EnvironmentVariablesNodeProperty(
								new Entry("KEY1", "value"), new Entry("KEY2",
										"$KEY1"))));
		CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
		project.getBuildersList().add(builder);
		
		AbstractBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
		
		Map<String, String> envVars = builder.getEnvVars();
		Assert.assertEquals("value", envVars.get("KEY1"));
		Assert.assertEquals("value", envVars.get("KEY2"));
	}

}
