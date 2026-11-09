package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import java.net.URI;
import java.util.List;
import jenkins.install.InstallState;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-4122")
@WithJenkins
class Security4122Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private Page postCreateFirstAccount(String username) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            WebRequest request = new WebRequest(
                    new URI(wc.getContextPath() + "securityRealm/createFirstAccount").toURL(),
                    HttpMethod.POST);
            request.setRequestParameters(List.of(
                    new NameValuePair("username", username),
                    new NameValuePair("password1", "password"),
                    new NameValuePair("password2", "password"),
                    new NameValuePair("fullname", username),
                    new NameValuePair("email", username + "@example.com")));
            return wc.getPage(request);
        }
    }

    @Test
    void attackerIsBlockedWhenSetupWizardIsActive() throws Exception {
        j.jenkins.setInstallState(InstallState.INITIAL_PLUGINS_INSTALLING);
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        j.jenkins.setCrumbIssuer(null);

        Page response = postCreateFirstAccount("attacker");

        assertEquals(401, response.getWebResponse().getStatusCode());
        assertNull(User.getById("attacker", false));
    }

    @Test
    void attackerIsBlockedWhenSetupWizardOpensZeroUserWindow() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("admin", "initial-password");
        j.jenkins.setInstallState(InstallState.INITIAL_PLUGINS_INSTALLING);
        j.jenkins.setCrumbIssuer(null);

        // Simulate doCreateAdminUser deleting admin before creating replacement
        User admin = User.getById("admin", false);
        assertNotNull(admin);
        admin.delete();
        // hasSomeUser() is now false — this was the exploitable moment before the fix

        Page response = postCreateFirstAccount("attacker");

        assertEquals(401, response.getWebResponse().getStatusCode());
        assertNull(User.getById("attacker", false));
    }

    @Test
    void createFirstAccountIsAllowedWhenSetupIsCompleteAndNoUsersExist() throws Exception {
        j.jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false, false, null));
        j.jenkins.setCrumbIssuer(null);

        Page response = postCreateFirstAccount("firstadmin");

        assertEquals(200, response.getWebResponse().getStatusCode());
        assertNotNull(User.getById("firstadmin", false));
    }

    @Test
    void createFirstAccountIsBlockedWhenAUserAlreadyExists() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("existingadmin", "password");
        j.jenkins.setCrumbIssuer(null);

        Page response = postCreateFirstAccount("attacker");

        assertEquals(401, response.getWebResponse().getStatusCode());
        assertNull(User.getById("attacker", false));
    }
}
