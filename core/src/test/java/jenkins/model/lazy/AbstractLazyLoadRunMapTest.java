/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
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

package jenkins.model.lazy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import jenkins.model.lazy.AbstractLazyLoadRunMap.Direction;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
class AbstractLazyLoadRunMapTest {
    // A=1, B=3, C=5
    private FakeMapBuilder aBuilder = new FakeMapBuilder();
    private FakeMap a;

    // empty map
    private FakeMapBuilder bBuilder = new FakeMapBuilder();
    private FakeMap b;

    private FakeMapBuilder localBuilder = new FakeMapBuilder();

    private FakeMapBuilder localExpiredBuilder = new FakeMapBuilder() {
        @Override
        public FakeMap make() {
            assertNotNull(getDir());
            return new FakeMap(getDir()) {
                @Override
                protected BuildReference<Build> createReference(Build r) {
                    return new BuildReference<>(Integer.toString(r.n), /* pretend referent expired */ null);
                }
            };
        }
    };

    private final Map<Integer, Semaphore> slowBuilderStartSemaphores = new HashMap<>();
    private final Map<Integer, Semaphore> slowBuilderEndSemaphores = new HashMap<>();
    private final Map<Integer, AtomicInteger> slowBuilderLoadCount = new HashMap<>();

    private FakeMapBuilder slowBuilder = new FakeMapBuilder() {
        @Override
        public FakeMap make() {
            return new FakeMap(getDir()) {
                @Override
                protected Build retrieve(File dir) throws IOException {
                    Build b = super.retrieve(dir);
                    slowBuilderStartSemaphores.get(b.n).release();
                    try {
                        slowBuilderEndSemaphores.get(b.n).acquire();
                    } catch (InterruptedException x) {
                        throw new IOException(x);
                    }
                    slowBuilderLoadCount.get(b.n).incrementAndGet();
                    return b;
                }
            };
        }
    };

    @BeforeAll
    static void setUpClass() {
        AbstractLazyLoadRunMap.LOGGER.setLevel(Level.OFF);
    }

    @BeforeEach
    void setUp() throws Exception {
        a = aBuilder.add(1).add(3).add(5).make();

        b = bBuilder.make();
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteDirectory(aBuilder.getDir());
        FileUtils.deleteDirectory(bBuilder.getDir());
        FileUtils.deleteDirectory(localBuilder.getDir());
        FileUtils.deleteDirectory(localExpiredBuilder.getDir());
        FileUtils.deleteDirectory(slowBuilder.getDir());
    }

    @Test
    void lookup() {
        assertNull(a.get(0));
        a.get(1).asserts(1);
        assertNull(a.get(2));
        a.get(3).asserts(3);
        assertNull(a.get(4));
        a.get(5).asserts(5);
        assertNull(a.get(6));

        assertNull(b.get(1));
        assertNull(b.get(3));
        assertNull(b.get(5));
    }

    @Test
    void lookup2() {
        assertNull(a.get(6));
    }

    @Test
    void idempotentLookup() {
        for (int i = 0; i < 5; i++) {
            a.get(1).asserts(1);
            a.get((Object) 1).asserts(1);
        }
    }

    @Test
    void lookupWithBogusKeyType() {
        assertNull(a.get(null));
        // don't inline, otherwise some IDEs will immediately complain about the wrong key type
        Object badKey = "foo";
        assertNull(a.get(badKey));
        badKey = this;
        assertNull(a.get(badKey));
    }

    @Test
    void firstKey() {
        assertEquals(5, a.firstKey().intValue());
        assertThrows(NoSuchElementException.class, () -> b.firstKey());
    }

    @Issue("JENKINS-26690")
    @Test
    void headMap() {
        assertEquals("[]", a.headMap(Integer.MAX_VALUE).keySet().toString());
        assertEquals("[]", a.headMap(6).keySet().toString());
        assertEquals("[]", a.headMap(5).keySet().toString());
        assertEquals("[5]", a.headMap(4).keySet().toString());
        assertEquals("[5]", a.headMap(3).keySet().toString());
        assertEquals("[5, 3]", a.headMap(2).keySet().toString());
        assertEquals("[5, 3]", a.headMap(1).keySet().toString());
        assertEquals("[5, 3, 1]", a.headMap(0).keySet().toString());
        assertEquals("[5, 3, 1]", a.headMap(-1).keySet().toString());
        assertEquals("[5, 3, 1]", a.headMap(-2).keySet().toString()); // this failed
        assertEquals("[5, 3, 1]", a.headMap(Integer.MIN_VALUE).keySet().toString());
    }

    @Test
    void lastKey() {
        assertEquals(1, a.lastKey().intValue());
        assertThrows(NoSuchElementException.class, () -> b.lastKey());
    }

    @Test
    void search() {
        // searching toward non-existent direction
        assertNull(a.search(99, Direction.ASC));
        assertNull(a.search(-99, Direction.DESC));
    }

    @Issue("JENKINS-19418")
    @Test
    void searchExactWhenIndexedButSoftReferenceExpired() throws IOException {
        final FakeMap m = localExpiredBuilder.add(1).add(2).make();

        // force index creation
        assertEquals(2, m.entrySet().size());

        m.search(1, Direction.EXACT).asserts(1);
        assertNull(m.search(3, Direction.EXACT));
        assertNull(m.search(0, Direction.EXACT));
    }

    @Issue("JENKINS-22681")
    @Test
    void exactSearchShouldNotReload() throws Exception {
        FakeMap m = localBuilder.add(1).add(2).make();
        assertNull(m.search(0, Direction.EXACT));
        Build a = m.search(1, Direction.EXACT);
        a.asserts(1);
        Build b = m.search(2, Direction.EXACT);
        b.asserts(2);
        assertNull(m.search(0, Direction.EXACT));
        assertSame(a, m.search(1, Direction.EXACT));
        assertSame(b, m.search(2, Direction.EXACT));
        assertNull(m.search(3, Direction.EXACT));
        assertNull(m.search(0, Direction.EXACT));
        assertSame(a, m.search(1, Direction.EXACT));
        assertSame(b, m.search(2, Direction.EXACT), "#2 should not have been reloaded by searching for #3");
        assertNull(m.search(3, Direction.EXACT));
    }

    /**
     * If load fails, search needs to gracefully handle it
     */
    @Test
    void unloadableData() throws IOException {
        FakeMap m = localBuilder.add(1).addUnloadable(3).add(5).make();

        assertNull(m.search(3, Direction.EXACT));
        m.search(3, Direction.DESC).asserts(1);
        m.search(3, Direction.ASC).asserts(5);
    }

    @Test
    void eagerLoading() {
        Map.Entry[] b = a.entrySet().toArray(new Map.Entry[3]);
        ((Build) b[0].getValue()).asserts(5);
        ((Build) b[1].getValue()).asserts(3);
        ((Build) b[2].getValue()).asserts(1);
    }

    @Test
    void fastSubMap() {
        SortedMap<Integer, Build> m = a.subMap(99, 2);
        assertEquals(2, m.size());

        Build[] b = m.values().toArray(new Build[2]);
        assertEquals(2, b.length);
        b[0].asserts(5);
        b[1].asserts(3);
    }

    @Test
    void identity() {
        assertEquals(a, a);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Issue("JENKINS-15439")
    @Test
    void indexOutOfBounds() throws Exception {
        FakeMapBuilder f = localBuilder;
        f.add(100)
            .addUnloadable(150)
            .addUnloadable(151)
            .addUnloadable(152)
            .addUnloadable(153)
            .addUnloadable(154)
            .addUnloadable(155)
            .add(200)
            .add(201);
        FakeMap map = f.make();

        Build x = map.search(Integer.MAX_VALUE, Direction.DESC);
        assertEquals(201, x.n);
    }

    @Issue("JENKINS-18065")
    @Test
    void all() {
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        Set<Map.Entry<Integer, Build>> entries = a.entrySet();
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        assertFalse(entries.isEmpty());
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString(), "5 since it is the latest");
        assertEquals(5, a.getById("5").n);
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString());
        assertEquals(1, a.getByNumber(1).n);
        assertEquals("[5, 1]", a.getLoadedBuilds().keySet().toString());
        a.purgeCache();
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        Iterator<Map.Entry<Integer, Build>> iterator = entries.iterator();
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString());
        assertTrue(iterator.hasNext());
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString());
        Map.Entry<Integer, Build> entry = iterator.next();
        assertEquals("[5, 3]", a.getLoadedBuilds().keySet().toString());
        assertEquals(5, entry.getKey().intValue());
        assertEquals("[5, 3]", a.getLoadedBuilds().keySet().toString());
        assertEquals(5, entry.getValue().n);
        assertEquals("[5, 3]", a.getLoadedBuilds().keySet().toString());
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals(3, entry.getKey().intValue());
        assertEquals("[5, 3, 1]", a.getLoadedBuilds().keySet().toString(), ".next() precomputes the one after that too");
        assertEquals(3, entry.getValue().n);
        assertEquals("[5, 3, 1]", a.getLoadedBuilds().keySet().toString());
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals(1, entry.getKey().intValue());
        assertEquals("[5, 3, 1]", a.getLoadedBuilds().keySet().toString());
        assertEquals(1, entry.getValue().n);
        assertEquals("[5, 3, 1]", a.getLoadedBuilds().keySet().toString());
        assertFalse(iterator.hasNext());
    }

    @Issue("JENKINS-18065")
    @Test
    void entrySetIterator() {
        Iterator<Map.Entry<Integer, Build>> itr = a.entrySet().iterator();

        // iterator, when created fresh, shouldn't force loading everything
        // this involves binary searching, so it can load several.
        assertTrue(a.getLoadedBuilds().size() < 3);

        // check if the first entry is legit
        assertTrue(itr.hasNext());
        Map.Entry<Integer, Build> e = itr.next();
        assertEquals((Integer) 5, e.getKey());
        e.getValue().asserts(5);

        // now that the first entry is returned, we expect there to be two loaded
        assertTrue(a.getLoadedBuilds().size() < 3);

        // check if the second entry is legit
        assertTrue(itr.hasNext());
        e = itr.next();
        assertEquals((Integer) 3, e.getKey());
        e.getValue().asserts(3);

        // repeat the process for the third one
        assertTrue(a.getLoadedBuilds().size() <= 3);

        // check if the third entry is legit
        assertTrue(itr.hasNext());
        e = itr.next();
        assertEquals((Integer) 1, e.getKey());
        e.getValue().asserts(1);

        assertFalse(itr.hasNext());
        assertEquals(3, a.getLoadedBuilds().size());
    }

    @Issue("JENKINS-18065")
    @Test
    void entrySetEmpty() {
        // entrySet().isEmpty() shouldn't cause full data load
        assertFalse(a.entrySet().isEmpty());
        assertTrue(a.getLoadedBuilds().size() < 3);
    }

    @Issue("JENKINS-18065")
    @Test
    void entrySetSize() {
        assertEquals(3, a.entrySet().size());
        assertEquals(0, b.entrySet().size());
    }

    @Issue("JENKINS-25655")
    @Test
    void entrySetChanges() {
        assertEquals(3, a.entrySet().size());
        a.put(new Build(7));
        assertEquals(4, a.entrySet().size());
    }

    @Issue("JENKINS-18065")
    @Test
    void entrySetContains() {
        for (Map.Entry<Integer, Build> e : a.entrySet()) {
            assertTrue(a.entrySet().contains(e));
        }
    }

    @Test
    void toArray() {
        var es = a.entrySet();
        assertThat(es.size(), is(3));
        assertThat(es.toArray(), arrayWithSize(3));
        assertThat(es.toArray(Object[]::new), arrayWithSize(3));
        var c = a.getLoadedBuilds().values();
        assertThat(c.size(), is(3));
        assertThat(c.toArray(), arrayWithSize(3));
        assertThat(c.toArray(Object[]::new), arrayWithSize(3));
        // TODO check behavior of subMap
    }

    @Issue("JENKINS-22767")
    @Test
    void slowRetrieve() throws Exception {
        for (int i = 1; i <= 3; i++) {
            slowBuilder.add(i);
            slowBuilderStartSemaphores.put(i, new Semaphore(0));
            slowBuilderEndSemaphores.put(i, new Semaphore(0));
            slowBuilderLoadCount.put(i, new AtomicInteger());
        }
        final FakeMap m = slowBuilder.make();
        Future<Build> firstLoad = Timer.get().submit(() -> m.getByNumber(2));
        Future<Build> secondLoad = Timer.get().submit(() -> m.getByNumber(2));
        slowBuilderStartSemaphores.get(2).acquire(1);
        // now one of them is inside retrieve(…); the other is waiting for the lock
        slowBuilderEndSemaphores.get(2).release(2); // allow both to proceed
        Build first = firstLoad.get();
        Build second = secondLoad.get();
        assertEquals(1, slowBuilderLoadCount.get(2).get());
        assertSame(second, first);
    }

}
