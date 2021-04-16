package hudson.slaves;

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
 * and that the priority is maintained: parameters > agent node properties >
 * global (controller) node properties
 * TODO confirm that the blub node has node properties separate from global (controller) node properties
 */
public class EnvironmentVariableNodePropertyTest extends HudsonTestCase {

	private DumbSlave agent;
	private FreeStyleProject project;

	/**
	 * Agent properties are available
	 */
	public void testAgentPropertyOnAgent() throws Exception {
		setVariables(agent, new Entry("KEY", "agentValue"));
		Map<String, String> envVars = executeBuild(agent);
		assertEquals("agentValue", envVars.get("KEY"));
	}
	
	/**
	 * Blub properties are available
	 */
	public void testGlobalPropertyOnBlub() throws Exception {
        jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "globalValue"))));

		Map<String, String> envVars = executeBuild(jenkins);

		assertEquals("globalValue", envVars.get("KEY"));
	}

	/**
	 * Both agent and controller properties are available, but agent properties have priority
	 */
	public void testAgentAndGlobalPropertyOnAgent() throws Exception {
        jenkins.getGlobalNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("KEY", "globalValue"))));
		setVariables(agent, new Entry("KEY", "agentValue"));

		Map<String, String> envVars = executeBuild(agent);

		assertEquals("agentValue", envVars.get("KEY"));
	}

	/**
	 * Agent and controller properties and parameters are available.
	 * Priority: parameters > agent > controller
	 * @throws Exception
	 */
	// TODO is this correct? This sets a blub node property, not a global property
	public void testAgentAndBlubPropertyAndParameterOnAgent()
			throws Exception {
		ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
				new StringParameterDefinition("KEY", "parameterValue"));
		project.addProperty(pdp);

		setVariables(jenkins, new Entry("KEY", "blubValue"));
		setVariables(agent, new Entry("KEY", "agentValue"));

		Map<String, String> envVars = executeBuild(agent);

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
	
	public void testFormRoundTripForBlub() throws Exception {
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

	public void testFormRoundTripForAgent() throws Exception {
		setVariables(agent, new Entry("KEY", "value"));
		
		WebClient webClient = new WebClient();
		HtmlPage page = webClient.getPage(agent, "configure");
		HtmlForm form = page.getFormByName("config");
		submit(form);
		
		assertEquals(1, agent.getNodeProperties().toList().size());
		
		EnvironmentVariablesNodeProperty prop = agent.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
		assertEquals(1, prop.getEnvVars().size());
		assertEquals("value", prop.getEnvVars().get("KEY"));
	}
	
	// //////////////////////// setup //////////////////////////////////////////

	public void setUp() throws Exception {
		super.setUp();
		agent = createSlave();
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
		
		System.out.println(build.getLog()); // TODO switch to BuildWatcher when converted to JenkinsRule
		assertEquals(Result.SUCCESS, build.getResult());

		return builder.getEnvVars();
	}

}
