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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * This class tests that environment variables from node properties are applied,
 * and that the priority is maintained: parameters > agent node properties >
 * master node properties
 */
public class EnvironmentVariableNodePropertyTest {

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule j = new JenkinsRule();

	private DumbSlave slave;
	private FreeStyleProject project;

	/**
	 * Agent properties are available
	 */
	@Test
	public void testSlavePropertyOnSlave() throws Exception {
		setVariables(slave, new Entry("KEY", "slaveValue"));
		Map<String, String> envVars = executeBuild(slave);
		assertEquals("slaveValue", envVars.get("KEY"));
	}
	
	/**
	 * Master properties are available
	 */
	@Test
	public void testMasterPropertyOnMaster() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "masterValue"))));

		Map<String, String> envVars = executeBuild(j.jenkins);

		assertEquals("masterValue", envVars.get("KEY"));
	}

	/**
	 * Both agent and master properties are available, but agent properties have priority
	 */
	@Test
	public void testSlaveAndMasterPropertyOnSlave() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "masterValue"))));
		setVariables(slave, new Entry("KEY", "slaveValue"));

		Map<String, String> envVars = executeBuild(slave);

		assertEquals("slaveValue", envVars.get("KEY"));
	}

	/**
	 * Agent and master properties and parameters are available.
	 * Priority: parameters > agent > master
	 * @throws Exception
	 */
	@Test
	public void testSlaveAndMasterPropertyAndParameterOnSlave()
			throws Exception {
		ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
				new StringParameterDefinition("KEY", "parameterValue"));
		project.addProperty(pdp);

		setVariables(j.jenkins, new Entry("KEY", "masterValue"));
		setVariables(slave, new Entry("KEY", "slaveValue"));

		Map<String, String> envVars = executeBuild(slave);

		assertEquals("parameterValue", envVars.get("KEY"));
	}
	
	@Test
	public void testVariableResolving() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY1", "value"), new Entry("KEY2", "$KEY1"))));
		Map<String,String> envVars = executeBuild(j.jenkins);
		assertEquals("value", envVars.get("KEY1"));
		assertEquals("value", envVars.get("KEY2"));
	}
	
	@Test
	public void testFormRoundTripForMaster() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "value"))));
		
		WebClient webClient = j.createWebClient();
		HtmlPage page = webClient.getPage(j.jenkins, "configure");
		HtmlForm form = page.getFormByName("config");
		j.submit(form);
		
		assertEquals(1, j.jenkins.getGlobalNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = j.jenkins.getGlobalNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		assertEquals(1, prop.getEnvVars().size());
		assertEquals("value", prop.getEnvVars().get("KEY"));
	}

	@Test
	public void testFormRoundTripForSlave() throws Exception {
		setVariables(slave, new Entry("KEY", "value"));
		
		WebClient webClient = j.createWebClient();
		HtmlPage page = webClient.getPage(slave, "configure");
		HtmlForm form = page.getFormByName("config");
		j.submit(form);
		
		assertEquals(1, slave.getNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = slave.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		assertEquals(1, prop.getEnvVars().size());
		assertEquals("value", prop.getEnvVars().get("KEY"));
	}
	
	// //////////////////////// setup //////////////////////////////////////////

	@Before
	public void setUp() throws Exception {
		slave = j.createSlave();
		project = j.createFreeStyleProject();
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
		
		assertEquals(Result.SUCCESS, build.getResult());

		return builder.getEnvVars();
	}

}
