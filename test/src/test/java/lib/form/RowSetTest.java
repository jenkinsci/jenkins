package lib.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.security.csrf.CrumbIssuer;
import net.sf.json.JSONObject;
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
class RowSetTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void json() throws Exception {
        HtmlPage p = j.createWebClient().goTo("test/test1");
        j.submit(p.getFormByName("config"));
    }

    @TestExtension
    public static class Subject extends InvisibleAction implements RootAction {
        public void doSubmitTest1(StaplerRequest2 req) throws Exception {
            JSONObject json = req.getSubmittedForm();
            json.remove(CrumbIssuer.DEFAULT_CRUMB_NAME);
            json.remove("Submit");
            System.out.println(json);

            JSONObject expected = JSONObject.fromObject(
                    "{'a':'aaa','b':'bbb','c':{'c1':'ccc1','c2':'ccc2'},'d':{'d1':'d1','d2':'d2'}}");
            assertEquals(expected, json);
        }

        @Override
        public String getUrlName() {
            return "test";
        }
    }
}
