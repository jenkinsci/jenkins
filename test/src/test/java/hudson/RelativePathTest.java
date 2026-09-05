package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.util.ListBoxModel;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.QueryParameter;

@WithJenkins
class RelativePathTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-18776")
    @Test
    void testRelativePath() throws Exception {
        // I was having trouble causing annotation processing on test stubs
//        jenkins.getDescriptorOrDie(RelativePathTest.class);
//        jenkins.getDescriptorOrDie(Model.class);

        j.createWebClient().goTo("self/");
        assertTrue(j.jenkins.getDescriptorByType(Model.DescriptorImpl.class).touched);
    }

    public static class Model implements Describable<Model> {

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Model> {

            boolean touched;

            public ListBoxModel doFillAbcItems(@RelativePath("..") @QueryParameter String personName) {
                assertEquals("Alice", personName);
                touched = true;
                return new ListBoxModel().add("foo").add("bar");
            }
        }
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements Describable<RootActionImpl>, RootAction {
        public String getPersonName() {
            return "Alice";
        }

        public Model getModel() {
            return new Model();
        }

        @Override
        public Descriptor<RootActionImpl> getDescriptor() {
            return Objects.requireNonNull(Jenkins.get().getDescriptorByType(DescriptorImpl.class));
        }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<RootActionImpl> {}

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
