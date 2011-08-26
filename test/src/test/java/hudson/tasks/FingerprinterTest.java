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
import hudson.model.Project;
import hudson.model.Result;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.RunList;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author dty
 */
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
