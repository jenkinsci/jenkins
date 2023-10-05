package executable;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void unsupported() {
        assertJavaCheckFails(8, false);
        assertJavaCheckFails(8, true);
    }

    @Test
    void supported() {
        assertJavaCheckPasses(11, false);
        assertJavaCheckPasses(11, true);
        assertJavaCheckPasses(17, false);
        assertJavaCheckPasses(17, true);
    }

    @Test
    void future() {
        assertJavaCheckFails(12, false);
        assertJavaCheckFails(13, false);
        assertJavaCheckFails(14, false);
        assertJavaCheckFails(15, false);
        assertJavaCheckFails(16, false);
        assertJavaCheckFails(18, false);
        assertJavaCheckFails(19, false);
        assertJavaCheckFails(20, false);
        assertJavaCheckPasses(12, true);
        assertJavaCheckPasses(13, true);
        assertJavaCheckPasses(14, true);
        assertJavaCheckPasses(15, true);
        assertJavaCheckPasses(16, true);
        assertJavaCheckPasses(18, true);
        assertJavaCheckPasses(19, true);
        assertJavaCheckPasses(20, true);
    }

    private static void assertJavaCheckFails(int releaseVersion, boolean enableFutureJava) {
        assertJavaCheckFails(null, releaseVersion, enableFutureJava);
    }

    private static void assertJavaCheckFails(
            @CheckForNull String message, int releaseVersion, boolean enableFutureJava) {
        if (message == null) {
            message = String.format(
                    "Java version check should have failed for Java version %d and enableFutureJava=%b",
                    releaseVersion, enableFutureJava);
        }

        // TODO use assertThrows once we drop support for Java 8 in this module
        boolean failed;
        try {
            Main.verifyJavaVersion(releaseVersion, enableFutureJava);
            failed = false;
        } catch (UnsupportedClassVersionError error) {
            failed = true;
        }
        if (!failed) {
            throw new AssertionError(message);
        }
    }

    private static void assertJavaCheckPasses(int releaseVersion, boolean enableFutureJava) {
        assertJavaCheckPasses(null, releaseVersion, enableFutureJava);
    }

    private static void assertJavaCheckPasses(
            @CheckForNull String message, int releaseVersion, boolean enableFutureJava) {
        if (message == null) {
            message = String.format(
                    "Java version check should have passed for Java version %d and enableFutureJava=%b",
                    releaseVersion, enableFutureJava);
        }
        try {
            Main.verifyJavaVersion(releaseVersion, enableFutureJava);
        } catch (UnsupportedClassVersionError e) {
            throw new AssertionError(message, e);
        }
    }
}
