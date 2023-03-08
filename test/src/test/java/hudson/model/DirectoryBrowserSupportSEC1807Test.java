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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import hudson.FilePath;
import hudson.Launcher;
import hudson.slaves.WorkspaceList;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * @author Kohsuke Kawaguchi
 */
public class DirectoryBrowserSupportSEC1807Test {

    @Rule public JenkinsRule j = new JenkinsRule();

    private File download(UnexpectedPage page) throws IOException {

        File file = File.createTempFile("DirectoryBrowserSupport", "zipDownload");
        file.delete();
        try (InputStream is = page.getInputStream();
             OutputStream os = Files.newOutputStream(file.toPath())) {
            IOUtils.copy(is, os);
        }

        return file;
    }

    private List<String> getListOfEntriesInDownloadedZip(UnexpectedPage zipPage) throws Exception {
        List<String> result;

        File zipfile = null;
        ZipFile readzip = null;
        try {
            zipfile = download(zipPage);

            readzip = new ZipFile(zipfile);
            result = readzip.stream().map(ZipEntry::getName).collect(Collectors.toList());
        }
        finally {
            if (readzip != null) {
                readzip.close();
            }
            if (zipfile != null) {
                zipfile.delete();
            }
        }
        return result;
    }

    @Test
    @Issue("SECURITY-1807")
    public void tmpNotListed() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("anotherDir").mkdirs();
                WorkspaceList.tempDir(ws.child("subdir")).mkdirs();
                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        String text = j.createWebClient().goTo("job/" + p.getName() + "/ws/").asNormalizedText();
        assertTrue(text, text.contains("anotherDir"));
        assertFalse(text, text.contains("subdir"));
    }

    @Test
    @Issue("SECURITY-1807")
    public void tmpNotListedWithGlob() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        FilePath ws = p.getSomeWorkspace();

        FilePath anotherDir = ws.child("anotherDir");
        anotherDir.mkdirs();
        anotherDir.child("insideDir").mkdirs();

        FilePath mainTmp = WorkspaceList.tempDir(ws.child("subDir"));
        mainTmp.mkdirs();

        FilePath anotherTmp = WorkspaceList.tempDir(anotherDir.child("insideDir"));
        anotherTmp.mkdirs();

        ws.child("anotherDir/one.txt").touch(0);
        ws.child("anotherDir/insideDir/two.txt").touch(0);
        mainTmp.child("three.txt").touch(0);
        anotherTmp.child("four.txt").touch(0);

        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        String text = j.createWebClient().goTo("job/" + p.getName() + "/ws/**/*.txt").asNormalizedText();
        assertTrue(text, text.contains("one.txt"));
        assertTrue(text, text.contains("two.txt"));
        assertFalse(text, text.contains("three.txt"));
        assertFalse(text, text.contains("four.txt"));
    }

    @Test
    @Issue("SECURITY-1807")
    public void noDirectAccessToTmp() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();

                FilePath folder = ws.child("anotherDir");
                folder.mkdirs();
                folder.child("one.txt").touch(0);

                FilePath mainTmp = WorkspaceList.tempDir(ws.child("subDir"));
                mainTmp.mkdirs();
                mainTmp.child("two.txt").touch(0);

                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        Page page = wc.goTo(p.getUrl() + "ws/anotherDir/", null);
        assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
        page = wc.goTo(p.getUrl() + "ws/anotherDir/one.txt", null);
        assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

        page = wc.goTo(p.getUrl() + "ws/subdir@tmp/", null);
        assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));

        page = wc.goTo(p.getUrl() + "ws/subdir@tmp/two.txt", null);
        assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
    }

    @Test
    @Issue("SECURITY-1807")
    public void tmpNotListedInPlain() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("anotherDir").mkdirs();
                WorkspaceList.tempDir(ws.child("subdir")).mkdirs();
                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        String text = j.createWebClient().goTo("job/" + p.getName() + "/ws/*plain*", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.contains("anotherDir"));
        assertFalse(text, text.contains("subdir"));
    }

    @Test
    @Issue("SECURITY-1807")
    public void tmpNotListedInZipWithoutGlob() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();

                FilePath anotherDir = ws.child("anotherDir");
                anotherDir.mkdirs();
                anotherDir.child("insideDir").mkdirs();

                FilePath mainTmp = WorkspaceList.tempDir(ws.child("subDir"));
                mainTmp.mkdirs();

                FilePath anotherTmp = WorkspaceList.tempDir(anotherDir.child("insideDir"));
                anotherTmp.mkdirs();

                ws.child("anotherDir/one.txt").touch(0);
                ws.child("anotherDir/insideDir/two.txt").touch(0);
                mainTmp.child("three.txt").touch(0);
                anotherTmp.child("four.txt").touch(0);
                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        //http://localhost:54407/jenkins/job/test0/ws/**/*.txt/*zip*/glob.zip
        Page zipPage = wc.goTo("job/" + p.getName() + "/ws/*zip*/" + p.getName(), null);
        assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

        List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
        assertThat(entryNames, hasSize(2));
        assertThat(entryNames, containsInAnyOrder(
                "test0/anotherDir/one.txt",
                "test0/anotherDir/insideDir/two.txt"
        ));

        zipPage = wc.goTo("job/" + p.getName() + "/ws/anotherDir/*zip*/" + p.getName(), null);
        assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

        entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
        assertThat(entryNames, hasSize(2));
        assertThat(entryNames, containsInAnyOrder(
                "anotherDir/one.txt",
                "anotherDir/insideDir/two.txt"
        ));
    }

    @Test
    @Issue("SECURITY-1807")
    public void tmpNotListedInZipWithGlob() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();

                FilePath anotherDir = ws.child("anotherDir");
                anotherDir.mkdirs();
                anotherDir.child("insideDir").mkdirs();

                FilePath mainTmp = WorkspaceList.tempDir(ws.child("subDir"));
                mainTmp.mkdirs();

                FilePath anotherTmp = WorkspaceList.tempDir(anotherDir.child("insideDir"));
                anotherTmp.mkdirs();

                ws.child("anotherDir/one.txt").touch(0);
                ws.child("anotherDir/insideDir/two.txt").touch(0);
                mainTmp.child("three.txt").touch(0);
                anotherTmp.child("four.txt").touch(0);
                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        Page zipPage = wc.goTo("job/" + p.getName() + "/ws/**/*.txt/*zip*/glob.zip", null);
        assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

        List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
        assertThat(entryNames, hasSize(2));
        assertThat(entryNames, containsInAnyOrder(
                "anotherDir/one.txt",
                "anotherDir/insideDir/two.txt"
        ));
    }

}
