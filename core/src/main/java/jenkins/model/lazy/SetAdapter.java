package jenkins.model.lazy;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.util.AdaptedIterator;
import hudson.util.Iterators;

import java.util.*;

public class SetAdapter<R> implements Set<Map.Entry<Integer, R>> {
    private final Set<Map.Entry<Integer, BuildReference<R>>> core;
    private AbstractLazyLoadRunMap<R> loader;
    public SetAdapter(Set<Map.Entry<Integer, BuildReference<R>>> core, AbstractLazyLoadRunMap<R> loader) {
        this.core = core;
        this.loader = loader;
    }

    @Override
    public int size() {
        return core.size();
    }

    @Override
    public boolean isEmpty() {
        return core.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<Integer, R>> iterator() {
        return Iterators.removeNull(new AdaptedIterator<>(core.iterator()) {
            @Override
            protected Map.Entry<Integer, R> adapt(Map.Entry<Integer, BuildReference<R>> e) {
                return _unwrap(e);
            }
        });
    }

    @Override
    public Object[] toArray() {
        List<Object> list = new ArrayList<>(size());
        for (var e : this) {
            list.add(e);
        }
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return new ArrayList<>(this).toArray(a);
    }

    @Override
    public boolean add(Map.Entry<Integer, R> value) {
        return core.add(_wrap(value));
    }

    @Override
    public boolean remove(Object o) {
//            return core.remove(o);
        // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends Map.Entry<Integer, R>> c) {
        boolean b = false;
        for (Map.Entry<Integer, R> r : c) {
            b |= add(r);
        }
        return b;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean b = false;
        for (Object o : c) {
            b |= remove(o);
        }
        return b;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        core.clear();
    }

    @Override
    public boolean equals(Object o) {
        return core.equals(o);
    }

    @Override
    public int hashCode() {
        return core.hashCode();
    }

    private Map.Entry<Integer, BuildReference<R>> _wrap(Map.Entry<Integer, R> e) {
        return new AbstractMap.SimpleEntry<>(e.getKey(), wrap(e.getValue()));
    }

    private BuildReference<R> wrap(@Nullable R value) {
        if (value == null)    return null;
        return this.loader.createReference(value);
    }

    private R unwrap(@Nullable BuildReference<R> ref) {
        if (ref == null)  return null;

        R v = ref.get();
        if (v == null)
            v = loader.getById(ref.id);
        return v;
    }

    private Map.Entry<Integer, R> _unwrap(Map.Entry<Integer, BuildReference<R>> e) {
        R v = unwrap(e.getValue());
        if (v == null)
            return null;
        return new AbstractMap.SimpleEntry<>(e.getKey(), v);
    }
}
