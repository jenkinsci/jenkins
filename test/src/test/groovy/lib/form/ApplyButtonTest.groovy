package lib.form

import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Bug
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
    @Test @Bug(18436)
    public void editDescription() {
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
