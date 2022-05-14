package jenkins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class SetContextClassLoaderTest {

    @Rule public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void positive() throws Throwable {
        rr.then(SetContextClassLoaderTest::_positive);
    }

    private static void _positive(JenkinsRule r) throws ClassNotFoundException {
        try (SetContextClassLoader sccl = new SetContextClassLoader(RealJenkinsRule.Endpoint.class)) {
            assertEquals("hudson.tasks.Mailer$UserProperty", getUserPropertyClass().getName());
        }
    }

    @Test
    public void negative() throws Throwable {
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
