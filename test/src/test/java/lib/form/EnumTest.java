package lib.form;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Extension;
import hudson.model.BallColor;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class EnumTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Test
    void testSelectionNoDefault() throws Exception {
        HtmlForm form = getForm("noDefault");
        HtmlSelect select;

        select = form.getSelectByName("enum1");
        assertEquals(BallColor.values().length, select.getOptionSize());
        assertEquals(BallColor.YELLOW.name(), select.getDefaultValue());

        select = form.getSelectByName("enum2");
        assertEquals(BallColor.values().length, select.getOptionSize());
        assertEquals(BallColor.values()[0].name(), select.getDefaultValue());
    }

    @Test
    void testSelectionWithDefault() throws Exception {
        HtmlForm form = getForm("withDefault");
        HtmlSelect select;

        select = form.getSelectByName("enum1");
        assertEquals(BallColor.YELLOW.name(), select.getDefaultValue());

        select = form.getSelectByName("enum2");
        assertEquals(BallColor.BLUE.name(), select.getDefaultValue());
    }

    private HtmlForm getForm(String viewName) throws Exception {
        rule.jenkins.setCrumbIssuer(null);
        HtmlPage page = rule.createWebClient().goTo("test/" + viewName);
        return page.getFormByName("config");
    }

    @TestExtension
    public static class Form extends InvisibleAction implements RootAction, Describable<EnumTest.Form> {

        public BallColor enum1 = BallColor.YELLOW;
        public BallColor enum2 = null;

        public void doSubmitForm(StaplerRequest2 req) throws Exception {
            JSONObject json = req.getSubmittedForm();
            System.out.println(json);
        }

        @Override
        public Form.DescriptorImpl getDescriptor() {
            return Jenkins.get().getDescriptorByType(Form.DescriptorImpl.class);
        }

        @Override
        public String getUrlName() {
            return "test";
        }

        @Extension
        public static final class DescriptorImpl extends Descriptor<EnumTest.Form> {}
    }
}
