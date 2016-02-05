package hudson.model;

import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class UserPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-9062")
    public void test() throws Exception {
        User u = User.get("foo");
        u.addProperty(new UserProperty1());
        j.configRoundtrip(u);
        for (UserProperty p : u.getAllProperties())
            assertNotNull(p);
    }

    public static class UserProperty1 extends UserProperty {
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            @Override
            public UserProperty newInstance(User user) {
                return new UserProperty1();
            }
        }
    }

    public static class UserProperty2 extends UserProperty {
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
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
