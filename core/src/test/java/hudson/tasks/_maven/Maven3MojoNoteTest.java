package hudson.tasks._maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.MarkupText;
import org.junit.jupiter.api.Test;

class Maven3MojoNoteTest {

    @Test
    void testAnnotateMavenPlugin() {
        check("[INFO] <b class=maven-mojo>--- maven-clean-plugin:2.4.1:clean (default-clean) @ jobConfigHistory ---</b>", "[INFO] --- maven-clean-plugin:2.4.1:clean (default-clean) @ jobConfigHistory ---");
    }

    @Test
    void testAnnotateCodehausPlugin() {
        check("[INFO] <b class=maven-mojo>--- cobertura-maven-plugin:2.4:instrument (report:cobertura) @ sardine ---</b>", "[INFO] --- cobertura-maven-plugin:2.4:instrument (report:cobertura) @ sardine ---");

    }

    @Test
    void testAnnotateOtherPlugin() {
        check("[INFO] <b class=maven-mojo>--- gmaven-plugin:1.0-rc-5:generateTestStubs (test-in-groovy) @ jobConfigHistory ---</b>", "[INFO] --- gmaven-plugin:1.0-rc-5:generateTestStubs (test-in-groovy) @ jobConfigHistory ---");
    }

    private void check(final String decorated, final String input) {
        assertTrue(Maven3MojoNote.PATTERN.matcher(input).matches(), input + " does not match" + Maven3MojoNote.PATTERN);
        assertEquals(decorated, annotate(input));
    }

    private String annotate(String text) {
        final MarkupText markupText = new MarkupText(text);
        new Maven3MojoNote().annotate(new Object(), markupText, 0);
        return markupText.toString(true);
    }

}
