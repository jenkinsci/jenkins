package hudson.util;

/**
 * Converts line endings of a string.
 *
 * @since 1.582
 * @author David Ruhmann
 */
public class LineEndingConversion {

    /**
     * Supported line ending types for conversion
     */
    public enum EOLType {
        CR,
        CRLF,
        LF,
        LFCR,
        Mac,
        Unix,
        Windows
    }

    /**
     * Convert line endings of a string to the given type.  Default to Unix type.
     *
     * @param input
     *     The string containing line endings to be converted.
     * @param type
     *     Type of line endings to convert the string into.
     * @return
     *     String updated with the new line endings or null if given null.
     */
    public static String convertEOL(String input, EOLType type) {
        if (null == input || input.isEmpty()) {
            return input;
        }
        // Convert line endings to Unix LF,
        // which also sets up the string for other conversions
        input = input.replace("\r\n", "\n");
        input = input.replace('\r', '\n');
        switch (type) {
            case CR:
            case Mac:
                // Convert line endings to CR
                input = input.replace('\n', '\r');
                break;
            case CRLF:
            case Windows:
                // Convert line endings to Windows CR/LF
                input = input.replace("\n", "\r\n");
                break;
            case LFCR:
                // Convert line endings to LF/CR
                input = input.replace("\n", "\n\r");
                break;
            case LF:
            case Unix:
            default:
                // Conversion already completed
                return input;
        }
        return input;
    }
}
