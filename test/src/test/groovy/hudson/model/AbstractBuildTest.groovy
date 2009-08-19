package hudson.model

import com.gargoylesoftware.htmlunit.Page
import hudson.model.BuildListener
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry
import junit.framework.Assert
import org.jvnet.hudson.test.CaptureEnvironmentBuilder
import org.jvnet.hudson.test.GroovyHudsonTestCase

public class AbstractBuildTest extends GroovyHudsonTestCase {
	void testVariablesResolved() {
		def project = createFreeStyleProject();
		hudson.getNodeProperties().replaceBy([
                new EnvironmentVariablesNodeProperty(new Entry("KEY1", "value"), new Entry("KEY2",'$KEY1'))]);
		def builder = new CaptureEnvironmentBuilder();
		project.getBuildersList().add(builder);
		
		assertBuildStatusSuccess(project.scheduleBuild2(0).get());
		
		def envVars = builder.getEnvVars();
		Assert.assertEquals("value", envVars.get("KEY1"));
		Assert.assertEquals("value", envVars.get("KEY2"));
	}

    /**
     * Makes sure that raw console output doesn't get affected by XML escapes.
     */
    void testRawConsoleOutput() {
        def out = "<test>&</test>";

        def p = createFreeStyleProject();
        p.buildersList.add(builder { builder,launcher,BuildListener listener ->
            listener.logger.println(out);
        })
        def b = assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        Page rsp = createWebClient().goTo("${b.url}/consoleText", "text/plain");
        println "Output:\n"+rsp.webResponse.contentAsString
        assertTrue(rsp.webResponse.contentAsString.contains(out));
    }
}
