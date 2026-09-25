package lib.form;

import hudson.model.BallColor;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.util.FormValidation;
import java.util.EnumSet;
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

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class EnumSetTest {

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

    public static final class EnumSetTestDescribable implements Describable<EnumSetTestDescribable> {

        EnumSet<BallColor> f;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public EnumSetTestDescribable(EnumSet<BallColor> colors) {
            f = colors;
        }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<EnumSetTestDescribable> {}
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        public FormValidation doSubmitTest1(StaplerRequest2 req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            System.out.println(f);
            EnumSetTestDescribable r = req.bindJSON(EnumSetTestDescribable.class, f);
            System.out.println(r.f);
            return FormValidation.ok();
        }

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
