package jenkins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SourceCodeEscapersTest {

    @Test
    public void testJavaCharEscaper() {
        assertThrows(NullPointerException.class, () -> SourceCodeEscapers.javaCharEscaper()
                .escape(null));
        assertEquals("", SourceCodeEscapers.javaCharEscaper().escape(""));

        assertEquals("foo", SourceCodeEscapers.javaCharEscaper().escape("foo"));
        assertEquals("\\t", SourceCodeEscapers.javaCharEscaper().escape("\t"));
        assertEquals("\\\\", SourceCodeEscapers.javaCharEscaper().escape("\\"));
        assertEquals("'", SourceCodeEscapers.javaCharEscaper().escape("'"));
        assertEquals("\\\\\\b\\t\\r", SourceCodeEscapers.javaCharEscaper().escape("\\\b\t\r"));
        assertEquals("\\u1234", SourceCodeEscapers.javaCharEscaper().escape("\u1234"));
        assertEquals("\\u0234", SourceCodeEscapers.javaCharEscaper().escape("\u0234"));
        assertEquals("\\u00EF", SourceCodeEscapers.javaCharEscaper().escape("\u00ef"));
        assertEquals("\\u0001", SourceCodeEscapers.javaCharEscaper().escape("\u0001"));
        assertEquals("\\uABCD", SourceCodeEscapers.javaCharEscaper().escape("\uabcd"));

        assertEquals(
                "He didn't say, \\\"stop!\\\"",
                SourceCodeEscapers.javaCharEscaper().escape("He didn't say, \"stop!\""));
        assertEquals(
                "This space is non-breaking:\\u00A0",
                SourceCodeEscapers.javaCharEscaper().escape("This space is non-breaking:\u00a0"));
        assertEquals(
                "\\uABCD\\u1234\\u012C", SourceCodeEscapers.javaCharEscaper().escape("\uABCD\u1234\u012C"));
        assertEquals(
                "\\uD83D\\uDC80\\uD83D\\uDD14",
                SourceCodeEscapers.javaCharEscaper().escape("\ud83d\udc80\ud83d\udd14"));
        assertEquals(
                "String with a slash (/) in it",
                SourceCodeEscapers.javaCharEscaper().escape("String with a slash (/) in it"));
    }
}
