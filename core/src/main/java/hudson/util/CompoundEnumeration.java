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
        while (!cur.hasMoreElements() && base.hasNext()) {
            cur = base.next();
        }
        return cur.hasMoreElements();
    }

    @Override
    public T nextElement() throws NoSuchElementException {
        return cur.nextElement();
    }
}
