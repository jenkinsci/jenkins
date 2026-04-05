package lib.form;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.Describable;
import hudson.model.Descriptor;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class BooleanRadioTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void test() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/test1");
        HtmlForm f = p.getFormByName("config");
        f.getInputByName("_.f").setChecked(true);
        j.submit(f);
    }

    public static final class BooleanRadioTestDescribable implements Describable<BooleanRadioTestDescribable> {

        boolean f;

        @SuppressWarnings("checkstyle:redundantmodifier")
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
