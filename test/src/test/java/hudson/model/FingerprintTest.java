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

import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Fingerprinter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.SecuredMockFolder;
import org.jvnet.hudson.test.WorkspaceCopyFileBuilder;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;


//TODO: Refactoring: Tests should be exchanged with FingerprinterTest somehow
/**
 * Tests for the {@link Fingerprint} class.
 * @author Oleg Nenashev
 */
public class FingerprintTest {
    
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Before
    public void setupRealm() {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
    }
    
    @Test
    public void shouldCreateFingerprintsForWorkspace() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        project.getPublishersList().add(new Fingerprinter("test.txt", false));
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);
        
        Fingerprint fp = getFingerprint(build, "test.txt");
    }
    
    @Test
    public void shouldCreateFingerprintsForArtifacts() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.txt", "Hello, world!"));
        ArtifactArchiver archiver = new ArtifactArchiver("test.txt");
        archiver.setFingerprint(true);
        project.getPublishersList().add(archiver);
        FreeStyleBuild build = rule.buildAndAssertSuccess(project);
        
        Fingerprint fp = getFingerprint(build, "test.txt");
    }
    
    @Test
    public void shouldCreateUsageLinks() throws Exception {
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
        assertEquals("Original reference contains a wrong job name", project.getName(), original.getName());
        assertEquals("Original reference contains a wrong build number", build.getNumber(), original.getNumber());
        
        Hashtable<String, Fingerprint.RangeSet> usages = fp.getUsages();
        assertTrue("Usages do not have a reference to " + project, usages.containsKey(project.getName()));
        assertTrue("Usages do not have a reference to " + project2, usages.containsKey(project2.getName()));       
    }
    
    @Test
    @Issue("SECURITY-153")
    public void shouldBeUnableToSeeJobsIfNoPermissions() throws Exception {
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
        User user1 = User.get("user1"); // can access project1
        User user2 = User.get("user2"); // can access project2
        User user3 = User.get("user3"); // cannot access anything
          
        // Project permissions
        setupProjectMatrixAuthStrategy(Jenkins.READ);
        setJobPermissionsOnce(project1, "user1", Item.READ, Item.DISCOVER);
        setJobPermissionsOnce(project2, "user2", Item.READ, Item.DISCOVER);
        
        ACL.impersonate(user1.impersonate(), new Runnable() {
            @Override
            public void run() {
                Fingerprint.BuildPtr original = fp.getOriginal();
                assertThat("user1 should be able to see the origin", fp.getOriginal(), notNullValue());
                assertEquals("user1 should be able to see the origin's project name", project1.getName(), original.getName());
                assertEquals("user1 should be able to see the origin's build number", build.getNumber(), original.getNumber());
                assertEquals("Only one usage should be visible to user1", 1, fp._getUsages().size());
                assertEquals("Only project1 should be visible to user1", project1.getFullName(), fp._getUsages().get(0).name);
            }
        });
        
        ACL.impersonate(user2.impersonate(), new Runnable() {
            @Override
            public void run() {
                assertThat("user2 should be unable to see the origin", fp.getOriginal(), nullValue());
                assertEquals("Only one usage should be visible to user2", 1, fp._getUsages().size());
                assertEquals("Only project2 should be visible to user2", project2.getFullName(), fp._getUsages().get(0).name);
            }
        });
        
        ACL.impersonate(user3.impersonate(), new Runnable() {
            @Override
            public void run() {
                Fingerprint.BuildPtr original = fp.getOriginal();
                assertThat("user3 should be unable to see the origin", fp.getOriginal(), nullValue());
                assertEquals("All usages should be invisible for user3", 0, fp._getUsages().size());
            }
        });
    }
    
    @Test
    public void shouldBeAbleToSeeOriginalWithDiscoverPermissionOnly() throws Exception {
        // Setup the environment
        final FreeStyleProject project = createAndRunProjectWithPublisher("project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");
        
        // Init Users and security
        User user1 = User.get("user1");   
        setupProjectMatrixAuthStrategy(Jenkins.READ, Item.DISCOVER);
        
        ACL.impersonate(user1.impersonate(), new Runnable() {
            @Override
            public void run() {
                Fingerprint.BuildPtr original = fingerprint.getOriginal();
                assertThat("user1 should able to see the origin", fingerprint.getOriginal(), notNullValue());
                assertEquals("user1 sees the wrong original name with Item.DISCOVER", project.getFullName(), original.getName());
                assertEquals("user1 sees the wrong original number with Item.DISCOVER", build.getNumber(), original.getNumber());
                assertEquals("Usage ref in fingerprint should be visible to user1", 1, fingerprint._getUsages().size());
            }
        });
    }
    
    @Test
    public void shouldBeAbleToSeeFingerprintsInReadableFolder() throws Exception {
        final SecuredMockFolder folder = rule.jenkins.createProject(SecuredMockFolder.class, "folder");
        final FreeStyleProject project = createAndRunProjectWithPublisher(folder, "project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");
        
        // Init Users and security
        User user1 = User.get("user1");   
        setupProjectMatrixAuthStrategy(false, Jenkins.READ, Item.DISCOVER);
        setJobPermissionsOnce(project, "user1", Item.DISCOVER); // Prevents the fallback to the folder ACL
        folder.setPermissions("user1", Item.READ);
        
        // Ensure we can read the original from user account
        ACL.impersonate(user1.impersonate(), new Runnable() {
            @Override
            public void run() {
                assertTrue("Test framework issue: User1 should be able to read the folder", folder.hasPermission(Item.READ));
                
                Fingerprint.BuildPtr original = fingerprint.getOriginal();
                assertThat("user1 should able to see the origin", fingerprint.getOriginal(), notNullValue());
                assertEquals("user1 sees the wrong original name with Item.DISCOVER", project.getFullName(), original.getName());
                assertEquals("user1 sees the wrong original number with Item.DISCOVER", build.getNumber(), original.getNumber());
                assertEquals("user1 should be able to see the job", 1, fingerprint._getUsages().size());
                
                assertThat("User should be unable do retrieve the job due to the missing read", original.getJob(), nullValue());
            }
        });
    }
    
    @Test
    public void shouldBeUnableToSeeFingerprintsInUnreadableFolder() throws Exception {
        final SecuredMockFolder folder = rule.jenkins.createProject(SecuredMockFolder.class, "folder");
        final FreeStyleProject project = createAndRunProjectWithPublisher(folder, "project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");
        
        // Init Users and security
        User user1 = User.get("user1"); // can access project1     
        setupProjectMatrixAuthStrategy(Jenkins.READ, Item.DISCOVER);
        
        // Ensure we can read the original from user account
        ACL.impersonate(user1.impersonate(), new Runnable() {
            @Override
            public void run() {
                assertFalse("Test framework issue: User1 should be unable to read the folder", folder.hasPermission(Item.READ));         
                assertThat("user1 should be unable to see the origin", fingerprint.getOriginal(), nullValue());
                assertEquals("No jobs should be visible to user1", 0, fingerprint._getUsages().size());
            }
        });
    }
    
    /**
     * A common non-admin user should not be able to see references to a
     * deleted job even if he used to have READ permissions before the deletion.
     * @throws Exception Test error
     */
    @Test
    @Issue("SECURITY-153")
    public void commonUserShouldBeUnableToSeeReferencesOfDeletedJobs() throws Exception {
        // Setup the environment
        FreeStyleProject project = createAndRunProjectWithPublisher("project", "test.txt");
        FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fp = getFingerprint(build, "test.txt");
        
        // Init Users and security
        User user1 = User.get("user1");  
        setupProjectMatrixAuthStrategy(Jenkins.READ, Item.READ, Item.DISCOVER);
        project.delete();
        
        ACL.impersonate(user1.impersonate(), new Runnable() {
            @Override
            public void run() {
                assertThat("user1 should be unable to see the origin", fp.getOriginal(), nullValue());
                assertEquals("No jobs should be visible to user1", 0, fp._getUsages().size());
            }
        });
    }
    
    @Test
    public void adminShouldBeAbleToSeeReferencesOfDeletedJobs() throws Exception {
        // Setup the environment
        final FreeStyleProject project = createAndRunProjectWithPublisher("project", "test.txt");
        final FreeStyleBuild build = project.getLastBuild();
        final Fingerprint fingerprint = getFingerprint(build, "test.txt");
        
        // Init Users and security
        User user1 = User.get("user1"); 
        setupProjectMatrixAuthStrategy(Jenkins.ADMINISTER);
        project.delete();
        
        ACL.impersonate(user1.impersonate(), new Runnable() {
            @Override
            public void run() {
                Fingerprint.BuildPtr original = fingerprint.getOriginal();
                assertThat("user1 should able to see the origin", fingerprint.getOriginal(), notNullValue());
                assertThat("Job has been deleted, so Job reference shoud return null", fingerprint.getOriginal().getJob(), nullValue());
                assertEquals("user1 sees the wrong original name with Item.DISCOVER", project.getFullName(), original.getName());
                assertEquals("user1 sees the wrong original number with Item.DISCOVER", build.getNumber(), original.getNumber());
                assertEquals("user1 should be able to see the job in usages", 1, fingerprint._getUsages().size());  
            }
        });
    }
    
    @Nonnull
    private Fingerprint getFingerprint(@CheckForNull Run<?, ?> run, @Nonnull String filename) {
        assertNotNull("Input run is null", run);
        Fingerprinter.FingerprintAction action = run.getAction(Fingerprinter.FingerprintAction.class);
        assertNotNull("Fingerprint action has not been created in " + run, action);
        Map<String, Fingerprint> fingerprints = action.getFingerprints();
        final Fingerprint fp = fingerprints.get(filename);
        assertNotNull("No reference to '" + filename + "' from the Fingerprint action", fp);
        return fp;
    }
    
    @Nonnull
    private FreeStyleProject createAndRunProjectWithPublisher(String projectName, String fpFileName) 
            throws Exception {
        return createAndRunProjectWithPublisher(null, projectName, fpFileName);
    }
    
    @Nonnull
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
    
    private void setupProjectMatrixAuthStrategy(@Nonnull Permission ... permissions) {
        setupProjectMatrixAuthStrategy(true, permissions);
    }
    
    private void setupProjectMatrixAuthStrategy(boolean inheritFromFolders, @Nonnull Permission ... permissions) {
        ProjectMatrixAuthorizationStrategy str = inheritFromFolders 
                ? new ProjectMatrixAuthorizationStrategy()
                : new NoInheritanceProjectMatrixAuthorizationStrategy();
        for (Permission p : permissions) {
            str.add(p, "anonymous");
        }
        rule.jenkins.setAuthorizationStrategy(str);
    }
    //TODO: could be reworked to support multiple assignments
    private void setJobPermissionsOnce(Job<?,?> job, String username, @Nonnull Permission ... s)
            throws IOException {
        assertThat("Cannot assign the property twice", job.getProperty(AuthorizationMatrixProperty.class), nullValue());
        
        Map<Permission, Set<String>> permissions = new HashMap<Permission, Set<String>>(); 
        HashSet<String> userSpec = new HashSet<String>(Arrays.asList(username));

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
                return amp.getACL().newInheritingACL(getRootACL());
            } else {
                return getRootACL();
            }
        }
    }
}
