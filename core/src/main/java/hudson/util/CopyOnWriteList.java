package hudson.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * {@link List}-like implementation that has copy-on-write semantics.
 *
 * <p>
 * This class is suitable where the write operation is relatively uncommon.
 *
 * @author Kohsuke Kawaguchi
 */
public class CopyOnWriteList<E> implements Iterable<E> {
    private volatile List<E> core;

    public CopyOnWriteList(List<E> core) {
        this.core = new ArrayList<E>(core);
    }

    public CopyOnWriteList() {
        this.core = Collections.emptyList();
    }

    public synchronized void add(E e) {
        List<E> n = new ArrayList<E>(core);
        n.add(e);
        core = n;
    }

    /**
     * Removes an item from the list.
     *
     * @return
     *      true if the list contained the item. False if it didn't,
     *      in which case there's no change.
     */
    public synchronized boolean remove(E e) {
        List<E> n = new ArrayList<E>(core);
        boolean r = n.remove(e);
        core = n;
        return r;
    }

    /**
     * Returns an iterator.
     *
     * The returned iterator doesn't support the <tt>remove</tt> operation.
     */
    public Iterator<E> iterator() {
        final Iterator<E> itr = core.iterator();
        return new Iterator<E>() {
            public boolean hasNext() {
                return itr.hasNext();
            }

            public E next() {
                return itr.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
