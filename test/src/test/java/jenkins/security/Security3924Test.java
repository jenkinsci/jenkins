package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("SECURITY-3924")
class Security3924Test {

    private static final String ADMIN = "admin";
    /** {@code "admın"} — the 4th character is U+0131 LATIN SMALL LETTER DOTLESS I. */
    private static final String ATTACKER = "admın";

    private static final String ADMINS_GROUP = "admins";
    /** {@code "admıns"} — the 4th character is U+0131 LATIN SMALL LETTER DOTLESS I. */
    private static final String ATTACKER_GROUP = "admıns";

    @Test
    void dotlessAdminIsNotTreatedAsAdmin(JenkinsRule j) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to(ATTACKER)
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN));

        User admin = User.getById(ADMIN, true);
        User attacker = User.getById(ATTACKER, true);

        assertThat(admin.getId(), is(not(attacker.getId())));

        assertFalse(admin.getACL().hasPermission2(attacker.impersonate2(), Jenkins.ADMINISTER));

        assertTrue(admin.getACL().hasPermission2(admin.impersonate2(), Jenkins.ADMINISTER));
    }

    @Test
    void dotlessAdminsGroupIsNotTreatedAsAdminsGroup(JenkinsRule j) {
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups(ATTACKER, ATTACKER_GROUP);
        realm.addGroups(ADMIN, ADMINS_GROUP);
        j.jenkins.setSecurityRealm(realm);

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "authenticated");
        auth.add(Jenkins.ADMINISTER, ADMINS_GROUP);
        j.jenkins.setAuthorizationStrategy(auth);

        User admin = User.getById(ADMIN, true);
        User attacker = User.getById(ATTACKER, true);

        assertThat(admin.getId(), is(not(attacker.getId())));

        assertTrue(j.jenkins.getACL().hasPermission2(admin.impersonate2(), Jenkins.ADMINISTER));
        assertFalse(j.jenkins.getACL().hasPermission2(attacker.impersonate2(), Jenkins.ADMINISTER));
    }
}
