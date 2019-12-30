package lib.form;

import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import jenkins.model.OptionalJobProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.junit.Assert.assertEquals;

//TODO meant to be merged back into ExpandableTextboxTest after security release to avoid conflict during the upmerge process
public class ExpandableTextboxSEC1498Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void noXssUsingInputValue() throws Exception {
        XssProperty xssProperty = new XssProperty("</textarea><h1>HACK</h1>");
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(xssProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage configurePage = wc.getPage(p, "configure");

        int numberOfH1Before = configurePage.getElementsByTagName("h1").size();

        HtmlInput xssInput = configurePage.getElementByName("_.xss");
        HtmlInput expandButton = (HtmlInput) xssInput.getParentNode().getNextSibling().getFirstChild();
        HtmlElementUtil.click(expandButton);

        // no additional h1, meaning the "payload" is not interpreted
        int numberOfH1After = configurePage.getElementsByTagName("h1").size();

        assertEquals(numberOfH1Before, numberOfH1After);
    }
    
    public static final class XssProperty extends OptionalJobProperty<Job<?,?>> {
        
        private String xss;
        
        public XssProperty(String xss){
            this.xss = xss;
        }

        public String getXss() {
            return xss;
        }

        @TestExtension("noXssUsingInputValue")
        public static class DescriptorImpl extends OptionalJobProperty.OptionalJobPropertyDescriptor {
        }
    }
}
