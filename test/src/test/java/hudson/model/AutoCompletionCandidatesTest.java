package hudson.model;

import static org.junit.Assert.assertEquals;

import hudson.matrix.AxisList;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.TreeSet;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class AutoCompletionCandidatesTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void completion() throws Exception {
        FreeStyleProject foo = j.createFreeStyleProject("foo");
        MatrixProject bar = j.jenkins.createProject(MatrixProject.class, "bar");
        bar.setAxes(new AxisList(new TextAxis("x","1","2","3")));
        MatrixConfiguration x3 = bar.getItem("x=3");

        AutoCompletionCandidates c;

        c = AutoCompletionCandidates.ofJobNames(Item.class, "", foo, j.jenkins);
        assertContains(c, "foo", "bar");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "ba", foo, j.jenkins);
        assertContains(c, "bar");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "bar/", foo, j.jenkins);
        assertContains(c, "bar/x=1", "bar/x=2", "bar/x=3");


        c = AutoCompletionCandidates.ofJobNames(FreeStyleProject.class, "", foo, j.jenkins);
        assertContains(c, "foo");

        c = AutoCompletionCandidates.ofJobNames(MatrixConfiguration.class, "bar/", foo, j.jenkins);
        assertContains(c, "bar/x=1", "bar/x=2", "bar/x=3");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "", x3, x3.getParent());
        assertContains(c, "x=1", "x=2", "x=3");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "/", x3, x3.getParent());
        assertContains(c, "/foo", "/bar");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "/bar/", x3, x3.getParent());
        assertContains(c, "/bar/x=1", "/bar/x=2", "/bar/x=3");

        // relative path
        c = AutoCompletionCandidates.ofJobNames(Item.class, "../", x3, x3.getParent());
        assertContains(c, "../bar", "../foo");
    }

    /** Checks that {@link ViewDescriptor#doAutoCompleteCopyNewItemFrom} honors {@link DirectlyModifiableTopLevelItemGroup#canAdd}. */
    @Test
    public void canAdd() throws Exception {
        MockFolder d1 = j.createFolder("d1");
        d1.createProject(MockFolder.class, "sub");
        d1.createProject(FreeStyleProject.class, "prj");
        MockFolder d2 = j.jenkins.createProject(RestrictiveFolder.class, "d2");
        assertContains(AutoCompletionCandidates.ofJobNames(TopLevelItem.class, "../d1/", d2), "../d1/prj");
    }

    public static class RestrictiveFolder extends MockFolder {

        public RestrictiveFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public boolean canAdd(TopLevelItem item) {
            return item instanceof FreeStyleProject;
        }

        @TestExtension("canAdd") public static class DescriptorImpl extends TopLevelItemDescriptor {

            @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new RestrictiveFolder(parent, name);
            }

        }

    }

    private void assertContains(AutoCompletionCandidates c, String... values) {
        assertEquals(new TreeSet<String>(Arrays.asList(values)), new TreeSet<String>(c.getValues()));
    }
}
