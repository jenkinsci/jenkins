package lib.form;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;


/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class AdvancedButtonTest extends HudsonTestCase {
    public void testNestedOptionalBlock() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNestedOptionalBlock");
        HtmlForm f = p.getFormByName("config");
        HtmlFormUtil.getButtonByCaption(f, "Advanced...").click();
        f.getInputByName("c").click();
        submit(f);
    }

    public FormValidation doSubmitNestedOptionalBlock(StaplerRequest req) throws Exception {
        JSONObject f = req.getSubmittedForm();
        System.out.println(f);
        assertEquals("avalue",f.getString("a"));
        assertEquals("bvalue",f.getString("b"));
        JSONObject c = f.getJSONObject("c");
        assertEquals("dvalue",c.getString("d"));
        return FormValidation.ok();
    }
}