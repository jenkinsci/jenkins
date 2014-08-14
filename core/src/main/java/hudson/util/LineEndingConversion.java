package hudson.util;

/**
 * Converts line endings of a string.
 *
 * @author David Ruhmann
 */
public class LineEndingConversion {

    /**
     * Supported line ending types for conversion
     */
    public enum EOLType {
        Unix,
        Windows
    }

    /**
     * Convert line endings to type.
     */
    public static String convertEOL(String input, EOLType type) {
        // Convert line endings to Unix LF
        input = input.replace("\r\n","\n");
        input = input.replace("\r","\n");
        if (EOLType.Unix == type)
        {
            return input;
        }
        // Convert line endings to Windows CR/LF
        input = input.replace("\n","\r\n");
        return input;
    }
}
