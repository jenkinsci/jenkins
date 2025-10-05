package hudson.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

public class JDKTest {

    /**
     * Test getJavaVersion() method.
     * Verifies that it returns a non-null string.
     */
    @Test
    public void testGetJavaVersion() {
        // Use a system JDK installation if available
        String javaHome = System.getProperty("java.home");
        JDK jdk = new JDK("TestJDK", javaHome);

        String version = jdk.getJavaVersion();

        // The version should never be null
        assertNotNull("Java version should not be null", version);

        // Optionally, check if it looks like a version string
        assertTrue("Java version should contain a dot", version.contains("."));

        // The executable should exist
        File javaExec = new File(javaHome, "bin/java");
        assertTrue("Java executable should exist", javaExec.exists() || new File(javaHome, "bin/java.exe").exists());
    }
}
