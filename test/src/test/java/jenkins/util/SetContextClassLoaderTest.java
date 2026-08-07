package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class SetContextClassLoaderTest {

    @RegisterExtension
    private final RealJenkinsExtension rr = new RealJenkinsExtension();

    @Test
    void positive() throws Throwable {
        rr.then(SetContextClassLoaderTest::_positive);
    }

    private static void _positive(JenkinsRule r) throws ClassNotFoundException {
        try (SetContextClassLoader sccl = new SetContextClassLoader(r.getPluginManager().uberClassLoader)) {
            assertEquals("hudson.tasks.Mailer$UserProperty", getUserPropertyClass().getName());
        }
    }

    @Test
    void negative() throws Throwable {
        rr.then(SetContextClassLoaderTest::_negative);
    }

    private static void _negative(JenkinsRule r) {
        assertThrows(ClassNotFoundException.class, SetContextClassLoaderTest::getUserPropertyClass);
    }

    private static Class<?> getUserPropertyClass() throws ClassNotFoundException {
        return Class.forName(
                "hudson.tasks.Mailer$UserProperty",
                true,
                Thread.currentThread().getContextClassLoader());
    }
}
