package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverageImprovementTest {

    // FormValidation.validateNonNegativeInteger (lines 378-386, 8 lines)
    @Test
    void nonNegativeInteger_zero() {
        assertEquals(Kind.OK, FormValidation.validateNonNegativeInteger("0").kind);
    }

    @Test
    void nonNegativeInteger_negative() {
        assertEquals(Kind.ERROR, FormValidation.validateNonNegativeInteger("-1").kind);
    }

    @Test
    void nonNegativeInteger_notANumber() {
        assertEquals(Kind.ERROR, FormValidation.validateNonNegativeInteger("abc").kind);
    }

    // FormValidation.validatePositiveInteger (lines 415-423, 8 lines)
    @Test
    void positiveInteger_one() {
        assertEquals(Kind.OK, FormValidation.validatePositiveInteger("1").kind);
    }

    @Test
    void positiveInteger_zero() {
        assertEquals(Kind.ERROR, FormValidation.validatePositiveInteger("0").kind);
    }

    @Test
    void positiveInteger_notANumber() {
        assertEquals(Kind.ERROR, FormValidation.validatePositiveInteger("xyz").kind);
    }

    // FormValidation.validateRequired (lines 428-432, 4 lines)
    @Test
    void validateRequired_ok() {
        assertEquals(Kind.OK, FormValidation.validateRequired("hello").kind);
    }

    @Test
    void validateRequired_null() {
        assertEquals(Kind.ERROR, FormValidation.validateRequired(null).kind);
    }

    // FormValidation.validateBase64 (lines 445-461, 16 lines)
    @Test
    void validateBase64_valid() {
        assertEquals(Kind.OK, FormValidation.validateBase64("aGVsbG8=", false, false, "bad").kind);
    }

    @Test
    void validateBase64_withSpace() {
        assertEquals(Kind.ERROR, FormValidation.validateBase64("aGVs bG8=", false, false, "bad").kind);
    }

    @Test
    void validateBase64_empty() {
        assertEquals(Kind.ERROR, FormValidation.validateBase64("", false, false, "bad").kind);
    }

    @Test
    void validateBase64_allowEmpty() {
        assertEquals(Kind.OK, FormValidation.validateBase64("", true, true, "bad").kind);
    }

    // FormValidation.aggregate (lines 229-246, 16 lines)
    @Test
    void aggregate_empty() {
        assertEquals(Kind.OK, FormValidation.aggregate(List.of()).kind);
    }

    @Test
    void aggregate_picksWorst() {
        FormValidation ok = FormValidation.ok("fine");
        FormValidation err = FormValidation.error("bad");
        assertEquals(Kind.ERROR, FormValidation.aggregate(List.of(ok, err)).kind);
    }

    // FormValidation factory methods with throwable (lines 194-220, ~20 lines)
    @Test
    void errorWithThrowable() {
        FormValidation fv = FormValidation.error(new Exception("oops"), "failed");
        assertEquals(Kind.ERROR, fv.kind);
    }

    @Test
    void warningWithThrowable() {
        FormValidation fv = FormValidation.warning(new Exception("hmm"), "check this");
        assertEquals(Kind.WARNING, fv.kind);
    }

    // Util.xmlEscape (lines 1048-1064, 16 lines)
    @Test
    void xmlEscape_basic() {
        assertEquals("&lt;b&gt;", Util.xmlEscape("<b>"));
        assertEquals("a &amp; b", Util.xmlEscape("a & b"));
        assertEquals("hello", Util.xmlEscape("hello"));
    }

    // Util.isRelativePath (lines 391-405, 14 lines)
    @Test
    void isRelativePath_relative() {
        assertTrue(Util.isRelativePath("foo/bar"));
    }

    @Test
    void isRelativePath_absoluteUnix() {
        assertFalse(Util.isRelativePath("/usr/bin"));
    }

    @Test
    void isRelativePath_absoluteWindows() {
        assertFalse(Util.isRelativePath("C:\\Windows"));
    }

    @Test
    void isRelativePath_uncPath() {
        assertFalse(Util.isRelativePath("\\\\server\\share\\file"));
    }

    // Util.getFileName (lines 1191-1199, 8 lines)
    @Test
    void getFileName_unix() {
        assertEquals("file.txt", Util.getFileName("/home/user/file.txt"));
    }

    @Test
    void getFileName_windows() {
        assertEquals("file.txt", Util.getFileName("C:\\Users\\file.txt"));
    }

    // Util.fixNull / fixEmpty / fixEmptyAndTrim (lines 1097-1129)
    @Test
    void fixNull_returnsEmpty() {
        assertEquals("", Util.fixNull((String) null));
    }

    @Test
    void fixEmpty_returnsNull() {
        assertNull(Util.fixEmpty(""));
        assertEquals("hi", Util.fixEmpty("hi"));
    }

    @Test
    void fixEmptyAndTrim_whitespace() {
        assertNull(Util.fixEmptyAndTrim("   "));
        assertEquals("hello", Util.fixEmptyAndTrim("  hello  "));
    }

    // Util.removeTrailingSlash (lines 604-607, 3 lines)
    @Test
    void removeTrailingSlash() {
        assertEquals("/path", Util.removeTrailingSlash("/path/"));
        assertEquals("/path", Util.removeTrailingSlash("/path"));
    }

    // Util.ensureEndsWith (lines 620-627, 7 lines)
    @Test
    void ensureEndsWith() {
        assertEquals("file.txt", Util.ensureEndsWith("file", ".txt"));
        assertEquals("file.txt", Util.ensureEndsWith("file.txt", ".txt"));
        assertNull(Util.ensureEndsWith(null, ".txt"));
    }

    // Util.tryParseNumber (lines 1492-1501, 9 lines)
    @Test
    void tryParseNumber_valid() {
        assertEquals(42L, Util.tryParseNumber("42", 0));
    }

    @Test
    void tryParseNumber_invalid() {
        assertEquals(99, Util.tryParseNumber("abc", 99));
        assertEquals(7, Util.tryParseNumber(null, 7));
    }
}
