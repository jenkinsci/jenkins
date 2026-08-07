package lib.form;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class DropdownListTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void test1() throws Exception {
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

        public FormValidation doSubmitTest1(StaplerRequest2 req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            System.out.println(f);
            return FormValidation.ok();
        }
    }
}
