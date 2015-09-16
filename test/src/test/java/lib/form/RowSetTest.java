package lib.form;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.RootAction;
import junit.framework.Assert;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Kohsuke Kawaguchi
 */
public class RowSetTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void json() throws Exception {
        HtmlPage p = j.createWebClient().goTo("test/test1");
        j.submit(p.getFormByName("config"));
    }

    @TestExtension
    public static class Subject implements RootAction {
        public void doSubmitTest1(StaplerRequest req) throws Exception {
            JSONObject json = req.getSubmittedForm();
            json.remove("crumb");
            System.out.println(json);

            JSONObject expected = JSONObject.fromObject(
                    "{'a':'aaa','b':'bbb','c':{'c1':'ccc1','c2':'ccc2'},'d':{'d1':'d1','d2':'d2'}}");
            Assert.assertEquals(json,expected);
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "test";
        }
    }
}
