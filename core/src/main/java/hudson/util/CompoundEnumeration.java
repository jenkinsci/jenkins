package hudson.util;

import java.util.Arrays;
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
        this((Iterable)Arrays.asList(e));
    }

    public CompoundEnumeration(Iterable<Enumeration<? extends T>> e) {
        this.base = e.iterator();
    }

    public boolean hasMoreElements() {
        while (!cur.hasMoreElements() && base.hasNext()) {
            cur = base.next();
        }
        return cur.hasMoreElements();
    }

    public T nextElement() throws NoSuchElementException {
        return cur.nextElement();
    }
}

