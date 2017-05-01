package hudson.slaves;

import static org.junit.Assert.*;

import hudson.model.Computer;
import hudson.security.Permission;
import hudson.security.SidACL;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.Sid;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class CloudTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test @WithoutJenkins @Issue("JENKINS-37616")
    public void provisionPermissionShouldBeIndependentFromAdminister() throws Exception {
        SidACL acl = new SidACL() {
            @Override protected Boolean hasPermission(Sid p, Permission permission) {
                return permission == Cloud.PROVISION;
            }
        };

        assertTrue(acl.hasPermission(Jenkins.ANONYMOUS, Cloud.PROVISION));
        assertFalse(acl.hasPermission(Jenkins.ANONYMOUS, Jenkins.ADMINISTER));
        assertEquals(Cloud.PROVISION, Computer.PERMISSIONS.find("Provision"));
    }

    @Test @Issue("JENKINS-37616")
    public void ensureProvisionPermissionIsLoadable() throws Exception {
        // Name introduced by JENKINS-37616
        Permission p = Permission.fromId("hudson.model.Computer.Provision");
        assertEquals("Provision", p.name);
    }
}
