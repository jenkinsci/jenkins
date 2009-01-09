package hudson.security;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Email;
import hudson.model.Hudson;

/**
 * @author Kohsuke Kawaguchi
 */
public class PermissionGroupTest extends HudsonTestCase {
    /**
     * "Overall" persmission group should be always the first.
     */
    @Email("http://www.nabble.com/Master-slave-refactor-td21361880.html")
    public void testOrder() {
        assertSame(PermissionGroup.getAll().get(0),Hudson.PERMISSIONS);
    }
}
