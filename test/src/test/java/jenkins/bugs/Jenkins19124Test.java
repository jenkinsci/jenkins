package jenkins.bugs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jakarta.inject.Inject;
import java.io.IOException;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTextInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.QueryParameter;

@WithJenkins
class Jenkins19124Test {

    @Inject
    public Foo.DescriptorImpl d;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-19124")
    @Test
    void interrelatedFormValidation() throws Exception {
        j.jenkins.getInjector().injectMembers(this);

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Foo());

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage c = wc.getPage(p, "configure");
        HtmlTextInput alpha = c.getElementByName("_.alpha");
        // the fireEvent is required as setValue's new behavior is not triggering the onChange event anymore
        alpha.setValue("hello");
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
