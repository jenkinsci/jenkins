package lib.form

import com.gargoylesoftware.htmlunit.html.HtmlForm
import com.gargoylesoftware.htmlunit.html.HtmlPage
import hudson.model.BallColor
import org.jvnet.hudson.test.HudsonTestCase
import hudson.util.FormValidation
import org.kohsuke.stapler.StaplerRequest
import net.sf.json.JSONObject
import org.kohsuke.stapler.DataBoundConstructor

/**
 *
 * @author Kohsuke Kawaguchi
 */
public class EnumSetTest extends HudsonTestCase {
    EnumSet<BallColor> f;

    EnumSetTest() {
        f = EnumSet.of(BallColor.BLUE, BallColor.RED);
    }

    @DataBoundConstructor
    EnumSetTest(EnumSet<BallColor> colors) {
        f = colors;
    }

    @SuppressWarnings
    public void test1() {
        HtmlPage p = createWebClient().goTo("self/test1");
        HtmlForm f = p.getFormByName("config");
        submit(f);
        interactiveBreak();
    }


    FormValidation doSubmitTest1(StaplerRequest req) {
        JSONObject f = req.getSubmittedForm();
        println f;
        EnumSetTest r = req.bindJSON(EnumSetTest,f);
        println r.f;
        return FormValidation.ok();
    }
}