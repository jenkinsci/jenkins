package lib.form;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public class BooleanRadioTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void test() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/test1");
        HtmlForm f = p.getFormByName("config");
        f.getInputByName("_.f").setChecked(true);
        j.submit(f);
    }

    public static final class BooleanRadioTestDescribable extends AbstractDescribableImpl<BooleanRadioTestDescribable> {

        boolean f;

        @DataBoundConstructor
        public BooleanRadioTestDescribable(boolean f) {
            this.f = f;
        }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<BooleanRadioTestDescribable> {}
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        public FormValidation doSubmitTest1(StaplerRequest2 req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            System.out.println(f);
            BooleanRadioTestDescribable r = req.bindJSON(BooleanRadioTestDescribable.class, f);
            System.out.println(r.f);

            assertThat(true, is(r.f));

            return FormValidation.ok();
        }

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
