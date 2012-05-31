/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements memoization semantics.
 *
 * <p>
 * Conceptually a function from K -> V that computes values lazily and remembers the results.
 * Often used to implement a data store per key.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.281
 */
public abstract class Memoizer<K,V> {
    private final ConcurrentHashMap<K,V> store = new ConcurrentHashMap<K,V>();

    public V get(K key) {
        V v = store.get(key);
        if(v!=null)     return v;

        // TODO: if we want to, we can avoid locking altogether by putting a sentinel value
        // that represents "the value is being computed". FingerprintMap does this.
        synchronized (this) {
            v = store.get(key);
            if(v!=null)     return v;

            v = compute(key);
            store.put(key,v);
            return v;
        }
    }

    /**
     * Creates a new instance.
     */
    public abstract V compute(K key);

    /**
     * Clears all the computed values.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Provides a snapshot view of all {@code V}s.
     */
    public Iterable<V> values() {
        return store.values();
    }
}
