package lib.form;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.markup.RawHtmlMarkupFormatter;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ApplyButtonTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Editing code mirror should still gets reflected when you click apply.
     */
    @Test
    @Issue("JENKINS-18436")
    public void editDescription() throws Exception {
        j.jenkins.setMarkupFormatter(RawHtmlMarkupFormatter.INSTANCE); // need something using CodeMirror
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        HtmlPage config = j.createWebClient().getPage(b, "configure");
        HtmlForm form = config.getFormByName("config");
        // HtmlUnit doesn't have JSON, so we need to emulate one
        config.executeJavaScript(IOUtils.toString(ApplyButtonTest.class.getResource("JSON.js")));
        // it's hard to emulate the keytyping, so we just set the value into codemirror and test if this gets
        // reflected back into TEXTAREA
        config.executeJavaScript("document.getElementsByTagName('TEXTAREA')[0].codemirrorObject.setLine(0,'foobar')");
        j.getButtonByCaption(form, "Apply").click();

        assertEquals("foobar", b.getDescription());
    }

}
