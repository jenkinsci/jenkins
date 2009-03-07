/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.matrix;

import hudson.model.Cause;
import hudson.model.Result;
import hudson.tasks.Ant;
import hudson.tasks.Maven;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Shell;
import hudson.tasks.Fingerprinter;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.Email;

import java.io.IOException;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class MatrixProjectTest extends HudsonTestCase {
    /**
     * Tests that axes are available as build variables in the Ant builds.
     */
    public void testBuildAxisInAnt() throws Exception {
        MatrixProject p = createMatrixProject();
        Ant.AntInstallation ant = configureDefaultAnt();
        p.getBuildersList().add(new Ant("-Dprop=${db} test",ant.getName(),null,null,null));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("build.xml","<project default='test'><target name='test'><echo>assertion ${prop}=${db}</echo></target></project>"));

        MatrixBuild build = p.scheduleBuild2(0, new Cause.UserCause()).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4,runs.size());
        for (MatrixRun run : runs) {
            assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            assertLogContains("assertion "+expectedDb+"="+expectedDb, run);
        }
    }

    /**
     * Tests that axes are available as build variables in the Maven builds.
     */
    public void testBuildAxisInMaven() throws Exception {
        MatrixProject p = createMatrixProject();
        p.getBuildersList().add(new Maven("-Dprop=${db} validate",null));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("pom.xml",getClass().getResource("echo-property.pom")));

        MatrixBuild build = p.scheduleBuild2(0).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4,runs.size());
        for (MatrixRun run : runs) {
            assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            System.out.println(run.getLog());
            assertLogContains("assertion "+expectedDb+"="+expectedDb, run);
            // also make sure that the variables are expanded at the command line level.
            assertFalse(run.getLog().contains("-Dprop=${db}"));
        }
    }

    @Override
    protected MatrixProject createMatrixProject() throws IOException {
        MatrixProject p = super.createMatrixProject();

        // set up 2x2 matrix
        AxisList axes = new AxisList();
        axes.add(new Axis("db","mysql","oracle"));
        axes.add(new Axis("direction","north","south"));
        p.setAxes(axes);

        return p;
    }

    /**
     * Fingerprinter failed to work on the matrix project.
     */
    @Email("http://www.nabble.com/1.286-version-and-fingerprints-option-broken-.-td22236618.html")
    public void testFingerprinting() throws Exception {
        MatrixProject p = createMatrixProject();
        p.getBuildersList().add(new Shell("touch p"));
        p.getPublishersList().add(new ArtifactArchiver("p",null,false));
        p.getPublishersList().add(new Fingerprinter("",true));
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
    }
}
