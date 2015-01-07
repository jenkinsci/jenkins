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
package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestBuilder;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author Kohsuke Kawaguchi
 */
public class DirectoryBrowserSupportTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Double dots that appear in file name is OK.
     */
    @Email("http://www.nabble.com/Status-Code-400-viewing-or-downloading-artifact-whose-filename-contains-two-consecutive-periods-tt21407604.html")
    @Test
    public void doubleDots() throws Exception {
        // create a problematic file name in the workspace
        FreeStyleProject p = j.createFreeStyleProject();
        if(Functions.isWindows())
            p.getBuildersList().add(new BatchFile("echo > abc..def"));
        else
            p.getBuildersList().add(new Shell("touch abc..def"));
        p.scheduleBuild2(0).get();

        // can we see it?
        j.createWebClient().goTo("job/"+p.getName()+"/ws/abc..def","application/octet-stream");

        // TODO: implement negative check to make sure we aren't serving unexpected directories.
        // the following trivial attempt failed. Someone in between is normalizing.
//        // but this should fail
//        try {
//            new WebClient().goTo("job/" + p.getName() + "/ws/abc/../", "application/octet-stream");
//        } catch (FailingHttpStatusCodeException e) {
//            assertEquals(400,e.getStatusCode());
//        }
    }

    /**
     * <strike>Also makes sure '\\' in the file name for Unix is handled correctly</strike>.
     *
     * To prevent directory traversal attack, we now treat '\\' just like '/'.
     */
    @Email("http://www.nabble.com/Status-Code-400-viewing-or-downloading-artifact-whose-filename-contains-two-consecutive-periods-tt21407604.html")
    @Test
    public void doubleDots2() throws Exception {
        if(Functions.isWindows())  return; // can't test this on Windows

        // create a problematic file name in the workspace
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Shell("mkdir abc; touch abc/def.bin"));
        p.scheduleBuild2(0).get();

        // can we see it?
        j.createWebClient().goTo("job/"+p.getName()+"/ws/abc%5Cdef.bin","application/octet-stream");
    }

    @Test
    public void nonAsciiChar() throws Exception {
        // create a problematic file name in the workspace
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("\u6F22\u5B57.bin").touch(0); // Kanji
                return true;
            }
        }); // Kanji
        p.scheduleBuild2(0).get();

        // can we see it?
        j.createWebClient().goTo("job/"+p.getName()+"/ws/%e6%bc%a2%e5%ad%97.bin","application/octet-stream");
    }

    @Test
    public void glob() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("pom.xml").touch(0);
                ws.child("src/main/java/p").mkdirs();
                ws.child("src/main/java/p/X.java").touch(0);
                ws.child("src/main/resources/p").mkdirs();
                ws.child("src/main/resources/p/x.txt").touch(0);
                ws.child("src/test/java/p").mkdirs();
                ws.child("src/test/java/p/XTest.java").touch(0);
                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        String text = j.createWebClient().goTo("job/"+p.getName()+"/ws/**/*.java").asText();
        assertTrue(text, text.contains("X.java"));
        assertTrue(text, text.contains("XTest.java"));
        assertFalse(text, text.contains("pom.xml"));
        assertFalse(text, text.contains("x.txt"));
    }

    @Issue("JENKINS-19752")
    @Test
    public void zipDownload() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new SingleFileSCM("artifact.out", "Hello world!"));
        p.getPublishersList().add(new ArtifactArchiver("*", "", true));
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        HtmlPage page = j.createWebClient().goTo("job/"+p.getName()+"/lastSuccessfulBuild/artifact/");
        Page download = page.getAnchorByHref("./*zip*/archive.zip").click();
        File zipfile = download((UnexpectedPage) download);

        ZipFile readzip = new ZipFile(zipfile);

        InputStream is = readzip.getInputStream(readzip.getEntry("archive/artifact.out"));

        // ZipException in case of JENKINS-19752
        assertFalse("Downloaded zip file must not be empty", is.read() == -1);

        is.close();
        readzip.close();
        zipfile.delete();
    }

    private File download(UnexpectedPage page) throws IOException {

        File file = File.createTempFile("DirectoryBrowserSupport", "zipDownload");
        file.delete();
        Util.copyStreamAndClose(page.getInputStream(), new FileOutputStream(file));

        return file;
    }
}
