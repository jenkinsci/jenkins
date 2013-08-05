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

package hudson.tasks;

import hudson.model.AbstractProject;
import org.junit.Test;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.tasks.LogRotatorTest.TestsFail;
import java.io.File;
import static hudson.tasks.LogRotatorTest.build;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import static org.junit.Assert.*;

/**
 * Verifies that artifacts from the last successful and stable builds of a job will be kept if requested.
 */
public class ArtifactArchiverTest {
    
    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testSuccessVsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", true, false)));
        assertEquals("(no artifacts)", Result.FAILURE, build(project)); // #1
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #2
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        project.getBuildersList().replaceBy(Arrays.asList(new CreateArtifact(), new FailureBuilder()));
        assertEquals(Result.FAILURE, build(project)); // #3
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertEquals(Result.FAILURE, build(project)); // #4
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertEquals(Result.FAILURE, build(project)); // #5
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse("no better than #4", project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #6
        assertTrue("#2 is still lastSuccessful until #6 is complete", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertEquals(Result.SUCCESS, build(project)); // #7
        assertFalse("lastSuccessful was #6 for ArtifactArchiver", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertFalse(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertTrue(project.getBuildByNumber(7).getHasArtifacts());
    }

    @Test
    @Bug(2417)
    public void testStableVsUnstable() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Publisher artifactArchiver = new ArtifactArchiver("f", "", true, false);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #1
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        project.getPublishersList().replaceBy(Arrays.asList(artifactArchiver, new TestsFail()));
        assertEquals(Result.UNSTABLE, build(project)); // #2
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertEquals(Result.UNSTABLE, build(project)); // #3
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertEquals(Result.UNSTABLE, build(project)); // #4
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        assertEquals(Result.SUCCESS, build(project)); // #5
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertEquals(Result.SUCCESS, build(project)); // #6
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
    }

    @Test
    @Bug(3227)
    public void testEmptyDirectories() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Publisher artifactArchiver = new ArtifactArchiver("dir/", "", false, false);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath dir = build.getWorkspace().child("dir");
                dir.child("subdir1").mkdirs();
                FilePath subdir2 = dir.child("subdir2");
                subdir2.mkdirs();
                subdir2.child("file").write("content", "UTF-8");
                return true;
            }
        }));
        assertEquals(Result.SUCCESS, build(project)); // #1
        File artifacts = project.getBuildByNumber(1).getArtifactsDir();
        File[] kids = artifacts.listFiles();
        assertEquals(1, kids.length);
        assertEquals("dir", kids[0].getName());
        kids = kids[0].listFiles();
        assertEquals(1, kids.length);
        assertEquals("subdir2", kids[0].getName());
        kids = kids[0].listFiles();
        assertEquals(1, kids.length);
        assertEquals("file", kids[0].getName());
    }

    @Test
    @Bug(10502)
    public void testAllowEmptyArchive() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", false, true)));
        assertEquals("(no artifacts)", Result.SUCCESS, build(project));
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
    }
    
    private void runNewBuildAndStartUnitlIsCreated(AbstractProject project) throws InterruptedException{
        int buildNumber = project.getNextBuildNumber();
        project.scheduleBuild2(0);
        int count = 0;
        while(project.getBuildByNumber(buildNumber)==null && count<30){
            Thread.sleep(100);
            count ++;
        }
        if(project.getBuildByNumber(buildNumber)==null)
            fail("Build " + buildNumber + " did not created.");
    }
    
    @Test
    public void testPrebuildWithConcurrentBuilds() throws IOException, Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        j.jenkins.setNumExecutors(4);
        //logest build
        project.getBuildersList().add(new Shell("sleep 100"));
        project.setConcurrentBuild(true);
        Publisher artifactArchiver = new ArtifactArchiver("dir/", "", true, false);
        runNewBuildAndStartUnitlIsCreated(project);
        //shortest build
        project.getBuildersList().clear();
        j.buildAndAssertSuccess(project);
        //longest build
        project.getBuildersList().add(new Shell("sleep 100"));
        runNewBuildAndStartUnitlIsCreated(project);
        AbstractBuild build = project.getLastBuild();
        BuildListener listner = new StreamBuildListener(BuildListener.NULL.getLogger(), Charset.defaultCharset());
        try{
            System.out.println("last build is " + project.getLastBuild());
            for(AbstractBuild b: project.getBuilds()){
                System.out.println(" build " + b + " sttus " + b.getResult());
            }
            boolean ok = artifactArchiver.prebuild(build, listner);
            assertTrue("Artefact archiver should not have any problem.", ok);
        }
        catch(Exception e){
            fail("Artefact archiver should not throw exception " + e + " for concurrent builds");
        }
                
    }

    static class CreateArtifact extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            build.getWorkspace().child("f").write("content", "UTF-8");
            return true;
        }
    }

}
