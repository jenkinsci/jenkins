package lib.form;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class DropdownListTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void test1() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/test1");
        HtmlForm f = p.getFormByName("config");
        j.submit(f);
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }

        public FormValidation doSubmitTest1(StaplerRequest req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            System.out.println(f);
            return FormValidation.ok();
        }
    }
}