package lib.form;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class AdvancedButtonTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testNestedOptionalBlock() throws Exception {
        HtmlPage page = j.createWebClient().goTo("self/testNestedOptionalBlock");
        HtmlForm form = page.getFormByName("config");
        HtmlFormUtil.getButtonByCaption(form, "Advanced").click();
        form.getInputByName("c").click();

        j.submit(form);
    }

    @Issue("JENKINS-14632")
    @Test
    public void testSectionInsideOfAdvanced() throws Exception {
        HtmlPage page = j.createWebClient().goTo("self/testSectionInsideOfAdvanced");
        HtmlForm form = page.getFormByName("config");
        assertFalse(form.getInputByName("b").isDisplayed());
        HtmlFormUtil.getButtonByCaption(form, "Advanced").click();
        assertTrue(form.getInputByName("b").isDisplayed());
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }

        public FormValidation doSubmitNestedOptionalBlock(StaplerRequest2 req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            assertEquals("avalue", f.getString("a"));
            assertEquals("bvalue", f.getString("b"));
            JSONObject c = f.getJSONObject("c");
            assertEquals("dvalue", c.getString("d"));
            return FormValidation.ok();
        }
    }
}
