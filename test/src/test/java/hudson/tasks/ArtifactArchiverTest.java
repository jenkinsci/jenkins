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

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import static hudson.tasks.LogRotatorTest.build;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import jenkins.util.VirtualFile;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

public class ArtifactArchiverTest {
    
    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-3227")
    public void testEmptyDirectories() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Publisher artifactArchiver = new ArtifactArchiver("dir/");
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
    @Issue("JENKINS-10502")
    public void testAllowEmptyArchive() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ArtifactArchiver aa = new ArtifactArchiver("f");
        assertFalse(aa.getAllowEmptyArchive());
        aa.setAllowEmptyArchive(true);
        project.getPublishersList().replaceBy(Collections.singleton(aa));
        assertEquals("(no artifacts)", Result.SUCCESS, build(project));
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
    }

    @Issue("JENKINS-21958")
    @Test public void symlinks() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                if (ws == null) {
                    return false;
                }
                FilePath dir = ws.child("dir");
                dir.mkdirs();
                dir.child("fizz").write("contents", null);
                dir.child("lodge").symlinkTo("fizz", listener);
                return true;
            }
        });
        ArtifactArchiver aa = new ArtifactArchiver("dir/lodge");
        aa.setAllowEmptyArchive(true);
        p.getPublishersList().add(aa);
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FilePath ws = b.getWorkspace();
        assertNotNull(ws);
        assumeTrue("May not be testable on Windows:\n" + JenkinsRule.getLog(b), ws.child("dir/lodge").exists());
        List<FreeStyleBuild.Artifact> artifacts = b.getArtifacts();
        assertEquals(1, artifacts.size());
        FreeStyleBuild.Artifact artifact = artifacts.get(0);
        assertEquals("dir/lodge", artifact.relativePath);
        VirtualFile[] kids = b.getArtifactManager().root().child("dir").list();
        assertEquals(1, kids.length);
        assertEquals("lodge", kids[0].getName());
        // do not check that it .exists() since its target has not been archived
    }

    @Issue("SECURITY-162")
    @Test public void outsideSymlinks() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                if (ws == null) {
                    return false;
                }
                ws.child("hack").symlinkTo(p.getConfigFile().getFile().getAbsolutePath(), listener);
                return true;
            }
        });
        p.getPublishersList().add(new ArtifactArchiver("hack", "", false, true));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        List<FreeStyleBuild.Artifact> artifacts = b.getArtifacts();
        assertEquals(1, artifacts.size());
        FreeStyleBuild.Artifact artifact = artifacts.get(0);
        assertEquals("hack", artifact.relativePath);
        VirtualFile[] kids = b.getArtifactManager().root().list();
        assertEquals(1, kids.length);
        assertEquals("hack", kids[0].getName());
        assertFalse(kids[0].isDirectory());
        assertFalse(kids[0].isFile());
        assertFalse(kids[0].exists());
        j.createWebClient().assertFails(b.getUrl() + "artifact/hack", HttpURLConnection.HTTP_NOT_FOUND);
    }
    
    private void runNewBuildAndStartUnitlIsCreated(AbstractProject project) throws InterruptedException{
        int buildNumber = project.getNextBuildNumber();
        project.scheduleBuild2(0);
        int count = 0;
        while(project.getBuildByNumber(buildNumber)==null && count<50){
            Thread.sleep(100);
            count ++;
        }
        if(project.getBuildByNumber(buildNumber)==null)
            fail("Build " + buildNumber + " did not created.");
    }
    
    static class CreateArtifact extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            build.getWorkspace().child("f").write("content", "UTF-8");
            return true;
        }
    }

    static class CreateArtifactAndFail extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            build.getWorkspace().child("f").write("content", "UTF-8");
            throw new AbortException("failing the build");
        }
    }

    @Test
    @Issue("JENKINS-22698")
    public void testArchivingSkippedWhenOnlyIfSuccessfulChecked() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ArtifactArchiver aa = new ArtifactArchiver("f");
        project.getPublishersList().replaceBy(Collections.singleton(aa));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifactAndFail()));
        assertEquals(Result.FAILURE, build(project));
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        aa.setOnlyIfSuccessful(true);
        assertEquals(Result.FAILURE, build(project));
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
    }




    static class CreateDefaultExcludesArtifact extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            FilePath dir = build.getWorkspace().child("dir");
            FilePath subSvnDir = dir.child(".svn");
            subSvnDir.mkdirs();
            subSvnDir.child("file").write("content", "UTF-8");

            FilePath svnDir = build.getWorkspace().child(".svn");
            svnDir.mkdirs();
            svnDir.child("file").write("content", "UTF-8");

            dir.child("file").write("content", "UTF-8");
            return true;
        }
    }

    @Test
    @Issue("JENKINS-20086")
    public void testDefaultExcludesOn() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        Publisher artifactArchiver = new ArtifactArchiver("**", "", false, false, true, true);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateDefaultExcludesArtifact()));

        assertEquals(Result.SUCCESS, build(project)); // #1
        VirtualFile artifacts = project.getBuildByNumber(1).getArtifactManager().root();
        assertFalse(artifacts.child(".svn").child("file").exists());
        assertFalse(artifacts.child("dir").child(".svn").child("file").exists());

    }

    @Test
    @Issue("JENKINS-20086")
    public void testDefaultExcludesOff() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        ArtifactArchiver artifactArchiver = new ArtifactArchiver("**");
        artifactArchiver.setDefaultExcludes(false);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateDefaultExcludesArtifact()));

        assertEquals(Result.SUCCESS, build(project)); // #1
        VirtualFile artifacts = project.getBuildByNumber(1).getArtifactManager().root();
        assertTrue(artifacts.child(".svn").child("file").exists());
        assertTrue(artifacts.child("dir").child(".svn").child("file").exists());
    }

    @LocalData
    @Test public void latestOnlyMigration() throws Exception {
        FreeStyleProject p = j.jenkins.getItemByFullName("sample", FreeStyleProject.class);
        assertNotNull(p);
        @SuppressWarnings("deprecation")
        LogRotator lr = p.getLogRotator();
        assertNotNull(lr);
        assertEquals(1, lr.getArtifactNumToKeep());
        String xml = p.getConfigFile().asString();
        assertFalse(xml, xml.contains("<latestOnly>"));
        assertTrue(xml, xml.contains("<artifactNumToKeep>1</artifactNumToKeep>"));
    }

    @LocalData
    @Test public void fingerprintMigration() throws Exception {
        FreeStyleProject p = j.jenkins.getItemByFullName("sample", FreeStyleProject.class);
        assertNotNull(p);
        String xml = p.getConfigFile().asString();
        assertFalse(xml, xml.contains("<recordBuildArtifacts>"));
        assertTrue(xml, xml.contains("<fingerprint>true</fingerprint>"));
        assertFalse(xml, xml.contains("<hudson.tasks.Fingerprinter>"));
        ArtifactArchiver aa = p.getPublishersList().get(ArtifactArchiver.class);
        assertTrue(aa.isFingerprint());
        FreeStyleBuild b1 = j.buildAndAssertSuccess(p);
        assertEquals(1, b1.getArtifacts().size());
        Fingerprinter.FingerprintAction a = b1.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(a);
        assertEquals("[stuff]", a.getFingerprints().keySet().toString());
    }

}
