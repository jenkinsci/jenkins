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

import jenkins.model.lazy.AbstractLazyLoadRunMap.Direction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.jvnet.hudson.test.Bug;

/**
 * @author Kohsuke Kawaguchi
 */
public class AbstractLazyLoadRunMapTest extends Assert {
    // A=1, B=3, C=5
    @Rule
    public FakeMapBuilder aBuilder = new FakeMapBuilder();
    private FakeMap a;

    // empty map
    @Rule
    public FakeMapBuilder bBuilder = new FakeMapBuilder();
    private FakeMap b;

    @Rule
    public FakeMapBuilder localBuilder = new FakeMapBuilder();

    @Rule
    public FakeMapBuilder localExpiredBuilder = new FakeMapBuilder() {
        @Override
        public FakeMap make() {
            assert getDir()!=null;
            return new FakeMap(getDir()) {
                @Override
                protected BuildReference<Build> createReference(Build r) {
                    return new BuildReference<Build>(getIdOf(r), /* pretend referent expired */ null);
                }
            };
        }
    };
 
    
    @BeforeClass
    public static void setUpClass() {
        AbstractLazyLoadRunMap.LOGGER.setLevel(Level.OFF);
    }

    @Before
    public void setUp() throws Exception {
        a = aBuilder.add(1, "A").add(3, "B").add(5, "C").make();

        b = bBuilder.make();
    }

    @Test
    public void lookup() {
        assertNull(a.get(0));
        a.get(1).asserts(1, "A");
        assertNull(a.get(2));
        a.get(3).asserts(3, "B");
        assertNull(a.get(4));
        a.get(5).asserts(5, "C");
        assertNull(a.get(6));

        assertNull(b.get(1));
        assertNull(b.get(3));
        assertNull(b.get(5));
    }

    @Test
    public void lookup2() {
        assertNull(a.get(6));
    }

    @Test
    public void idempotentLookup() {
        for (int i=0; i<5; i++) {
            a.get(1).asserts(1,"A");
            a.get((Object)1).asserts(1, "A");
        }
    }

    @Test
    public void lookupWithBogusKeyType() {
        assertNull(a.get(null));
        assertNull(a.get("foo"));
        assertNull(a.get(this));
    }

    @Test
    public void firstKey() {
        assertEquals(5, a.firstKey().intValue());

        try {
            b.firstKey();
            fail();
        } catch (NoSuchElementException e) {
            // as expected
        }
    }

    @Test
    public void lastKey() {
        assertEquals(1, a.lastKey().intValue());
        try {
            b.lastKey();
            fail();
        } catch (NoSuchElementException e) {
            // as expected
        }
    }

    @Test
    public void search() {
        // searching toward non-existent direction
        assertNull(a.search(99, Direction.ASC));
        assertNull(a.search(-99, Direction.DESC));
    }

    @Test
    public void searchExactWhenIndexedButSoftReferenceExpired() throws IOException {
        final FakeMap m = localExpiredBuilder.add(1, "A").add(2, "B").make();

        // force index creation
        m.entrySet();

        m.search(1, Direction.EXACT).asserts(1, "A");
        assertNull(m.search(3, Direction.EXACT));
        assertNull(m.search(0, Direction.EXACT));
    }

    /**
     * If load fails, search needs to gracefully handle it
     */
    @Test
    public void unloadableData() throws IOException {
        FakeMap m = localBuilder.add(1, "A").addUnloadable("B").add(5, "C").make();

        assertNull(m.search(3, Direction.EXACT));
        m.search(3,Direction.DESC).asserts(1, "A");
        m.search(3, Direction.ASC ).asserts(5, "C");
    }

    @Test
    public void eagerLoading() throws IOException {
        Map.Entry[] b = a.entrySet().toArray(new Map.Entry[3]);
        ((Build)b[0].getValue()).asserts(5, "C");
        ((Build)b[1].getValue()).asserts(3, "B");
        ((Build)b[2].getValue()).asserts(1, "A");
    }

    @Test
    public void fastLookup() throws IOException {
        FakeMap a = localBuilder.addBoth(1, "A").addBoth(3, "B").addBoth(5, "C"). make();

        a.get(1).asserts(1,"A");
        assertNull(a.get(2));
        a.get(3).asserts(3,"B");
        assertNull(a.get(4));
        a.get(5).asserts(5,"C");
    }

    @Test
    public void fastSearch() throws IOException {
        FakeMap a = localBuilder.addBoth(1, "A").addBoth(3, "B").addBoth(5, "C").addBoth(7, "D").make();

        // we should be using the cache to find the entry efficiently
        a.search(6, Direction.ASC).asserts(7,"D");
        a.search(2, Direction.DESC).asserts(1, "A");
    }

    @Test
    public void bogusCache() throws IOException {
        FakeMap a = localBuilder.addUnloadableCache(1).make();
        assertNull(a.get(1));
    }

    @Test
    public void bogusCacheAndHiddenRealData() throws IOException {
        FakeMap a = localBuilder.addUnloadableCache(1).add(1, "A").make();
        a.get(1).asserts(1, "A");
    }

    @Test
    public void bogusCache2() throws IOException {
        FakeMap a = localBuilder.addBogusCache(1,3,"A").make();
        assertNull(a.get(1));
        a.get(3).asserts(3,"A");
    }

    @Test
    public void incompleteCache() throws IOException {
        FakeMapBuilder setup = localBuilder.addBoth(1, "A").add(3, "B").addBoth(5, "C");

        // each test uses a fresh map since cache lookup causes additional loads
        // to verify the results

        // if we just rely on cache,
        // it'll pick up 5:C as the first ascending value,
        // but we should be then verifying this by loading B, so in the end we should
        // find the correct value
        setup.make().search(2, Direction.ASC).asserts(3,"B");
        setup.make().search(4, Direction.DESC).asserts(3,"B");

        // variation of the cache based search where we find the outer-most value via cache
        setup.make().search(0, Direction.ASC).asserts(1,"A");
        setup.make().search(6, Direction.DESC).asserts(5,"C");

        // variation of the cache search where the cache tells us that we are searching
        // in the direction that doesn't have any records
        assertNull(setup.make().search(0, Direction.DESC));
        assertNull(setup.make().search(6, Direction.ASC));
    }

    @Test
    public void fastSubMap() throws Exception {
        SortedMap<Integer,Build> m = a.subMap(99, 2);
        assertEquals(2, m.size());

        Build[] b = m.values().toArray(new Build[2]);
        assertEquals(2, b.length);
        b[0].asserts(5, "C");
        b[1].asserts(3, "B");
    }

    @Test
    public void identity() {
        assertTrue(a.equals(a));
        assertTrue(!a.equals(b));
        a.hashCode();
        b.hashCode();
    }

    @Bug(15439)
    @Test
    public void indexOutOfBounds() throws Exception {
        FakeMapBuilder f = localBuilder;
        f.add(100,"A")
            .addUnloadable("B")
            .addUnloadable("C")
            .addUnloadable("D")
            .addUnloadable("E")
            .addUnloadable("F")
            .addUnloadable("G")
            .add(200,"H")
            .add(201,"I");
        FakeMap map = f.make();

        Build x = map.search(Integer.MAX_VALUE, Direction.DESC);
        assert x.n==201;
    }

    @Bug(15652)
    @Test public void outOfOrder() throws Exception {
        FakeMap map = localBuilder
                .add( 4, "2012-A")
                .add( 5, "2012-B")
                .add( 6, "2012-C")
                .add( 7, "2012-D")
                .add( 8, "2012-E")
                .add( 9, "2012-F")
                .add(10, "2012-G")
                .add(11, "2012-H")
                .add(12, "2012-I")
                .add( 1, "2013-A")
                .add( 7, "2013-B")
                .add( 9, "2013-C")
                .add(10, "2013-D")
                .add(11, "2013-E")
                .make();
        map.entrySet(); // forces Index to be populated
        assertNull(map.search(3, Direction.DESC));
    }

    @Ignore("just calling entrySet triggers loading of every build!")
    @Bug(18065)
    @Test public void all() throws Exception {
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        Set<Map.Entry<Integer,Build>> entries = a.entrySet();
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        assertFalse(entries.isEmpty());
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        assertEquals(5, a.getById("C").n);
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString());
        assertEquals("A", a.getByNumber(1).id);
        assertEquals("[5, 1]", a.getLoadedBuilds().keySet().toString());
        a.purgeCache();
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        Iterator<Map.Entry<Integer,Build>> iterator = entries.iterator();
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        assertTrue(iterator.hasNext());
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        Map.Entry<Integer,Build> entry = iterator.next();
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        assertEquals(5, entry.getKey().intValue());
        assertEquals("[]", a.getLoadedBuilds().keySet().toString());
        assertEquals("C", entry.getValue().id);
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString());
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals(3, entry.getKey().intValue());
        assertEquals("[5]", a.getLoadedBuilds().keySet().toString());
        assertEquals("B", entry.getValue().id);
        assertEquals("[5, 3]", a.getLoadedBuilds().keySet().toString());
        assertTrue(iterator.hasNext());
        entry = iterator.next();
        assertEquals(1, entry.getKey().intValue());
        assertEquals("[5, 3]", a.getLoadedBuilds().keySet().toString());
        assertEquals("A", entry.getValue().id);
        assertEquals("[5, 3, 1]", a.getLoadedBuilds().keySet().toString());
        assertFalse(iterator.hasNext());
    }

}
