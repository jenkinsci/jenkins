/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import hudson.Util;
import hudson.model.Fingerprint.RangeSet;
import java.io.File;
import jenkins.model.FingerprintFacet;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Kohsuke Kawaguchi
 */
public class FingerprintTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    
    @Test public void rangeSet() {
        RangeSet rs = new RangeSet();
        assertFalse(rs.includes(0));
        assertFalse(rs.includes(3));
        assertFalse(rs.includes(5));

        rs.add(3);
        assertFalse(rs.includes(2));
        assertTrue(rs.includes(3));
        assertFalse(rs.includes(4));
        assertEquals("[3,4)",rs.toString());

        rs.add(4);
        assertFalse(rs.includes(2));
        assertTrue(rs.includes(3));
        assertTrue(rs.includes(4));
        assertFalse(rs.includes(5));
        assertEquals("[3,5)",rs.toString());

        rs.add(10);
        assertEquals("[3,5),[10,11)",rs.toString());

        rs.add(9);
        assertEquals("[3,5),[9,11)",rs.toString());

        rs.add(6);
        assertEquals("[3,5),[6,7),[9,11)",rs.toString());

        rs.add(5);
        assertEquals("[3,7),[9,11)",rs.toString());
    }

    @Test public void merge() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(2);
        x.add(3);
        x.add(5);
        x.add(6);
        assertEquals("[1,4),[5,7)",x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(4);
        y.add(5);
        assertEquals("[3,6)",y.toString());

        x.add(y);
        assertEquals("[1,7)",x.toString());
    }

    @Test public void merge2() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(2);
        x.add(5);
        x.add(6);
        assertEquals("[1,3),[5,7)",x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(4);
        assertEquals("[3,5)",y.toString());

        x.add(y);
        assertEquals("[1,7)",x.toString());
    }

    @Test public void merge3() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(5);
        assertEquals("[1,2),[5,6)",x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(5);
        y.add(7);
        assertEquals("[3,4),[5,6),[7,8)",y.toString());

        x.add(y);
        assertEquals("[1,2),[3,4),[5,6),[7,8)",x.toString());
    }

    @Test
    public void retainAll1() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1,2,3, 10,11,       20);
        y.addAll(  2,      11,12, 19,20,21);

        assertTrue(x.retainAll(y));

        RangeSet z = new RangeSet();
        z.addAll(2,11,20);

        assertEquals(x,z);
    }

    @Test
    public void retainAll2() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1,2,3,4,5,6,7,8,9,10,      13,14,15,16,17,18,19,20);
        y.addAll(  2,3,  5,6,    9,10,11,12,13,   15,16,   18,19);

        assertTrue(x.retainAll(y));

        RangeSet z = new RangeSet();
        z.addAll(2,3,5,6,9,10,13,15,16,18,19);

        assertEquals(x,z);
    }

    @Test
    public void retainAll3() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1,2,3,4,5);

        assertTrue(x.retainAll(y));
        assertTrue(x.isEmpty());
    }

    @Test
    public void removeAll1() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1,2,3, 10,11,       20);
        y.addAll(  2,      11,12, 19,20,21);

        assertTrue(x.removeAll(y));

        RangeSet z = new RangeSet();
        z.addAll(1,3,10);

        assertEquals(x,z);
    }

    @Test
    public void removeAll2() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1,2,3,4,5,6,7,8,9,10,      13,14,15,16,17,18,19,20);
        y.addAll(  2,3,  5,6,    9,10,11,12,13,   15,16,   18,19);

        assertTrue(x.removeAll(y));

        RangeSet z = new RangeSet();
        z.addAll(1,4,7,8,14,17,20);

        assertEquals(x,z);
    }

    @Test
    public void removeAll3() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1,2,3,4,5);

        assertFalse(x.removeAll(y));
    }

    @Test public void deserialize() throws Exception {
        assertEquals("Fingerprint["
                + "original=stapler/org.kohsuke.stapler:stapler-jelly #123,"
                + "hash=069484c9e963cc615c51278327da8eab,"
                + "fileName=org.kohsuke.stapler:stapler-jelly-1.207.jar,"
                + "timestamp=2013-05-21 19:20:03.534 UTC,"
                + "usages={stuff=[304,306),[307,324),[328,330), stuff/test:stuff=[2,67),[72,77),[84,223),[228,229),[232,268)},"
                + "facets=[]]",
                Fingerprint.load(new File(FingerprintTest.class.getResource("fingerprint.xml").toURI())).toString());
    }

    @Test public void roundTrip() throws Exception {
        Fingerprint f = new Fingerprint(new Fingerprint.BuildPtr("foo", 13), "stuff&more.jar", SOME_MD5);
        f.addWithoutSaving("some", 1);
        f.addWithoutSaving("some", 2);
        f.addWithoutSaving("some", 3);
        f.addWithoutSaving("some", 10);
        f.addWithoutSaving("other", 6);
        File xml = new File(new File(tmp.getRoot(), "dir"), "fp.xml");
        f.save(xml);
        Fingerprint f2 = Fingerprint.load(xml);
        assertNotNull(f2);
        assertEquals(f.toString(), f2.toString());
        f.facets.setOwner(Saveable.NOOP);
        f.facets.add(new TestFacet(f, 123, "val"));
        f.save(xml);
        //System.out.println(FileUtils.readFileToString(xml));
        f2 = Fingerprint.load(xml);
        assertEquals(f.toString(), f2.toString());
        assertEquals(1, f2.facets.size());
        TestFacet facet = (TestFacet) f2.facets.get(0);
        assertEquals(f2, facet.getFingerprint());
    }
    private static byte[] toByteArray(String md5sum) {
        byte[] data = new byte[16];
        for( int i=0; i<md5sum.length(); i+=2 )
            data[i/2] = (byte)Integer.parseInt(md5sum.substring(i,i+2),16);
        return data;
    }
    private static final byte[] SOME_MD5 = toByteArray(Util.getDigestOf("whatever"));
    public static final class TestFacet extends FingerprintFacet {
        final String property;
        public TestFacet(Fingerprint fingerprint, long timestamp, String property) {
            super(fingerprint, timestamp);
            this.property = property;
        }
        @Override public String toString() {
            return "TestFacet[" + property + "@" + getTimestamp() + "]";
        }
    }

}
