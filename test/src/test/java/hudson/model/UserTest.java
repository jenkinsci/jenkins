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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.AccessDeniedException3;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.GroupDetails;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException;
import hudson.security.UserMayOrMayNotExistException2;
import hudson.tasks.MailAddressResolver;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.WebAssert;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.WebConnectionWrapper;
import org.jenkinsci.plugins.matrixauth.PermissionEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Tag("SmokeTest")
@WithJenkins
class UserTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    public static class UserPropertyImpl extends UserProperty implements Action {

        private final String testString;
        private UserPropertyDescriptor descriptorImpl = new UserPropertyDescriptorImpl();

        UserPropertyImpl(String testString) {
            this.testString = testString;
        }

        public String getTestString() {
            return testString;
        }

        @Override
        public UserPropertyDescriptor getDescriptor() {
            return descriptorImpl;
        }

        @Override
        public String getIconFileName() {
          return "/images/24x24/gear.png";
        }

        @Override
        public String getDisplayName() {
          return "UserPropertyImpl";
        }

        @Override
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
    @Test
    void userPropertySummaryAndActionAreShownInUserPage() throws Exception {

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
    @Test
    void defaultUserAvatarCanBeFetched() throws Exception {
        User user = User.get("avatar-user", true);
        HtmlPage page = j.createWebClient().goTo("user/" + user.getDisplayName());
        j.assertAllImageLoadSuccessfully(page);
    }

    @Test
    void getAuthorities() {
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
            seccon.setAuthentication(User.get("administrator").impersonate2());
            assertEquals("[admins]", User.get("administrator").getAuthorities().toString());
            assertEquals("[users]", User.get("alice").getAuthorities().toString());
            assertEquals("[lpadmin, users]", User.get("bob").getAuthorities().toString());
            assertEquals("[]", User.get("MasterOfXaos").getAuthorities().toString());
            seccon.setAuthentication(User.get("alice").impersonate2());
            assertEquals("[]", User.get("alice").getAuthorities().toString());
            assertEquals("[]", User.get("bob").getAuthorities().toString());
        } finally {
            seccon.setAuthentication(orig);
        }
    }

    @Test
    void testGetUser() throws Exception {
        {
        User user = User.get("John Smith");
        User user2 = User.get("John Smith2");
        user2.setFullName("John Smith");
        assertNotSame(user.getId(), user2.getId(), "Users should not have the same id.");
        }
        j.jenkins.reload();
        {
        User user3 = User.get("John Smith");
        user3.setFullName("Alice Smith");
        assertEquals("John Smith", user3.getId(), "What was this asserting exactly?");
        User user4 = User.get("Marie", false, Collections.EMPTY_MAP);
        assertNull(user4, "User should not be created because Marie does not exists.");
        }
    }

    @Test
    void caseInsensitivity() {
        j.jenkins.setSecurityRealm(new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseInsensitive()));
        User user = User.get("john smith");
        User user2 = User.get("John Smith");
        assertSame(user.getId(), user2.getId(), "Users should have the same id.");
    }

    @Test
    void caseSensitivity() {
        j.jenkins.setSecurityRealm(new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseSensitive()));
        User user = User.get("john smith");
        User user2 = User.get("John Smith");
        assertNotSame(user.getId(), user2.getId(), "Users should not have the same id.");
        assertEquals("john smith", User.idStrategy().keyFor(user.getId()));
        assertEquals("John Smith", User.idStrategy().keyFor(user2.getId()));
    }

    @Test
    void caseSensitivityEmail() {
        j.jenkins.setSecurityRealm(new IdStrategySpecifyingSecurityRealm(new IdStrategy.CaseSensitiveEmailAddress()));
        User user = User.get("john.smith@acme.org");
        User user2 = User.get("John.Smith@acme.org");
        assertNotSame(user.getId(), user2.getId(), "Users should not have the same id.");
        assertEquals("john.smith@acme.org", User.idStrategy().keyFor(user.getId()));
        assertEquals("John.Smith@acme.org", User.idStrategy().keyFor(user2.getId()));
        user2 = User.get("john.smith@ACME.ORG");
        assertEquals(user.getId(), user2.getId(), "Users should have the same id.");
        assertEquals("john.smith@acme.org", User.idStrategy().keyFor(user2.getId()));
    }

    private static class IdStrategySpecifyingSecurityRealm extends HudsonPrivateSecurityRealm {
        private final IdStrategy idStrategy;

        IdStrategySpecifyingSecurityRealm(IdStrategy idStrategy) {
            super(true, false, null);
            this.idStrategy = idStrategy;
        }

        @Override
        public IdStrategy getUserIdStrategy() {
            return idStrategy;
        }
    }

    @Test
    void testAddAndGetProperty() throws Exception {
        {
        User user = User.get("John Smith");
        UserProperty prop = new SomeUserProperty();
        user.addProperty(prop);
        assertNotNull(user.getProperty(SomeUserProperty.class), "User should have SomeUserProperty property.");
        assertEquals(prop, user.getProperties().get(prop.getDescriptor()), "UserProperty1 should be assigned to its descriptor");
        assertTrue(user.getAllProperties().contains(prop), "User should contain SomeUserProperty.");
        }
        j.jenkins.reload();
        {
        assertNotNull(User.getById("John Smith", false).getProperty(SomeUserProperty.class), "User should have SomeUserProperty property.");
        }
    }

    @Test
    void testImpersonateAndCurrent() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.get("John Smith");
        assertNotSame(User.current().getId(), user.getId(), "User John Smith should not be the current user.");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertEquals(user.getId(), User.current().getId(), "User John Smith should be the current user.");
    }

    @Test
    void testGetUnknown() {
        User user = User.get("John Smith");
        assertNotNull(user, "User should not be null.");
    }

    @Test
    void testGetAndGetAll() {
        User user = User.get("John Smith", false, Collections.emptyMap());
        assertNull(user, "User John Smith should not be created.");
        assertFalse(User.getAll().contains(user), "Jenkins should not contain user John Smith.");
        User user2 = User.get("John Smith2", true, Collections.emptyMap());
        assertNotNull(user2, "User John Smith2 should be created.");
        assertTrue(User.getAll().contains(user2), "Jenkins should contain user John Smith2.");
        user = User.get("John Smith2", false, Collections.emptyMap());
        assertNotNull(user, "User John Smith should be created.");
        assertTrue(User.getAll().contains(user), "Jenkins should contain user John Smith.");
    }

    @Test
    void testReload() throws Exception {
        String originalName = "John Smith";
        User user = User.get(originalName, true, Collections.emptyMap());
        user.save();
        String temporaryName = "Alice Smith";
        user.setFullName(temporaryName);

        j.jenkins.reload();

        user = User.get(originalName, false, Collections.emptyMap());
        assertEquals(originalName, user.getFullName(), "User should have original name.");
    }

    @Test
    void testGetBuildsAndGetProjects() throws Exception {
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
        assertTrue(user.getBuilds().contains(build), "User should participate in the last build of project free.");
        assertFalse(user.getBuilds().contains(build2), "User should not participate in the last build of project free2.");
        assertTrue(user.getProjects().contains(project), "User should participate in the project free.");
        assertFalse(user.getProjects().contains(project2), "User should not participate in the project free2.");

        //JENKINS-16178: build should include also builds scheduled by user

        build2.replaceAction(new CauseAction(new Cause.UserIdCause()));
        assertFalse(user.getBuilds().contains(build2), "User should not participate in the last build of project free2.");
        assertFalse(User.current().getBuilds().contains(build), "Current user should not participate in the last build of project free.");
        assertTrue(User.current().getBuilds().contains(build2), "Current user should participate in the last build of project free2.");
    }

    @Test
    void testSave() throws Exception {
        {
        User user = User.get("John Smith", true, Collections.emptyMap());
        }
        j.jenkins.reload();
        {
        User user = User.get("John Smith", false, Collections.emptyMap());
        assertNull(user, "User should be null.");
        user = User.get("John Smithl", true, Collections.emptyMap());
        user.addProperty(new SomeUserProperty());
        user.save();
        }
        j.jenkins.reload();
        {
        User user = User.get("John Smithl", false, Collections.emptyMap());
        assertNotNull(user, "User should not be null.");
        assertNotNull(user.getProperty(SomeUserProperty.class), "User should be saved with all changes.");
        }
    }

    @Issue("JENKINS-16332")
    @Test
    void unrecoverableFullName() throws Throwable {
        String id;
        {
        User u = User.get("John Smith <jsmith@nowhere.net>");
        assertEquals("jsmith@nowhere.net", MailAddressResolver.resolve(u));
        id = u.getId();
        }
        j.jenkins.reload();
        {
        User u = User.get(id);
        assertEquals("jsmith@nowhere.net", MailAddressResolver.resolve(u));
        }
    }

    @Test
    void testDelete() throws Exception {
        {
         User user = User.get("John Smith", true, Collections.emptyMap());
         user.save();
         File configFolder = user.getUserFolder();
         user.delete();
         assertFalse(configFolder.exists(), "User should be deleted with his persistent data.");
         assertFalse(User.getAll().contains(user), "User should be deleted from memory.");
         user = User.get("John Smith", false, Collections.emptyMap());
         assertNull(user, "User should be deleted from memory.");
        }
        j.jenkins.reload();
        {
         boolean contained = false;
         for (User u : User.getAll()) {
             if (u.getId().equals("John Smith")) {
                 contained = true;
                 break;
             }
         }
         assertFalse(contained, "User should not be loaded.");
        }
    }

    @Test
    void testDoConfigSubmit() throws Exception {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        User user2 = realm.createAccount("John Smith2", "password");
        user2.save();
        auth.add(Jenkins.ADMINISTER, PermissionEntry.user(user.getId()));
        auth.add(Jenkins.READ, PermissionEntry.user(user2.getId()));
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        HtmlForm form = j.createWebClient().withBasicCredentials(user.getId(), "password").goTo(user2.getUrl() + "/account/").getFormByName("config");
        form.getInputByName("_.fullName").setValue("Alice Smith");
        j.submit(form);
        assertEquals("Alice Smith", user2.getFullName(), "User should have full name Alice Smith.");
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        try (JenkinsRule.WebClient webClient = j.createWebClient().withBasicCredentials(user2.getId(), "password")) {
            FailingHttpStatusCodeException failingHttpStatusCodeException = assertThrows(FailingHttpStatusCodeException.class, () -> webClient.goTo(user.getUrl() + "/account/"), "User should not have permission to configure another user.");
            assertThat(failingHttpStatusCodeException.getStatusCode(), is(403));
            form = webClient.goTo(user2.getUrl() + "/account/").getFormByName("config");
            form.getInputByName("_.fullName").setValue("John");
            j.submit(form);
        }

        assertEquals("John", user2.getFullName(), "User should be able to configure himself.");

    }

    /* TODO cannot follow what this is purporting to test
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
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        HtmlForm form = j.createWebClient().login(user.getId(), "password").goTo(user2.getUrl() + "/delete").getFormByName("delete");
        j.submit(form);
        assertFalse("User should be deleted from memory.", User.getAll().contains(user2));
        assertFalse("User should be deleted with his persistent data.", user2.getConfigFile().exists());
        User.reload();
        assertNull("Deleted user should not be loaded.", User.get(user2.getId(),false, Collections.EMPTY_MAP));
        user2 = realm.createAccount("John Smith2", "password");
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        try{
            user.doDoDelete(null, null);
            fail("User should not have permission to delete another user.");
        }
        catch(Exception e){
            if (!(e.getClass().isAssignableFrom(AccessDeniedException3.class))){
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
    }
    */

    @Test
    void testHasPermission() throws IOException {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        User user2 = realm.createAccount("John Smith2", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertFalse(user2.hasPermission(Permission.READ), "Current user should not have permission read.");
        assertTrue(user.hasPermission(Permission.READ), "Current user should always have permission read to himself.");
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue(user2.hasPermission(Permission.READ), "Current user should have permission read, because he has permission administer.");
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS2);
        user2 = User.get("anonymous");
        assertFalse(user2.hasPermission(Permission.READ), "Current user should not have permission read, because does not have global permission read and authentication is anonymous.");
    }

    @Test
    void testCanDelete() throws IOException {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        User user2 = realm.createAccount("John Smith2", "password");
        user2.save();

        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertFalse(user2.canDelete(), "Ordinary user cannot delete somebody else");
        auth.add(Jenkins.ADMINISTER, user.getId());
        assertTrue(user2.canDelete(), "Administrator can delete anybody else");
        assertFalse(user.canDelete(), "User (even admin) cannot delete himself");

        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        auth.add(Jenkins.ADMINISTER, user2.getId());
        User user3 = User.get("Random Somebody");
        assertFalse(user3.canDelete(), "Storage-less temporary user cannot be deleted");
        user3.save();
        assertTrue(user3.canDelete(), "But once storage is allocated, he can be deleted");
    }

    // @Issue("SECURITY-180")
    @Test
    void security180() throws Exception {
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
        SecurityContextHolder.getContext().setAuthentication(admin.impersonate2());
        admin.getProperty(ApiTokenProperty.class).changeApiToken();
        alice.getProperty(ApiTokenProperty.class).changeApiToken();

        // User can change only own token
        SecurityContextHolder.getContext().setAuthentication(bob.impersonate2());
        bob.getProperty(ApiTokenProperty.class).changeApiToken();
        assertThrows(AccessDeniedException3.class, () -> alice.getProperty(ApiTokenProperty.class).changeApiToken(), "Bob should not be authorized to change alice's token");

        // ANONYMOUS2 can not change any token
        SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS2);
        assertThrows(AccessDeniedException3.class, () -> alice.getProperty(ApiTokenProperty.class).changeApiToken(), "Anonymous should not be authorized to change alice's token");
    }

    @Issue("SECURITY-243")
    @Test
    void resolveByIdThenName() throws Exception {
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null));

        User u1 = User.get("user1");
        u1.setFullName("User One");
        u1.save();

        User u2 = User.get("user2");
        u2.setFullName("User Two");
        u2.save();

        assertNotSame(u1.getId(), u2.getId(), "Users should not have the same id.");

        User u = User.get("User One");
        assertEquals(u1.getId(), u.getId(), "'User One' should resolve to u1");

        u = User.get("User Two");
        assertEquals(u2.getId(), u.getId(), "'User Two' should resolve to u2");

        u = User.get("user1");
        assertEquals(u1.getId(), u.getId(), "'user1' should resolve to u1");

        u = User.get("user2");
        assertEquals(u2.getId(), u.getId(), "'user2' should resolve to u2");

        u1.setFullName("user2");
        u1.save();
        u = User.get("user2");
        assertEquals(u2.getId(), u.getId(), "'user2' should resolve to u2");
        u = User.get("user1");
        assertEquals(u1.getId(), u.getId(), "'user1' should resolve to u1");


        u1.setFullName("user1");
        u1.save();
        u2.setFullName("user1");
        u2.save();
        u = User.get("user1");
        assertEquals(u1.getId(), u.getId(), "'user1' should resolve to u1");
        u = User.get("user2");
        assertEquals(u2.getId(), u.getId(), "'user2' should resolve to u2");
    }

    @Issue("SECURITY-243")
    @Test
    void resolveByUnloadedIdThenName() {
        j.jenkins.setSecurityRealm(new ExternalSecurityRealm());
        // do *not* call this here: User.get("victim");
        User attacker1 = User.get("attacker1");
        attacker1.setFullName("victim1");
        User victim1 = User.get("victim1");
        assertEquals("victim1", victim1.getId(), "victim1 is a real user ID, we must ignore the attacker1’s fullName");
        assertNull(victim1.getProperty(MyViewsProperty.class).getPrimaryViewName(), "a recursive call to User.get was OK");
        assertEquals("victim1", attacker1.getProperty(MyViewsProperty.class).getPrimaryViewName(), "(though the realm mistakenly added metadata to the attacker)");
        User.get("attacker2").setFullName("nonexistent");
        assertEquals("attacker2", User.get("nonexistent").getId(), "but if we cannot find such a user ID, allow the fullName");
        User.get("attacker3").setFullName("unknown");
        assertEquals("attacker3", User.get("unknown").getId(), "or if we are not sure, allow the fullName");
        User.get("attacker4").setFullName("Victim2");
        assertEquals("victim2", User.get("Victim2").getId(), "victim2 is a real (canonical) user ID");

    }

    private static class ExternalSecurityRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
            if (username.equals("nonexistent")) {
                throw new UsernameNotFoundException(username);
            } else if (username.equals("unknown")) {
                throw new UserMayOrMayNotExistException2(username);
            } else {
                String canonicalName = username.toLowerCase(Locale.ENGLISH);
                try {
                    User.get(canonicalName).addProperty(new MyViewsProperty(canonicalName));
                } catch (IOException x) {
                    throw new RuntimeException(x);
                }
                return new org.springframework.security.core.userdetails.User(canonicalName, "", true, true, true, true, Set.of(AUTHENTICATED_AUTHORITY2));
            }
        }

        @Override
        protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
            return loadUserByUsername2(username); // irrelevant
        }

        @Override
        public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            throw new UsernameNotFoundException(groupname); // irrelevant
        }
    }

    @Test
    void resolveById() throws Exception {
        User u1 = User.get("user1");
        u1.setFullName("User One");
        u1.save();

        User u2 = User.get("user2");
        u2.setFullName("User Two");
        u2.save();

        assertNotSame(u1.getId(), u2.getId(), "Users should not have the same id.");

        // We can get the same user back.
        User u = User.getById("user1", false);
        assertSame(u1, u, "'user1' should return u1");

        // passing true should not create a new user if it does not exist.
        u = User.getById("user1", true);
        assertSame(u1, u, "'user1' should return u1");

        // should not lookup by name.
        u = User.getById("User One", false);
        assertNull(u, "'User One' should not resolve to any user");

        // We can get the same user back.
        u = User.getById("user2", false);
        assertSame(u2, u, "'user2' should return u2");

        // passing true should not create a new user if it does not exist.
        u = User.getById("user2", true);
        assertSame(u2, u, "'user2' should return u1");

        // should not lookup by name.
        u = User.getById("User Two", false);
        assertNull(u, "'User Two' should not resolve to any user");

        u1.setFullName("user1");
        u1.save();
        u2.setFullName("user1");
        u2.save();
        u = User.getById("user1", false);
        assertSame(u1, u, "'user1' should resolve to u1");
        u = User.getById("user2", false);
        assertSame(u2, u, "'user2' should resolve to u2");
    }

    @Test
    @Issue("SECURITY-514")
    void getAllPropertiesRequiresAdmin() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().toEveryone());
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User admin = User.get("admin");
        User alice = User.get("alice");
        User bob = User.get("bob");

        // Admin can access user properties for all users
        try (ACLContext as = ACL.as(admin)) {
            assertThat(alice.getAllProperties(), not(empty()));
            assertThat(bob.getAllProperties(), not(empty()));
            assertThat(admin.getAllProperties(), not(empty()));
        }

        // Non admins can only view their own
        try (ACLContext as = ACL.as(alice)) {
            assertThat(alice.getAllProperties(), not(empty()));
            assertThat(bob.getAllProperties(), empty());
            assertThat(admin.getAllProperties(), empty());
        }
    }

    @Test
    @LocalData
    void differentUserIdInConfigFileIsIgnored() {
        String fredUserId = "fred";
        User fred = User.getById(fredUserId, false);
        assertThat(fred, notNullValue());
        assertThat(fred.getId(), is(fredUserId));
        assertThat(fred.getFullName(), is("Fred Smith"));
        User jane = User.getById("jane", false);
        assertThat(jane, nullValue());
    }

    @Test
    @LocalData
    void corruptConfigFile() {
        String fredUserId = "fred";
        User fred = User.getById(fredUserId, true);
        assertThat(fred, notNullValue());
        assertThat(fred.getFullName(), is("fred"));
    }

    @Test
    void parentDirectoryUserDoesNotExist() {
        String userId = "admin";
        User admin = User.getById(userId, true);
        assertNotNull(admin);
        assertThat(admin.getId(), is(userId));
        User parentDirectoryUserId = User.getById("../" + admin, false);
        assertThat(parentDirectoryUserId, nullValue());
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

    @Issue("JENKINS-45977")
    @Test
    void missingDescriptor() throws Exception {
        ExtensionList.lookup(Descriptor.class).remove(j.jenkins.getDescriptor(SomeUserProperty.class));
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("alice"));
        User alice = User.get("alice");
        alice.addProperty(new SomeUserProperty());
        assertThat(alice.getProperties().values(), not(empty()));
        JenkinsRule.WebClient wc = j.createWebClient();
        final List<URL> failingResources = new ArrayList<>();
        new WebConnectionWrapper(wc) { // https://stackoverflow.com/a/18853796/12916
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                WebResponse r = super.getResponse(request);
                if (r.getStatusCode() >= 400) {
                    failingResources.add(request.getUrl());
                }
                return r;
            }
        };
        wc.login("alice").goTo("me/account/");
        assertThat(failingResources, empty());
    }

    @Test
    void legacyCallerGetsUserMayOrMayNotExistException() {
        final SecurityRealm realm = new NonEnumeratingAcegiSecurityRealm();
        assertThrows(UserMayOrMayNotExistException.class, () -> realm.loadUserByUsername("unknown"));

        final SecurityRealm realm2 = new NonEnumeratingSpringSecurityRealm();
        assertThrows(UserMayOrMayNotExistException.class, () -> realm2.loadUserByUsername("unknown"));
    }

    private static class NonEnumeratingAcegiSecurityRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        public org.acegisecurity.userdetails.UserDetails loadUserByUsername(String username) throws org.acegisecurity.userdetails.UsernameNotFoundException {
            throw new UserMayOrMayNotExistException(username + " not found");
        }
    }

    private static class NonEnumeratingSpringSecurityRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
            throw new UserMayOrMayNotExistException2(username + " not found");
        }
    }
}
