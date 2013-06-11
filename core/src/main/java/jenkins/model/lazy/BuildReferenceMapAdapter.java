package jenkins.model.lazy;

import groovy.util.MapEntry;
import hudson.util.AdaptedIterator;
import hudson.util.Iterators;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
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
class BuildReferenceMapAdapter<R> implements SortedMap<Integer,R> {
    private final AbstractLazyLoadRunMap<R> loader;

    private final SortedMap<Integer,BuildReference<R>> core;

    BuildReferenceMapAdapter(AbstractLazyLoadRunMap<R> loader, SortedMap<Integer, BuildReference<R>> core) {
        this.loader = loader;
        this.core = core;
    }

    private R unwrap(@Nullable BuildReference<R> ref) {
        if (ref==null)  return null;

        R v = ref.get();
        if (v==null)
            v = loader.getById(ref.id);
        return v;
    }

    private BuildReference<R> wrap(@Nullable R value) {
        if (value==null)    return null;
        return loader.createReference(value);
    }




    public Comparator<? super Integer> comparator() {
        return core.comparator();
    }

    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        return new BuildReferenceMapAdapter<R>(loader,core.subMap(fromKey, toKey));
    }

    public SortedMap<Integer, R> headMap(Integer toKey) {
        return new BuildReferenceMapAdapter<R>(loader,core.headMap(toKey));
    }

    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return new BuildReferenceMapAdapter<R>(loader,core.tailMap(fromKey));
    }

    public Integer firstKey() {
        return core.firstKey();
    }

    public Integer lastKey() {
        return core.lastKey();
    }

    public Set<Integer> keySet() {
        return core.keySet();
    }

    public Collection<R> values() {
        return new CollectionAdapter(core.values());
    }

    public Set<Entry<Integer,R>> entrySet() {
        return new SetAdapter(core.entrySet());
    }

    public int size() {
        return core.size();
    }

    public boolean isEmpty() {
        return core.isEmpty();
    }

    public boolean containsKey(Object key) {
        return core.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return core.containsValue(value); // TODO should this be core.containsValue(wrap(value))?
    }

    public R get(Object key) {
        return unwrap(core.get(key));
    }

    public R put(Integer key, R value) {
        return unwrap(core.put(key, wrap(value)));
    }

    public R remove(Object key) {
        return unwrap(core.remove(key));
    }

    public void putAll(Map<? extends Integer, ? extends R> m) {
        for (Entry<? extends Integer, ? extends R> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

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
        return new LinkedHashMap<Integer,R>(this).toString();
    }

    private class CollectionAdapter implements Collection<R> {
        private final Collection<BuildReference<R>> core;

        private CollectionAdapter(Collection<BuildReference<R>> core) {
            this.core = core;
        }

        public int size() {
            return core.size();
        }

        public boolean isEmpty() {
            return core.isEmpty();
        }

        public boolean contains(Object o) {
            // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
            throw new UnsupportedOperationException();
        }

        public Iterator<R> iterator() {
            // silently drop null, as if we didn't have them in this collection in the first place
            // this shouldn't be indistinguishable from concurrent modifications to the collection
            return Iterators.removeNull(new AdaptedIterator<BuildReference<R>,R>(core.iterator()) {
                protected R adapt(BuildReference<R> ref) {
                    return unwrap(ref);
                }
            });
        }

        public Object[] toArray() {
            List<Object> list = new ArrayList<Object>();
            for (R r : this)
                list.add(r);
            return list.toArray();
        }

        public <T> T[] toArray(T[] a) {
            int size = size();
            T[] r = a;
            if (r.length>size)
                r = (T[]) Array.newInstance(a.getClass().getComponentType(), size);

            Iterator<R> itr = iterator();
            int i=0;

            while (itr.hasNext()) {
                r[i++] = (T)itr.next();
            }

            return r;
        }

        public boolean add(R value) {
            return core.add(wrap(value));
        }

        public boolean remove(Object o) {
//            return core.remove(o);
            // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
            throw new UnsupportedOperationException();
        }

        public boolean containsAll(Collection<?> c) {
            for (Object o : c) {
                if (!contains(o))
                    return false;
            }
            return true;
        }

        public boolean addAll(Collection<? extends R> c) {
            boolean b=false;
            for (R r : c) {
                b |= add(r);
            }
            return b;
        }

        public boolean removeAll(Collection<?> c) {
            boolean b=false;
            for (Object o : c) {
                b|=remove(o);
            }
            return b;
        }

        public boolean retainAll(Collection<?> c) {
            // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
            throw new UnsupportedOperationException();
        }

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

        public int size() {
            return core.size();
        }

        public boolean isEmpty() {
            return core.isEmpty();
        }

        public boolean contains(Object o) {
            // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
            throw new UnsupportedOperationException();
        }

        public Iterator<Entry<Integer, R>> iterator() {
            return Iterators.removeNull(new AdaptedIterator<Entry<Integer,BuildReference<R>>,Entry<Integer, R>>(core.iterator()) {
                protected Entry<Integer, R> adapt(Entry<Integer, BuildReference<R>> e) {
                    return _unwrap(e);
                }
            });
        }

        public Object[] toArray() {
            List<Object> list = new ArrayList<Object>();
            for (Entry<Integer, R> r : this)
                list.add(r);
            return list.toArray();
        }

        public <T> T[] toArray(T[] a) {
            int size = size();
            T[] r = a;
            if (r.length>size)
                r = (T[]) Array.newInstance(a.getClass().getComponentType(), size);

            Iterator<Entry<Integer, R>> itr = iterator();
            int i=0;

            while (itr.hasNext()) {
                r[i++] = (T)itr.next();
            }

            return r;
        }

        public boolean add(Entry<Integer, R> value) {
            return core.add(_wrap(value));
        }

        public boolean remove(Object o) {
//            return core.remove(o);
            // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
            throw new UnsupportedOperationException();
        }

        public boolean containsAll(Collection<?> c) {
            for (Object o : c) {
                if (!contains(o))
                    return false;
            }
            return true;
        }

        public boolean addAll(Collection<? extends Entry<Integer,R>> c) {
            boolean b=false;
            for (Entry<Integer,R> r : c) {
                b |= add(r);
            }
            return b;
        }

        public boolean removeAll(Collection<?> c) {
            boolean b=false;
            for (Object o : c) {
                b|=remove(o);
            }
            return b;
        }

        public boolean retainAll(Collection<?> c) {
            // TODO: to properly pass this onto core, we need to wrap o into BuildReference but also needs to figure out ID.
            throw new UnsupportedOperationException();
        }

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

        private Entry<Integer,BuildReference<R>> _wrap(Entry<Integer,R> e) {
            return new MapEntry(e.getKey(),wrap(e.getValue()));
        }
        private Entry<Integer, R> _unwrap(Entry<Integer, BuildReference<R>> e) {
            R v = unwrap(e.getValue());
            if (v==null)
                return null;
            return new MapEntry(e.getKey(), v);
        }
    }
}
