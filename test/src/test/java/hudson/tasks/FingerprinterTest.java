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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.XmlFile;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Fingerprint;
import hudson.model.FingerprintCleanupThread;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 *
 * @author dty
 */
@SuppressWarnings("rawtypes")
public class FingerprinterTest {
    private static final String[] singleContents = {
        "abcdef",
    };
    private static final String[] singleFiles = {
        "test.txt",
    };
    private static final String[] singleContents2 = {
        "ghijkl",
    };
    private static final String[] singleFiles2 = {
        "test2.txt",
    };
    private static final String[] doubleContents = {
        "abcdef",
        "ghijkl",
    };
    private static final String[] doubleFiles = {
        "test.txt",
        "test2.txt",
    };

    private static final String renamedProject1 = "renamed project 1";
    private static final String renamedProject2 = "renamed project 2";

    @Rule public JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setUp() {
        Fingerprinter.enableFingerprintsInDependencyGraph = true;
    }

    @Test public void fingerprintDependencies() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);

        j.jenkins.rebuildDependencyGraph();

        List<AbstractProject> downstreamProjects = upstream.getDownstreamProjects();
        List<AbstractProject> upstreamProjects = downstream.getUpstreamProjects();

        assertEquals(1, downstreamProjects.size());
        assertEquals(1, upstreamProjects.size());
        assertTrue(upstreamProjects.contains(upstream));
        assertTrue(downstreamProjects.contains(downstream));
    }

    private static class FingerprintAddingBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            build.addAction(new Fingerprinter.FingerprintAction(build, Map.of(singleFiles2[0], "fakefingerprint")));
            return true;
        }
    }

    @Test public void presentFingerprintActionIsReused() throws Exception {
        FreeStyleProject project = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        project.getBuildersList().add(new FingerprintAddingBuilder());

        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        assertThat(build.getActions(Fingerprinter.FingerprintAction.class), hasSize(1));

        Fingerprinter.FingerprintAction action = build.getAction(Fingerprinter.FingerprintAction.class);
        assertThat(action.getRecords().keySet(), containsInAnyOrder(singleFiles2[0], singleFiles[0]));
    }

    @Test public void multipleUpstreamDependencies() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject upstream2 = createFreeStyleProjectWithFingerprints(singleContents2, singleFiles2);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(doubleContents, doubleFiles);

        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(upstream2);
        j.buildAndAssertSuccess(downstream);

        j.jenkins.rebuildDependencyGraph();

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

    @Test public void multipleDownstreamDependencies() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(doubleContents, doubleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream2 = createFreeStyleProjectWithFingerprints(singleContents2, singleFiles2);

        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        j.buildAndAssertSuccess(downstream2);

        j.jenkins.rebuildDependencyGraph();

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

    @Test public void dependencyExclusion() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        FreeStyleBuild upstreamBuild = j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);

        upstreamBuild.delete();

        Jenkins.get().rebuildDependencyGraph();

        List<AbstractProject> upstreamProjects = downstream.getUpstreamProjects();
        List<AbstractProject> downstreamProjects = upstream.getDownstreamProjects();

        assertEquals(0, upstreamProjects.size());
        assertEquals(0, downstreamProjects.size());
    }

    @Test public void circularDependency() throws Exception {
        FreeStyleProject p = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        j.buildAndAssertSuccess(p);
        j.buildAndAssertSuccess(p);

        Jenkins.get().rebuildDependencyGraph();

        List<AbstractProject> upstreamProjects = p.getUpstreamProjects();
        List<AbstractProject> downstreamProjects = p.getDownstreamProjects();

        assertEquals(0, upstreamProjects.size());
        assertEquals(0, downstreamProjects.size());
    }

    @Test public void matrixDependency() throws Exception {
        MatrixProject matrixProject = j.jenkins.createProject(MatrixProject.class, "p");
        matrixProject.setAxes(new AxisList(new Axis("foo", "a", "b")));
        FreeStyleProject freestyleProject = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        addFingerprinterToProject(matrixProject, singleContents, singleFiles);

        j.jenkins.rebuildDependencyGraph();

        j.buildAndAssertSuccess(matrixProject);
        j.buildAndAssertSuccess(freestyleProject);
        j.waitUntilNoActivity();

        j.jenkins.rebuildDependencyGraph();

        RunList<FreeStyleBuild> builds = freestyleProject.getBuilds();
        assertEquals("There should only be one FreestyleBuild", 1, builds.size());
        FreeStyleBuild build = builds.iterator().next();
        assertEquals(Result.SUCCESS, build.getResult());
        List<AbstractProject> downstream = j.jenkins.getDependencyGraph().getDownstream(matrixProject);
        assertTrue(downstream.contains(freestyleProject));
        List<AbstractProject> upstream = j.jenkins.getDependencyGraph().getUpstream(freestyleProject);
        assertTrue(upstream.contains(matrixProject));
    }

    @Test public void projectRename() throws Exception {
        FreeStyleProject upstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject downstream = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        FreeStyleBuild upstreamBuild = j.buildAndAssertSuccess(upstream);
        FreeStyleBuild downstreamBuild = j.buildAndAssertSuccess(downstream);

        String oldUpstreamName = upstream.getName();

        // Verify that owner entry in fingerprint record is changed
        // after source project is renamed
        upstream.renameTo(renamedProject1);
        Fingerprinter.FingerprintAction action = upstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        Collection<Fingerprint> fingerprints = action.getFingerprints().values();
        for (Fingerprint f : fingerprints) {
            assertTrue(f.getOriginal().is(upstream));
            assertEquals(renamedProject1, f.getOriginal().getName());
            assertNotEquals(f.getOriginal().getName(), oldUpstreamName);
        }

        action = downstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        fingerprints = action.getFingerprints().values();
        for (Fingerprint f : fingerprints) {
            assertTrue(f.getOriginal().is(upstream));
            assertEquals(renamedProject1, f.getOriginal().getName());
            assertNotEquals(f.getOriginal().getName(), oldUpstreamName);
        }

        String oldDownstreamName = downstream.getName();

        // Verify that usage entry in fingerprint record is changed after
        // sink project is renamed
        downstream.renameTo(renamedProject2);
        upstream.renameTo(renamedProject1);
        action = upstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        fingerprints = action.getFingerprints().values();
        for (Fingerprint f : fingerprints) {
            List<String> jobs = f.getJobs();

            assertTrue(jobs.contains(renamedProject2));
            assertFalse(jobs.contains(oldDownstreamName));
        }

        action = downstreamBuild.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        fingerprints = action.getFingerprints().values();
        for (Fingerprint f : fingerprints) {
            List<String> jobs = f.getJobs();

            assertTrue(jobs.contains(renamedProject2));
            assertFalse(jobs.contains(oldDownstreamName));
        }
    }

    @Issue("JENKINS-17125")
    @LocalData
    @Test public void actionSerialization() throws Exception {
        FreeStyleProject job = j.jenkins.getItemByFullName(Functions.isWindows() ? "j-windows" : "j", FreeStyleProject.class);
        assertNotNull(job);
        FreeStyleBuild build = job.getBuildByNumber(2);
        assertNotNull(build);
        Fingerprinter.FingerprintAction action = build.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        assertEquals(build, action.getBuild());
        if (Functions.isWindows()) {
            assertEquals("{a=603bc9e16cc05bdbc5e595969f42e3b8}", action.getRecords().toString());
        } else {
            assertEquals("{a=2d5fac981a2e865baf0e15db655c7d63}", action.getRecords().toString());
        }
        j.buildAndAssertSuccess(job);
        job._getRuns().purgeCache(); // force build records to be reloaded
        build = job.getBuildByNumber(3);
        assertNotNull(build);
        System.out.println(new XmlFile(new File(build.getRootDir(), "build.xml")).asString());
        action = build.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action);
        assertEquals(build, action.getBuild());
        if (Functions.isWindows()) {
            assertEquals("{a=a97a39fb51de0eee9fd908174dccc304}", action.getRecords().toString());
        } else {
            assertEquals("{a=f31efcf9afe30617d6c46b919e702822}", action.getRecords().toString());
        }
    }

    @SuppressWarnings("unchecked")
    // TODO randomly fails: for p3.upstreamProjects expected:<[hudson.model.FreeStyleProject@590e5b8[test0]]> but was:<[]>
    @Issue("JENKINS-18417")
    @Test
    public void fingerprintCleanup() throws Exception {
        // file names shouldn't matter
        FreeStyleProject p1 = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);
        FreeStyleProject p2 = createFreeStyleProjectWithFingerprints(singleContents, singleFiles2);
        FreeStyleProject p3 = createFreeStyleProjectWithFingerprints(singleContents, singleFiles);

        j.buildAndAssertSuccess(p1);
        j.buildAndAssertSuccess(p2);
        j.buildAndAssertSuccess(p3);

        Fingerprint f = j.jenkins._getFingerprint(Util.getDigestOf(singleContents[0] + System.lineSeparator()));
        assertNotNull(f);
        assertEquals(3, f.getUsages().size());

        j.jenkins.rebuildDependencyGraph();

        assertEquals(List.of(p1), p2.getUpstreamProjects());
        assertEquals(List.of(p1), p3.getUpstreamProjects());
        assertEquals(new HashSet(Arrays.asList(p2, p3)), new HashSet(p1.getDownstreamProjects()));

        // discard the p3 records
        p3.delete();
        new FingerprintCleanupThread().execute(StreamTaskListener.fromStdout());

        j.jenkins.rebuildDependencyGraph();

        // records for p3 should have been deleted now
        assertEquals(2, f.getUsages().size());
        assertEquals(List.of(p1), p2.getUpstreamProjects());
        assertEquals(List.of(p2), p1.getDownstreamProjects());


        // do a new build in p2 #2 that points to a separate fingerprints
        p2.getBuildersList().clear();
        p2.getPublishersList().clear();
        addFingerprinterToProject(p2, singleContents2, singleFiles2);
        j.buildAndAssertSuccess(p2);

        // another garbage collection that gets rid of p2 records from the fingerprint
        p2.getBuildByNumber(1).delete();
        new FingerprintCleanupThread().execute(StreamTaskListener.fromStdout());

        assertEquals(1, f.getUsages().size());
    }


    private FreeStyleProject createFreeStyleProjectWithFingerprints(String[] contents, String[] files) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        addFingerprinterToProject(project, contents, files);

        return project;
    }

    private void addFingerprinterToProject(AbstractProject<?, ?> project, String[] contents, String[] files) {
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < contents.length; i++) {
            if (project instanceof MatrixProject) {
                ((MatrixProject) project).getBuildersList().add(
                        Functions.isWindows()
                                ? new BatchFile("echo " + contents[i] + "> " + files[i])
                                : new Shell("echo " + contents[i] + " > " + files[i]));
            } else {
                ((FreeStyleProject) project).getBuildersList().add(
                        Functions.isWindows()
                                ? new BatchFile("echo " + contents[i] + "> " + files[i])
                                : new Shell("echo " + contents[i] + " > " + files[i]));
            }

            targets.append(files[i]).append(',');
        }

        project.getPublishersList().add(new Fingerprinter(targets.toString(), false));
    }
}
