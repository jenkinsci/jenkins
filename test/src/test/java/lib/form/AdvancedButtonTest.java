package lib.form;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class AdvancedButtonTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testNestedOptionalBlock() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testNestedOptionalBlock");
        HtmlForm f = p.getFormByName("config");
        HtmlFormUtil.getButtonByCaption(f, "Advanced...").click();
        f.getInputByName("c").click();
        j.submit(f);
    }

    @Issue("JENKINS-14632")
    @Test
    public void testSectionInsideOfAdvanced() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/testSectionInsideOfAdvanced");
        HtmlForm f = p.getFormByName("config");
        assertFalse(f.getInputByName("b").isDisplayed());
        HtmlFormUtil.getButtonByCaption(f, "Advanced...").click();
        assertTrue(f.getInputByName("b").isDisplayed());
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }

        public FormValidation doSubmitNestedOptionalBlock(StaplerRequest req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            System.out.println(f);
            assertEquals("avalue", f.getString("a"));
            assertEquals("bvalue", f.getString("b"));
            JSONObject c = f.getJSONObject("c");
            assertEquals("dvalue", c.getString("d"));
            return FormValidation.ok();
        }
    }
}
