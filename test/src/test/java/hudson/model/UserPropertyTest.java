package hudson.model;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class UserPropertyTest extends HudsonTestCase {
    @Bug(9062)
    public void test9062() throws Exception {
        User u = User.get("foo");
        u.addProperty(new UserProperty1());
        configRoundtrip(u);
        for (UserProperty p : u.getAllProperties())
            assertNotNull(p);
    }

    public static class UserProperty1 extends UserProperty {
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            public String getDisplayName() {
                return "UserProperty1";
            }

            @Override
            public UserProperty newInstance(User user) {
                return new UserProperty1();
            }
        }
    }

    public static class UserProperty2 extends UserProperty {
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            public String getDisplayName() {
                return "UserProperty2";
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public UserProperty newInstance(User user) {
                return new UserProperty1();
            }
        }
    }
}
