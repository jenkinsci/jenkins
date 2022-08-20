package executable;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void shouldFailForOldJava() {
        assertJavaCheckFails(52, false);
        assertJavaCheckFails(52, true);
    }

    @Test
    void shouldBeOkForJava11() {
        assertJavaCheckPasses(55, false);
        assertJavaCheckPasses(55, true);
    }

    @Test
    void shouldFailForMidJavaVersionsIfNoFlag() {
        assertJavaCheckFails(56, false);
        assertJavaCheckPasses(56, true);
        assertJavaCheckFails(57, false);
        assertJavaCheckPasses(57, true);
        assertJavaCheckFails(58, false);
        assertJavaCheckPasses(58, true);
        assertJavaCheckFails(59, false);
        assertJavaCheckPasses(59, true);
        assertJavaCheckFails(60, false);
        assertJavaCheckPasses(60, true);
    }

    @Test
    void shouldBeOkForJava17() {
        assertJavaCheckPasses(61, false);
        assertJavaCheckPasses(61, true);
    }

    @Test
    void shouldFailForNewJavaVersionsIfNoFlag() {
        assertJavaCheckFails(62, false);
        assertJavaCheckPasses(62, true);
        assertJavaCheckFails(63, false);
        assertJavaCheckPasses(63, true);
    }

    private static void assertJavaCheckFails(int classVersion, boolean enableFutureJava) {
        assertJavaCheckFails(null, classVersion, enableFutureJava);
    }

    private static void assertJavaCheckFails(@CheckForNull String message, int classVersion, boolean enableFutureJava) {
        boolean failed = false;
        try {
            Main.verifyJavaVersion(classVersion, enableFutureJava);
        } catch (Error error) {
            failed = true;
            System.out.printf("Java class version check failed as it was expected for Java class version %s.0 and enableFutureJava=%s%n",
                classVersion, enableFutureJava);
            error.printStackTrace(System.out);
        }

        if (!failed) {
            Assertions.fail(message != null ? message :
                    String.format("Java version Check should have failed for Java class version %s.0 and enableFutureJava=%s",
                            classVersion, enableFutureJava));
        }
    }

    private static void assertJavaCheckPasses(int classVersion, boolean enableFutureJava) {
        assertJavaCheckPasses(null, classVersion, enableFutureJava);
    }

    private static void assertJavaCheckPasses(@CheckForNull String message, int classVersion, boolean enableFutureJava) {
        try {
            Main.verifyJavaVersion(classVersion, enableFutureJava);
        } catch (Error error) {
            throw new AssertionError(message != null ? message :
                    String.format("Java version Check should have passed for Java class version %s.0 and enableFutureJava=%s",
                            classVersion, enableFutureJava), error);
        }
    }
}
