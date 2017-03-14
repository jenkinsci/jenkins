package hudson.slaves;

import static org.junit.Assert.*;

import hudson.model.Computer;
import hudson.security.Permission;
import hudson.security.SidACL;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.Sid;
import org.junit.Test;

public class CloudTest {

    @Test
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
}
