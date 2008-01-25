package hudson.util;

import java.util.Comparator;
import java.io.Serializable;

/**
 * Case-insensitive string comparator.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CaseInsensitiveComparator implements Comparator<String>, Serializable {
    public static final Comparator<String> INSTANCE = new CaseInsensitiveComparator();

    private CaseInsensitiveComparator() {}

    public int compare(String lhs, String rhs) {
        return lhs.compareToIgnoreCase(rhs);
    }

    private static final long serialVersionUID = 1L;
}
