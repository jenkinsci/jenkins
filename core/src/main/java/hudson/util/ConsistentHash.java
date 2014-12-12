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

import com.trilead.ssh2.crypto.digest.MD5;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import hudson.util.Iterators.DuplicateFilterIterator;

/**
 * Consistent hash.
 *
 * <p>
 * This implementation is concurrency safe; additions and removals are serialized, but look up
 * can be performed concurrently even when modifications is in progress.
 *
 * <p>
 * Since typical hash functions we use in {@link Object#hashCode()} isn't random enough to
 * evenly populate the 2^32 ring space, we only ask the user to give us
 * <a href="http://en.wikipedia.org/wiki/Injective_function">an injective function</a> to a string,
 * and then we use MD5 to create random enough distribution.
 *
 * <p>
 * This consistent hash implementation is consistent both to the addition/removal of Ts, as well
 * as increase/decrease of the replicas.
 *
 * <p>
 * See http://en.wikipedia.org/wiki/Consistent_hashing for references, and
 * http://weblogs.java.net/blog/tomwhite/archive/2007/11/consistent_hash.html is probably a reasonable depiction.
 * If we trust his experiments, creating 100 replicas will reduce the stddev to 10% of the mean for 10 nodes.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.302
 */
public class ConsistentHash<T> {
    /**
     * All the items in the hash, to their replication factors.
     */
    private final Map<T,Point[]> items = new HashMap<T,Point[]>();

    private final int defaultReplication;
    private final Hash<T> hash;

    /**
     * Used for remembering the computed MD5 hash, since it's bit expensive to do it all over again.
     */
    private static final class Point implements Comparable<Point> {
        final int hash;
        final Object item;

        private Point(int hash, Object item) {
            this.hash = hash;
            this.item = item;
        }

        public int compareTo(Point that) {
            if(this.hash<that.hash) return -1;
            if(this.hash==that.hash) return 0;
            return 1;
        }
    }

    /**
     * Table that gets atomically replaced for concurrency safe operation.
     */
    private volatile Table table;

    /**
     * Immutable consistent hash table.
     */
    private final class Table {
        private final int[] hash;
        private final Object[] owner; // really T[]

        private Table() {
            // merge all points from all nodes and sort them into a single array
            Point[] allPoints = new Point[countAllPoints()];
            int p=0;
            for (Point[] v : items.values()) {
                System.arraycopy(v,0,allPoints,p,v.length);
                p+=v.length;
            }
            Arrays.sort(allPoints);

            hash = new int[allPoints.length];
            owner = new Object[allPoints.length];

            for (int i=0; i<allPoints.length; i++) {
                Point pt = allPoints[i];
                hash[i]=pt.hash;
                owner[i]=pt.item;
            }
        }

        T lookup(int queryPoint) {
            int i = index(queryPoint);
            if(i<0) return null;
            return (T)owner[i];
        }

        /**
         * Returns a consistent stream of nodes starting the given query point.
         *
         * <p>
         * This is a permutation of all the nodes, where nodes with more replicas
         * are more likely to show up early on. 
         */
        Iterator<T> list(int queryPoint) {
            final int start = index(queryPoint);
            return new DuplicateFilterIterator<T>(new Iterator<T>() {
                int pos=0;
                public boolean hasNext() {
                    return pos<owner.length;
                }

                public T next() {
                    if(!hasNext())  throw new NoSuchElementException();
                    return (T)owner[(start+(pos++))%owner.length];
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            });
        }

        private int index(int queryPoint) {
            int idx = Arrays.binarySearch(hash, queryPoint);
            if(idx<0) {
                idx = -idx-1; // idx is now 'insertion point'
                if(hash.length==0)  return -1;
                idx %= hash.length; // make it a circle
            }
            return idx;
        }
    }

    /**
     * Hashes an object to some value.
     *
     * <p>
     * By default, {@link ConsistentHash} uses {@link Object#toString()} on 'T' to
     * obtain the hash, but that behavior can be changed by providing
     * a {@link Hash} implementation.
     *
     * <p>
     * This hash function need not produce a very uniform distribution, as the
     * output is rehashed with MD5. But it does need to make sure it doesn't
     * produce the same value for two different 'T's (and that's why this returns
     * String, not the usual int.) 
     */
    public interface Hash<T> {
        /**
         * @param t
         *      The object to be hashed. Never null.
         * @return
         *      The hash value.
         */
        String hash(T t);
    }

    static final Hash DEFAULT_HASH = new Hash() {
        public String hash(Object o) {
            return o.toString();
        }
    };

    public ConsistentHash() {
        this(DEFAULT_HASH);
    }

    public ConsistentHash(int defaultReplication) {
        this(DEFAULT_HASH,defaultReplication);
    }

    public ConsistentHash(Hash<T> hash) {
        this(hash, 100);
    }

    public ConsistentHash(Hash<T> hash, int defaultReplication) {
        this.hash = hash;
        this.defaultReplication = defaultReplication;
        refreshTable();
    }

    public int countAllPoints() {
        int r=0;
        for (Point[] v : items.values())
            r+=v.length;
        return r;
    }

    /**
     * Adds a new node with the default number of replica.
     */
    public void add(T node) {
        add(node,defaultReplication);
    }

    /**
     * Calls {@link #add(Object)} with all the arguments.
     */
    public void addAll(T... nodes) {
        for (T node : nodes)
            addInternal(node,defaultReplication);
        refreshTable();
    }

    /**
     * Calls {@link #add(Object)} with all the arguments.
     */
    public void addAll(Collection<? extends T> nodes) {
        for (T node : nodes)
            addInternal(node,defaultReplication);
        refreshTable();
    }

    /**
     * Calls {@link #add(Object,int)} with all the arguments.
     */
    public void addAll(Map<? extends T,Integer> nodes) {
        for (Map.Entry<? extends T,Integer> node : nodes.entrySet())
            addInternal(node.getKey(),node.getValue());
        refreshTable();
    }

    /**
     * Removes the node entirely. This is the same as {@code add(node,0)}
     */
    public void remove(T node) {
        add(node, 0);
    }

    /**
     * Adds a new node with the given number of replica.
     *
     * <p>
     * This is the only function that manipulates {@link #items}.
     */
    public synchronized void add(T node, int replica) {
        addInternal(node, replica);
        refreshTable();
    }

    private void addInternal(T node, int replica) {
        if(replica==0) {
            items.remove(node);
        } else {
            Point[] points = new Point[replica];
            String seed = hash.hash(node);
            for (int i=0; i<replica; i++)
                points[i] = new Point(md5(seed+':'+i),node);
            items.put(node,points);
        }
    }

    private void refreshTable() {
        table = new Table();
    }


    /**
     * Compresses a string into an integer with MD5.
     */
    private int md5(String s) {
        MD5 md5 = new MD5();
        md5.update(s.getBytes());
        byte[] digest = new byte[16];
        md5.digest(digest);

        // 16 bytes -> 4 bytes
        for (int i=0; i<4; i++)
            digest[i] ^= digest[i+4]+digest[i+8]+digest[i+12];
        return (b2i(digest[0])<< 24)|(b2i(digest[1])<<16)|(b2i(digest[2])<< 8)|b2i(digest[3]);
    }

    /**
     * unsigned byte->int.
     */
    private int b2i(byte b) {
        return ((int)b)&0xFF;
    }

    /**
     * Looks up a consistent hash with the given data point.
     *
     * <p>
     * The whole point of this class is that if the same query point is given,
     * it's likely to return the same result even when other nodes are added/removed,
     * or the # of replicas for the given node is changed.
     *
     * @return
     *      null if the consistent hash is empty. Otherwise always non-null.
     */
    public T lookup(int queryPoint) {
        return table.lookup(queryPoint);
    }

    /**
     * Takes a string, hash it with MD5, then calls {@link #lookup(int)}. 
     */
    public T lookup(String queryPoint) {
        return lookup(md5(queryPoint));
    }

    /**
     * Creates a permutation of all the nodes for the given data point.
     *
     * <p>
     * The returned permutation is consistent, in the sense that small change
     * to the consistent hash (like addition/removal/change of replicas) only
     * creates a small change in the permutation.
     *
     * <p>
     * Nodes with more replicas are more likely to show up early in the list
     */
    public Iterable<T> list(final int queryPoint) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return table.list(queryPoint);
            }
        };
    }

    /**
     * Takes a string, hash it with MD5, then calls {@link #list(int)}.
     */
    public Iterable<T> list(String queryPoint) {
        return list(md5(queryPoint));
    }
}
