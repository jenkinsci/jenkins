package lib.form;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.util.ComboBoxModel;
import jenkins.model.OptionalJobProperty;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;

//TODO meant to be merged back into ComboBoxTest after security release to avoid conflict during the upmerge process
public class ComboBoxSEC1525Test extends HudsonTestCase {
    public static class XssProperty extends OptionalJobProperty<Job<?,?>> {
        @TestExtension("testEnsureXSSnotPossible")
        public static class DescriptorImpl extends OptionalJobProperty.OptionalJobPropertyDescriptor {

            @Override
            public String getDisplayName() {
                return "XSS Property";
            }
            
            public ComboBoxModel doFillXssItems() {
                return new ComboBoxModel("<h1>HACK</h1>");
            }
        }
    }

    @Issue("SECURITY-1525")
    public void testEnsureXSSnotPossible() throws Exception {
        XssProperty xssProperty = new XssProperty();
        FreeStyleProject p = createFreeStyleProject();
        p.addProperty(xssProperty);

        WebClient wc = new WebClient();

        HtmlPage configurePage = wc.getPage(p, "configure");
        int numberOfH1Before = configurePage.getElementsByTagName("h1").size();

        HtmlElement comboBox = configurePage.getElementByName("_.xss");
        HtmlElementUtil.click(comboBox);

        // no additional h1, meaning the "payload" is not interpreted
        int numberOfH1After = configurePage.getElementsByTagName("h1").size();

        assertEquals(numberOfH1Before, numberOfH1After);
    }
}
