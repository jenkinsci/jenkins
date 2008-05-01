package hudson.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a two dimensional area.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.213
 */
public final class Area {
    public final int width;
    public final int height;

    public Area(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Opposite of {@link #toString()}.
     */
    public static Area parse(String s) {
        Matcher m = PATTERN.matcher(s);
        if(m.matches())
            return new Area(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)));
        return null;
    }

    public int area() {
        return width*height;
    }

    public String toString() {
        return width+"x"+height;
    }

    private static final Pattern PATTERN = Pattern.compile("(\\d+)x(\\d+)");
}
