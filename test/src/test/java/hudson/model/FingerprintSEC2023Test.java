package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Fingerprinter;
import jenkins.fingerprints.FileFingerprintStorage;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FingerprintSEC2023Test {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    @Test
    public void checkNormalFingerprint() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        Fingerprint loadedFingerprint = Fingerprint.load(fp.getHashString());
        assertEquals(fp.getDisplayName(), loadedFingerprint.getDisplayName());
    }

    @Test
    public void checkNormalFingerprintWithWebClient() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");

        Page page = rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "fingerprint/" + fp.getHashString() + "/")));
        assertEquals(200, page.getWebResponse().getStatusCode());

        // could also be reached using static/<anything>/
        Page page2 = rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + fp.getHashString() + "/")));
        assertEquals(200, page2.getWebResponse().getStatusCode());
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryEmptyFileExistence() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf1.xml");
        Util.touch(targetFile);
        // required as cf1.xml is outside the temporary folders created for the test
        // and if the test is failing, it will not be deleted
        targetFile.deleteOnExit();
        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);
        String id = first + second + "/../../" + first + "/" + second + "/../../../../cf1";
        Fingerprint fingerprint = Fingerprint.load(id);
        assertNull(fingerprint);
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryEmptyFileExistenceWithWebClient() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf2.xml");
        Util.touch(targetFile);
        targetFile.deleteOnExit();
        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);
        rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf2/")));
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryFileExistence() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File sourceFile = new File(rule.jenkins.getRootDir(), "config.xml");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf3.xml");
        Util.copyFile(sourceFile, targetFile);
        targetFile.deleteOnExit();
        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);
        String id = first + second + "/../../" + first + "/" + second + "/../../../../cf3";
        Fingerprint fingerprint = Fingerprint.load(id);
        assertNull(fingerprint);
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryFileExistenceWithWebClient() throws Exception {
        loggerRule.record(FileFingerprintStorage.class, Level.WARNING)
                .record(FileFingerprintStorage.class, Level.WARNING)
                .capture(1000);
        
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File sourceFile = new File(rule.jenkins.getRootDir(), "config.xml");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf4.xml");
        Util.copyFile(sourceFile, targetFile);
        targetFile.deleteOnExit();
        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);
        
        rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf4/")));
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryFileNonexistence() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);
        String id = first + second + "/../../" + first + "/" + second + "/../../../../cf5";
        Fingerprint fingerprint = Fingerprint.load(id);
        assertNull(fingerprint);
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryFileNonexistenceWithWebClient() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);
        rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf6/")));
    }

    @Test
    @Issue("SECURITY-2023")
    public void checkArbitraryFingerprintConfigFileExistenceWithWebClient() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf7.xml");
        FileUtils.writeStringToFile(targetFile, TEST_FINGERPRINT_CONFIG_FILE_CONTENT, StandardCharsets.UTF_8);
        targetFile.deleteOnExit();

        String first = fp.getHashString().substring(0,2);
        String second = fp.getHashString().substring(2,4);

        Page page = null;
        try {
            // that file exists, so we need to ensure if it's returned, the content is not the expected one from the test data.
            String partialUrl = "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf7/";
            page = rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), partialUrl)));
        } catch (FailingHttpStatusCodeException e) {
            // expected refusal after the correction
            assertEquals(500, e.getStatusCode());
        }
        if (page != null) {
            // content retrieval occurred before the correction, we have to check the content to ensure non-regression
            String pageContent = page.getWebResponse().getContentAsString();
            assertThat(pageContent, not(containsString(TEST_FINGERPRINT_ID)));
        }
        assertTrue(targetFile.exists());
    }

    @NonNull
    private Fingerprint getFingerprint(@CheckForNull Run<?, ?> run, @NonNull String filename) {
        assertNotNull("Input run is null", run);
        Fingerprinter.FingerprintAction action = run.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull("Fingerprint action has not been created in " + run, action);
        Map<String, Fingerprint> fingerprints = action.getFingerprints();
        final Fingerprint fp = fingerprints.get(filename);
        assertNotNull("No reference to '" + filename + "' from the Fingerprint action", fp);
        return fp;
    }

    private static final String TEST_FINGERPRINT_ID = "0123456789abcdef0123456789abcdef";
    private static final String TEST_FINGERPRINT_CONFIG_FILE_CONTENT = "<?xml version='1.1' encoding='UTF-8'?>\n" +
            "<fingerprint>\n" +
            "  <timestamp>2020-10-27 14:01:22.551 UTC</timestamp>\n" +
            "  <original>\n" +
            "    <name>test0</name>\n" +
            "    <number>1</number>\n" +
            "  </original>\n" +
            "  <md5sum>"+TEST_FINGERPRINT_ID+"</md5sum>\n" +
            "  <fileName>test.txt</fileName>\n" +
            "  <usages>\n" +
            "  </usages>\n" +
            "  <facets/>\n" +
            "</fingerprint>";
}
