package jenkins.model.lazy;

import hudson.util.AdaptedIterator;
import hudson.util.Iterators;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;

/**
 * Take {@code SortedMap<Integer,BuildReference<R>>} and make it look like {@code SortedMap<Integer,R>}.
 *
 * <p>
 * When {@link BuildReference} lost the build object, we'll use {@link Resolver} to obtain one.
 * </p>
 *
 * <p>
 * By default, this adapter provides a read-only view of the underlying {@link #core} map,
 * which may be modified externally to change the adapter's state.
 * Support for removal operations through {@link #entrySet()}, {@link #values()},
 * {@link #keySet()}, and their iterators can be enabled by overriding
 * {@link #removeValue(Object)}. This method is invoked by the internal collection views
 * to perform the actual removal logic (such as updating {@link #core} or performing other actions).
 * </p>
 *
 * <p>
 * Some operations are weakly implemented (for example, {@link #size()} may be approximate).
 * This adapter implements {@link SortedMap}, which does not allow {@code null} keys; however,
 * methods such as {@code get(null)} or {@code containsKey(null)} do not throw a {@link NullPointerException}
 * and instead return {@code null} or {@code false}, respectively, indicating that the key was not found.
 * </p>
 *
 * @author Kohsuke Kawaguchi
 */
class BuildReferenceMapAdapter<R> extends AbstractMap<Integer, R> implements SortedMap<Integer, R> {
    private final NavigableMap<Integer, BuildReference<R>> core;
    private final Resolver<R> resolver;

    private final Set<Integer> keySet = new KeySetAdapter();
    private final Collection<R> values = new ValuesAdapter();
    private final Set<Map.Entry<Integer, R>> entrySet = new EntrySetAdapter();

    BuildReferenceMapAdapter(NavigableMap<Integer, BuildReference<R>> core, Resolver<R> resolver) {
        this.core = core;
        this.resolver = resolver;
    }

    @Override
    public Comparator<? super Integer> comparator() {
        return core.comparator();
    }

    @Override
    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        return new BuildReferenceMapAdapter<>(core.subMap(fromKey, true, toKey, false), resolver);
    }

    @Override
    public SortedMap<Integer, R> headMap(Integer toKey) {
        return new BuildReferenceMapAdapter<>(core.headMap(toKey, false), resolver);
    }

    @Override
    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return new BuildReferenceMapAdapter<>(core.tailMap(fromKey, true), resolver);
    }

    @Override
    public Integer firstKey() {
        return keySet.stream().findFirst().orElseThrow(NoSuchElementException::new);
    }

    @Override
    public Integer lastKey() {
        return new BuildReferenceMapAdapter<>(core.descendingMap(), resolver).firstKey();
    }

    @Override
    public Set<Integer> keySet() {
        return keySet;
    }

    @Override
    public Collection<R> values() {
        return values;
    }

    @Override
    public Set<Entry<Integer, R>> entrySet() {
        return entrySet;
    }

    @Override
    public boolean isEmpty() {
        return entrySet().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        BuildReference<R> ref = key instanceof Integer ? core.get(key) : null;
        if (ref == null) {
            return false;
        }
        // if found, check if value is loadable
        if (!ref.isSet()) {
            resolver.resolveBuildRef(ref);
        }
        return !ref.isUnloadable();
    }

    @Override
    public boolean containsValue(Object value) {
        if (!resolver.getBuildClass().isInstance(value)) {
            return false;
        }
        R val = resolver.getBuildClass().cast(value);
        return val.equals(get(resolver.getNumberOf(val)));
    }

    @Override
    public R get(Object key) {
        return key instanceof Integer ? resolver.resolveBuildRef(core.get(key)) : null;
    }

    @Override
    public R remove(Object key) {
        R val = get(key);
        if (val == null) {
            return null;
        }
        return removeValue(val) ? val : null;
    }

    protected boolean removeValue(R value) {
        throw new UnsupportedOperationException();
    }

    private class KeySetAdapter extends AbstractSet<Integer> {
        @Override
        public int size() {
            return BuildReferenceMapAdapter.this.size();
        }

        @Override
        public boolean isEmpty() {
            return BuildReferenceMapAdapter.this.isEmpty();
        }

        @Override
        public boolean contains(Object k) {
            return BuildReferenceMapAdapter.this.containsKey(k);
        }

        @Override
        public boolean remove(Object o) {
            return BuildReferenceMapAdapter.this.remove(o) != null;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new AdaptedIterator<>(BuildReferenceMapAdapter.this.entrySet().iterator()) {
                @Override
                protected Integer adapt(Entry<Integer, R> e) {
                    return e.getKey();
                }
            };
        }

        @Override
        public Spliterator<Integer> spliterator() {
            return new Spliterators.AbstractIntSpliterator(Long.MAX_VALUE,
                    Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.SORTED) {
                private final Iterator<Integer> it = KeySetAdapter.this.iterator();

                @Override
                public boolean tryAdvance(IntConsumer action) {
                    Objects.requireNonNull(action);
                    if (it.hasNext()) {
                        action.accept(it.next());
                        return true;
                    }
                    return false;
                }

                @Override
                public Comparator<? super Integer> getComparator() {
                    return BuildReferenceMapAdapter.this.comparator();
                }
            };
        }
    }

    private class ValuesAdapter extends AbstractCollection<R> {
        @Override
        public int size() {
            return BuildReferenceMapAdapter.this.size();
        }

        @Override
        public boolean isEmpty() {
            return BuildReferenceMapAdapter.this.isEmpty();
        }

        @Override
        public boolean contains(Object v) {
            return BuildReferenceMapAdapter.this.containsValue(v);
        }

        @Override
        public boolean remove(Object o) {
            return resolver.getBuildClass().isInstance(o) &&
                    BuildReferenceMapAdapter.this.removeValue(resolver.getBuildClass().cast(o));
        }

        @Override
        public Iterator<R> iterator() {
            return new AdaptedIterator<>(BuildReferenceMapAdapter.this.entrySet().iterator()) {
                @Override
                protected R adapt(Entry<Integer, R> e) {
                    return e.getValue();
                }
            };
        }

        @Override
        public Spliterator<R> spliterator() {
            return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.DISTINCT | Spliterator.ORDERED);
        }
    }

    private class EntrySetAdapter extends AbstractSet<Entry<Integer, R>> {
        @Override
        public int size() {
            return BuildReferenceMapAdapter.this.core.size();
        }

        @Override
        public boolean isEmpty() {
            return this.stream().findFirst().isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Map.Entry<?, ?> e && e.getKey() instanceof Integer key) {
                return e.getValue() != null && e.getValue().equals(BuildReferenceMapAdapter.this.get(key));
            }
            return false;
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry<?, ?> e) {
                return resolver.getBuildClass().isInstance(e.getValue()) &&
                        BuildReferenceMapAdapter.this.removeValue(resolver.getBuildClass().cast(e.getValue()));
            }
            return false;
        }

        @Override
        public Iterator<Entry<Integer, R>> iterator() {
            return new Iterator<>() {
                private Entry<Integer, R> current;
                private final Iterator<Entry<Integer, R>> it = Iterators.removeNull(Iterators.map(
                        BuildReferenceMapAdapter.this.core.entrySet().iterator(), coreEntry -> {
                            R v = BuildReferenceMapAdapter.this.resolver.resolveBuildRef(coreEntry.getValue());
                            return v == null ? null : new AbstractMap.SimpleEntry<>(coreEntry.getKey(), v);
                        }));

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<Integer, R> next() {
                    return current = it.next();
                }

                @Override
                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    BuildReferenceMapAdapter.this.removeValue(current.getValue());
                }
            };
        }

        @Override
        public Spliterator<Map.Entry<Integer, R>> spliterator() {
            return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.DISTINCT | Spliterator.ORDERED);
        }
    }

    /**
     * An interface for resolving build references into actual build instances
     * and extracting basic metadata from them.
     **/
    public interface Resolver<R> {

        /**
         * Resolves the given build reference into an actual build instance.
         *
         * @param buildRef the reference to a build to resolve, can be {@code null}
         * @return the resolved build instance, or {@code null} if the reference is {@code null}
         *      or could not be resolved
         */
        R resolveBuildRef(BuildReference<R> buildRef);

        /**
         * Returns the build number associated with the given build instance.
         *
         * @param build the build instance, cannot be null
         * @return the build number
         */
        Integer getNumberOf(R build);

        /**
         * Returns the class of the build type handled by this resolver.
         *
         * @return the {@link Class} of the build type {@code R}
         */
        Class<R> getBuildClass();
    }
}
