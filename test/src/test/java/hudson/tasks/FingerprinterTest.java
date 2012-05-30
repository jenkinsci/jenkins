/*
 *  The MIT License
 * 
 *  Copyright 2011 Yahoo!, Inc.
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package hudson.tasks;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.util.RunList;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author dty
 */
@SuppressWarnings("rawtypes")
public class FingerprinterTest extends HudsonTestCase {
    private static final String[] singleContents = {
        "abcdef"
    };
    private static final String[] singleFiles = {
        "test.txt"
    };
    private static final String[] singleContents2 = {
        "ghijkl"
    };
    private static final String[] singleFiles2 = {
        "test2.txt"
    };
    private static final String[] doubleContents = {
        "abcdef",
        "ghijkl"
    };
    private static final String[] doubleFiles = {
        "test.txt",
        "test2.txt"
    };
    
    private static final String renamedProject1 = "renamed project 1";
    private static final String renamedProject2 = "renamed project 2";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Fingerprinter.enableFingerprintsInDependencyGraph = true;
    }
    
    public void testFingerprintDependencies() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        assertBuildStatusSuccess(downstream.scheduleBuild2(0).get());

        List<AbstractProject> downstreamProjects = upstream.getDownstreamProjects();
        List<AbstractProject> upstreamProjects = downstream.getUpstreamProjects();

        assertEquals(1, downstreamProjects.size());
        assertEquals(1, upstreamProjects.size());
        assertTrue(upstreamProjects.contains(upstream));
        assertTrue(downstreamProjects.contains(downstream));
    }

    public void testMultipleUpstreamDependencies() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject upstream2 = createFreeStyleProjectWithFingerprints(singleContents2, singleFiles2);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(doubleContents, doubleFiles);

        assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        assertBuildStatusSuccess(upstream2.scheduleBuild2(0).get());
        assertBuildStatusSuccess(downstream.scheduleBuild2(0).get());

        List<AbstractProject> downstreamProjects = upstream.getDownstreamProjects();
        List<AbstractProject> downstreamProjects2 = upstream2.getDownstreamProjects();
        List<AbstractProject> upstreamProjects = downstream.getUpstreamProjects();

        assertEquals(1, downstreamProjects.size());
        assertEquals(1, downstreamProjects2.size());
        assertEquals(2, upstreamProjects.size());
        assertTrue(upstreamProjects.contains(upstream));
        assertTrue(upstreamProjects.contains(upstream2));
        assertTrue(downstreamProjects.contains(downstream));
    }

    public void testMultipleDownstreamDependencies() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(doubleContents, doubleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream2 = createFreeStyleProjectWithFingerprints(singleContents2, singleFiles2);

        assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        assertBuildStatusSuccess(downstream.scheduleBuild2(0).get());
        assertBuildStatusSuccess(downstream2.scheduleBuild2(0).get());

        List<AbstractProject> downstreamProjects = upstream.getDownstreamProjects();
        List<AbstractProject> upstreamProjects = downstream.getUpstreamProjects();
        List<AbstractProject> upstreamProjects2 = downstream2.getUpstreamProjects();

        assertEquals(2, downstreamProjects.size());
        assertEquals(1, upstreamProjects.size());
        assertEquals(1, upstreamProjects2.size());
        assertTrue(upstreamProjects.contains(upstream));
        assertTrue(upstreamProjects2.contains(upstream));
        assertTrue(downstreamProjects.contains(downstream));
        assertTrue(downstreamProjects.contains(downstream2));
    }

    public void testDependencyExclusion() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        FreeStyleBuild upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        assertBuildStatusSuccess(downstream.scheduleBuild2(0).get());

        upstreamBuild.delete();

        Hudson.getInstance().rebuildDependencyGraph();

        List<AbstractProject> upstreamProjects = downstream.getUpstreamProjects();
        List<AbstractProject> downstreamProjects = upstream.getDownstreamProjects();

        assertEquals(0, upstreamProjects.size());
        assertEquals(0, downstreamProjects.size());
    }

    public void testCircularDependency() throws Exception {
        FreeStyleProject p = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        
        List<AbstractProject> upstreamProjects = p.getUpstreamProjects();
        List<AbstractProject> downstreamProjects = p.getDownstreamProjects();
        
        assertEquals(0, upstreamProjects.size());
        assertEquals(0, downstreamProjects.size());
    }
    
    public void testMatrixDependency() throws Exception {
        MatrixProject matrixProject = createMatrixProject();
        matrixProject.setAxes(new AxisList(new Axis("foo", "a", "b")));
        FreeStyleProject freestyleProject = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        addFingerprinterToProject(matrixProject, singleContents, singleFiles);

        jenkins.rebuildDependencyGraph();

        buildAndAssertSuccess(matrixProject);
        buildAndAssertSuccess(freestyleProject);
        waitUntilNoActivity();

        RunList<FreeStyleBuild> builds = freestyleProject.getBuilds();
        assertEquals("There should only be one FreestyleBuild", 1, builds.size());
        FreeStyleBuild build = builds.iterator().next();
        assertEquals(Result.SUCCESS, build.getResult());
        List<AbstractProject> downstream = jenkins.getDependencyGraph().getDownstream(matrixProject);
        assertTrue(downstream.contains(freestyleProject));        
        List<AbstractProject> upstream = jenkins.getDependencyGraph().getUpstream(freestyleProject);
        assertTrue(upstream.contains(matrixProject));
    }

    public void testProjectRename() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        FreeStyleBuild upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        FreeStyleBuild downstreamBuild = assertBuildStatusSuccess(downstream.scheduleBuild2(0).get());

        String oldUpstreamName = upstream.getName();
        String oldDownstreamName = downstream.getName();
        
        // Verify that owner entry in fingerprint record is changed
        // after source project is renamed
        upstream.renameTo(renamedProject1);
        Fingerprinter.FingerprintAction action = upstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        Collection<Fingerprint> fingerprints = action.getFingerprints().values();
        for (Fingerprint f: fingerprints) {
            assertTrue(f.getOriginal().is(upstream));
            assertTrue(f.getOriginal().getName().equals(renamedProject1));
            assertFalse(f.getOriginal().getName().equals(oldUpstreamName));
        }
        
        action = downstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        fingerprints = action.getFingerprints().values();
        for (Fingerprint f: fingerprints) {
            assertTrue(f.getOriginal().is(upstream));
            assertTrue(f.getOriginal().getName().equals(renamedProject1));
            assertFalse(f.getOriginal().getName().equals(oldUpstreamName));
        }
         
        // Verify that usage entry in fingerprint record is changed after
        // sink project is renamed
        downstream.renameTo(renamedProject2);
        upstream.renameTo(renamedProject1);
        action = upstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        fingerprints = action.getFingerprints().values();
        for (Fingerprint f: fingerprints) {
            List<String> jobs = f.getJobs();
            
            assertTrue(jobs.contains(renamedProject2));
            assertFalse(jobs.contains(oldDownstreamName));
        }

        action = downstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        fingerprints = action.getFingerprints().values();
        for (Fingerprint f: fingerprints) {
            List<String> jobs = f.getJobs();
            
            assertTrue(jobs.contains(renamedProject2));
            assertFalse(jobs.contains(oldDownstreamName));
        }
    }
    
    private FreeStyleProject createFreeStyleProjectWithFingerprints(String[] contents, String[] files) throws IOException, Exception {
        FreeStyleProject project = createFreeStyleProject();

        addFingerprinterToProject(project, contents, files);
        
        return project;
    }
    
    private void addFingerprinterToProject(AbstractProject<?, ?> project, String[] contents, String[] files) throws Exception {
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < contents.length; i++) {
            if (project instanceof MatrixProject) {
                ((MatrixProject)project).getBuildersList().add(new Shell("echo " + contents[i] + " > " + files[i]));
            } else {
                ((FreeStyleProject)project).getBuildersList().add(new Shell("echo " + contents[i] + " > " + files[i]));                
            }
            
            targets.append(files[i]).append(',');
        }

        project.getPublishersList().add(new Fingerprinter(targets.toString(), false));
    }
}
