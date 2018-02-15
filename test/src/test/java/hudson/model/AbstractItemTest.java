package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException2;
import hudson.util.FormValidation;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class AbstractItemTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the reload functionality
     */
    @Test
    public void reload() throws Exception {
        Jenkins jenkins = j.jenkins;
        FreeStyleProject p = jenkins.createProject(FreeStyleProject.class, "foo");
        p.setDescription("Hello World");

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        b.setDescription("This is my build");

        // update on disk representation
        File f = p.getConfigFile().getFile();
        FileUtils.writeStringToFile(f, FileUtils.readFileToString(f).replaceAll("Hello World", "Good Evening"));

        // reload away
        p.doReload();

        assertEquals("Good Evening", p.getDescription());

        FreeStyleBuild b2 = p.getBuildByNumber(1);

        assertNotEquals(b, b2); // should be different object
        assertEquals(b.getDescription(), b2.getDescription()); // but should have the same properties
    }

    @Test
    public void checkRenameValidity() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo");
        p.getBuildersList().add(new SleepBuilder(10));

        assertThat(renameAndReturnError(p, ""), equalTo(Messages.Hudson_NoName()));
        assertThat(renameAndReturnError(p, ".."), equalTo(Messages.Jenkins_NotAllowedName("..")));
        assertThat(renameAndReturnError(p, "50%"), equalTo(Messages.Hudson_UnsafeChar('%')));
        assertThat(renameAndReturnError(p, "foo"), equalTo(Messages.AbstractItem_NewNameInUse("foo")));

        j.jenkins.setProjectNamingStrategy(new ProjectNamingStrategy.PatternProjectNamingStrategy("bar", "", false));
        assertThat(renameAndReturnError(p, "foo1"), equalTo(jenkins.model.Messages.Hudson_JobNameConventionNotApplyed("foo1", "bar")));

        p.scheduleBuild2(0).waitForStart();
        assertThat(renameAndReturnError(p, "bar"), equalTo(Messages.Job_NoRenameWhileBuilding()));
    }

    @Test
    public void checkRenamePermissions() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Item.CONFIGURE).everywhere().to("alice", "bob");
        mas.grant(Item.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(mas);
        FreeStyleProject p = j.createFreeStyleProject("foo");

        try (ACLContext unused = ACL.as(User.getById("alice", true))) {
            assertThat(renameAndReturnError(p, "foo"), equalTo(Messages.AbstractItem_NewNameInUse("foo")));
        }
        try (ACLContext unused = ACL.as(User.getById("bob", true))) {
            assertThat(renameAndReturnError(p, "foo"), equalTo(Messages.Jenkins_NotAllowedName("foo")));
        }
        try (ACLContext unused = ACL.as(User.getById("carol", true))) {
            try {
                p.doCheckNewName("foo");
                fail("Expecting AccessDeniedException");
            } catch (AccessDeniedException2 e) {
                assertThat(e.permission, equalTo(Item.CREATE));
            }
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
        WebRequest wr = new WebRequest(w.createCrumbedUrl(p.getUrl() + "doRename"), HttpMethod.POST);
        wr.setRequestParameters(Arrays.asList(new NameValuePair("newName", "bar")));
        w.login("alice", "alice");
        assertThat(getPath(w.getPage(wr).getUrl()), equalTo(p.getUrl()));
        assertThat(p.getName(), equalTo("bar"));

        wr = new WebRequest(w.createCrumbedUrl(p.getUrl() + "doRename"), HttpMethod.POST);
        wr.setRequestParameters(Arrays.asList(new NameValuePair("newName", "baz")));
        w.login("bob", "bob");
        try {
            assertThat(getPath(w.getPage(wr).getUrl()), equalTo(p.getUrl()));
            fail("Expecting HTTP 403 Forbidden");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(), equalTo(403));
        }
        assertThat(p.getName(), equalTo("bar"));
    }

    private String renameAndReturnError(AbstractItem i, String newName) {
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

}
