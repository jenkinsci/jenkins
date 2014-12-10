package jenkins.model.lazy;

import jenkins.model.lazy.AbstractLazyLoadRunMap.Direction;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Set that backs {@link AbstractLazyLoadRunMap#entrySet()}.
 *
 * @author Kohsuke Kawaguchi
 */
class LazyLoadRunMapEntrySet<R> extends AbstractSet<Entry<Integer,R>> {
    private final AbstractLazyLoadRunMap<R> owner;

    /**
     * Lazily loaded all entries.
     */
    private Set<Entry<Integer,R>> all;

    LazyLoadRunMapEntrySet(AbstractLazyLoadRunMap<R> owner) {
        this.owner = owner;
    }

    private synchronized Set<Entry<Integer,R>> all() {
        if (all==null)
            all = new BuildReferenceMapAdapter<R>(owner,owner.all()).entrySet();
        return all;
    }

    synchronized void clearCache() {
        all = null;
    }

    @Override
    public int size() {
        return all().size();
    }

    @Override
    public boolean isEmpty() {
        return owner.newestBuild()==null;
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Entry) {
            Entry e = (Entry) o;
            Object k = e.getKey();
            if (k instanceof Integer) {
                return owner.getByNumber((Integer)k).equals(e.getValue());
            }
        }
        return false;
    }

    @Override
    public Iterator<Entry<Integer,R>> iterator() {
        return new Iterator<Entry<Integer,R>>() {
            R last = null;
            R next = owner.newestBuild();

            public boolean hasNext() {
                return next!=null;
            }

            public Entry<Integer,R> next() {
                last = next;
                if (last!=null) {
                    next = owner.search(owner.getNumberOf(last)-1, Direction.DESC);
                } else
                    throw new NoSuchElementException();
                return entryOf(last);
            }

            private Entry<Integer, R> entryOf(R r) {
                return new SimpleImmutableEntry<Integer, R>(owner.getNumberOf(r),r);
            }

            public void remove() {
                if (last==null)
                    throw new UnsupportedOperationException();
                owner.removeValue(last);
            }
        };
    }

    @Override
    public Object[] toArray() {
        return all().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return all().toArray(a);
    }

    @Override
    public boolean add(Entry<Integer, R> integerREntry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Entry) {
            Entry e = (Entry) o;
            return owner.removeValue((R)e.getValue());
        }
        return false;
    }
}
