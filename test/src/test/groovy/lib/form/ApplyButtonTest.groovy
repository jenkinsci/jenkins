package lib.form

import hudson.markup.RawHtmlMarkupFormatter
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.JenkinsRule

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ApplyButtonTest {
    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Editing code mirror should still gets reflected when you click apply.
     */
    @Test @Issue("JENKINS-18436")
    public void editDescription() {
        j.jenkins.markupFormatter = RawHtmlMarkupFormatter.INSTANCE // need something using CodeMirror
        def p = j.createFreeStyleProject()
        def b = j.assertBuildStatusSuccess(p.scheduleBuild2(0))

        def config = j.createWebClient().getPage(b, "configure")
        def form = config.getFormByName("config")
        // HtmlUnit doesn't have JSON, so we need to emulate one
        config.executeJavaScript(getClass().getResource("JSON.js").text)
        // it's hard to emulate the keytyping, so we just set the value into codemirror and test if this gets
        // reflected back into TEXTAREA
        config.executeJavaScript("document.getElementsByTagName('TEXTAREA')[0].codemirrorObject.setLine(0,'foobar')")
        j.getButtonByCaption(form,"Apply").click()

        assert "foobar"==b.description
    }
}
