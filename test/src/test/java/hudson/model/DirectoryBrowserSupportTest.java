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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import org.junit.Assume;
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
import hudson.ExtensionList;
import hudson.Util;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
        Assume.assumeFalse("can't test this on Windows", Functions.isWindows());

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

    @Issue("SECURITY-95")
    @Test
    public void contentSecurityPolicy() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new SingleFileSCM("test.html", "<html><body><h1>Hello world!</h1></body></html>"));
        p.getPublishersList().add(new ArtifactArchiver("*", "", true));
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());

        HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/test.html");
        for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
            assertEquals("Header set: " + header, page.getWebResponse().getResponseHeaderValue(header), DirectoryBrowserSupport.DEFAULT_CSP_VALUE);
        }

        String propName = DirectoryBrowserSupport.class.getName() + ".CSP";
        String initialValue = System.getProperty(propName);
        try {
            System.setProperty(propName, "");
            page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/test.html");
            for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
                assertFalse("Header not set: " + header, page.getWebResponse().getResponseHeaders().contains(header));
            }
        } finally {
            if (initialValue == null) {
                System.clearProperty(DirectoryBrowserSupport.class.getName() + ".CSP");
            } else {
                System.setProperty(DirectoryBrowserSupport.class.getName() + ".CSP", initialValue);
            }
        }
    }

    private File download(UnexpectedPage page) throws IOException {

        File file = File.createTempFile("DirectoryBrowserSupport", "zipDownload");
        file.delete();
        try (InputStream is = page.getInputStream();
             OutputStream os = Files.newOutputStream(file.toPath())) {
            IOUtils.copy(is, os);
        }

        return file;
    }

    @Issue("JENKINS-49635")
    @Test
    public void externalURLDownload() throws Exception {
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new ExternalArtifactManagerFactory());
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new SingleFileSCM("f", "Hello world!"));
        p.getPublishersList().add(new ArtifactArchiver("f"));
        j.buildAndAssertSuccess(p);
        HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/");
        try {
            Page download = page.getAnchorByText("f").click();
            assertEquals("Hello world!", download.getWebResponse().getContentAsString());
        } catch (FailingHttpStatusCodeException x) {
            IOUtils.copy(x.getResponse().getContentAsStream(), System.err);
            throw x;
        }
    }
    /** Simulation of a storage service with URLs unrelated to {@link Run#doArtifact}. */
    @TestExtension("externalURLDownload")
    public static final class ContentAddressableStore implements UnprotectedRootAction {
        final List<byte[]> files = new ArrayList<>();
        @Override
        public String getUrlName() {
            return "files";
        }
        @Override
        public String getIconFileName() {
            return null;
        }
        @Override
        public String getDisplayName() {
            return null;
        }
        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws Exception {
            String hash = req.getRestOfPath().substring(1);
            for (byte[] file : files) {
                if (Util.getDigestOf(new ByteArrayInputStream(file)).equals(hash)) {
                    rsp.setContentType("application/octet-stream");
                    rsp.getOutputStream().write(file);
                    return;
                }
            }
            rsp.sendError(404);
        }
    }
    public static final class ExternalArtifactManagerFactory extends ArtifactManagerFactory {
        @Override
        public ArtifactManager managerFor(Run<?, ?> build) {
            return new ExternalArtifactManager();
        }
        @TestExtension("externalURLDownload")
        public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {}
    }
    private static final class ExternalArtifactManager extends ArtifactManager {
        String hash;
        @Override
        public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts) throws IOException, InterruptedException {
            assertEquals(1, artifacts.size());
            Map.Entry<String, String> entry = artifacts.entrySet().iterator().next();
            assertEquals("f", entry.getKey());
            try (InputStream is = workspace.child(entry.getValue()).read()) {
                byte[] data = IOUtils.toByteArray(is);
                ExtensionList.lookupSingleton(ContentAddressableStore.class).files.add(data);
                hash = Util.getDigestOf(new ByteArrayInputStream(data));
            }
        }
        @Override
        public VirtualFile root() {
            final VirtualFile file = new VirtualFile() { // the file inside the root
                @Override
                public String getName() {
                    return "f";
                }
                @Override
                public URI toURI() {
                    return URI.create("root:f");
                }
                @Override
                public VirtualFile getParent() {
                    return root();
                }
                @Override
                public boolean isDirectory() throws IOException {
                    return false;
                }
                @Override
                public boolean isFile() throws IOException {
                    return true;
                }
                @Override
                public boolean exists() throws IOException {
                    return true;
                }
                @Override
                public VirtualFile[] list() throws IOException {
                    return new VirtualFile[0];
                }
                @Override
                public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) throws IOException {
                    return Collections.emptySet();
                }
                @Override
                public VirtualFile child(String name) {
                    throw new UnsupportedOperationException();
                }
                @Override
                public long length() throws IOException {
                    return 0;
                }
                @Override
                public long lastModified() throws IOException {
                    return 0;
                }
                @Override
                public boolean canRead() throws IOException {
                    return true;
                }
                @Override
                public InputStream open() throws IOException {
                    throw new FileNotFoundException("expect to be opened via URL only");
                }
                @Override
                public URL toExternalURL() throws IOException {
                    return new URL(Jenkins.get().getRootUrl() + "files/" + hash);
                }
            };
            return new VirtualFile() { // the root
                @Override
                public String getName() {
                    return "";
                }
                @Override
                public URI toURI() {
                    return URI.create("root:");
                }
                @Override
                public VirtualFile getParent() {
                    return this;
                }
                @Override
                public boolean isDirectory() throws IOException {
                    return true;
                }
                @Override
                public boolean isFile() throws IOException {
                    return false;
                }
                @Override
                public boolean exists() throws IOException {
                    return true;
                }
                @Override
                public VirtualFile[] list() throws IOException {
                    return new VirtualFile[] {file};
                }
                @Override
                public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) throws IOException {
                    throw new UnsupportedOperationException();
                }
                @Override
                public VirtualFile child(String name) {
                    if (name.equals("f")) {
                        return file;
                    } else if (name.isEmpty()) {
                        return this;
                    } else {
                        throw new UnsupportedOperationException("trying to call child on " + name);
                    }
                }
                @Override
                public long length() throws IOException {
                    return 0;
                }
                @Override
                public long lastModified() throws IOException {
                    return 0;
                }
                @Override
                public boolean canRead() throws IOException {
                    return true;
                }
                @Override
                public InputStream open() throws IOException {
                    throw new FileNotFoundException();
                }
            };
        }
        @Override
        public void onLoad(Run<?, ?> build) {}
        @Override
        public boolean delete() throws IOException, InterruptedException {
            return false;
        }
    }

}
