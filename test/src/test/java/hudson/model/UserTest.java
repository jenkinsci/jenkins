/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Vincent Latombe
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
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.security.AccessDeniedException2;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import hudson.tasks.MailAddressResolver;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;

import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

public class UserTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    public static class UserPropertyImpl extends UserProperty implements Action {

        private final String testString;
        private UserPropertyDescriptor descriptorImpl = new UserPropertyDescriptorImpl();
        
        public UserPropertyImpl(String testString) {
            this.testString = testString;
        }
        
        public String getTestString() {
            return testString;
        }

        @Override
        public UserPropertyDescriptor getDescriptor() {
            return descriptorImpl;
        }
        
        public String getIconFileName() {
          return "/images/24x24/gear.png";
        }

        public String getDisplayName() {
          return "UserPropertyImpl";
        }

        public String getUrlName() {
          return "userpropertyimpl";
        }
        
        public static class UserPropertyDescriptorImpl extends UserPropertyDescriptor {
          @Override
          public UserProperty newInstance(User user) {
              return null;
          }
      }
    }

    @Issue("JENKINS-2331")
    @Test public void userPropertySummaryAndActionAreShownInUserPage() throws Exception {
        
        UserProperty property = new UserPropertyImpl("NeedleInPage");
        UserProperty.all().add(property.getDescriptor());
        
        User user = User.get("user-test-case");
        user.addProperty(property);
        
        HtmlPage page = j.createWebClient().goTo("user/user-test-case");
        
        WebAssert.assertTextPresentInElement(page, "NeedleInPage", "main-panel");
        WebAssert.assertTextPresentInElement(page, ((Action) property).getDisplayName(), "side-panel");
        
    }
    
    /**
     * Asserts that the default user avatar can be fetched (ie no 404)
     */
    @Issue("JENKINS-7494")
    @Test public void defaultUserAvatarCanBeFetched() throws Exception {
        User user = User.get("avatar-user", true);
        HtmlPage page = j.createWebClient().goTo("user/" + user.getDisplayName());
        j.assertAllImageLoadSuccessfully(page);
    }

    @Test public void getAuthorities() throws Exception {
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        realm.addGroups("alice", "users");
        realm.addGroups("bob", "users", "lpadmin", "bob");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admins");
        auth.add(Permission.READ, "users");
        j.jenkins.setAuthorizationStrategy(auth);
        SecurityContext seccon = SecurityContextHolder.getContext();
        Authentication orig = seccon.getAuthentication();
        try {
            seccon.setAuthentication(User.get("administrator").impersonate());
            assertEquals("[admins]", User.get("administrator").getAuthorities().toString());
            assertEquals("[users]", User.get("alice").getAuthorities().toString());
            assertEquals("[lpadmin, users]", User.get("bob").getAuthorities().toString());
            assertEquals("[]", User.get("MasterOfXaos").getAuthorities().toString());
            seccon.setAuthentication(User.get("alice").impersonate());
            assertEquals("[]", User.get("alice").getAuthorities().toString());
            assertEquals("[]", User.get("bob").getAuthorities().toString());
        } finally {
            seccon.setAuthentication(orig);
        }
    }
   
    @Test
    public void testGetUser() {
        User user = User.get("John Smith");
        User user2 = User.get("John Smith2");
        user2.setFullName("John Smith");
        assertNotSame("Users should not have the same id.", user.getId(), user2.getId());
        User.clear();
        User user3 = User.get("John Smith");
        user3.setFullName("Alice Smith");
        assertEquals("Users should not have the same id.", user.getId(), user3.getId());
        User user4 = User.get("Marie",false, Collections.EMPTY_MAP);
        assertNull("User should not be created because Marie does not exists.", user4);
    }

    @Test
    public void caseInsensitivity() {
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null){
            @Override
            public IdStrategy getUserIdStrategy() {
                return new IdStrategy.CaseInsensitive();
            }
        });
        User user = User.get("john smith");
        User user2 = User.get("John Smith");
        assertSame("Users should have the same id.", user.getId(), user2.getId());
        assertEquals(user.getId(), User.idStrategy().idFromFilename(User.idStrategy().filenameOf(user.getId())));
        assertEquals(user2.getId(), User.idStrategy().idFromFilename(User.idStrategy().filenameOf(user2.getId())));
    }
    
    @Test
    public void caseSensitivity() {
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null){
            @Override
            public IdStrategy getUserIdStrategy() {
                return new IdStrategy.CaseSensitive();
            }
        });
        User user = User.get("john smith");
        User user2 = User.get("John Smith");
        assertNotSame("Users should not have the same id.", user.getId(), user2.getId());
        assertEquals("john smith", User.idStrategy().keyFor(user.getId()));
        assertEquals("john smith", User.idStrategy().filenameOf(user.getId()));
        assertEquals("John Smith", User.idStrategy().keyFor(user2.getId()));
        assertEquals("~john ~smith", User.idStrategy().filenameOf(user2.getId()));
        assertEquals(user.getId(), User.idStrategy().idFromFilename(User.idStrategy().filenameOf(user.getId())));
        assertEquals(user2.getId(), User.idStrategy().idFromFilename(User.idStrategy().filenameOf(user2.getId())));
    }

    @Test
    public void caseSensitivityEmail() {
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null){
            @Override
            public IdStrategy getUserIdStrategy() {
                return new IdStrategy.CaseSensitiveEmailAddress();
            }
        });
        User user = User.get("john.smith@acme.org");
        User user2 = User.get("John.Smith@acme.org");
        assertNotSame("Users should not have the same id.", user.getId(), user2.getId());
        assertEquals("john.smith@acme.org", User.idStrategy().keyFor(user.getId()));
        assertEquals("john.smith@acme.org", User.idStrategy().filenameOf(user.getId()));
        assertEquals("John.Smith@acme.org", User.idStrategy().keyFor(user2.getId()));
        assertEquals("~john.~smith@acme.org", User.idStrategy().filenameOf(user2.getId()));
        user2 = User.get("john.smith@ACME.ORG");
        assertEquals("Users should have the same id.", user.getId(), user2.getId());
        assertEquals("john.smith@acme.org", User.idStrategy().keyFor(user2.getId()));
        assertEquals("john.smith@acme.org", User.idStrategy().filenameOf(user2.getId()));
        assertEquals(user.getId(), User.idStrategy().idFromFilename(User.idStrategy().filenameOf(user.getId())));
        assertEquals(user2.getId(), User.idStrategy().idFromFilename(User.idStrategy().filenameOf(user2.getId())));
    }

    @Issue("JENKINS-24317")
    @LocalData
    @Test public void migration() throws Exception {
        assumeFalse("was not a problem on a case-insensitive FS to begin with", new File(j.jenkins.getRootDir(), "users/bob").isDirectory());
        User bob = User.get("bob");
        assertEquals("Bob Smith", bob.getFullName());
        assertEquals("Bob Smith", User.get("Bob").getFullName());
        assertEquals("nonexistent", User.get("nonexistent").getFullName());
        assertEquals("[bob]", Arrays.toString(new File(j.jenkins.getRootDir(), "users").list()));
    }

    @Test
    public void testAddAndGetProperty() throws IOException {
        User user = User.get("John Smith");  
        UserProperty prop = new SomeUserProperty();
        user.addProperty(prop);
        assertNotNull("User should have SomeUserProperty property.", user.getProperty(SomeUserProperty.class));
        assertEquals("UserProperty1 should be assigned to its descriptor", prop, user.getProperties().get(prop.getDescriptor()));
        assertTrue("User should should contains SomeUserProperty.", user.getAllProperties().contains(prop));
        User.reload();
        assertNotNull("User should have SomeUserProperty property.", user.getProperty(SomeUserProperty.class));
    }

    @Test
    public void testImpersonateAndCurrent() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.get("John Smith"); 
        assertNotSame("User John Smith should not be the current user.", User.current().getId(), user.getId());
        SecurityContextHolder.getContext().setAuthentication(user.impersonate()); 
        assertEquals("User John Smith should be the current user.", user.getId(), User.current().getId());
    }

    @Test
    public void testGetUnknown() {
        User user = User.get("John Smith"); 
        assertNotNull("User should not be null.", user);
    }

    @Test
    public void testGetAndGetAll() {
        User user = User.get("John Smith", false, Collections.emptyMap());
        assertNull("User John Smith should not be created.", user);
        assertFalse("Jenkins should not contain user John Smith.", User.getAll().contains(user));
        User user2 = User.get("John Smith2", true, Collections.emptyMap());
        assertNotNull("User John Smith2 should be created.", user2);
        assertTrue("Jenkins should contain user John Smith2.", User.getAll().contains(user2));
        user = User.get("John Smith2", false, Collections.emptyMap());
        assertNotNull("User John Smith should be created.", user);
        assertTrue("Jenkins should contain user John Smith.", User.getAll().contains(user));
    }
    
    @Test
    public void testReload() throws IOException{
        User user = User.get("John Smith", true, Collections.emptyMap());
        user.save();
        String config = user.getConfigFile().asString();
        config = config.replace("John Smith", "Alice Smith");
        PrintStream st = new PrintStream(user.getConfigFile().getFile());
        st.print(config);
        User.clear();
        assertEquals("User should have full name John Smith.", "John Smith", user.getFullName());
        User.reload();
        user = User.get(user.getId(), false, Collections.emptyMap());
        assertEquals("User should have full name Alice Smith.", "Alice Smith", user.getFullName());
    }

    @Test
    public void testClear() {
        User user = User.get("John Smith", true, Collections.emptyMap());
        assertNotNull("User should not be null.", user);
        user.clear();
        user = User.get("John Smith", false, Collections.emptyMap());
        assertNull("User should be null", user);       
    }

    @Test
    public void testGetBuildsAndGetProjects() throws Exception {
        User user = User.get("John Smith", true, Collections.emptyMap());
        FreeStyleProject project = j.createFreeStyleProject("free");
        FreeStyleProject project2 = j.createFreeStyleProject("free2");
        project.save();
        FakeChangeLogSCM scm = new FakeChangeLogSCM();
        scm.addChange().withAuthor(user.getId());
        project.setScm(scm);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project2);
        Build build = project.getLastBuild();
        Build build2 = project2.getLastBuild();
        assertTrue("User should participate in the last build of project free.", user.getBuilds().contains(build));
        assertFalse("User should not participate in the last build of project free2.", user.getBuilds().contains(build2));
        assertTrue("User should participate in the project free.", user.getProjects().contains(project));
        assertFalse("User should not participate in the project free2.", user.getProjects().contains(project2));

        //JENKINS-16178: build should include also builds scheduled by user

        build2.replaceAction(new CauseAction(new Cause.UserIdCause()));
        assertFalse("User should not participate in the last build of project free2.", user.getBuilds().contains(build2));
        assertFalse("Current user should not participate in the last build of project free.", User.current().getBuilds().contains(build));
        assertTrue("Current user should participate in the last build of project free2.", User.current().getBuilds().contains(build2));
    }

    @Test
    public void testSave() throws IOException {
        User user = User.get("John Smith", true, Collections.emptyMap());
        User.clear();
        User.reload();
        user = User.get("John Smith", false, Collections.emptyMap());
        assertNull("User should be null.", user);
        user = User.get("John Smithl", true, Collections.emptyMap());
        user.addProperty(new SomeUserProperty());
        user.save();
        User.clear();
        User.reload();
        user = User.get("John Smithl", false, Collections.emptyMap());
        assertNotNull("User should not be null.", user);
        assertNotNull("User should be saved with all changes.", user.getProperty(SomeUserProperty.class));
    }

    @Issue("JENKINS-16332")
    @Test public void unrecoverableFullName() throws Throwable {
        User u = User.get("John Smith <jsmith@nowhere.net>");
        assertEquals("jsmith@nowhere.net", MailAddressResolver.resolve(u));
        String id = u.getId();
        User.clear(); // simulate Jenkins restart
        u = User.get(id);
        assertEquals("jsmith@nowhere.net", MailAddressResolver.resolve(u));
    }

    @Test
    public void testDelete() throws IOException {
         User user = User.get("John Smith", true, Collections.emptyMap());
         user.save();
         user.delete();
         assertFalse("User should be deleted with his persistent data.", user.getConfigFile().exists());
         assertFalse("User should be deleted from memory.", User.getAll().contains(user));
         user = User.get("John Smith", false, Collections.emptyMap());
         assertNull("User should be deleted from memory.", user);
         User.reload();
         boolean contained = false;
         for(User u: User.getAll()){
             if(u.getId().equals(user.getId())){
                 contained = true;
                 break;
             }
         }
         assertFalse("User should not be loaded.", contained);
    }

    @Test
    public void testDoConfigSubmit() throws Exception {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        User user2 = realm.createAccount("John Smith2", "password");
        user2.save();
        auth.add(Jenkins.ADMINISTER, user.getId());
        auth.add(Jenkins.READ, user2.getId());
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        HtmlForm form = j.createWebClient().login(user.getId(), "password").goTo(user2.getUrl() + "/configure").getFormByName("config");
        form.getInputByName("_.fullName").setValueAttribute("Alice Smith");
        j.submit(form);
        assertEquals("User should have full name Alice Smith.", "Alice Smith", user2.getFullName());
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate());
        try{
            user.doConfigSubmit(null, null);
            fail("User should not have permission to configure antoher user.");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        }
        form = j.createWebClient().login(user2.getId(), "password").goTo(user2.getUrl() + "/configure").getFormByName("config");
        
        form.getInputByName("_.fullName").setValueAttribute("John");
        j.submit(form);
        assertEquals("User should be albe to configure himself.", "John", user2.getFullName());

    }

    @Test
    public void testDoDoDelete() throws Exception {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        User user2 = realm.createAccount("John Smith2", "password");
        user2.save();
        auth.add(Jenkins.ADMINISTER, user.getId());
        auth.add(Jenkins.READ, user2.getId());
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        HtmlForm form = j.createWebClient().login(user.getId(), "password").goTo(user2.getUrl() + "/delete").getFormByName("delete");
        j.submit(form);
        assertFalse("User should be deleted from memory.", User.getAll().contains(user2));
        assertFalse("User should be deleted with his persistent data.", user2.getConfigFile().exists());
        User.reload();
        assertNull("Deleted user should not be loaded.", User.get(user2.getId(),false, Collections.EMPTY_MAP));
        user2 = realm.createAccount("John Smith2", "password");
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate());
        try{
            user.doDoDelete(null, null);
            fail("User should not have permission to delete antoher user.");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        }
        user.save();
        JenkinsRule.WebClient client = j.createWebClient();
        form = client.login(user.getId(), "password").goTo(user.getUrl() + "/delete").getFormByName("delete");
        try{
            j.submit(form);
            fail("User should not be able to delete himself");
        }
        catch(FailingHttpStatusCodeException e){
            //ok exception should be thrown
            Assert.assertEquals(400, e.getStatusCode());
        }
        assertTrue("User should not delete himself from memory.", User.getAll().contains(user));
        assertTrue("User should not delete his persistent data.", user.getConfigFile().exists());
        User.reload();
        assertNotNull("Deleted user should be loaded.",User.get(user.getId(),false, Collections.EMPTY_MAP));     
    }

    @Test
    public void testHasPermission() throws IOException {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith","password");
        User user2 = realm.createAccount("John Smith2", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertFalse("Current user should not have permission read.", user2.hasPermission(Permission.READ));
        assertTrue("Current user should always have permission read to himself.", user.hasPermission(Permission.READ));
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue("Current user should have permission read, because he has permission administer.", user2.hasPermission(Permission.READ));
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS);
        user2 = User.get("anonymous");
        assertFalse("Current user should not have permission read, because does not have global permission read and authentication is anonymous.", user2.hasPermission(Permission.READ));
    }

    @Test
    public void testCanDelete() throws IOException {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith","password");
        User user2 = realm.createAccount("John Smith2","password");
        user2.save();

        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertFalse("Ordinary user cannot delete somebody else", user2.canDelete());
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue("Administrator can delete anybody else", user2.canDelete());
        assertFalse("User (even admin) cannot delete himself", user.canDelete());

        SecurityContextHolder.getContext().setAuthentication(user2.impersonate());
        auth.add(Jenkins.ADMINISTER, user2.getId());
        User user3 = User.get("Random Somebody");
        assertFalse("Storage-less temporary user cannot be deleted", user3.canDelete());
        user3.save();
        assertTrue("But once storage is allocated, he can be deleted", user3.canDelete());
    }

    @Test
    // @Issue("SECURITY-180")
    public void security180() throws Exception {
        final GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User alice = User.get("alice");
        User bob = User.get("bob");
        User admin = User.get("admin");

        auth.add(Jenkins.READ, alice.getId());
        auth.add(Jenkins.READ, bob.getId());
        auth.add(Jenkins.ADMINISTER, admin.getId());

        // Admin can change everyone's token
        SecurityContextHolder.getContext().setAuthentication(admin.impersonate());
        admin.getProperty(ApiTokenProperty.class).changeApiToken();
        alice.getProperty(ApiTokenProperty.class).changeApiToken();

        // User can change only own token
        SecurityContextHolder.getContext().setAuthentication(bob.impersonate());
        bob.getProperty(ApiTokenProperty.class).changeApiToken();
        try {
            alice.getProperty(ApiTokenProperty.class).changeApiToken();
            fail("Bob should not be authorized to change alice's token");
        } catch (AccessDeniedException expected) { }

        // ANONYMOUS can not change any token
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS);
        try {
            alice.getProperty(ApiTokenProperty.class).changeApiToken();
            fail("Anonymous should not be authorized to change alice's token");
        } catch (AccessDeniedException expected) { }
    }

     public static class SomeUserProperty extends UserProperty {
         
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            @Override
            public UserProperty newInstance(User user) {
                return new SomeUserProperty();
            }
        }
    }

}
