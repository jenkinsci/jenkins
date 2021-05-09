package jenkins.monitor;

import hudson.util.VersionNumber;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JavaLevelAdminMonitorTest {

    @Test
    public void isActivatedOnJava8() {
        JavaLevelAdminMonitor javaLevelAdminMonitor = new JavaLevelAdminMonitor(
                JavaLevelAdminMonitor.class.getName(),
                new VersionNumber("1.8.0_275")
        );

        assertTrue(javaLevelAdminMonitor.isActivated());
    }

    @Test
    public void isNotActivatedOnJava11() {
        JavaLevelAdminMonitor javaLevelAdminMonitor = new JavaLevelAdminMonitor(
                JavaLevelAdminMonitor.class.getName(),
                new VersionNumber("11.0.9.1")
        );

        assertFalse(javaLevelAdminMonitor.isActivated());
    }
}
