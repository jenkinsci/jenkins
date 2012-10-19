package hudson.model;

import hudson.matrix.AxisList;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * @author Kohsuke Kawaguchi
 */
public class AutoCompletionCandidatesTest extends HudsonTestCase {
    public void testCompletion() throws Exception {
        FreeStyleProject foo = jenkins.createProject(FreeStyleProject.class, "foo");
        MatrixProject bar = jenkins.createProject(MatrixProject.class, "bar");
        bar.setAxes(new AxisList(new TextAxis("x","1","2","3")));
        MatrixConfiguration x3 = bar.getItem("x=3");

        AutoCompletionCandidates c;

        c = AutoCompletionCandidates.ofJobNames(Item.class, "", foo, jenkins);
        assertContains(c, "foo", "bar");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "ba", foo, jenkins);
        assertContains(c, "bar");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "bar/", foo, jenkins);
        assertContains(c, "bar/x=1", "bar/x=2", "bar/x=3");


        c = AutoCompletionCandidates.ofJobNames(FreeStyleProject.class, "", foo, jenkins);
        assertContains(c, "foo");

        c = AutoCompletionCandidates.ofJobNames(MatrixConfiguration.class, "bar/", foo, jenkins);
        assertContains(c, "bar/x=1", "bar/x=2", "bar/x=3");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "", x3, x3.getParent());
        assertContains(c, "x=1", "x=2", "x=3");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "/", x3, x3.getParent());
        assertContains(c, "/foo", "/bar");

        c = AutoCompletionCandidates.ofJobNames(Item.class, "/bar/", x3, x3.getParent());
        assertContains(c, "/bar/x=1", "/bar/x=2", "/bar/x=3");
    }

    private void assertContains(AutoCompletionCandidates c, String... values) {
        assertEquals(new TreeSet<String>(Arrays.asList(values)), new TreeSet<String>(c.getValues()));
    }
}
