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
 * When {@link BuildReference} lost the build object, we'll use {@link BuildReferenceResolver} to obtain one.
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
class BuildReferenceMapAdapter<R> extends AbstractMap<Integer, R>
        implements SortedMap<Integer, R> {
    protected final NavigableMap<Integer, BuildReference<R>> core;
    protected final BuildReferenceResolver<R> resolver;
    protected final BuildTypeDescriptor<R>  typeDescriptor;

    private final Set<Integer> keySet = new KeySetAdapter();
    private final Collection<R> values = new ValuesAdapter();
    private final Set<Map.Entry<Integer, R>> entrySet = new EntrySetAdapter();

    BuildReferenceMapAdapter(NavigableMap<Integer, BuildReference<R>> core, BuildReferenceResolver<R> resolver,
                             BuildTypeDescriptor<R> typeDescriptor) {
        this.core = core;
        this.resolver = resolver;
        this.typeDescriptor = typeDescriptor;
    }

    @Override
    public Comparator<? super Integer> comparator() {
        return core.comparator();
    }

    @Override
    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        return createInstance(core.subMap(fromKey, true, toKey, false));
    }

    @Override
    public SortedMap<Integer, R> headMap(Integer toKey) {
        return createInstance(core.headMap(toKey, false));
    }

    @Override
    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return createInstance(core.tailMap(fromKey, true));
    }

    @Override
    public SortedMap<Integer, R> reversed() {
        return createInstance(core.descendingMap());
    }

    @Override
    public Integer firstKey() {
        return keySet.stream().findFirst().orElseThrow(NoSuchElementException::new);
    }

    @Override
    public Integer lastKey() {
        return reversed().firstKey();
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
    public Set<Map.Entry<Integer, R>> entrySet() {
        return entrySet;
    }

    @Override
    public int size() {
        return core.size();
    }

    @Override
    public boolean isEmpty() {
        return entrySet().stream().findFirst().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return resolver.isBuildRefResolvable(key instanceof Integer ? core.get(key) : null);
    }

    @Override
    public boolean containsValue(Object value) {
        if (!typeDescriptor.getBuildClass().isInstance(value)) {
            return false;
        }
        R val = typeDescriptor.getBuildClass().cast(value);
        return val.equals(get(typeDescriptor.getNumberOf(val)));
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

    protected BuildReferenceMapAdapter<R> createInstance(NavigableMap<Integer, BuildReference<R>> coreMap) {
        return new BuildReferenceMapAdapter<>(coreMap, resolver, typeDescriptor);
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
            return Iterators.removeNull(Iterators.map(
                    BuildReferenceMapAdapter.this.core.entrySet().iterator(), coreEntry -> {
                        BuildReference<R> ref = coreEntry.getValue();
                        return resolver.isBuildRefResolvable(ref) ? ref.number : null;
                    }));
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
            return typeDescriptor.getBuildClass().isInstance(o) &&
                    BuildReferenceMapAdapter.this.removeValue(typeDescriptor.getBuildClass().cast(o));
        }

        @Override
        public Iterator<R> iterator() {
            return new AdaptedIterator<>(BuildReferenceMapAdapter.this.entrySet().iterator()) {
                @Override
                protected R adapt(Map.Entry<Integer, R> e) {
                    return e.getValue();
                }
            };
        }

        @Override
        public Spliterator<R> spliterator() {
            return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.DISTINCT | Spliterator.ORDERED);
        }
    }

    private class EntrySetAdapter extends AbstractSet<Map.Entry<Integer, R>> {
        @Override
        public int size() {
            return BuildReferenceMapAdapter.this.size();
        }

        @Override
        public boolean isEmpty() {
            return BuildReferenceMapAdapter.this.isEmpty();
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
                return typeDescriptor.getBuildClass().isInstance(e.getValue()) &&
                        BuildReferenceMapAdapter.this.removeValue(typeDescriptor.getBuildClass().cast(e.getValue()));
            }
            return false;
        }

        @Override
        public Iterator<Map.Entry<Integer, R>> iterator() {
            return new Iterator<>() {
                private Map.Entry<Integer, R> current;
                private final Iterator<Map.Entry<Integer, R>> it = Iterators.removeNull(Iterators.map(
                        BuildReferenceMapAdapter.this.core.entrySet().iterator(), coreEntry -> {
                            R r = resolver.resolveBuildRef(coreEntry.getValue());
                            // if null - load not allowed or build is unloadable
                            return r == null ? null : new AbstractMap.SimpleImmutableEntry<>(coreEntry.getKey(), r);
                        }));

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Map.Entry<Integer, R> next() {
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
     **/
    interface BuildReferenceResolver<R> {
        /**
         * Resolves the given build reference into an actual build instance.
         * This method is used by {@link BuildReferenceMapAdapter} for operations
         * that return map values or entries.
         *
         * @param buildRef the reference to a build to resolve, can be {@code null}
         * @return the resolved build instance, or {@code null} if the reference is {@code null}
         *      or could not be resolved
         */
        R resolveBuildRef(BuildReference<R> buildRef);

        /**
         * Checks whether the given build reference can be resolved to a non-null build instance.
         * This method is used by {@link BuildReferenceMapAdapter} to determine whether a key
         * effectively exists in the map.
         *
         * <p>This method may be weakly consistent with {@link #resolveBuildRef(BuildReference)}.
         * However, the following guarantee must hold:
         * if this method returns {@code false}, then {@link #resolveBuildRef(BuildReference)}
         * must return {@code null} for the same reference.</p>
         *
         * @param buildRef the reference to a build, may be {@code null}
         * @return {@code true} if the reference can be resolved to a non-null build instance,
         *         {@code false} otherwise
         */
        boolean isBuildRefResolvable(BuildReference<R> buildRef);
    }

    /**
     * An interface for extracting basic metadata from the build instance.
     **/
    interface BuildTypeDescriptor<R> {
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
