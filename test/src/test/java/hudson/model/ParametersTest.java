package hudson.model;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.Set;

/**
 * @author huybrechts
 */
public class ParametersTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void parameterTypes() throws Exception {
        FreeStyleProject otherProject = j.createFreeStyleProject();
        otherProject.scheduleBuild2(0).get();

        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new BooleanParameterDefinition("boolean", true, "boolean description"),
                new ChoiceParameterDefinition("choice", "Choice 1\nChoice 2", "choice description"),
                new RunParameterDefinition("run", otherProject.getName(), "run description", null));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode("//tr[td/div/input/@value='string']");
        assertNotNull(element);
        assertEquals("string description", ((HtmlElement) element.getNextSibling().getNextSibling().selectSingleNode("td[@class='setting-description']")).getTextContent());

        HtmlTextInput stringParameterInput = (HtmlTextInput) element.selectSingleNode(".//input[@name='value']");
        assertEquals("defaultValue", stringParameterInput.getAttribute("value"));
        assertEquals("string", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());
        stringParameterInput.setAttribute("value", "newValue");

        element = (HtmlElement) form.selectSingleNode("//tr[td/div/input/@value='boolean']");
        assertNotNull(element);
        assertEquals("boolean description", ((HtmlElement) element.getNextSibling().getNextSibling().selectSingleNode("td[@class='setting-description']")).getTextContent());
        Object o = element.selectSingleNode(".//input[@name='value']");
        System.out.println(o);
        HtmlCheckBoxInput booleanParameterInput = (HtmlCheckBoxInput) o;
        assertEquals(true, booleanParameterInput.isChecked());
        assertEquals("boolean", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='choice']");
        assertNotNull(element);
        assertEquals("choice description", ((HtmlElement) element.getNextSibling().getNextSibling().selectSingleNode("td[@class='setting-description']")).getTextContent());
        assertEquals("choice", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.getNextSibling().getNextSibling().selectSingleNode("td[@class='setting-description']")).getTextContent());
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        j.submit(form);
        Queue.Item q = j.jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertEquals("newValue", builder.getEnvVars().get("STRING"));
        assertEquals("true", builder.getEnvVars().get("BOOLEAN"));
        assertEquals("Choice 1", builder.getEnvVars().get("CHOICE"));
        assertEquals(j.jenkins.getRootUrl() + otherProject.getLastBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

    @Test
    public void choiceWithLTGT() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new ChoiceParameterDefinition("choice", "Choice 1\nChoice <2>", "choice description"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + project.getName() + "/build?delay=0sec");
        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='choice']");
        assertNotNull(element);
        assertEquals("choice description", ((HtmlElement) element.getNextSibling().getNextSibling().selectSingleNode("td[@class='setting-description']")).getTextContent());
        assertEquals("choice", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());
        HtmlOption opt = (HtmlOption)element.selectSingleNode("td/div/select/option[@value='Choice <2>']");
        assertNotNull(opt);
        assertEquals("Choice <2>", opt.asText());
        opt.setSelected(true);

        j.submit(form);
        Queue.Item q = j.jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertNotNull(builder.getEnvVars());
        assertEquals("Choice <2>", builder.getEnvVars().get("CHOICE"));
    }

    @Test
    public void sensitiveParameters() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new PasswordParameterDefinition("password", "12345", "password description"));
        project.addProperty(pdb);

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();

        assertNotNull(sensitiveVars);
        assertTrue(sensitiveVars.contains("password"));
    }

    @Test
    public void nonSensitiveParameters() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"));
        project.addProperty(pdb);

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();

        assertNotNull(sensitiveVars);
        assertFalse(sensitiveVars.contains("string"));
    }

    @Test
    public void mixedSensitivity() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new PasswordParameterDefinition("password", "12345", "password description"),
                new StringParameterDefinition("string2", "Value2", "string description")
        );
        project.addProperty(pdb);

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();

        assertNotNull(sensitiveVars);
        assertFalse(sensitiveVars.contains("string"));
        assertTrue(sensitiveVars.contains("password"));
        assertFalse(sensitiveVars.contains("string2"));
    }

    @Test
    @Issue("JENKINS-3539")
    public void fileParameterNotSet() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new FileParameterDefinition("filename", "description"));
        project.addProperty(pdp);

        WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + project.getName() + "/build?delay=0sec");
        HtmlForm form = page.getFormByName("parameters");

        j.submit(form);
        Queue.Item q = j.jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertFalse("file must not exist", project.getSomeWorkspace().child("filename").exists());
    }

    @Test
    @Issue("JENKINS-11543")
    public void unicodeParametersArePresetCorrectly() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("sname:a¶‱ﻷ", "svalue:a¶‱ﻷ", "sdesc:a¶‱ﻷ"),
                new FileParameterDefinition("fname:a¶‱ﻷ", "fdesc:a¶‱ﻷ")
        );
        p.addProperty(pdb);

        WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false); // Ignore 405
        HtmlPage page = wc.getPage(p, "build");

        // java.lang.IllegalArgumentException: No such parameter definition: <gibberish>.
        wc.setThrowExceptionOnFailingStatusCode(true);
        final HtmlForm form = page.getFormByName("parameters");
        form.submit(form.getButtonByCaption("Build"));
    }
}
