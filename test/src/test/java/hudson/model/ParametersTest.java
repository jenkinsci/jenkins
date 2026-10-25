package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.markup.MarkupFormatter;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTextInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author huybrechts
 */
@WithJenkins
class ParametersTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void parameterTypes() throws Exception {
        FreeStyleProject otherProject = j.createFreeStyleProject();
        j.buildAndAssertSuccess(otherProject);

        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new BooleanParameterDefinition("boolean", true, "boolean description"),
                new ChoiceParameterDefinition("choice", "Choice 1\nChoice 2", "choice description"),
                new RunParameterDefinition("run", otherProject.getName(), "run description", null));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) ((HtmlElement) DomNodeUtil.selectSingleNode(form, "//div[input/@value='string']")).getParentNode();
        assertNotNull(element);
        assertEquals("string description", element.getParentNode().querySelector(".jenkins-form-description").getTextContent());

        HtmlTextInput stringParameterInput = DomNodeUtil.selectSingleNode(element, ".//input[@name='value']");
        assertEquals("defaultValue", stringParameterInput.getAttribute("value"));
        assertEquals("string", ((HtmlElement) DomNodeUtil.selectSingleNode(element.getParentNode(), "div[contains(@class, 'jenkins-form-label')]")).getTextContent());
        stringParameterInput.setValue("newValue");

        element = DomNodeUtil.selectSingleNode(form, "//div[input/@value='boolean']");
        assertNotNull(element);
        assertEquals("boolean description", element.getParentNode().getParentNode().querySelector(".jenkins-form-description").getTextContent());
        Object o = DomNodeUtil.selectSingleNode(element, ".//input[@name='value']");
        HtmlCheckBoxInput booleanParameterInput = (HtmlCheckBoxInput) o;
        assertTrue(booleanParameterInput.isChecked());
        assertEquals("boolean", element.getTextContent());

        element = (HtmlElement) ((HtmlElement) DomNodeUtil.selectSingleNode(form, ".//div[input/@value='choice']")).getParentNode();
        assertNotNull(element);
        assertEquals("choice description", element.getParentNode().querySelector(".jenkins-form-description").getTextContent());
        assertEquals("choice", ((HtmlElement) DomNodeUtil.selectSingleNode(element.getParentNode(), "div[contains(@class, 'jenkins-form-label')]")).getTextContent());

        element = (HtmlElement) ((HtmlElement) DomNodeUtil.selectSingleNode(form, ".//div[input/@value='run']")).getParentNode();
        assertNotNull(element);
        assertEquals("run description", element.getParentNode().querySelector(".jenkins-form-description").getTextContent());
        assertEquals("run", ((HtmlElement) DomNodeUtil.selectSingleNode(element.getParentNode(), "div[contains(@class, 'jenkins-form-label')]")).getTextContent());

        j.submit(form);
        j.waitUntilNoActivity();

        assertEquals("newValue", builder.getEnvVars().get("STRING"));
        assertEquals("true", builder.getEnvVars().get("BOOLEAN"));
        assertEquals("Choice 1", builder.getEnvVars().get("CHOICE"));
        assertEquals(j.jenkins.getRootUrl() + otherProject.getLastBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

    @Test
    void choiceWithLTGT() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new ChoiceParameterDefinition("choice", "Choice 1\nChoice <2>", "choice description"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + project.getName() + "/build?delay=0sec");
        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) (form.getElementsByAttribute("input", "name", "name")).getFirst().getParentNode();
        assertNotNull(element);
        assertEquals("choice description", ((HtmlElement) DomNodeUtil.selectSingleNode(form, "//div[contains(@class, 'jenkins-form-description')]")).getTextContent());
        assertEquals("choice", ((HtmlElement) DomNodeUtil.selectSingleNode(form, "//div[contains(@class, 'jenkins-form-label')]")).getTextContent());

        HtmlSelect choiceSelect = (HtmlSelect) form.getElementsByAttribute("select", "name", "value").getFirst();

        HtmlOption opt = DomNodeUtil.selectSingleNode(choiceSelect, "option[@value='Choice <2>']");
        assertNotNull(opt);
        assertEquals("Choice <2>", opt.asNormalizedText());
        opt.setSelected(true);

        j.submit(form);
        j.waitUntilNoActivity();

        assertNotNull(builder.getEnvVars());
        assertEquals("Choice <2>", builder.getEnvVars().get("CHOICE"));
    }

    @Test
    void sensitiveParameters() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new PasswordParameterDefinition("password", "12345", "password description"));
        project.addProperty(pdb);

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();

        assertNotNull(sensitiveVars);
        assertTrue(sensitiveVars.contains("password"));
    }

    @Test
    void nonSensitiveParameters() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"));
        project.addProperty(pdb);

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();

        assertNotNull(sensitiveVars);
        assertFalse(sensitiveVars.contains("string"));
    }

    @Test
    void mixedSensitivity() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new PasswordParameterDefinition("password", "12345", "password description"),
                new StringParameterDefinition("string2", "Value2", "string description")
        );
        project.addProperty(pdb);

        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();

        assertNotNull(sensitiveVars);
        assertFalse(sensitiveVars.contains("string"));
        assertTrue(sensitiveVars.contains("password"));
        assertFalse(sensitiveVars.contains("string2"));
    }

    @Test
    @Issue("JENKINS-3539")
    void fileParameterNotSet() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new FileParameterDefinition("filename", "description"));
        project.addProperty(pdp);

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + project.getName() + "/build?delay=0sec");
        HtmlForm form = page.getFormByName("parameters");

        j.submit(form);
        j.waitUntilNoActivity();

        assertFalse(project.getSomeWorkspace().child("filename").exists(), "file must not exist");
    }

    @Test
    @Issue("JENKINS-11543")
    void unicodeParametersArePresetCorrectly() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("sname:a¶‱ﻷ", "svalue:a¶‱ﻷ", "sdesc:a¶‱ﻷ"),
                new FileParameterDefinition("fname:a¶‱ﻷ", "fdesc:a¶‱ﻷ")
        );
        p.addProperty(pdb);

        WebClient wc = j.createWebClient()
                // Ignore 405
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "build");

        // java.lang.IllegalArgumentException: No such parameter definition: <gibberish>.
        wc.setThrowExceptionOnFailingStatusCode(true);
        final HtmlForm form = page.getFormByName("parameters");
        HtmlFormUtil.submit(form, HtmlFormUtil.getButtonByCaption(form, "Build"));
        j.waitUntilNoActivity();
    }

    @Issue("SECURITY-353")
    @Test
    void xss() throws Exception {
        j.jenkins.setMarkupFormatter(new MyMarkupFormatter());
        FreeStyleProject p = j.createFreeStyleProject("p");
        StringParameterDefinition param = new StringParameterDefinition("<param name>", "<param default>", "<param description>");
        assertEquals("<b>[</b>param description<b>]</b>", param.getFormattedDescription());
        p.addProperty(new ParametersDefinitionProperty(param));
        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.getPage(p, "build?delay=0sec");
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_BAD_METHOD)); // 405 to dissuade scripts from thinking this triggered the build
        final String text = page.getWebResponse().getContentAsString();
        assertAll(
                () -> assertThat("build page should escape param name", text, containsString("&lt;param name&gt;")),
                () -> assertThat("build page should not leave param name unescaped", text, not(containsString("<param name>"))),
                () -> assertThat("build page should escape param default", text, containsString("&lt;param default&gt;")),
                () -> assertThat("build page should not leave param default unescaped", text, not(containsString("<param default>"))),
                () -> assertThat("build page should mark up param description", text, containsString("<b>[</b>param description<b>]</b>")),
                () -> assertThat("build page should not leave param description unescaped", text, not(containsString("<param description>")))
        );
        HtmlForm form = page.getFormByName("parameters");
        HtmlTextInput value = form.getInputByValue("<param default>");
        value.setText("<param value>");
        j.submit(form);
        j.waitUntilNoActivity();
        FreeStyleBuild b = p.getBuildByNumber(1);
        page = j.createWebClient().getPage(b, "parameters/");
        final String text2 = page.getWebResponse().getContentAsString();
        assertAll(
                () -> assertThat("parameters page should escape param name", text2, containsString("&lt;param name&gt;")),
                () -> assertThat("parameters page should not leave param name unescaped", text2, not(containsString("<param name>"))),
                () -> assertThat("parameters page should escape param value", text2, containsString("&lt;param value>")),
                () -> assertThat("parameters page should not leave param value unescaped", text2, not(containsString("<param value>"))),
                () -> assertThat("parameters page should mark up param description", text2, containsString("<b>[</b>param description<b>]</b>")),
                () -> assertThat("parameters page should not leave param description unescaped", text2, not(containsString("<param description>")))
        );
    }

    @Test
    @Issue("JENKINS-69637")
    void emptyParameterDefinitionProperty() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty());

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage page;

        page = wc.getPage(p, "build?delay=0sec");
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_BAD_METHOD));
        HtmlForm form = page.getFormByName("parameters");
        page = j.submit(form);
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_OK));
        j.waitUntilNoActivity();
        FreeStyleBuild b = p.getBuildByNumber(1);
        assertThat(b.getResult(), is(Result.SUCCESS));
    }

    static class MyMarkupFormatter extends MarkupFormatter {
        @Override
        public void translate(String markup, @NonNull Writer output) throws IOException {
            Matcher m = Pattern.compile("[<>]").matcher(markup);
            StringBuilder buf = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(buf, m.group().equals("<") ? "<b>[</b>" : "<b>]</b>");
            }
            m.appendTail(buf);
            output.write(buf.toString());
        }
    }

}
