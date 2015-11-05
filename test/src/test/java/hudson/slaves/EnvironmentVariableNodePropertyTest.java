package hudson.slaves;

import static org.junit.Assert.assertEquals;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

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
		assertEquals("slaveValue", envVars.get("KEY"));
	}
	
	/**
	 * Master properties are available
	 */
	public void testMasterPropertyOnMaster() throws Exception {
        jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "masterValue"))));

		Map<String, String> envVars = executeBuild(jenkins);

		assertEquals("masterValue", envVars.get("KEY"));
	}

	/**
	 * Both slave and master properties are available, but slave properties have priority
	 */
	public void testSlaveAndMasterPropertyOnSlave() throws Exception {
        jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "masterValue"))));
		setVariables(slave, new Entry("KEY", "slaveValue"));

		Map<String, String> envVars = executeBuild(slave);

		assertEquals("slaveValue", envVars.get("KEY"));
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

		setVariables(jenkins, new Entry("KEY", "masterValue"));
		setVariables(slave, new Entry("KEY", "slaveValue"));

		Map<String, String> envVars = executeBuild(slave);

		assertEquals("parameterValue", envVars.get("KEY"));
	}
	
	public void testVariableResolving() throws Exception {
        jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY1", "value"), new Entry("KEY2", "$KEY1"))));
		Map<String,String> envVars = executeBuild(jenkins);
		assertEquals("value", envVars.get("KEY1"));
		assertEquals("value", envVars.get("KEY2"));
	}
	
	public void testFormRoundTripForMaster() throws Exception {
        jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "value"))));
		
		WebClient webClient = new WebClient();
		HtmlPage page = webClient.getPage(jenkins, "configure");
		HtmlForm form = page.getFormByName("config");
		submit(form);
		
		assertEquals(1, jenkins.getGlobalNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = jenkins.getGlobalNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		assertEquals(1, prop.getEnvVars().size());
		assertEquals("value", prop.getEnvVars().get("KEY"));
	}

	public void testFormRoundTripForSlave() throws Exception {
		setVariables(slave, new Entry("KEY", "value"));
		
		WebClient webClient = new WebClient();
		HtmlPage page = webClient.getPage(slave, "configure");
		HtmlForm form = page.getFormByName("config");
		submit(form);
		
		assertEquals(1, slave.getNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = slave.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		assertEquals(1, prop.getEnvVars().size());
		assertEquals("value", prop.getEnvVars().get("KEY"));
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
		assertEquals(Result.SUCCESS, build.getResult());

		return builder.getEnvVars();
	}

}
