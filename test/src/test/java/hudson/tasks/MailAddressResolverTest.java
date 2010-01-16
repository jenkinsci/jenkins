package hudson.tasks;

import hudson.model.User;
import hudson.tasks.Mailer.UserProperty;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class MailAddressResolverTest extends HudsonTestCase {
    @Bug(5164)
    public void test5164() {
        Mailer.descriptor().setDefaultSuffix("@example.com");
        String a = User.get("DOMAIN\\user").getProperty(UserProperty.class).getAddress();
        assertEquals("user@example.com",a);
    }
}
