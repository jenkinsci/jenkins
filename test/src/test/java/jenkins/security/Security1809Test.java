package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.View;
import hudson.slaves.DumbSlave;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security1809Test {

    private JenkinsRule j;

    private final String password = "p4ssw0rd";

    private final Secret secretPassword = Secret.fromString(password);

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-1809")
    void passwordIsMaskedForView() throws Exception {
        final PasswordView view = new PasswordView("view1", secretPassword);
        j.jenkins.addView(view);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, View.READ).everywhere().to("readUser")
                .grant(Jenkins.READ, View.READ, View.CONFIGURE).everywhere().to("configureUser"));

        String url = view.getUrl() + "password";

        // configure permission allow to see encrypted value
        assertContainsOnlyEncryptedSecret("configureUser", url);

        // read permission get only redacted value
        assertContainsOnlyMaskedSecret("readUser", url);
    }

    @Test
    @Issue("SECURITY-1809")
    void passwordIsMaskedForPrimaryView() throws Exception {
        final PasswordView view = new PasswordView("view1", secretPassword);
        j.jenkins.addView(view);
        j.jenkins.setPrimaryView(view);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("readUser")
                .grant(Jenkins.READ, View.READ, View.CONFIGURE).everywhere().to("configureUser"));

        String url = "password";

        // configure permission allow to see encrypted value
        assertContainsOnlyEncryptedSecret("configureUser", url);

        // read permission get only redacted value
        assertContainsOnlyMaskedSecret("readUser", url);
    }

    @Test
    void passwordIsMaskedForAgent() throws Exception {
        final DumbSlave agent = j.createSlave("agent1", "", null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Computer.CONFIGURE).everywhere().to("configureUser")
                .grant(Jenkins.READ).everywhere().to("readUser"));

        agent.toComputer().addAction(new PasswordAction(secretPassword));

        String url = agent.toComputer().getUrl() + "password";

        // configure permission allow to see encrypted value
        assertContainsOnlyEncryptedSecret("configureUser", url);

        // read permission get only redacted value
        assertContainsOnlyMaskedSecret("readUser", url);
    }

    @Test
    void passwordIsMaskedForJob() throws Exception {
        final FreeStyleProject job = j.createFreeStyleProject();
        FreeStyleBuild build = j.buildAndAssertSuccess(job);
        build.addAction(new PasswordAction(secretPassword));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE).everywhere().to("configureUser")
                .grant(Jenkins.READ, Item.READ).everywhere().to("readUser"));

        String url = build.getUrl() + "password";

        // configure permission allow to see encrypted value
        assertContainsOnlyEncryptedSecret("configureUser", url);

        // read permission get only redacted value
        assertContainsOnlyMaskedSecret("readUser", url);
    }

    @Test
    void permissionIsCheckedOnClosestAncestor() throws Exception {
        final PasswordView view = new PasswordView("view1", secretPassword);
        j.jenkins.addView(view);

        final FreeStyleProject job = j.createFreeStyleProject("job1");
        FreeStyleBuild build = j.buildAndAssertSuccess(job);
        build.addAction(new ActionWithView(view));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ, View.READ, Item.CONFIGURE).everywhere().to("itemConfigureUser")
                .grant(Jenkins.READ, Item.READ, View.READ, View.CONFIGURE).everywhere().to("viewConfigureUser"));

        String url = build.getUrl() + "myAction/view/password";

        // View/Configure permission allow to see encrypted value
        assertContainsOnlyEncryptedSecret("viewConfigureUser", url);

        // Item/Configure permission get only redacted value
        assertContainsOnlyMaskedSecret("itemConfigureUser", url);
    }

    @Test
    @Issue("SECURITY-1809")
    void permissionIsCorrectlyCheckedOnNestedObject() throws Exception {
        final Folder folder = j.jenkins.createProject(Folder.class, "folder1");
        final FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job1");
        FreeStyleBuild build = j.buildAndAssertSuccess(job);
        build.addAction(new PasswordAction(secretPassword));

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // Item.CONFIGURE on job1 but NOT on folder1
                .grant(Jenkins.READ, Item.READ).everywhere().to("jobConfigureUser")
                .grant(Item.CONFIGURE).onItems(job).to("jobConfigureUser")
                // Item.CONFIGURE on folder1 but NOT on job1
                .grant(Jenkins.READ, Item.READ).everywhere().to("folderConfigureUser")
                .grant(Item.CONFIGURE).onItems(folder).to("folderConfigureUser"));

        String url = build.getUrl() + "password";

        // Item/Configure permission on job1 allow to see encrypted value
        assertContainsOnlyEncryptedSecret("jobConfigureUser", url);

        // Item/Configure permission only on folder1 get only redacted value
        assertContainsOnlyMaskedSecret("folderConfigureUser", url);
    }

    private void assertContainsOnlyEncryptedSecret(String user, String url) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient().login(user)) {
            Page page = wc.goTo(url);
            String content = page.getWebResponse().getContentAsString();

            assertThat(content, not(containsString(password)));
            assertThat(content, containsString(secretPassword.getEncryptedValue()));
        }
    }

    private void assertContainsOnlyMaskedSecret(String user, String url) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient().login(user)) {
            Page page = wc.goTo(url);
            String content = page.getWebResponse().getContentAsString();

            assertThat(content, containsString("********"));
            assertThat(content, not(containsString(password)));
            assertThat(content, not(containsString(secretPassword.getEncryptedValue())));
        }
    }

    public static class PasswordView extends ListView {
        private final Secret secret;

        PasswordView(String name, Secret secret) {
            super(name);
            this.secret = secret;
        }

        public Secret getSecret() {
            return secret;
        }
    }

    public static class PasswordAction implements Action {
        private final Secret secret;

        PasswordAction(Secret secret) {
            this.secret = secret;
        }

        public Secret getSecret() {
            return secret;
        }

        @Override
        public String getIconFileName() { return null; }

        @Override
        public String getDisplayName() { return null; }

        @Override
        public String getUrlName() { return "password"; }
    }

    public static class ActionWithView implements Action {
        private final PasswordView view;

        ActionWithView(PasswordView view) {
            this.view = view;
        }

        public PasswordView getView() {
            return view;
        }

        @Override
        public String getIconFileName() { return null; }

        @Override
        public String getDisplayName() { return null; }

        @Override
        public String getUrlName() { return "myAction"; }
    }
}
