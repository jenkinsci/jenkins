package executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void unsupported() {
        assertJavaCheckFails(8, false);
        assertJavaCheckFails(8, true);
        assertJavaCheckFails(11, false);
        assertJavaCheckFails(11, true);
        assertJavaCheckFails(17, false);
        assertJavaCheckFails(17, true);
    }

    @Test
    void supported() {
        assertJavaCheckPasses(21, false);
        assertJavaCheckPasses(21, true);
        assertJavaCheckPasses(25, false);
        assertJavaCheckPasses(25, true);
    }

    @Test
    void future() {
        assertJavaCheckFails(22, false);
        assertJavaCheckFails(23, false);
        assertJavaCheckFails(24, false);
        assertJavaCheckPasses(22, true);
        assertJavaCheckPasses(23, true);
        assertJavaCheckPasses(24, true);
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

        assertThrows(
                UnsupportedClassVersionError.class,
                () -> Main.verifyJavaVersion(releaseVersion, enableFutureJava),
                message);
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
