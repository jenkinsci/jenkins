package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;

/**
 * @author huybrechts
 */
public class ParametersTest extends HudsonTestCase {

    public void testStringParameter() throws Exception {
        FreeStyleProject otherProject = createFreeStyleProject();
        otherProject.scheduleBuild2(0).get();

        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new BooleanParameterDefinition("boolean", true, "boolean description"),
                new ChoiceParameterDefinition("choice", "Choice 1\nChoice 2", "choice description"),
                new RunParameterDefinition("run", otherProject.getName(), "run description"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page= wc.goTo("/job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode("//tr[td/div/input/@value='string']");
        assertNotNull(element);
        assertEquals("string description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        HtmlTextInput stringParameterInput = (HtmlTextInput) element.selectSingleNode(".//input[@name='value']");
        assertEquals("defaultValue", stringParameterInput.getAttribute("value"));
        assertEquals("string", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());
        stringParameterInput.setAttribute("value", "newValue");

        element = (HtmlElement) form.selectSingleNode("//tr[td/div/input/@value='boolean']");
        assertNotNull(element);
        assertEquals("boolean description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        Object o = element.selectSingleNode(".//input[@name='value']");
        System.out.println(o);
        HtmlCheckBoxInput booleanParameterInput = (HtmlCheckBoxInput) o;
        assertEquals(true, booleanParameterInput.isChecked());
        assertEquals("boolean", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='choice']");
        assertNotNull(element);
        assertEquals("choice description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
//        HtmlCheckBoxInput booleanParameterInput = (HtmlCheckBoxInput) element.selectSingleNode("//input[@name='value']");
//        assertEquals(true, booleanParameterInput.isChecked());
        assertEquals("choice", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        submit(form);

        Thread.sleep(1000);

        assertEquals("newValue", builder.getEnvVars().get("STRING"));
        assertEquals("true", builder.getEnvVars().get("BOOLEAN"));
        assertEquals("Choice 1", builder.getEnvVars().get("CHOICE"));
        assertEquals(hudson.getRootUrl() + otherProject.getLastBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

}
