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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.management.UnixOperatingSystemMXBean;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.slaves.WorkspaceList;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.htmlunit.Page;
import org.htmlunit.UnexpectedPage;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

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
        if (Functions.isWindows())
            p.getBuildersList().add(new BatchFile("echo > abc..def"));
        else
            p.getBuildersList().add(new Shell("touch abc..def"));
        j.buildAndAssertSuccess(p);

        // can we see it?
        j.createWebClient().goTo("job/" + p.getName() + "/ws/abc..def", "application/octet-stream");

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
     * <del>Also makes sure '\\' in the file name for Unix is handled correctly</del>.
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
        j.buildAndAssertSuccess(p);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // normal path provided by the UI succeeds
            wc.goTo("job/" + p.getName() + "/ws/abc/def.bin", "application/octet-stream");

            // suspicious path is rejected with 400
            wc.setThrowExceptionOnFailingStatusCode(false);
            Page page = wc.goTo("job/" + p.getName() + "/ws/abc%5Cdef.bin", "application/octet-stream");
            assertEquals(200, page.getWebResponse().getStatusCode());
        }
    }

    @Test
    public void nonAsciiChar() throws Exception {
        // create a problematic file name in the workspace
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("漢字.bin").touch(0); // Kanji
                return true;
            }
        }); // Kanji
        j.buildAndAssertSuccess(p);

        // can we see it?
        j.createWebClient().goTo("job/" + p.getName() + "/ws/%e6%bc%a2%e5%ad%97.bin", "application/octet-stream");
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
        j.buildAndAssertSuccess(p);
        String text = j.createWebClient().goTo("job/" + p.getName() + "/ws/**/*.java").asNormalizedText();
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
        j.buildAndAssertSuccess(p);

        HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/");
        Page download = page.getAnchorByHref("./*zip*/archive.zip").click();
        File zipfile = download((UnexpectedPage) download);

        ZipFile readzip = new ZipFile(zipfile);

        InputStream is = readzip.getInputStream(readzip.getEntry("archive/artifact.out"));

        // ZipException in case of JENKINS-19752
        assertNotEquals("Downloaded zip file must not be empty", is.read(), -1);

        is.close();
        readzip.close();
        zipfile.delete();
    }

    @Test
    public void zipDownloadFileLeakMx_hypothesis() throws Exception {
        // this test is meant to just ensure zipDownloadFileLeakMx hypothesis about the UI work fine

        String content = "Hello world!";
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new SingleFileSCM("artifact.out", content));
        p.getPublishersList().add(new ArtifactArchiver("*", "", true));
        j.buildAndAssertSuccess(p);

        HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/");
        Page downloadPage = page.getAnchorByHref("artifact.out").click();
        assertEquals(content, downloadPage.getWebResponse().getContentAsString());
    }

    @Test
    @Issue({"JENKINS-64632", "JENKINS-61121"})
    public void zipDownloadFileLeakMx() throws Exception {
        Assume.assumeFalse(Functions.isWindows());

        int numOfClicks = 10;
        int totalRuns = 10;
        boolean freeFromLeak = false;
        long[][] openFds = new long[totalRuns][2];
        for (int runs = 0; runs < totalRuns && !freeFromLeak; runs++) {
            long initialOpenFds = getOpenFdCount();
            FreeStyleProject p = j.createFreeStyleProject();

            // add randomness just to prevent any potential caching issue
            p.setScm(new SingleFileSCM("artifact.out", "Hello world! " + Math.random()));
            p.getPublishersList().add(new ArtifactArchiver("*", "", true));
            j.buildAndAssertSuccess(p);

            HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/");
            for (int clicks = 0; clicks < numOfClicks; clicks++) {
                page.getAnchorByHref("artifact.out").click();
            }
            long finalOpenFds = getOpenFdCount();

            if (finalOpenFds < initialOpenFds + numOfClicks) {
                // when there was a file leak, the number of open file handle was always
                // greater or equal to the number of download
                // in reverse, since the correction, the likelihood to overpass the limit was less than 1%
                freeFromLeak = true;
            }

            openFds[runs][0] = initialOpenFds;
            openFds[runs][1] = finalOpenFds;
        }

        List<String> messages = new ArrayList<>();
        Map<Long, Long> differences = new TreeMap<>();
        for (int runs = 0; runs < totalRuns; runs++) {
            long difference = openFds[runs][1] - openFds[runs][0];
            Long storedDifference = differences.get(difference);
            if (storedDifference == null) {
                differences.put(difference, 1L);
            } else {
                differences.put(difference, ++storedDifference);
            }
            messages.add("Initial=" + openFds[runs][0] + ", Final=" + openFds[runs][1] + ", difference=" + difference);
        }
        for (Long difference : differences.keySet()) {
            messages.add("Difference=" + difference + " occurs " + differences.get(difference) + " times");
        }

        String summary = String.join("\n", messages);
        System.out.println("Summary of the test: \n" + summary);
        assertTrue("There should be no difference greater than " + numOfClicks + ", but the output was: \n" + summary, freeFromLeak);
    }

    private long getOpenFdCount() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof UnixOperatingSystemMXBean) {
            return ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount();
        }
        return -1;
    }

    @Issue("SECURITY-95")
    @Test
    public void contentSecurityPolicy() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new SingleFileSCM("test.html", "<html><body><h1>Hello world!</h1></body></html>"));
        p.getPublishersList().add(new ArtifactArchiver("*", "", true));
        j.buildAndAssertSuccess(p);

        HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/test.html");
        for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
            assertEquals("Header set: " + header, DirectoryBrowserSupport.DEFAULT_CSP_VALUE, page.getWebResponse().getResponseHeaderValue(header));
        }

        String propName = DirectoryBrowserSupport.class.getName() + ".CSP";
        String initialValue = System.getProperty(propName);
        try {
            System.setProperty(propName, "");
            page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/artifact/test.html");
            List<String> headers = page.getWebResponse().getResponseHeaders().stream().map(NameValuePair::getName).collect(Collectors.toList());
            for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
                assertThat(headers, not(hasItem(header)));
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
        Page download = page.getAnchorByText("f").click();
        assertEquals("Hello world!", download.getWebResponse().getContentAsString());
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

        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws Exception {
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
                byte[] data = is.readAllBytes();
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
                public boolean isDirectory() {
                    return false;
                }

                @Override
                public boolean isFile() {
                    return true;
                }

                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public VirtualFile[] list() {
                    return new VirtualFile[0];
                }

                @Override
                public Collection<String> list(@NonNull String includes, String excludes, boolean useDefaultExcludes) {
                    return Collections.emptySet();
                }

                @NonNull
                @Override
                public VirtualFile child(@NonNull String name) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public long length() {
                    return 0;
                }

                @Override
                public long lastModified() {
                    return 0;
                }

                @Override
                public boolean canRead() {
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
                @NonNull
                @Override
                public String getName() {
                    return "";
                }

                @NonNull
                @Override
                public URI toURI() {
                    return URI.create("root:");
                }

                @Override
                public VirtualFile getParent() {
                    return this;
                }

                @Override
                public boolean isDirectory() {
                    return true;
                }

                @Override
                public boolean isFile() {
                    return false;
                }

                @Override
                public boolean exists() {
                    return true;
                }

                @NonNull
                @Override
                public VirtualFile[] list() {
                    return new VirtualFile[] {file};
                }

                @NonNull
                @Override
                public Collection<String> list(@NonNull String includes, String excludes, boolean useDefaultExcludes) {
                    throw new UnsupportedOperationException();
                }

                @NonNull
                @Override
                public VirtualFile child(@NonNull String name) {
                    if (name.equals("f")) {
                        return file;
                    } else if (name.isEmpty()) {
                        return this;
                    } else {
                        throw new UnsupportedOperationException("trying to call child on " + name);
                    }
                }

                @Override
                public long length() {
                    return 0;
                }

                @Override
                public long lastModified() {
                    return 0;
                }

                @Override
                public boolean canRead() {
                    return true;
                }

                @Override
                public InputStream open() throws IOException {
                    throw new FileNotFoundException();
                }
            };
        }

        @Override
        public void onLoad(@NonNull Run<?, ?> build) {}

        @Override
        public boolean delete() {
            return false;
        }
    }

    @Test
    @Issue("SECURITY-904")
    public void symlink_outsideWorkspace_areNotAllowed() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        File secretsFolder = new File(j.jenkins.getRootDir(), "secrets");
        File secretTarget = new File(secretsFolder, "goal.txt");
        String secretContent = "secret";
        Files.writeString(secretTarget.toPath(), secretContent, StandardCharsets.UTF_8);

        /*
         *  secrets/
         *      goal.txt
         *  workspace/
         *      intermediateFolder/
         *          public2.key
         *          otherFolder/
         *              to_secret3 -> ../../../../secrets/
         *          to_secret2 -> ../../../secrets/
         *          to_secret_goal2 -> ../../../secrets/goal.txt
         *      public1.key
         *      to_secret1 -> ../../secrets/
         *      to_secret_goal1 -> ../../secrets/goal.txt
         *
         */
        if (Functions.isWindows()) {
            // no need to test mklink /H since we cannot create an hard link to a non-existing file
            // and so you need to have access to the master file system directly which is already a problem

            String script = loadContentFromResource("outsideWorkspaceStructure.bat");
            p.getBuildersList().add(new BatchFile(script));
        } else {
            String script = loadContentFromResource("outsideWorkspaceStructure.sh");
            p.getBuildersList().add(new Shell(script));
        }

        j.buildAndAssertSuccess(p);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        { // workspace root must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("public1.key"),
                    containsString("intermediateFolder"),
                    not(containsString("to_secrets1")),
                    not(containsString("to_secrets_goal1")),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets_goal2"))
            ));
        }
        { // to_secrets1 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_secrets_goal1 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets_goal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // intermediateFolder must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    not(containsString("to_secrets1")),
                    not(containsString("to_secrets_goal1")),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets_goal2"))
            ));
        }
        { // to_secrets2 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // using symbolic in the intermediate path
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2/master.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_secrets_goal2 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets_goal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }

        // pattern search feature
        { // the pattern allow us to search inside the files / folders,
            // without the patch the master.key from inside the outside symlinks would have been linked
            Page page = wc.goTo(p.getUrl() + "ws/**/*.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    not(containsString("master.key")),
                    containsString("public1.key"),
                    containsString("public2.key")
            ));
        }

        // zip feature
        { // all the outside folders / files are not included in the zip, also the parent folder is included
            Page zipPage = wc.goTo(p.getUrl() + "ws/*zip*/ws.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, containsInAnyOrder(
                    p.getName() + "/intermediateFolder/public2.key",
                    p.getName() + "/public1.key"
            ));
        }
        { // workaround for JENKINS-19947 is still supported, i.e. no parent folder
            Page zipPage = wc.goTo(p.getUrl() + "ws/**/*zip*/ws.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, containsInAnyOrder(
                    "intermediateFolder/public2.key",
                    "public1.key"
            ));
        }
        { // all the outside folders / files are not included in the zip
            Page zipPage = wc.goTo(p.getUrl() + "ws/intermediateFolder/*zip*/intermediateFolder.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, contains("intermediateFolder/public2.key"));
        }
        { // workaround for JENKINS-19947 is still supported, i.e. no parent folder, even inside a sub-folder
            Page zipPage = wc.goTo(p.getUrl() + "ws/intermediateFolder/**/*zip*/intermediateFolder.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, contains("public2.key"));
        }
    }

    /*
     * If the glob filter is used, we do not want that it leaks some information.
     * Presence of a folder means that the folder contains one or multiple results, so we need to hide it completely
     */
    @Test
    @Issue("SECURITY-904")
    public void symlink_avoidLeakingInformation_aboutIllegalFolder() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        File secretsFolder = new File(j.jenkins.getRootDir(), "secrets");
        File secretTarget = new File(secretsFolder, "goal.txt");
        String secretContent = "secret";
        Files.writeString(secretTarget.toPath(), secretContent, StandardCharsets.UTF_8);
        Files.writeString(secretsFolder.toPath().resolve("public_fake1.key"), secretContent, StandardCharsets.UTF_8);
        Files.writeString(secretsFolder.toPath().resolve("public_fake2.key"), secretContent, StandardCharsets.UTF_8);
        Files.writeString(secretsFolder.toPath().resolve("public_fake3.key"), secretContent, StandardCharsets.UTF_8);

        /*
         *  secrets/
         *      goal.txt
         *      public_fake1.key
         *      public_fake2.key
         *      public_fake3.key
         *  workspace/
         *      intermediateFolder/
         *          public2.key
         *          otherFolder/
         *              to_secret3 -> ../../../../secrets/
         *          to_secret2 -> ../../../secrets/
         *          to_secret_goal2 -> ../../../secrets/goal.txt
         *      public1.key
         *      to_secret1 -> ../../secrets/
         *      to_secret_goal1 -> ../../secrets/goal.txt
         *
         */
        if (Functions.isWindows()) {
            // no need to test mklink /H since we cannot create an hard link to a non-existing file
            // and so you need to have access to the master file system directly which is already a problem

            String script = loadContentFromResource("outsideWorkspaceStructure.bat");
            p.getBuildersList().add(new BatchFile(script));
        } else {
            String script = loadContentFromResource("outsideWorkspaceStructure.sh");
            p.getBuildersList().add(new Shell(script));
        }

        j.buildAndAssertSuccess(p);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // the pattern allow us to search inside the files / folders,
        // but it should not provide / leak information about non readable folders

        { // without the patch the otherFolder and to_secrets[1,2,3] will appear in the results (once)
            Page page = wc.goTo(p.getUrl() + "ws/**/goal.txt", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // without the patch the otherFolder and to_secrets[1,2,3] will appear in the results (3 times each)
            Page page = wc.goTo(p.getUrl() + "ws/**/public*.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("public1.key"),
                    containsString("public2.key"),
                    // those following presences would have leak information that there is some file satisfying that pattern inside
                    not(containsString("otherFolder")),
                    not(containsString("to_secrets")),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets3"))
            ));
        }
    }

    // The hard links (mklink /H) to file are impossible to be detected and will allow a user to retrieve any file in the system
    // to achieve that they should already have access to the system or the Script Console.
    @Test
    @Issue("SECURITY-904")
    public void junctionAndSymlink_outsideWorkspace_areNotAllowed_windowsJunction() throws Exception {
        Assume.assumeTrue(Functions.isWindows());

        FreeStyleProject p = j.createFreeStyleProject();

        File secretsFolder = new File(j.jenkins.getRootDir(), "secrets");
        File secretTarget = new File(secretsFolder, "goal.txt");
        String secretContent = "secret";
        Files.writeString(secretTarget.toPath(), secretContent, StandardCharsets.UTF_8);

        /*
         *  secrets/
         *      goal.txt
         *  workspace/
         *      intermediateFolder/
         *          public2.key
         *          otherFolder/
         *              to_secret3s -> symlink ../../../../secrets/
         *              to_secret3j -> junction ../../../../secrets/
         *          to_secret2s -> symlink ../../../secrets/
         *          to_secret2j -> junction ../../../secrets/
         *          to_secret_goal2 -> symlink ../../../secrets/goal.txt
         *      public1.key
         *      to_secret1s -> symlink ../../secrets/
         *      to_secret1j -> junction ../../secrets/
         *      to_secret_goal1 -> symlink ../../secrets/goal.txt
         *
         */
        String script = loadContentFromResource("outsideWorkspaceStructureWithJunctions.bat");
        p.getBuildersList().add(new BatchFile(script));

        j.buildAndAssertSuccess(p);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        { // workspace root must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("public1.key"),
                    containsString("intermediateFolder"),
                    not(containsString("to_secrets1j")),
                    not(containsString("to_secrets1s")),
                    not(containsString("to_secrets_goal1")),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets_goal2"))
            ));
        }
        { // to_secrets1s not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets1s/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_secrets1j not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets1j/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_secrets_goal1 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets_goal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // intermediateFolder must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    not(containsString("to_secrets1")),
                    not(containsString("to_secrets_goal1")),
                    not(containsString("to_secrets2s")),
                    not(containsString("to_secrets2j")),
                    not(containsString("to_secrets_goal2"))
            ));
        }
        { // to_secrets2s not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2s/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_secrets2j not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2j/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // using symbolic in the intermediate path
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2s/master.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // using symbolic in the intermediate path
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2j/master.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_secrets_goal2 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets_goal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }

        // pattern search feature
        { // the pattern allow us to search inside the files / folders,
            // without the patch the master.key from inside the outside symlinks would have been linked
            Page page = wc.goTo(p.getUrl() + "ws/**/*.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    not(containsString("master.key")),
                    containsString("public1.key"),
                    containsString("public2.key"),
                    containsString("intermediateFolder"),
                    not(containsString("otherFolder")),
                    not(containsString("to_secrets3j")),
                    not(containsString("to_secrets3s")),
                    not(containsString("to_secrets2j")),
                    not(containsString("to_secrets2s")),
                    not(containsString("to_secrets1j")),
                    not(containsString("to_secrets1s"))
            ));
        }

        // zip feature
        { // all the outside folders / files are not included in the zip
            Page zipPage = wc.goTo(p.getUrl() + "ws/*zip*/ws.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, containsInAnyOrder(
                    p.getName() + "/intermediateFolder/public2.key",
                    p.getName() + "/public1.key"
            ));
        }
        { // all the outside folders / files are not included in the zip
            Page zipPage = wc.goTo(p.getUrl() + "ws/intermediateFolder/*zip*/intermediateFolder.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, contains("intermediateFolder/public2.key"));
        }
        // Explicitly delete everything including junctions, which TemporaryDirectoryAllocator.dispose may have trouble with:
        new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds("cmd", "/c", "rmdir", "/s", "/q", j.jenkins.getRootDir().getAbsolutePath()).start().join();
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
    @Issue("SECURITY-904")
    public void directSymlink_forTestingZip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        j.buildAndAssertSuccess(p);
        FilePath ws = p.getSomeWorkspace();

        /*
         *  secrets/
         *      goal.txt
         *  workspace/
         *      /a1/to_secrets1
         *      /b1/b2/to_secrets1
         *      /c1/c2/c3/to_secrets1
         */
        File secretsFolder = new File(j.jenkins.getRootDir(), "secrets");
        FilePath a1 = ws.child("a1");
        a1.mkdirs();
        a1.child("to_secrets1").symlinkTo(secretsFolder.getAbsolutePath(), TaskListener.NULL);
        FilePath b2 = ws.child("b1").child("b2");
        b2.mkdirs();
        b2.child("to_secrets2").symlinkTo(secretsFolder.getAbsolutePath(), TaskListener.NULL);
        FilePath c3 = ws.child("c1").child("c2").child("c3");
        c3.mkdirs();
        c3.child("to_secrets3").symlinkTo(secretsFolder.getAbsolutePath(), TaskListener.NULL);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        {
            Page zipPage = wc.goTo(p.getUrl() + "ws/*zip*/ws.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, hasSize(0));
        }
        {
            Page zipPage = wc.goTo(p.getUrl() + "ws/a1/*zip*/a1.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, hasSize(0));
        }
        {
            Page zipPage = wc.goTo(p.getUrl() + "ws/b1/b2/*zip*/b2.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, hasSize(0));
        }
        {
            Page zipPage = wc.goTo(p.getUrl() + "ws/c1/c2/c3/*zip*/c3.zip", null);
            assertThat(zipPage.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));

            List<String> entryNames = getListOfEntriesInDownloadedZip((UnexpectedPage) zipPage);
            assertThat(entryNames, hasSize(0));
        }
    }

    @Test
    @Issue({"SECURITY-904", "SECURITY-1452"})
    public void symlink_insideWorkspace_areNotAllowedAnymore() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        // build once to have the workspace set up
        j.buildAndAssertSuccess(p);

        File jobWorkspaceFolder = new File(new File(j.jenkins.getRootDir(), "workspace"), p.name);
        File folderInsideWorkspace = new File(jobWorkspaceFolder, "asset");
        folderInsideWorkspace.mkdir();
        File fileTarget = new File(folderInsideWorkspace, "goal.txt");
        String publicContent = "not-secret";
        Files.writeString(fileTarget.toPath(), publicContent, StandardCharsets.UTF_8);

        /*
         *  workspace/
         *      asset/
         *          goal.txt
         *      intermediateFolder/
         *          to_internal2 -> ../asset
         *          to_internal_goal2 -> ../asset/goal.txt
         *      to_internal1 -> ./asset/
         *      to_internal_goal1 -> ./asset/goal.txt
         */
        if (Functions.isWindows()) {
            String script = loadContentFromResource("insideWorkspaceStructure.bat");
            p.getBuildersList().add(new BatchFile(script));
        } else {
            String script = loadContentFromResource("insideWorkspaceStructure.sh");
            p.getBuildersList().add(new Shell(script));
        }

        j.buildAndAssertSuccess(p);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        { // workspace root must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("asset"),
                    not(containsString("to_internal1")),
                    not(containsString("to_internal_goal1")),
                    containsString("intermediateFolder"),
                    not(containsString("to_internal2")),
                    not(containsString("to_internal_goal2")
                    )));
        }
        { // to_internal1 reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_internal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_internal_goal1 reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_internal_goal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_internal2 reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_internal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // to_internal_goal2 reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_internal_goal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_NOT_FOUND));
        }
        { // direct to goal
            Page page = wc.goTo(p.getUrl() + "ws/asset/goal.txt/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, containsString(publicContent));
        }
        { // the zip will only contain folder from inside the workspace
            Page page = wc.goTo(p.getUrl() + "ws/*zip*/ws.zip", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
        }
        { // the zip will only contain folder from inside the workspace
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/*zip*/intermediateFolder.zip", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
        }
        { // the zip will only contain folder from inside the workspace
            Page page = wc.goTo(p.getUrl() + "ws/asset/*zip*/asset.zip", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
        }
    }

    private String loadContentFromResource(String fileNameInResources) throws IOException, URISyntaxException {
        URL resourceUrl = DirectoryBrowserSupportTest.class.getResource(DirectoryBrowserSupportTest.class.getSimpleName() + "/" + fileNameInResources);
        if (resourceUrl == null) {
            fail("The resource with fileName " + fileNameInResources + " is not present in the resources of the test");
        }
        Path resourcePath = Paths.get(resourceUrl.toURI());
        return Files.readString(resourcePath, StandardCharsets.UTF_8);
    }

    @Test
    @Issue("SECURITY-2481")
    public void windows_cannotViewAbsolutePath() throws Exception {
        Assume.assumeTrue("can only be tested this on Windows", Functions.isWindows());

        Path targetTmpPath = Files.createTempFile("sec2481", "tmp");
        String content = "random data provided as fixed value";
        Files.writeString(targetTmpPath, content, StandardCharsets.UTF_8);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // suspicious path is rejected with 400
            wc.setThrowExceptionOnFailingStatusCode(false);
            HtmlPage page = wc.goTo("userContent/" + targetTmpPath.toAbsolutePath() + "/*view*");
            assertEquals(200, page.getWebResponse().getStatusCode());
        }
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

    @Test
    public void canViewRelativePath() throws Exception {
        File testFile = new File(j.jenkins.getRootDir(), "userContent/test.txt");
        String content = "random data provided as fixed value";

        Files.writeString(testFile.toPath(), content, StandardCharsets.UTF_8);

        JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        Page page = wc.goTo("userContent/test.txt/*view*", null);

        MatcherAssert.assertThat(page.getWebResponse().getStatusCode(), equalTo(200));
        MatcherAssert.assertThat(page.getWebResponse().getContentAsString(), containsString(content));
    }

    public static final class SimulatedExternalArtifactManagerFactory extends ArtifactManagerFactory {
        @Override
        public ArtifactManager managerFor(Run<?, ?> build) {
            return new SimulatedExternalArtifactManager();
        }

        @TestExtension("externalURLDownload")
        public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {}
    }

    private static final class SimulatedExternalArtifactManager extends ArtifactManager {
        String hash;

        @Override
        public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts) throws IOException, InterruptedException {
            assertEquals(1, artifacts.size());
            Map.Entry<String, String> entry = artifacts.entrySet().iterator().next();
            assertEquals("f", entry.getKey());
            try (InputStream is = workspace.child(entry.getValue()).read()) {
                byte[] data = is.readAllBytes();
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
                public boolean isDirectory() {
                    return false;
                }

                @Override
                public boolean isFile() {
                    return true;
                }

                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public VirtualFile[] list() {
                    return new VirtualFile[0];
                }

                @Override
                public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) {
                    return Collections.emptySet();
                }

                @Override
                public VirtualFile child(String name) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public long length() {
                    return 0;
                }

                @Override
                public long lastModified() {
                    return 0;
                }

                @Override
                public boolean canRead() {
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
                public boolean isDirectory() {
                    return true;
                }

                @Override
                public boolean isFile() {
                    return false;
                }

                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public VirtualFile[] list() {
                    return new VirtualFile[] {file};
                }

                @Override
                public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) {
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
                public long length() {
                    return 0;
                }

                @Override
                public long lastModified() {
                    return 0;
                }

                @Override
                public boolean canRead() {
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
        public boolean delete() {
            return false;
        }
    }

}
