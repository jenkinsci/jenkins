package hudson;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.QueryParameter;

public class RelativePathTest extends HudsonTestCase implements Describable<RelativePathTest> {

    @Issue("JENKINS-18776")
    public void testRelativePath() throws Exception {
        // I was having trouble causing annotation processing on test stubs
//        jenkins.getDescriptorOrDie(RelativePathTest.class);
//        jenkins.getDescriptorOrDie(Model.class);

        createWebClient().goTo("/self/");
        assertTrue(((Model.DescriptorImpl) jenkins.getDescriptorOrDie(Model.class)).touched);
    }

    @Override // TODO this is horrible, should change the property here and in @QueryParameter and in RelativePathTest/index.groovy to, say, personName
    public String getName() {
        return "Alice";
    }

    public Model getModel() {
        return new Model();
    }

    @Override // TODO would suffice to extend AbstractDescribableImpl
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) jenkins.getDescriptorOrDie(getClass());
    }

    @TestExtension
    public static class DescriptorImpl extends Descriptor<RelativePathTest> {}

    public static class Model extends AbstractDescribableImpl<Model> {

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Model> {

            boolean touched;

            public ListBoxModel doFillAbcItems(@RelativePath("..") @QueryParameter String name) {
                assertEquals("Alice", name);
                touched = true;
                return new ListBoxModel().add("foo").add("bar");
            }

        }

    }

}
