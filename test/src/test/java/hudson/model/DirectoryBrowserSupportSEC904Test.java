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

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import hudson.FilePath;
import hudson.Functions;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class DirectoryBrowserSupportSEC904Test {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @Issue("SECURITY-904")
    public void symlink_outsideWorkspace_areNotAllowed() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        File secretsFolder = new File(j.jenkins.getRootDir(), "secrets");
        File secretTarget = new File(secretsFolder, "goal.txt");
        String secretContent = "secret";
        FileUtils.write(secretTarget, secretContent);
        
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
        
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        { // workspace root must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("public1.key"),
                    containsString("intermediateFolder"),
                    containsString("to_secrets1"),
                    containsString("to_secrets_goal1"),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets_goal2"))
            ));
        }
        { // to_secrets1 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // to_secrets_goal1 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets_goal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // intermediateFolder must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    not(containsString("to_secrets1")),
                    not(containsString("to_secrets_goal1")),
                    containsString("to_secrets2"),
                    containsString("to_secrets_goal2")
            ));
        }
        { // to_secrets2 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // using symbolic in the intermediate path
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2/master.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // to_secrets_goal2 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets_goal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
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
        FileUtils.write(secretTarget, secretContent);
        FileUtils.write(new File(secretsFolder, "public_fake1.key"), secretContent);
        FileUtils.write(new File(secretsFolder, "public_fake2.key"), secretContent);
        FileUtils.write(new File(secretsFolder, "public_fake3.key"), secretContent);

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
    
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
    
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
    
        // the pattern allow us to search inside the files / folders, 
        // but it should not provide / leak information about non readable folders

        { // without the patch the otherFolder and to_secrets[1,2,3] will appear in the results (once)
            Page page = wc.goTo(p.getUrl() + "ws/**/goal.txt", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    // really not satisfying the query
                    not(containsString("public1.key")),
                    not(containsString("public2.key")),
                    // those following presences would have leak information that there is some file satisfying that pattern inside
                    not(containsString("to_secrets")),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets3"))
            ));
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
        FileUtils.write(secretTarget, secretContent);
        
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
        
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        { // workspace root must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("public1.key"),
                    containsString("intermediateFolder"),
                    containsString("to_secrets1j"),
                    containsString("to_secrets1s"),
                    containsString("to_secrets_goal1"),
                    not(containsString("to_secrets2")),
                    not(containsString("to_secrets_goal2"))
            ));
        }
        { // to_secrets1s not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets1s/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // to_secrets1j not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets1j/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // to_secrets_goal1 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_secrets_goal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // intermediateFolder must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    not(containsString("to_secrets1")),
                    not(containsString("to_secrets_goal1")),
                    containsString("to_secrets2s"),
                    containsString("to_secrets2j"),
                    containsString("to_secrets_goal2")
            ));
        }
        { // to_secrets2s not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2s/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // to_secrets2j not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2j/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // using symbolic in the intermediate path
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2s/master.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // using symbolic in the intermediate path
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets2j/master.key", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
        }
        { // to_secrets_goal2 not reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_secrets_goal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_FORBIDDEN));
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
    
    //TODO taken from existing test without modification to ease merge back
    private File download(UnexpectedPage page) throws IOException {
        File file = File.createTempFile("DirectoryBrowserSupport", "zipDownload");
        file.delete();
        try (InputStream is = page.getInputStream();
             OutputStream os = Files.newOutputStream(file.toPath())) {
            IOUtils.copy(is, os);
        }
        
        return file;
    }
    
    @Test
    @Issue("SECURITY-904")
    public void directSymlink_forTestingZip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
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
    @Issue("SECURITY-904")
    public void symlink_insideWorkspace_areStillAllowed() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        // build once to have the workspace set up
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        
        File jobWorkspaceFolder = new File(new File(j.jenkins.getRootDir(), "workspace"), p.name);
        File folderInsideWorkspace = new File(jobWorkspaceFolder, "asset");
        folderInsideWorkspace.mkdir();
        File fileTarget = new File(folderInsideWorkspace, "goal.txt");
        String publicContent = "not-secret";
        FileUtils.write(fileTarget, publicContent);
        
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
        
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        { // workspace root must be reachable (regular case)
            Page page = wc.goTo(p.getUrl() + "ws/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, allOf(
                    containsString("asset"),
                    containsString("to_internal1"),
                    containsString("to_internal_goal1"),
                    containsString("intermediateFolder"),
                    not(containsString("to_internal2")),
                    not(containsString("to_internal_goal2")
            )));
        }
        { // to_internal1 reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_internal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, containsString("goal.txt"));
        }
        { // to_internal_goal1 reachable
            Page page = wc.goTo(p.getUrl() + "ws/to_internal_goal1/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, containsString(publicContent));
        }
        { // to_internal2 reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_internal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, containsString("goal.txt"));
        }
        { // to_internal_goal2 reachable
            Page page = wc.goTo(p.getUrl() + "ws/intermediateFolder/to_internal_goal2/", null);
            assertThat(page.getWebResponse().getStatusCode(), equalTo(HttpURLConnection.HTTP_OK));
            String workspaceContent = page.getWebResponse().getContentAsString();
            assertThat(workspaceContent, containsString(publicContent));
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
        URL resourceUrl = DirectoryBrowserSupportSEC904Test.class.getResource(DirectoryBrowserSupportSEC904Test.class.getSimpleName() + "/" + fileNameInResources);
        if (resourceUrl == null) {
            fail("The resource with fileName " + fileNameInResources + " is not present in the resources of the test");
        }
        File resourceFile = new File(resourceUrl.toURI());
        return FileUtils.readFileToString(resourceFile);
    }
}
