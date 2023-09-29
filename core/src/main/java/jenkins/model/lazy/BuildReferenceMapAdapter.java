package jenkins.model.lazy;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.util.AdaptedIterator;
import hudson.util.Iterators;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Take {@code SortedMap<Integer,BuildReference<R>>} and make it look like {@code SortedMap<Integer,R>}.
 *
 * When {@link BuildReference} lost the build object, we'll use {@link AbstractLazyLoadRunMap#getById(String)}
 * to obtain one.
 *
 * @author Kohsuke Kawaguchi
 */
class BuildReferenceMapAdapter<R> implements SortedMap<Integer, R> {
    private final AbstractLazyLoadRunMap<R> loader;

    private final SortedMap<Integer, BuildReference<R>> core;

    BuildReferenceMapAdapter(AbstractLazyLoadRunMap<R> loader, SortedMap<Integer, BuildReference<R>> core) {
        this.loader = loader;
        this.core = core;
    }

    private R unwrap(@Nullable BuildReference<R> ref) {
        if (ref == null)  return null;

        R v = ref.get();
        if (v == null)
            v = loader.getById(ref.id);
        return v;
    }

    private BuildReference<R> wrap(@Nullable R value) {
        if (value == null)    return null;
        return loader.createReference(value);
    }




    @Override
    public Comparator<? super Integer> comparator() {
        return core.comparator();
    }

    @Override
    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        return new BuildReferenceMapAdapter<>(loader, core.subMap(fromKey, toKey));
    }

    @Override
    public SortedMap<Integer, R> headMap(Integer toKey) {
        return new BuildReferenceMapAdapter<>(loader, core.headMap(toKey));
    }

    @Override
    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return new BuildReferenceMapAdapter<>(loader, core.tailMap(fromKey));
    }

    @Override
    public Integer firstKey() {
        return core.firstKey();
    }

    @Override
    public Integer lastKey() {
        return core.lastKey();
    }

    @Override
    public Set<Integer> keySet() {
        return core.keySet();
    }

    @Override
    public Collection<R> values() {
        return new CollectionAdapter(core.values());
    }

    @Override
    public Set<Entry<Integer, R>> entrySet() {
        return new SetAdapter(core.entrySet());
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
    public boolean containsKey(Object key) {
        return core.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return core.containsValue(value); // TODO should this be core.containsValue(wrap(value))?
    }

    @Override
    public R get(Object key) {
        return unwrap(core.get(key));
    }

    @Override
    public R put(Integer key, R value) {
        return unwrap(core.put(key, wrap(value)));
    }

    @Override
    public R remove(Object key) {
        return unwrap(core.remove(key));
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends R> m) {
        for (Entry<? extends Integer, ? extends R> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    @Override
    public void clear() {
        core.clear();
    }

    @Override
    public boolean equals(Object o) {
        return core.equals(o); // TODO this is wrong
    }

    @Override
    public int hashCode() {
        return core.hashCode();
    }

    @Override public String toString() {
        return new LinkedHashMap<>(this).toString();
    }

    private class CollectionAdapter implements Collection<R> {
        private final Collection<BuildReference<R>> core;

        private CollectionAdapter(Collection<BuildReference<R>> core) {
            this.core = core;
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
        public Iterator<R> iterator() {
            // silently drop null, as if we didn't have them in this collection in the first place
            // this shouldn't be indistinguishable from concurrent modifications to the collection
            return Iterators.removeNull(new AdaptedIterator<>(core.iterator()) {
                @Override
                protected R adapt(BuildReference<R> ref) {
                    return unwrap(ref);
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
        public boolean add(R value) {
            return core.add(wrap(value));
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
        public boolean addAll(Collection<? extends R> c) {
            boolean b = false;
            for (R r : c) {
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
    }

    private class SetAdapter implements Set<Entry<Integer, R>> {
        private final Set<Entry<Integer, BuildReference<R>>> core;

        private SetAdapter(Set<Entry<Integer, BuildReference<R>>> core) {
            this.core = core;
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
        public Iterator<Entry<Integer, R>> iterator() {
            return Iterators.removeNull(new AdaptedIterator<>(core.iterator()) {
                @Override
                protected Entry<Integer, R> adapt(Entry<Integer, BuildReference<R>> e) {
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
        public boolean add(Entry<Integer, R> value) {
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
        public boolean addAll(Collection<? extends Entry<Integer, R>> c) {
            boolean b = false;
            for (Entry<Integer, R> r : c) {
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

        private Entry<Integer, BuildReference<R>> _wrap(Entry<Integer, R> e) {
            return new AbstractMap.SimpleEntry<>(e.getKey(), wrap(e.getValue()));
        }

        private Entry<Integer, R> _unwrap(Entry<Integer, BuildReference<R>> e) {
            R v = unwrap(e.getValue());
            if (v == null)
                return null;
            return new AbstractMap.SimpleEntry<>(e.getKey(), v);
        }
    }
}
