package lib.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.markup.RawHtmlMarkupFormatter;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ApplyButtonTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Editing code mirror should still gets reflected when you click apply.
     */
    @Test
    @Issue("JENKINS-18436")
    void editDescription() throws Exception {
        j.jenkins.setMarkupFormatter(RawHtmlMarkupFormatter.INSTANCE); // need something using CodeMirror
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.buildAndAssertSuccess(p);

        HtmlPage config = j.createWebClient().getPage(b, "configure");
        HtmlForm form = config.getFormByName("config");
        // HtmlUnit doesn't have JSON, so we need to emulate one
        config.executeJavaScript(IOUtils.toString(ApplyButtonTest.class.getResource("JSON.js"), StandardCharsets.UTF_8));
        // it's hard to emulate the keytyping, so we just set the value into codemirror and test if this gets
        // reflected back into TEXTAREA
        config.executeJavaScript("document.getElementsByTagName('TEXTAREA')[0].codemirrorObject.setLine(0,'foobar')");
        j.getButtonByCaption(form, "Apply").click();

        assertEquals("foobar", b.getDescription());
    }

}
