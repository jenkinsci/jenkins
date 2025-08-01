package jenkins.model.lazy;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import jenkins.model.lazy.AbstractLazyLoadRunMap.Direction;

/**
 * Set that backs {@link AbstractLazyLoadRunMap#entrySet()}.
 *
 * @author Kohsuke Kawaguchi
 */
class LazyLoadRunMapEntrySet<R> extends AbstractSet<Map.Entry<Integer, R>> {
    private final AbstractLazyLoadRunMap<R> owner;
    private final BuildReferenceMapAdapter<R> adapter;

    LazyLoadRunMapEntrySet(AbstractLazyLoadRunMap<R> owner) {
        this.owner = owner;
        this.adapter = new BuildReferenceMapAdapter<>(owner, owner.all());
    }

    private synchronized Set<Map.Entry<Integer, R>> all() {
        return this.adapter.entrySet();
    }

    @Override
    public int size() {
        return all().size();
    }

    @Override
    public boolean isEmpty() {
        return owner.newestBuild() == null;
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof Map.Entry) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object k = e.getKey();
            if (k instanceof Integer) {
                return owner.getByNumber((Integer) k).equals(e.getValue());
            }
        }
        return false;
    }

    @Override
    public Iterator<Map.Entry<Integer, R>> iterator() {
        return new Iterator<>() {
            R last = null;
            R next = owner.newestBuild();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Map.Entry<Integer, R> next() {
                last = next;
                if (last != null) {
                    next = owner.search(owner.getNumberOf(last) - 1, Direction.DESC);
                } else
                    throw new NoSuchElementException();
                return entryOf(last);
            }

            private Map.Entry<Integer, R> entryOf(R r) {
                return new AbstractMap.SimpleImmutableEntry<>(owner.getNumberOf(r), r);
            }

            @Override
            public void remove() {
                if (last == null)
                    throw new UnsupportedOperationException();
                owner.removeValue(last);
            }
        };
    }

    @Override
    public Spliterator<Map.Entry<Integer, R>> spliterator() {
        return Spliterators.spliteratorUnknownSize(
                iterator(), Spliterator.DISTINCT | Spliterator.ORDERED);
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
    public boolean add(Map.Entry<Integer, R> integerREntry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Map.Entry) {
            Map.Entry e = (Map.Entry) o;
            return owner.removeValue((R) e.getValue());
        }
        return false;
    }
}
