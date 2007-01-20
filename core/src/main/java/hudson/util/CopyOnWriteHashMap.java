package hudson.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link Map} that has copy-on-write semantics, backed by {@link HashMap}.
 *
 * <p>
 * This class is suitable where highly concurrent access is needed, yet
 * the write operation is relatively uncommon.
 *
 * @author Kohsuke Kawaguchi
 */
public class CopyOnWriteHashMap<K,V> implements Map<K,V> {
    private volatile Map<K,V> core;
    /**
     * Read-only view of {@link #core}.
     */
    private volatile Map<K,V> view;

    public CopyOnWriteHashMap(Map<K, V> core) {
        update(new HashMap<K,V>(core));
    }

    public CopyOnWriteHashMap() {
        update(Collections.<K,V>emptyMap());
    }

    private void update(Map<K, V> m) {
        core = m;
        view = Collections.unmodifiableMap(core);
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
        return core.containsValue(value);
    }

    public V get(Object key) {
        return core.get(key);
    }

    public synchronized V put(K key, V value) {
        Map<K,V> m = new HashMap<K,V>(core);
        V r = m.put(key,value);
        update(m);

        return r;
    }

    public synchronized V remove(Object key) {
        Map<K,V> m = new HashMap<K,V>(core);
        V r = m.remove(key);
        update(m);

        return r;
    }

    public synchronized void putAll(Map<? extends K, ? extends V> t) {
        Map<K,V> m = new HashMap<K,V>(core);
        m.putAll(t);
        update(m);
    }

    public synchronized void clear() {
        update(Collections.<K,V>emptyMap());
    }

    /**
     * This method will return a read-only {@link Set}.
     */
    public Set<K> keySet() {
        return view.keySet();
    }

    /**
     * This method will return a read-only {@link Collection}.
     */
    public Collection<V> values() {
        return view.values();
    }

    /**
     * This method will return a read-only {@link Set}.
     */
    public Set<Entry<K,V>> entrySet() {
        return view.entrySet();
    }
}
