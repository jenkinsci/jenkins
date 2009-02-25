package hudson.slaves;

import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import junit.framework.Assert;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.HudsonTestCase.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * This class tests that environment variables from node properties are applied,
 * and that the priority is maintained: parameters > slave node properties >
 * master node properties
 */
public class EnvironmentVariableNodePropertyTest extends HudsonTestCase {

	private DumbSlave slave;
	private FreeStyleProject project;

	/**
	 * Slave properties are available
	 */
	public void testSlavePropertyOnSlave() throws Exception {
		setVariables(slave, new Entry("KEY", "slaveValue"));
		Map<String, String> envVars = executeBuild(slave);
		Assert.assertEquals("slaveValue", envVars.get("KEY"));
	}
	
	/**
	 * Master properties are available
	 */
	public void testMasterPropertyOnMaster() throws Exception {
        hudson.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "masterValue"))));

		Map<String, String> envVars = executeBuild(hudson);

		Assert.assertEquals("masterValue", envVars.get("KEY"));
	}

	/**
	 * Both slave and master properties are available, but slave properties have priority
	 */
	public void testSlaveAndMasterPropertyOnSlave() throws Exception {
        hudson.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "masterValue"))));
		setVariables(slave, new Entry("KEY", "slaveValue"));

		Map<String, String> envVars = executeBuild(slave);

		Assert.assertEquals("slaveValue", envVars.get("KEY"));
	}

	/**
	 * Slave and master properties and parameters are available.
	 * Priority: parameters > slave > master
	 * @throws Exception
	 */
	public void testSlaveAndMasterPropertyAndParameterOnSlave()
			throws Exception {
		ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
				new StringParameterDefinition("KEY", "parameterValue"));
		project.addProperty(pdp);

		setVariables(hudson, new Entry("KEY", "masterValue"));
		setVariables(slave, new Entry("KEY", "slaveValue"));

		Map<String, String> envVars = executeBuild(slave);

		Assert.assertEquals("parameterValue", envVars.get("KEY"));
	}
	
	public void testVariableResolving() throws Exception {
        hudson.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY1", "value"), new Entry("KEY2", "$KEY1"))));
		Map<String,String> envVars = executeBuild(hudson);
		Assert.assertEquals("value", envVars.get("KEY1"));
		Assert.assertEquals("value", envVars.get("KEY2"));
	}
	
	public void testFormRoundTripForMaster() throws Exception {
        hudson.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "value"))));
		
		WebClient webClient = new WebClient();
		HtmlPage page = webClient.getPage(hudson, "configure");
		HtmlForm form = page.getFormByName("config");
		submit(form);
		
		Assert.assertEquals(1, hudson.getGlobalNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = hudson.getGlobalNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		Assert.assertEquals(1, prop.getEnvVars().size());
		Assert.assertEquals("value", prop.getEnvVars().get("KEY"));
	}

	public void testFormRoundTripForSlave() throws Exception {
		setVariables(slave, new Entry("KEY", "value"));
		
		WebClient webClient = new WebClient();
		HtmlPage page = webClient.getPage(slave, "configure");
		HtmlForm form = page.getFormByName("config");
		submit(form);
		
		Assert.assertEquals(1, slave.getNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = slave.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		Assert.assertEquals(1, prop.getEnvVars().size());
		Assert.assertEquals("value", prop.getEnvVars().get("KEY"));
	}
	
	// //////////////////////// setup //////////////////////////////////////////

	public void setUp() throws Exception {
		super.setUp();
		slave = createSlave();
		project = createFreeStyleProject();
	}

	// ////////////////////// helper methods /////////////////////////////////

	private void setVariables(Node node, Entry... entries) throws IOException {
		node.getNodeProperties().replaceBy(
				Collections.singleton(new EnvironmentVariablesNodeProperty(
						entries)));

	}

	/**
	 * Launches project on this node, waits for the result, and returns the environment that is used
	 */
	private Map<String, String> executeBuild(Node node) throws Exception {
		CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();

		project.getBuildersList().add(builder);
		project.setAssignedLabel(node.getSelfLabel());

		// use a timeout so we don't wait infinitely in case of failure
		FreeStyleBuild build = project.scheduleBuild2(0).get(/*10, TimeUnit.SECONDS*/);
		
		System.out.println(build.getLog());
		Assert.assertEquals(Result.SUCCESS, build.getResult());

		return builder.getEnvVars();
	}

}
