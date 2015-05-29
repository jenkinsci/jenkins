package hudson

import hudson.model.AbstractDescribableImpl
import hudson.model.Describable
import hudson.model.Descriptor
import hudson.util.ListBoxModel
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.HudsonTestCase
import org.jvnet.hudson.test.TestExtension
import org.kohsuke.stapler.QueryParameter

/**
 * Regression test for JENKINS-18776
 *
 * @author Kohsuke Kawaguchi
 */
class RelativePathTest extends HudsonTestCase implements Describable<RelativePathTest> {
    @Issue("JENKINS-18776")
    void testRelativePath() {
        // I was having trouble causing annotation processing on test stubs
        jenkins.getDescriptorOrDie(RelativePathTest.class)
        jenkins.getDescriptorOrDie(Model.class)

        createWebClient().goTo("/self/");
        assert jenkins.getDescriptorOrDie(Model.class).touched
    }

    String getName() {
        return "Alice";
    }

    Model getModel() {
        return new Model();
    }

    DescriptorImpl getDescriptor() {
        return jenkins.getDescriptorOrDie(getClass());
    }

    @TestExtension
    static class DescriptorImpl extends Descriptor<RelativePathTest> {
        @Override
        String getDisplayName() {
            return "";
        }
    }

    static class Model extends AbstractDescribableImpl<Model> {
        @TestExtension
        static class DescriptorImpl extends Descriptor<Model> {
            boolean touched;

            @Override
            String getDisplayName() {
                return "test";
            }

            ListBoxModel doFillAbcItems(@RelativePath("..") @QueryParameter String name) {
                assert name=="Alice";
                touched = true;
                return new ListBoxModel().add("foo").add("bar")
            }
        }
    }
}
