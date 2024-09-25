package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.util.FormValidation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.springframework.security.core.Authentication;

public class AbstractItemTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the reload functionality
     */
    @Test
    public void reload() throws Exception {
        Jenkins jenkins = j.jenkins;
        jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Item.CONFIGURE).everywhere().to("alice", "bob");
        mas.grant(Item.READ).everywhere().to("alice");

        FreeStyleProject p;
        FreeStyleBuild b;
        var alice = User.getById("alice", true);
        try (ACLContext ignored = ACL.as(alice)) {
            p = jenkins.createProject(FreeStyleProject.class, "foo");
            p.setDescription("Hello World");

            b = j.buildAndAssertSuccess(p);
            b.setDescription("This is my build");
        }
        // update on disk representation
        Path path = p.getConfigFile().getFile().toPath();
        Files.writeString(path, Files.readString(path, StandardCharsets.UTF_8).replaceAll("Hello World", "Good Evening"), StandardCharsets.UTF_8);

        TestSaveableListener testSaveableListener = ExtensionList.lookupSingleton(TestSaveableListener.class);
        testSaveableListener.setSaveable(p);

        // reload away
        p.doReload();

        assertFalse(SaveableListener.class.getSimpleName() + " should not have been called", testSaveableListener.isChangeCalled());
        assertEquals("Good Evening", p.getDescription());

        FreeStyleBuild b2 = p.getBuildByNumber(1);

        assertNotEquals(b, b2); // should be different object
        assertEquals(b.getDescription(), b2.getDescription()); // but should have the same properties

        try (var ignored = ACL.as(alice)) {
            p.setDescription("This is Alice's project");
        }
        assertTrue(SaveableListener.class.getSimpleName() + " should have been called", testSaveableListener.isChangeCalled());
        assertThat(testSaveableListener.getChangeUser(), equalTo(alice.impersonate2()));

        try (var ignored = ACL.as(alice)) {
            p.delete();
        }
        assertTrue(SaveableListener.class.getSimpleName() + " should have been called", testSaveableListener.isDeleteCalled());
        assertThat(testSaveableListener.getDeleteUser(), equalTo(alice.impersonate2()));
    }

    @Test
    public void checkRenameValidity() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo");
        p.getBuildersList().add(new SleepBuilder(10));
        j.createFreeStyleProject("foo-exists");

        assertThat(checkNameAndReturnError(p, ""), equalTo(Messages.Hudson_NoName()));
        assertThat(checkNameAndReturnError(p, ".."), equalTo(Messages.Jenkins_NotAllowedName("..")));
        assertThat(checkNameAndReturnError(p, "50%"), equalTo(Messages.Hudson_UnsafeChar('%')));
        assertThat(checkNameAndReturnError(p, "foo"), equalTo(Messages.AbstractItem_NewNameUnchanged()));
        assertThat(checkNameAndReturnError(p, "foo-exists"), equalTo(Messages.AbstractItem_NewNameInUse("foo-exists")));

        j.jenkins.setProjectNamingStrategy(new ProjectNamingStrategy.PatternProjectNamingStrategy("bar", "", false));
        assertThat(checkNameAndReturnError(p, "foo1"), equalTo(jenkins.model.Messages.Hudson_JobNameConventionNotApplyed("foo1", "bar")));

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();
        assertThat(checkNameAndReturnError(p, "bar"), equalTo(Messages.Job_NoRenameWhileBuilding()));
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }

    @Test
    public void checkRenamePermissions() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Item.CONFIGURE).everywhere().to("alice", "bob");
        mas.grant(Item.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(mas);
        FreeStyleProject p = j.createFreeStyleProject("foo");
        j.createFreeStyleProject("foo-exists");

        try (ACLContext unused = ACL.as(User.getById("alice", true))) {
            assertThat(checkNameAndReturnError(p, "foo-exists"), equalTo(Messages.AbstractItem_NewNameInUse("foo-exists")));
        }
        try (ACLContext unused = ACL.as(User.getById("bob", true))) {
            assertThat(checkNameAndReturnError(p, "foo-exists"), equalTo(Messages.Jenkins_NotAllowedName("foo-exists")));
        }
        try (ACLContext unused = ACL.as(User.getById("carol", true))) {
            AccessDeniedException3 e = assertThrows(AccessDeniedException3.class, () -> p.doCheckNewName("foo"));
            assertThat(e.permission, equalTo(Item.CREATE));
        }
    }

    @Test
    public void renameViaRestApi() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Item.READ, Jenkins.READ).everywhere().to("alice", "bob");
        mas.grant(Item.CONFIGURE).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(mas);
        FreeStyleProject p = j.createFreeStyleProject("foo");

        WebClient w = j.createWebClient();
        WebRequest wr = new WebRequest(w.createCrumbedUrl(p.getUrl() + "confirmRename"), HttpMethod.POST);
        wr.setRequestParameters(List.of(new NameValuePair("newName", "bar")));
        w.login("alice", "alice");
        Page page = w.getPage(wr);
        assertThat(getPath(page.getUrl()), equalTo(p.getUrl()));
        assertThat(p.getName(), equalTo("bar"));

        wr = new WebRequest(w.createCrumbedUrl(p.getUrl() + "confirmRename"), HttpMethod.POST);
        wr.setRequestParameters(List.of(new NameValuePair("newName", "baz")));
        w.login("bob", "bob");

        w.setThrowExceptionOnFailingStatusCode(false);
        page = w.getPage(wr);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode());
        assertThat(p.getName(), equalTo("bar"));
    }

    private String checkNameAndReturnError(AbstractItem i, String newName) {
        FormValidation fv = i.doCheckNewName(newName);
        if (FormValidation.Kind.OK.equals(fv.kind)) {
            throw new AssertionError("Expecting Failure");
        } else {
            return fv.getMessage();
        }
    }

    private String getPath(URL u) {
        return u.getPath().substring(j.contextPath.length() + 1);
    }

    @TestExtension("reload")
    public static class TestSaveableListener extends SaveableListener {
        private Saveable saveable;

        private boolean changeCalled;
        private Authentication changeUser;

        private boolean deleteCalled;
        private Authentication deleteUser;

        private void setSaveable(Saveable saveable) {
            this.saveable = saveable;
        }

        public boolean isChangeCalled() {
            return changeCalled;
        }

        public Authentication getChangeUser() {
            return changeUser;
        }

        public boolean isDeleteCalled() {
            return deleteCalled;
        }

        public Authentication getDeleteUser() {
            return deleteUser;
        }

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o == saveable) {
                changeCalled = true;
                changeUser = Jenkins.getAuthentication2();
            }
        }

        @Override
        public void onDeleted(Saveable o, XmlFile file) {
            if (o == saveable) {
                deleteCalled = true;
                deleteUser = Jenkins.getAuthentication2();
            }
        }
    }
}
