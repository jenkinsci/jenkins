package hudson.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * {@link Enumeration} that aggregates multiple {@link Enumeration}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class CompoundEnumeration<T> implements Enumeration<T> {
    private final Iterator<Enumeration<? extends T>> base;

    private Enumeration<? extends T> cur;

    public CompoundEnumeration(Enumeration... e) {
        this(Arrays.asList(e));
    }

    public CompoundEnumeration(Iterable<Enumeration<? extends T>> e) {
        this.base = e.iterator();
        if (this.base.hasNext()) {
            this.cur = this.base.next();
        } else {
            this.cur = Collections.emptyEnumeration();
        }
    }

    @Override
    public boolean hasMoreElements() {
        return advanceToNonEmpty();
    }

    @Override
    public T nextElement() throws NoSuchElementException {
        // Advance here as well, not only in hasMoreElements. Enumeration does not require a
        // caller to consult hasMoreElements first, so a caller that knows an element is there
        // would otherwise get NoSuchElementException whenever an earlier enumeration in the
        // chain is exhausted or was empty to begin with -- as the first one is in
        // PluginFirstClassLoader2#getResources for every resource the plugin does not itself
        // contain. This matches java.lang.ClassLoader's own compound enumeration, which
        // advances in both methods.
        advanceToNonEmpty();
        return cur.nextElement();
    }

    /**
     * Advances {@link #cur} past any exhausted enumerations.
     *
     * @return true if {@link #cur} has an element left to return.
     */
    private boolean advanceToNonEmpty() {
        while (!cur.hasMoreElements() && base.hasNext()) {
            cur = base.next();
        }
        return cur.hasMoreElements();
    }
}
