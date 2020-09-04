package jenkins.bugs;

import com.gargoylesoftware.htmlunit.WebClientUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import javax.inject.Inject;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.QueryParameter;

public class Jenkins19124Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    public Foo.DescriptorImpl d;

    @Issue("JENKINS-19124")
    @Test
    public void interrelatedFormValidation() throws Exception {
        j.jenkins.getInjector().injectMembers(this);

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Foo());

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage c = wc.getPage(p, "configure");
        HtmlTextInput alpha = c.getElementByName("_.alpha");
        // the fireEvent is required as setValueAttribute's new behavior is not triggering the onChange event anymore
        alpha.setValueAttribute("hello");
        alpha.fireEvent("change");

        WebClientUtil.waitForJSExec(wc);
        assertEquals("hello", d.alpha);
        assertEquals("2", d.bravo);

        HtmlSelect bravo = c.getElementByName("_.bravo");
        bravo.setSelectedAttribute("1", true);
        WebClientUtil.waitForJSExec(wc);
        assertEquals("hello", d.alpha);
        assertEquals("1", d.bravo);
    }

    public static class Foo extends Builder {

        public String getAlpha() {
            return "alpha";
        }
        public String getBravo() {
            return "2";
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            return true;
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            String alpha, bravo;

            public FormValidation doCheckAlpha(@QueryParameter String value, @QueryParameter String bravo) {
                this.alpha = value;
                this.bravo = bravo;
                return FormValidation.ok();
            }

            public ListBoxModel doFillBravoItems() {
                return new ListBoxModel().add("1").add("2").add("3");
            }

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

        }

    }

}
