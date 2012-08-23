import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Implementing AbstractSortedMap really ends up implelmenting AbstractNavigableMap
 * because SortedMap isn't really self contained / closed to itself.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractSortedMap<K,V> extends AbstractMap<K,V> implements SortedMap<K,V>, NavigableMap<K,V> {
    @Override
    public abstract NavigableSet<Entry<K, V>> entrySet();

    public K lowerKey(K key) {
        return key(lowerEntry(key));
    }

    public K floorKey(K key) {
        return key(floorEntry(key));
    }

    public K ceilingKey(K key) {
        return key(ceilingEntry(key));
    }

    public K higherKey(K key) {
        return key(higherEntry(key));
    }

    public K firstKey() {
        return key(firstEntry());
    }

    public K lastKey() {
        return key(lastEntry());
    }

    private K key(Entry<K, V> e) {
        return e!=null ? e.getKey() : null;
    }

    public NavigableMap<K,V> subMap(K fromKey, K toKey) {
        return subMap(fromKey,true,toKey,false);
    }

    public SortedMap<K, V> headMap(K toKey) {
        return headMap(toKey,false);
    }

    public SortedMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey,true);
    }

    public Entry<K, V> pollFirstEntry() {
        return remove(copy(firstEntry()));
    }

    public Entry<K, V> pollLastEntry() {
        return remove(copy(lastEntry()));
    }

    public NavigableSet<K> navigableKeySet() {
        // TODO
        throw new UnsupportedOperationException();
    }

    private Entry<K,V> remove(Entry<K,V> e) {
        if (e != null)
            remove(e.getKey());
        return e;
    }

    protected Entry<K,V> copy(Entry<K, V> e) {
        return e!=null ? new SimpleEntry<K, V>(e.getKey(),e.getValue()) : null;
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        final AbstractSortedMap<K,V> back = this;
        return new AbstractSortedMap<K,V>() {
            @Override
            public NavigableSet<Entry<K, V>> entrySet() {
                return back.entrySet().subSet(fromKey, toKey)
            }

            public Comparator<? super K> comparator() {
                // TODO
                throw new UnsupportedOperationException();
            }

            public K firstKey() {
                // TODO
                throw new UnsupportedOperationException();
            }

            public K lastKey() {
                // TODO
                throw new UnsupportedOperationException();
            }
        };
    }

    final int compare(Object k1, Object k2) {
        Comparator c = comparator();
        if (c==null)    return ((Comparable)k1).compareTo(k2);
        else            return c.compare(k1,k2);
    }
}
