package jenkins.util;

import com.google.common.escape.ArrayBasedCharEscaper;
import com.google.common.escape.CharEscaper;
import com.google.common.escape.Escaper;
import java.util.Map;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public final class SourceCodeEscapers {

    // Suppress default constructor for noninstantiability
    private SourceCodeEscapers() {
        throw new AssertionError();
    }

    private static final Map<Character, String> REPLACEMENTS = Map.of(
            '\b', "\\b",
            '\f', "\\f",
            '\n', "\\n",
            '\r', "\\r",
            '\t', "\\t",
            '\"', "\\\"",
            '\\', "\\\\");

    private static final char PRINTABLE_ASCII_MIN = 0x20;

    private static final char PRINTABLE_ASCII_MAX = 0x7E;

    private static final CharEscaper JAVA_CHAR_ESCAPER = new JavaCharEscaper();

    /**
     * Returns an {@link Escaper} instance that escapes special characters in a string so it can
     * safely be included in either a Java character literal or string literal.
     */
    public static CharEscaper javaCharEscaper() {
        return JAVA_CHAR_ESCAPER;
    }

    private static class JavaCharEscaper extends ArrayBasedCharEscaper {
        JavaCharEscaper() {
            super(REPLACEMENTS, PRINTABLE_ASCII_MIN, PRINTABLE_ASCII_MAX);
        }

        @Override
        protected char[] escapeUnsafe(char c) {
            return String.format("\\u%04X", (int) c).toCharArray();
        }
    }
}
