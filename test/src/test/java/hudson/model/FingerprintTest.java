/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.XmlFile;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.security.SidACL;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Fingerprinter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import jenkins.fingerprints.FileFingerprintStorage;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.SecuredMockFolder;
import org.jvnet.hudson.test.WorkspaceCopyFileBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

//TODO: Refactoring: Tests should be exchanged with FingerprinterTest somehow
/**
 * Tests for the {@link Fingerprint} class.
 * @author Oleg Nenashev
 */
@WithJenkins
class FingerprintTest {

    private static final String SOME_MD5 = Util.getDigestOf("whatever");

    private final LogRecorder loggerRule = new LogRecorder();

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
    }

    @Test
    void roundTrip() throws Exception {
        Fingerprint f = new Fingerprint(new Fingerprint.BuildPtr("foo", 13), "stuff&more.jar",
                Util.fromHexString(SOME_MD5));
        f.addWithoutSaving("some", 1);
        f.addWithoutSaving("some", 2);
        f.addWithoutSaving("some", 3);
        f.addWithoutSaving("some", 10);
        f.addWithoutSaving("other", 6);
        f.save();
        Fingerprint f2 = Fingerprint.load(SOME_MD5);
        assertNotNull(f2);
        assertEquals(f.toString(), f2.toString());
        f.facets.setOwner(Saveable.NOOP);
        f.facets.add(new TestFacet(f, 123, "val"));
        f.save();
        f2 = Fingerprint.load(SOME_MD5);
        assertEquals(f.toString(), f2.toString());
        assertEquals(1, f2.facets.size());
        TestFacet facet = (TestFacet) f2.facets.get(0);
        assertEquals(f2, facet.getFingerprint());
    }

    public static final class TestFacet extends FingerprintFacet {
        final String property;

        TestFacet(Fingerprint fingerprint, long timestamp, String property) {
            super(fingerprint, timestamp);
            this.property = property;
        }

        @Override public String toString() {
            return "TestFacet[" + property + "@" + getTimestamp() + "]";
        }
    }


    @Test
    void shouldCreateFingerprintsForWorkspace() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        project.getPublishersList().add(new Fingerprinter("test.txt", false));
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
    }

    @Test
    void shouldCreateFingerprintsForArtifacts() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
    }

    @Test
    void shouldCreateUsageLinks() throws Exception {
        // Project 1
        FreeStyleProject project = createAndRunProjectWithPublisher("fpProducer", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();

        // Project 2
        FreeStyleProject project2 = rule.createFreeStyleProject();
        project2.getBuildersList().add(new WorkspaceCopyFileBuilder("test.txt", project.getName(), build.getNumber()));
        project2.getPublishersList().add(new Fingerprinter("test.txt"));
        FreeStyleBuild build2 = rule.buildAndAssertSuccess(project2);

        Fingerprint fp = getFingerprint(build, "test.txt");

        // Check references
        Fingerprint.BuildPtr original = fp.getOriginal();
        assertEquals(project.getName(), original.getName(), "Original reference contains a wrong job name");
        assertEquals(build.getNumber(), original.getNumber(), "Original reference contains a wrong build number");

        Hashtable<String, Fingerprint.RangeSet> usages = fp.getUsages();
        assertTrue(usages.containsKey(project.getName()), "Usages do not have a reference to " + project);
        assertTrue(usages.containsKey(project2.getName()), "Usages do not have a reference to " + project2);
    }

    @Test
    @Issue("JENKINS-51179")
    void shouldThrowIOExceptionWhenFileIsInvalid() throws Exception {
        XmlFile f = new XmlFile(new File(rule.jenkins.getRootDir(), "foo.xml"));
        f.write("Hello, world!");
        IOException e = assertThrows(IOException.class, () -> FileFingerprintStorage.load(f.getFile()));
        assertThat(e.getMessage(), containsString("Unexpected Fingerprint type"));
    }

    @Test
    @Issue("SECURITY-153")
    void shouldBeUnableToSeeJobsIfNoPermissions() throws Exception {
        // Project 1
        final FreeStyleProject project1 = createAndRunProjectWithPublisher("fpProducer", "test.txt");
        final FreeStyleBuild build = project1.getLastBuild();

        // Project 2
        final FreeStyleProject project2 = rule.createFreeStyleProject("project2");
        project2.getBuildersList().add(new WorkspaceCopyFileBuilder("test.txt", project1.getName(), build.getNumber()));
        project2.getPublishersList().add(new Fingerprinter("test.txt"));
        final FreeStyleBuild build2 = rule.buildAndAssertSuccess(project2);

        // Get fingerprint
        final Fingerprint fp = getFingerprint(build, "test.txt");

        // Init Users
        User user1 = User.getOrCreateByIdOrFullName("user1"); // can access project1
        User user2 = User.getOrCreateByIdOrFullName("user2"); // can access project2
        User user3 = User.getOrCreateByIdOrFullName("user3"); // cannot access anything

        // Project permissions
        setupProjectMatrixAuthStrategy(Jenkins.READ);
        setJobPermissionsOnce(project1, "user1", Item.READ, Item.DISCOVER);
        setJobPermissionsOnce(project2, "user2", Item.READ, Item.DISCOVER);

        try (ACLContext acl = ACL.as(user1)) {
            Fingerprint.BuildPtr original = fp.getOriginal();
            assertThat("user1 should be able to see the origin", fp.getOriginal(), notNullValue());
            assertEquals(project1.getName(), original.getName(), "user1 should be able to see the origin's project name");
            assertEquals(build.getNumber(), original.getNumber(), "user1 should be able to see the origin's build number");
            assertEquals(1, fp._getUsages().size(), "Only one usage should be visible to user1");
            assertEquals(project1.getFullName(), fp._getUsages().get(0).name, "Only project1 should be visible to user1");
        }

        try (ACLContext acl = ACL.as(user2)) {
            assertThat("user2 should be unable to see the origin", fp.getOriginal(), nullValue());
            assertEquals(1, fp._getUsages().size(), "Only one usage should be visible to user2");
            assertEquals(project2.getFullName(), fp._getUsages().get(0).name, "Only project2 should be visible to user2");
        }

        try (ACLContext acl = ACL.as(user3)) {
            Fingerprint.BuildPtr original = fp.getOriginal();
            assertThat("user3 should be unable to see the origin", fp.getOriginal(), nullValue());
            assertEquals(0, fp._getUsages().size(), "All usages should be invisible for user3");
        }
    }

    @Test
    void shouldBeAbleToSeeOriginalWithDiscoverPermissionOnly() throws Exception {
        // Setup the environment
        final FreeStyleProject project = createAndRunProjectWithPublisher("project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");

        // Init Users and security
        User user1 = User.get("user1");
        setupProjectMatrixAuthStrategy(Jenkins.READ, Item.DISCOVER);

        try (ACLContext acl = ACL.as(user1)) {
            Fingerprint.BuildPtr original = fingerprint.getOriginal();
            assertThat("user1 should able to see the origin", fingerprint.getOriginal(), notNullValue());
            assertEquals(project.getFullName(), original.getName(), "user1 sees the wrong original name with Item.DISCOVER");
            assertEquals(build.getNumber(), original.getNumber(), "user1 sees the wrong original number with Item.DISCOVER");
            assertEquals(1, fingerprint._getUsages().size(), "Usage ref in fingerprint should be visible to user1");
        }
    }

    @Test
    void shouldBeAbleToSeeFingerprintsInReadableFolder() throws Exception {
        final SecuredMockFolder folder = rule.jenkins.createProject(SecuredMockFolder.class, "folder");
        final FreeStyleProject project = createAndRunProjectWithPublisher(folder, "project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");

        // Init Users and security
        User user1 = User.getOrCreateByIdOrFullName("user1");
        setupProjectMatrixAuthStrategy(false, Jenkins.READ, Item.DISCOVER);
        setJobPermissionsOnce(project, "user1", Item.DISCOVER); // Prevents the fallback to the folder ACL
        folder.setPermissions("user1", Item.READ);

        // Ensure we can read the original from user account
        try (ACLContext acl = ACL.as(user1)) {
            assertTrue(folder.hasPermission(Item.READ), "Test framework issue: User1 should be able to read the folder");

            Fingerprint.BuildPtr original = fingerprint.getOriginal();
            assertThat("user1 should able to see the origin", fingerprint.getOriginal(), notNullValue());
            assertEquals(project.getFullName(), original.getName(), "user1 sees the wrong original name with Item.DISCOVER");
            assertEquals(build.getNumber(), original.getNumber(), "user1 sees the wrong original number with Item.DISCOVER");
            assertEquals(1, fingerprint._getUsages().size(), "user1 should be able to see the job");

            assertThat("User should be unable do retrieve the job due to the missing read", original.getJob(), nullValue());
        }
    }

    @Test
    void shouldBeUnableToSeeFingerprintsInUnreadableFolder() throws Exception {
        final SecuredMockFolder folder = rule.jenkins.createProject(SecuredMockFolder.class, "folder");
        final FreeStyleProject project = createAndRunProjectWithPublisher(folder, "project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");

        // Init Users and security
        User user1 = User.getOrCreateByIdOrFullName("user1"); // can access project1
        setupProjectMatrixAuthStrategy(Jenkins.READ, Item.DISCOVER);

        // Ensure we can read the original from user account
        try (ACLContext acl = ACL.as(user1)) {
            assertFalse(folder.hasPermission(Item.READ), "Test framework issue: User1 should be unable to read the folder");
            assertThat("user1 should be unable to see the origin", fingerprint.getOriginal(), nullValue());
            assertEquals(0, fingerprint._getUsages().size(), "No jobs should be visible to user1");
        }
    }

    /**
     * A common non-admin user should not be able to see references to a
     * deleted job even if he used to have READ permissions before the deletion.
     * @throws Exception Test error
     */
    @Test
    @Issue("SECURITY-153")
    void commonUserShouldBeUnableToSeeReferencesOfDeletedJobs() throws Exception {
        // Setup the environment
        FreeStyleProject project = createAndRunProjectWithPublisher("project", "test.txt");
        FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fp = getFingerprint(build, "test.txt");

        // Init Users and security
        User user1 = User.getOrCreateByIdOrFullName("user1");
        setupProjectMatrixAuthStrategy(Jenkins.READ, Item.READ, Item.DISCOVER);
        project.delete();

        try (ACLContext acl = ACL.as(user1)) {
            assertThat("user1 should be unable to see the origin", fp.getOriginal(), nullValue());
            assertEquals(0, fp._getUsages().size(), "No jobs should be visible to user1");
        }
    }

    @Test
    void adminShouldBeAbleToSeeReferencesOfDeletedJobs() throws Exception {
        // Setup the environment
        final FreeStyleProject project = createAndRunProjectWithPublisher("project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");

        // Init Users and security
        User user1 = User.getOrCreateByIdOrFullName("user1");
        setupProjectMatrixAuthStrategy(Jenkins.ADMINISTER);
        project.delete();

        try (ACLContext acl = ACL.as(user1)) {
            Fingerprint.BuildPtr original = fingerprint.getOriginal();
            assertThat("user1 should able to see the origin", fingerprint.getOriginal(), notNullValue());
            assertThat("Job has been deleted, so Job reference should return null", fingerprint.getOriginal().getJob(), nullValue());
            assertEquals(project.getFullName(), original.getName(), "user1 sees the wrong original name with Item.DISCOVER");
            assertEquals(build.getNumber(), original.getNumber(), "user1 sees the wrong original number with Item.DISCOVER");
            assertEquals(1, fingerprint._getUsages().size(), "user1 should be able to see the job in usages");
        }
    }

    @Test
    void shouldDeleteFingerprint() throws IOException {
        String id = SOME_MD5;
        Fingerprint fingerprintSaved = new Fingerprint(new Fingerprint.BuildPtr("foo", 3),
                "stuff&more.jar", Util.fromHexString(id));
        fingerprintSaved.save();

        Fingerprint fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));

        Fingerprint.delete(id);
        fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(nullValue()));

        Fingerprint.delete(id);
        fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    void checkNormalFingerprint() throws Exception {
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
    void checkNormalFingerprintWithWebClient() throws Exception {
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
    @Issue("JENKINS-65611")
    void canModifyFacets() {
        Fingerprint fingerprint = new Fingerprint(new Fingerprint.BuildPtr("foo", 3),
                "stuff&more.jar", Util.fromHexString(SOME_MD5));
        TestFacet testFacet = new TestFacet(fingerprint, 0, "test");
        assertThat(fingerprint.getFacets().size(), is(0));
        fingerprint.getFacets().add(testFacet);
        assertThat(fingerprint.getFacets().size(), is(1));
        assertTrue(fingerprint.getFacets().contains(testFacet));
        fingerprint.getFacets().remove(testFacet);
        assertThat(fingerprint.getFacets().size(), is(0));
        fingerprint.getFacets().add(testFacet);
        assertThat(fingerprint.getFacets().size(), is(1));
        Iterator<FingerprintFacet> itr = fingerprint.getFacets().iterator();
        itr.next();
        itr.remove();
        assertThat(fingerprint.getFacets().size(), is(0));
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryEmptyFileExistence() throws Exception {
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
        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);
        String id = first + second + "/../../" + first + "/" + second + "/../../../../cf1";
        Fingerprint fingerprint = Fingerprint.load(id);
        assertNull(fingerprint);
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryEmptyFileExistenceWithWebClient() throws Exception {
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
        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);
        rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf2/")));
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryFileExistence() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File sourceFile = new File(rule.jenkins.getRootDir(), "config.xml");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf3.xml");
        Files.copy(sourceFile.toPath(), targetFile.toPath(), REPLACE_EXISTING);
        targetFile.deleteOnExit();
        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);
        String id = first + second + "/../../" + first + "/" + second + "/../../../../cf3";
        Fingerprint fingerprint = Fingerprint.load(id);
        assertNull(fingerprint);
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryFileExistenceWithWebClient() throws Exception {
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
        Files.copy(sourceFile.toPath(), targetFile.toPath(), REPLACE_EXISTING);
        targetFile.deleteOnExit();
        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);

        rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf4/")));
        assertTrue(targetFile.exists());
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryFileNonexistence() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);
        String id = first + second + "/../../" + first + "/" + second + "/../../../../cf5";
        Fingerprint fingerprint = Fingerprint.load(id);
        assertNull(fingerprint);
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryFileNonexistenceWithWebClient() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);
        rule.createWebClient().getPage(new WebRequest(new URL(rule.getURL(), "static/abc/fingerprint/" + first + second + "%2f..%2f..%2f" + first + "%2f" + second + "%2f..%2f..%2f..%2f..%2fcf6/")));
    }

    @Test
    @Issue("SECURITY-2023")
    void checkArbitraryFingerprintConfigFileExistenceWithWebClient() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);

        Fingerprint fp = getFingerprint(build, "test.txt");
        File targetFile = new File(rule.jenkins.getRootDir(), "../cf7.xml");
        Files.writeString(targetFile.toPath(), TEST_FINGERPRINT_CONFIG_FILE_CONTENT, StandardCharsets.UTF_8);
        targetFile.deleteOnExit();

        String first = fp.getHashString().substring(0, 2);
        String second = fp.getHashString().substring(2, 4);

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
        assertNotNull(run, "Input run is null");
        Fingerprinter.FingerprintAction action = run.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull(action, "Fingerprint action has not been created in " + run);
        Map<String, Fingerprint> fingerprints = action.getFingerprints();
        final Fingerprint fp = fingerprints.get(filename);
        assertNotNull(fp, "No reference to '" + filename + "' from the Fingerprint action");
        return fp;
    }

    @NonNull
    private FreeStyleProject createAndRunProjectWithPublisher(String projectName, String fpFileName)
            throws Exception {
        return createAndRunProjectWithPublisher(null, projectName, fpFileName);
    }

    @NonNull
    private FreeStyleProject createAndRunProjectWithPublisher(@CheckForNull MockFolder folder,
            String projectName, String fpFileName) throws Exception {
        final FreeStyleProject project;
        if (folder == null) {
            project = rule.createFreeStyleProject(projectName);
        } else {
            project = folder.createProject(FreeStyleProject.class, projectName);
        }
        project.getBuildersList().add(new CreateFileBuilder(fpFileName, "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver(fpFileName);
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        rule.buildAndAssertSuccess(project);
        return project;
    }

    private void setupProjectMatrixAuthStrategy(@NonNull Permission ... permissions) {
        setupProjectMatrixAuthStrategy(true, permissions);
    }

    private void setupProjectMatrixAuthStrategy(boolean inheritFromFolders, @NonNull Permission ... permissions) {
        ProjectMatrixAuthorizationStrategy str = inheritFromFolders
                ? new ProjectMatrixAuthorizationStrategy()
                : new NoInheritanceProjectMatrixAuthorizationStrategy();
        for (Permission p : permissions) {
            str.add(p, "anonymous");
        }
        rule.jenkins.setAuthorizationStrategy(str);
    }
    //TODO: could be reworked to support multiple assignments

    private void setJobPermissionsOnce(Job<?, ?> job, String username, @NonNull Permission ... s)
            throws IOException {
        assertThat("Cannot assign the property twice", job.getProperty(AuthorizationMatrixProperty.class), nullValue());

        Map<Permission, Set<String>> permissions = new HashMap<>();
        HashSet<String> userSpec = new HashSet<>(List.of(username));

        for (Permission p : s) {
            permissions.put(p, userSpec);
        }
        AuthorizationMatrixProperty property = new AuthorizationMatrixProperty(permissions);
        job.addProperty(property);
    }

    /**
     * Security strategy, which prevents the permission inheritance from upper folders.
     */
    private static class NoInheritanceProjectMatrixAuthorizationStrategy extends ProjectMatrixAuthorizationStrategy {

        @Override
        public ACL getACL(Job<?, ?> project) {
            AuthorizationMatrixProperty amp = project.getProperty(AuthorizationMatrixProperty.class);
            if (amp != null) {
                return amp.getACL().newInheritingACL((SidACL) getRootACL());
            } else {
                return getRootACL();
            }
        }
    }

    private static final String TEST_FINGERPRINT_ID = "0123456789abcdef0123456789abcdef";
    private static final String TEST_FINGERPRINT_CONFIG_FILE_CONTENT = "<?xml version='1.1' encoding='UTF-8'?>\n" +
            "<fingerprint>\n" +
            "  <timestamp>2020-10-27 14:01:22.551 UTC</timestamp>\n" +
            "  <original>\n" +
            "    <name>test0</name>\n" +
            "    <number>1</number>\n" +
            "  </original>\n" +
            "  <md5sum>" + TEST_FINGERPRINT_ID + "</md5sum>\n" +
            "  <fileName>test.txt</fileName>\n" +
            "  <usages>\n" +
            "  </usages>\n" +
            "  <facets/>\n" +
            "</fingerprint>";
}
