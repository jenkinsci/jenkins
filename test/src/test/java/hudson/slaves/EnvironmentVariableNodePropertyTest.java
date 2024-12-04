package hudson.slaves;

import static org.junit.Assert.assertEquals;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 * This class tests that environment variables from node properties are applied,
 * and that the priority is maintained: parameters > agent node properties >
 * global (controller) node properties
 * TODO(terminology) confirm that the built-in node has node properties separate from global (controller) node properties
 */
public class EnvironmentVariableNodePropertyTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private DumbSlave agent;
    private FreeStyleProject project;

    /**
     * Agent properties are available
     */
    @Test
    public void testAgentPropertyOnAgent() throws Exception {
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("KEY", "agentValue"));
        Map<String, String> envVars = executeBuild(agent);
        assertEquals("agentValue", envVars.get("KEY"));
    }

    /**
     * Built-in node properties are available
     */
    @Test
    public void testControllerPropertyOnBuiltInNode() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("KEY", "globalValue"))));

        Map<String, String> envVars = executeBuild(j.jenkins);

        assertEquals("globalValue", envVars.get("KEY"));
    }

    /**
     * Both agent and controller properties are available, but agent properties have priority
     */
    @Test
    public void testAgentAndControllerPropertyOnAgent() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("KEY", "globalValue"))));
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("KEY", "agentValue"));

        Map<String, String> envVars = executeBuild(agent);

        assertEquals("agentValue", envVars.get("KEY"));
    }

    /**
     * Agent and controller properties and parameters are available.
     * Priority: parameters > agent > controller
     */
    @Test
    // TODO(terminology) is this correct? This sets a built-in node property, not a global property
    public void testAgentAndBuiltInNodePropertyAndParameterOnAgent()
            throws Exception {
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("KEY", "parameterValue"));
        project.addProperty(pdp);

        setVariables(j.jenkins, new EnvironmentVariablesNodeProperty.Entry("KEY", "builtInNodeValue"));
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("KEY", "agentValue"));

        Map<String, String> envVars = executeBuild(agent);

        assertEquals("parameterValue", envVars.get("KEY"));
    }

    @Test
    public void testVariableResolving() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("KEY1", "value"), new EnvironmentVariablesNodeProperty.Entry("KEY2", "$KEY1"))));
        Map<String, String> envVars = executeBuild(j.jenkins);
        assertEquals("value", envVars.get("KEY1"));
        assertEquals("value", envVars.get("KEY2"));
    }

    @Test
    public void testFormRoundTripForController() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("KEY", "value"))));

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
    public void testFormRoundTripForAgent() throws Exception {
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("KEY", "value"));

        WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(agent, "configure");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        assertEquals(1, agent.getNodeProperties().toList().size());

        EnvironmentVariablesNodeProperty prop = agent.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
        assertEquals(1, prop.getEnvVars().size());
        assertEquals("value", prop.getEnvVars().get("KEY"));
    }

    @Test
    public void testExtraSpacesForController() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("      KEY       ", "         globalValue        "))));

        Map<String, String> envVars = executeBuild(j.jenkins);

        assertEquals("globalValue", envVars.get("KEY"));
    }

    @Test
    public void testExtraSpacesForAgent() throws Exception {
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("      KEY      ", "        agentValue      "));
        Map<String, String> envVars = executeBuild(agent);
        assertEquals("agentValue", envVars.get("KEY"));
    }

    @Test
    public void testEmptyForController() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("KEY", ""))));

        Map<String, String> envVars = executeBuild(j.jenkins);

        assertEquals("", envVars.get("KEY"));
    }

    @Test
    public void testEmptyForAgent() throws Exception {
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("KEY", ""));
        Map<String, String> envVars = executeBuild(agent);
        assertEquals("", envVars.get("KEY"));
    }

    @Test
    public void testOnlySpacesForController() throws Exception {
        j.jenkins.getGlobalNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("KEY", "                  "))));

        Map<String, String> envVars = executeBuild(j.jenkins);

        assertEquals("", envVars.get("KEY"));
    }

    @Test
    public void testOnlySpacesForAgent() throws Exception {
        setVariables(agent, new EnvironmentVariablesNodeProperty.Entry("KEY", "                "));
        Map<String, String> envVars = executeBuild(agent);
        assertEquals("", envVars.get("KEY"));
    }

    // //////////////////////// setup //////////////////////////////////////////

    @Before
    public void setUp() throws Exception {
        agent = j.createSlave();
        project = j.createFreeStyleProject();
    }

    // ////////////////////// helper methods /////////////////////////////////

    private void setVariables(Node node, EnvironmentVariablesNodeProperty.Entry... entries) throws IOException {
        node.getNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        entries)));

    }

    /**
     * Launches project on this node, waits for the result, and returns the environment that is used
     */
    private Map<String, String> executeBuild(Node node) throws Exception {
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();

        project.getBuildersList().add(builder);
        project.setAssignedLabel(node.getSelfLabel());

        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        return builder.getEnvVars();
    }

}
