package lib.form;

import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

/**
 * @author Kohsuke Kawaguchi
 */
public class DropdownListTest extends HudsonTestCase {
    public void test1() throws Exception {
        HtmlPage p = createWebClient().goTo("self/test1");
        HtmlForm f = p.getFormByName("config");
        submit(f);
    }

    public FormValidation doSubmitTest1(StaplerRequest req) throws Exception {
        JSONObject f = req.getSubmittedForm();
        System.out.println(f);
        return FormValidation.ok();
    }
}