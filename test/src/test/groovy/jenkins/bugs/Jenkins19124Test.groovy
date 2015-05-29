package jenkins.bugs

import hudson.Launcher
import hudson.model.AbstractBuild
import hudson.model.AbstractProject
import hudson.model.BuildListener
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.Builder
import hudson.util.FormValidation
import hudson.util.ListBoxModel
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestExtension
import org.kohsuke.stapler.QueryParameter

import javax.inject.Inject

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class Jenkins19124Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    DescriptorImpl d;

    @Issue("JENKINS-19124")
    @Test
    public void interrelatedFormValidation() {
        j.jenkins.injector.injectMembers(this);

        def p = j.createFreeStyleProject();
        p.buildersList.add(new Foo());

        def wc = j.createWebClient();
        def c = wc.getPage(p, "configure");
        c.getElementByName("_.alpha").valueAttribute = "hello";
        assert d.alpha=="hello";
        assert d.bravo=="2";

        c.getElementByName("_.bravo").setSelectedAttribute("1",true);
        assert d.alpha=="hello";
        assert d.bravo=="1";
    }

    public static class Foo extends Builder {
        String getAlpha() { return "alpha"; }
        String getBravo() { return "2"; }

        @Override
        boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            return true;
        }

        private Object writeReplace() { return new Object(); }
    }

    @TestExtension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        String alpha,bravo;

        public DescriptorImpl() {
            super(Foo.class)
        }

        @Override
        String getDisplayName() {
            return "---";
        }

        FormValidation doCheckAlpha(@QueryParameter String value, @QueryParameter String bravo) {
            this.alpha = value;
            this.bravo = bravo;
            return FormValidation.ok();
        }

        ListBoxModel doFillBravoItems() {
            return new ListBoxModel().add("1").add("2").add("3");
        }

        @Override
        boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
