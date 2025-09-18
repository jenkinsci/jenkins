/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class FileParameterValueTest {

    @TempDir
    private File tmp;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private JenkinsRule.WebClient getWebClient() {
        var wc = j.createWebClient();
        wc.getOptions().setJavaScriptEnabled(false);
        return wc;
    }

    @Test
    @Issue("SECURITY-1074")
    void fileParameter_cannotCreateFile_outsideOfBuildFolder() throws Exception {
        // you can test the behavior before the correction by setting FileParameterValue.ALLOW_FOLDER_TRAVERSAL_OUTSIDE_WORKSPACE to true

        FilePath root = j.jenkins.getRootPath();

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("../../../../../root-level.txt", null)
        )));

        assertThat(root.child("root-level.txt").exists(), equalTo(false));

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("../../../../../root-level.txt", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));
        assertThat(root.child("root-level.txt").exists(), equalTo(false));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/..%2F..%2F..%2F..%2F..%2Froot-level.txt/uploaded-file.txt", uploadedContent);
        // encoding dots
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/%2E%2E%2F%2E%2E%2F%2E%2E%2F%2E%2E%2F%2E%2E%2Froot-level.txt/uploaded-file.txt", uploadedContent);
        // 16-bit encoding
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/%u002e%u002e%u2215%u002e%u002e%u2215%u002e%u002e%u2215%u002e%u002e%u2215%u002e%u002e%u2215root-level.txt/uploaded-file.txt", uploadedContent);
        // double encoding
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/%252e%252e%252f%252e%252e%252f%252e%252e%252f%252e%252e%252f%252e%252e%252froot-level.txt/uploaded-file.txt", uploadedContent);
        // overlong utf-8 encoding
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/%c0%2e%c0%2e%c0%af%c0%2e%c0%2e%c0%af%c0%2e%c0%2e%c0%af%c0%2e%c0%2e%c0%af%c0%2e%c0%2e%c0%afroot-level.txt/uploaded-file.txt", uploadedContent);
    }

    @Test
    @Issue("SECURITY-1424")
    void fileParameter_cannotCreateFile_outsideOfBuildFolder_SEC1424() throws Exception {
        // you can test the behavior before the correction by setting FileParameterValue.ALLOW_FOLDER_TRAVERSAL_OUTSIDE_WORKSPACE to true

        FilePath root = j.jenkins.getRootPath();

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("dir/../../../pwned", null)
        )));

        assertThat(root.child("pwned").exists(), equalTo(false));

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("dir/../../../pwned", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));
        assertThat(root.child("pwned").exists(), equalTo(false));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
    }

    @Test
    void fileParameter_cannotCreateFile_outsideOfBuildFolder_LeadingDoubleDot() throws Exception {
        FilePath root = j.jenkins.getRootPath();

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("../pwned", null)
        )));

        assertThat(root.child("pwned").exists(), equalTo(false));

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("../pwned", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));
        assertThat(root.child("pwned").exists(), equalTo(false));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
    }

    private void checkUrlNot200AndNotContains(JenkinsRule.WebClient wc, String url, String contentNotPresent) throws Exception {
        Page pageForEncoded = wc.goTo(url, null);
        assertThat(pageForEncoded.getWebResponse().getStatusCode(), not(equalTo(200)));
        assertThat(pageForEncoded.getWebResponse().getContentAsString(), not(containsString(contentNotPresent)));
    }

    @Test
    @Issue("SECURITY-1074")
    void fileParameter_cannotCreateFile_outsideOfBuildFolder_backslashEdition() throws Exception {
        Assumptions.assumeTrue(Functions.isWindows(), "Backslashes are only dangerous on Windows");

        // you can test the behavior before the correction by setting FileParameterValue.ALLOW_FOLDER_TRAVERSAL_OUTSIDE_WORKSPACE to true

        FilePath root = j.jenkins.getRootPath();

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("..\\..\\..\\..\\..\\root-level.txt", null)
        )));

        assertThat(root.child("root-level.txt").exists(), equalTo(false));

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("..\\..\\..\\..\\..\\root-level.txt", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));
        assertThat(root.child("root-level.txt").exists(), equalTo(false));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/..\\..\\..\\..\\..\\root-level.txt/uploaded-file.txt", uploadedContent);
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/..%2F..%2F..%2F..%2F..%2Froot-level.txt/uploaded-file.txt", uploadedContent);
    }

    @Test
    @Issue("SECURITY-1074")
    void fileParameter_withSingleDot() throws Exception {
        // this case was not working even before the patch

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition(".", null)
        )));

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue(".", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/uploaded-file.txt", uploadedContent);
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/./uploaded-file.txt", uploadedContent);
    }

    @Test
    @Issue("SECURITY-1074")
    void fileParameter_withDoubleDot() throws Exception {
        // this case was not working even before the patch

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("..", null)
        )));

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("..", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/uploaded-file.txt", uploadedContent);
        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/../uploaded-file.txt", uploadedContent);
    }

    @Test
    @Issue("SECURITY-1074")
    void fileParameter_cannotEraseFile_outsideOfBuildFolder() throws Exception {
        // you can test the behavior before the correction by setting FileParameterValue.ALLOW_FOLDER_TRAVERSAL_OUTSIDE_WORKSPACE to true

        FilePath root = j.jenkins.getRootPath();

        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("../../../../../root-level.txt", null)
        )));

        assertThat(root.child("root-level.txt").exists(), equalTo(false));
        String initialContent = "do-not-erase-me";
        root.child("root-level.txt").write(initialContent, StandardCharsets.UTF_8.name());

        String uploadedContent = "test-content";
        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), uploadedContent, StandardCharsets.UTF_8);

        FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("../../../../../root-level.txt", uploadedFile, "uploaded-file.txt")
        )).get();

        assertThat(build.getResult(), equalTo(Result.FAILURE));
        assertThat(root.child("root-level.txt").readToString(), equalTo(initialContent));

        // ensure also the file is not reachable by request
        JenkinsRule.WebClient wc = getWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        checkUrlNot200AndNotContains(wc, build.getUrl() + "parameters/parameter/..%2F..%2F..%2F..%2F..%2Froot-level.txt/uploaded-file.txt", uploadedContent);
    }

    @Test
    void fileParameter_canStillUse_internalHierarchy() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(Arrays.asList(
                new FileParameterDefinition("direct-child1.txt", null),
                new FileParameterDefinition("parent/child2.txt", null)
        )));

        File uploadedFile1 = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile1.toPath(), "test1", StandardCharsets.UTF_8);
        File uploadedFile2 = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile2.toPath(), "test2", StandardCharsets.UTF_8);

        FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("direct-child1.txt", uploadedFile1, "uploaded-file-1.txt"),
                new FileParameterValue("parent/child2.txt", uploadedFile2, "uploaded-file-2.txt")
        )));

        // files are correctly saved in the build "fileParameters" folder
        File directChild = new File(build.getRootDir(), "fileParameters/" + "direct-child1.txt");
        assertTrue(directChild.exists());

        File parentChild = new File(build.getRootDir(), "fileParameters/" + "parent/child2.txt");
        assertTrue(parentChild.exists());

        // both are correctly copied inside the workspace
        assertTrue(build.getWorkspace().child("direct-child1.txt").exists());
        assertTrue(build.getWorkspace().child("parent").child("child2.txt").exists());

        // and reachable using request
        JenkinsRule.WebClient wc = getWebClient();
        HtmlPage workspacePage = wc.goTo(p.getUrl() + "ws");
        String workspaceContent = workspacePage.getWebResponse().getContentAsString();
        assertThat(workspaceContent, allOf(
                containsString("direct-child1.txt"),
                containsString("parent")
        ));
        HtmlPage workspaceParentPage = wc.goTo(p.getUrl() + "ws" + "/parent");
        String workspaceParentContent = workspaceParentPage.getWebResponse().getContentAsString();
        assertThat(workspaceParentContent, containsString("child2.txt"));
    }

    @Test
    void fileParameter_canStillUse_doubleDotsInFileName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("weird..name.txt", null)
        )));

        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), "test1", StandardCharsets.UTF_8);

        FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("weird..name.txt", uploadedFile, "uploaded-file.txt")
        )));

        // files are correctly saved in the build "fileParameters" folder
        File directChild = new File(build.getRootDir(), "fileParameters/weird..name.txt");
        assertTrue(directChild.exists());

        // both are correctly copied inside the workspace
        assertTrue(build.getWorkspace().child("weird..name.txt").exists());

        // and reachable using request
        JenkinsRule.WebClient wc = getWebClient();
        HtmlPage workspacePage = wc.goTo(p.getUrl() + "ws");
        String workspaceContent = workspacePage.getWebResponse().getContentAsString();
        assertThat(workspaceContent, containsString("weird..name.txt"));
    }

    @Test
    void fileParameter_canStillUse_TildeInFileName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("~name", null)
        )));

        File uploadedFile = File.createTempFile("junit", null, tmp);
        Files.writeString(uploadedFile.toPath(), "test1", StandardCharsets.UTF_8);

        FreeStyleBuild build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new FileParameterValue("~name", uploadedFile, "uploaded-file.txt")
        )));

        // files are correctly saved in the build "fileParameters" folder
        File directChild = new File(build.getRootDir(), "fileParameters/~name");
        assertTrue(directChild.exists());

        // both are correctly copied inside the workspace
        assertTrue(build.getWorkspace().child("~name").exists());

        // and reachable using request
        JenkinsRule.WebClient wc = getWebClient();
        HtmlPage workspacePage = wc.goTo(p.getUrl() + "ws");
        String workspaceContent = workspacePage.getWebResponse().getContentAsString();
        assertThat(workspaceContent, containsString("~name"));
    }

    @Issue("SECURITY-1793")
    @Test
    @LocalData
    void contentSecurityPolicy() throws Exception {
        FreeStyleProject p = j.jenkins.getItemByFullName("SECURITY-1793", FreeStyleProject.class);

        var wc = getWebClient();
        HtmlPage page = wc.goTo("job/" + p.getName() + "/lastSuccessfulBuild/parameters/parameter/html.html/html.html");
        for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
            assertEquals(DirectoryBrowserSupport.DEFAULT_CSP_VALUE, page.getWebResponse().getResponseHeaderValue(header), "Header set: " + header);
        }

        String propName = DirectoryBrowserSupport.class.getName() + ".CSP";
        String initialValue = System.getProperty(propName);
        try {
            System.setProperty(propName, "");
            page = wc.goTo("job/" + p.getName() + "/lastSuccessfulBuild/parameters/parameter/html.html/html.html");
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

    @Issue("JENKINS-19017")
    @Test
    void compareParamsWithSameName() throws IOException {
        final String paramName = "MY_FILE_PARAM"; // Same paramName (location) reproduces the bug
        File ws_param1 = createParamFile("ws_param1.txt");
        File ws_param2 = createParamFile("ws_param2.txt");

        final FileParameterValue param1 = new FileParameterValue(paramName, ws_param1, "param1.txt");
        final FileParameterValue param2 = new FileParameterValue(paramName, ws_param2, "param2.txt");

        assertNotEquals(param1, param2, "Files with same locations should be considered as different");
        assertNotEquals(param2, param1, "Files with same locations should be considered as different");
    }

    @Test
    void compareNullParams() throws IOException {
        final String paramName = "MY_FILE_PARAM";
        File ws_param1 = createParamFile("ws_param1.txt");
        File null_param1 = createParamFile("null_param1.txt");
        File null_param2 = createParamFile("null_param2.txt");
        FileParameterValue nonNullParam = new FileParameterValue(paramName, ws_param1, "param1.txt");
        FileParameterValue nullParam1 = new FileParameterValue(null, null_param1, "null_param1.txt");
        FileParameterValue nullParam2 = new FileParameterValue(null, null_param2, "null_param2.txt");

        // Combine nulls
        assertEquals(nullParam1, nullParam1);
        assertEquals(nullParam1, nullParam2);
        assertEquals(nullParam2, nullParam1);
        assertEquals(nullParam2, nullParam2);

        // Compare with non-null
        assertNotEquals(nullParam1, nonNullParam);
        assertNotEquals(nonNullParam, nullParam1);
    }

    private File createParamFile(String fileName) {
        File f = new File(tmp, fileName);
        try {
            FileUtils.writeStringToFile(f, "content", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return f;
    }
}
