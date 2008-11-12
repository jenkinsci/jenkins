package hudson.matrix;

import hudson.model.Result;
import hudson.tasks.Ant;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class MatrixProjectTest extends HudsonTestCase {
    /**
     * Tests that axes are available as build variables in the Ant builds.
     */
    public void testBuildAxis() throws Exception {
        MatrixProject p = (MatrixProject) hudson.createProject(MatrixProject.DESCRIPTOR, "test");

        // set up 2x2 matrix
        AxisList axes = new AxisList();
        axes.add(new Axis("db","mysql","oracle"));
        axes.add(new Axis("direction","north","south"));
        p.setAxes(axes);
        p.getBuildersList().add(new Ant("-Dprop=${db} test",null,null,null,null));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("build.xml","<project><target name='test'><echo>assertion ${prop}=${db}</echo></target></project>"));

        MatrixBuild build = p.scheduleBuild2(0).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4,runs.size());
        for (MatrixRun run : runs) {
            assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            assertLogContains("assertion "+expectedDb+"="+expectedDb, run);
        }
    }
}
