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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Fingerprint.RangeSet;
import java.io.File;
import jenkins.fingerprints.FileFingerprintStorage;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class FingerprintTest {

    @Test
    void rangeSet() {
        RangeSet rs = new RangeSet();
        assertFalse(rs.includes(0));
        assertFalse(rs.includes(3));
        assertFalse(rs.includes(5));

        rs.add(3);
        assertFalse(rs.includes(2));
        assertTrue(rs.includes(3));
        assertFalse(rs.includes(4));
        assertEquals("[3,4)", rs.toString());

        rs.add(4);
        assertFalse(rs.includes(2));
        assertTrue(rs.includes(3));
        assertTrue(rs.includes(4));
        assertFalse(rs.includes(5));
        assertEquals("[3,5)", rs.toString());

        rs.add(10);
        assertEquals("[3,5),[10,11)", rs.toString());

        rs.add(9);
        assertEquals("[3,5),[9,11)", rs.toString());

        rs.add(6);
        assertEquals("[3,5),[6,7),[9,11)", rs.toString());

        rs.add(5);
        assertEquals("[3,7),[9,11)", rs.toString());
    }

    @Test
    void merge() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(2);
        x.add(3);
        x.add(5);
        x.add(6);
        assertEquals("[1,4),[5,7)", x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(4);
        y.add(5);
        assertEquals("[3,6)", y.toString());

        x.add(y);
        assertEquals("[1,7)", x.toString());
    }

    @Test
    void merge2() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(2);
        x.add(5);
        x.add(6);
        assertEquals("[1,3),[5,7)", x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(4);
        assertEquals("[3,5)", y.toString());

        x.add(y);
        assertEquals("[1,7)", x.toString());
    }

    @Test
    void merge3() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(5);
        assertEquals("[1,2),[5,6)", x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(5);
        y.add(7);
        assertEquals("[3,4),[5,6),[7,8)", y.toString());

        x.add(y);
        assertEquals("[1,2),[3,4),[5,6),[7,8)", x.toString());
    }

    @Test
    void retainAll1() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1, 2, 3, 10, 11, 20);
        y.addAll(2, 11, 12, 19, 20, 21);

        assertTrue(x.retainAll(y));

        RangeSet z = new RangeSet();
        z.addAll(2, 11, 20);

        assertEquals(x, z);
    }

    @Test
    void retainAll2() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 15, 16, 17, 18, 19, 20);
        y.addAll(2, 3,  5, 6, 9, 10, 11, 12, 13, 15, 16, 18, 19);

        assertTrue(x.retainAll(y));

        RangeSet z = new RangeSet();
        z.addAll(2, 3, 5, 6, 9, 10, 13, 15, 16, 18, 19);

        assertEquals(x, z);
    }

    @Test
    void retainAll3() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1, 2, 3, 4, 5);

        assertTrue(x.retainAll(y));
        assertTrue(x.isEmpty());
    }

    @Test
    void removeAll1() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1, 2, 3, 10, 11, 20);
        y.addAll(2, 11, 12, 19, 20, 21);

        assertTrue(x.removeAll(y));

        RangeSet z = new RangeSet();
        z.addAll(1, 3, 10);

        assertEquals(x, z);
    }

    @Test
    void removeAll2() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 15, 16, 17, 18, 19, 20);
        y.addAll(2, 3, 5, 6, 9, 10, 11, 12, 13, 15, 16, 18, 19);

        assertTrue(x.removeAll(y));

        RangeSet z = new RangeSet();
        z.addAll(1, 4, 7, 8, 14, 17, 20);

        assertEquals(x, z);
    }

    @Test
    void removeAll3() {
        RangeSet x = new RangeSet();
        RangeSet y = new RangeSet();

        x.addAll(1, 2, 3, 4, 5);

        assertFalse(x.removeAll(y));
    }

    @Test
    void deserialize() throws Exception {
        assertEquals("Fingerprint["
                + "original=stapler/org.kohsuke.stapler:stapler-jelly #123,"
                + "hash=069484c9e963cc615c51278327da8eab,"
                + "fileName=org.kohsuke.stapler:stapler-jelly-1.207.jar,"
                + "timestamp=2013-05-21 19:20:03.534 UTC,"
                + "usages={stuff=[304,306),[307,324),[328,330), stuff/test:stuff=[2,67),[72,77),[84,223),[228,229),[232,268)},"
                + "facets=[]]",
                FileFingerprintStorage.load(new File(FingerprintTest.class.getResource("fingerprint.xml").toURI())).toString());
    }

    @Test
    void loadFingerprintWithoutUsages() throws Exception {
        Fingerprint fp = FileFingerprintStorage.load(new File(FingerprintTest.class.getResource("fingerprintWithoutUsages.xml").toURI()));
        assertNotNull(fp);
        assertEquals("test:jenkinsfile-example-1.0-SNAPSHOT.jar", fp.getFileName());
        assertNotNull(fp.getUsages());
    }

    @Test
    void fromString() {
        //
        // Single
        //
        // Numbers
        assertThat(RangeSet.fromString("1", true).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("1", false).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("+1", true).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("+1", false).toString(), equalTo("[1,2)"));
        // Zero
        assertThat(RangeSet.fromString("0", true).toString(), equalTo("[0,1)"));
        assertThat(RangeSet.fromString("0", false).toString(), equalTo("[0,1)"));
        assertThat(RangeSet.fromString("+0", true).toString(), equalTo("[0,1)"));
        assertThat(RangeSet.fromString("+0", false).toString(), equalTo("[0,1)"));
        // Negative number
        assertThat(RangeSet.fromString("-1", true).toString(), equalTo(""));
        expectIAE("-1", "Unable to parse '-1', expected string with a range M-N");
        // Exceeded int number
        assertThat(RangeSet.fromString("2147483648", true).toString(), equalTo(""));
        expectIAE("2147483648", "Unable to parse '2147483648', expected number");
        // Invalid number
        assertThat(RangeSet.fromString("1a", true).toString(), equalTo(""));
        expectIAE("1a", "Unable to parse '1a', expected number");
        assertThat(RangeSet.fromString("aa", true).toString(), equalTo(""));
        expectIAE("aa", "Unable to parse 'aa', expected number");
        //Empty
        assertThat(RangeSet.fromString("", true).toString(), equalTo(""));
        assertThat(RangeSet.fromString("", false).toString(), equalTo(""));
        //Space
        assertThat(RangeSet.fromString(" ", true).toString(), equalTo(""));
        expectIAE(" ", "Unable to parse ' ', expected number");
        // Comma
        assertThat(RangeSet.fromString(",", true).toString(), equalTo(""));
        assertThat(RangeSet.fromString(",", false).toString(), equalTo(""));
        // Hyphen
        assertThat(RangeSet.fromString("-", true).toString(), equalTo(""));
        expectIAE("-", "Unable to parse '-', expected string with a range M-N");

        //
        // Multiple numbers
        //
        // Numbers
        assertThat(RangeSet.fromString("1,2", true).toString(), equalTo("[1,2),[2,3)"));
        assertThat(RangeSet.fromString("1,2", false).toString(), equalTo("[1,2),[2,3)"));
        assertThat(RangeSet.fromString("1,+2,5", true).toString(), equalTo("[1,2),[2,3),[5,6)"));
        assertThat(RangeSet.fromString("1,+2,5", false).toString(), equalTo("[1,2),[2,3),[5,6)"));
        assertThat(RangeSet.fromString("1,1", true).toString(), equalTo("[1,2),[1,2)"));
        assertThat(RangeSet.fromString("1,1", false).toString(), equalTo("[1,2),[1,2)"));
        // Zero
        assertThat(RangeSet.fromString("0,1,2", true).toString(), equalTo("[0,1),[1,2),[2,3)"));
        assertThat(RangeSet.fromString("0,1,2", false).toString(), equalTo("[0,1),[1,2),[2,3)"));
        assertThat(RangeSet.fromString("1,0,2", true).toString(), equalTo("[1,2),[0,1),[2,3)"));
        assertThat(RangeSet.fromString("1,0,2", false).toString(), equalTo("[1,2),[0,1),[2,3)"));
        assertThat(RangeSet.fromString("1,2,0", true).toString(), equalTo("[1,2),[2,3),[0,1)"));
        assertThat(RangeSet.fromString("1,2,0", false).toString(), equalTo("[1,2),[2,3),[0,1)"));
        // Negative number
        assertThat(RangeSet.fromString("-1,2,3", true).toString(), equalTo("[2,3),[3,4)"));
        expectIAE("-1,2,3", "Unable to parse '-1,2,3', expected string with a range M-N");
        assertThat(RangeSet.fromString("1,-2,3", true).toString(), equalTo("[1,2),[3,4)"));
        expectIAE("1,-2,3", "Unable to parse '1,-2,3', expected string with a range M-N");
        assertThat(RangeSet.fromString("1,2,-3", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,2,-3", "Unable to parse '1,2,-3', expected string with a range M-N");
        // Exceeded int number
        assertThat(RangeSet.fromString("2147483648,2,3", true).toString(), equalTo("[2,3),[3,4)"));
        expectIAE("2147483648,1,2", "Unable to parse '2147483648,1,2', expected number");
        assertThat(RangeSet.fromString("1,2147483648,3", true).toString(), equalTo("[1,2),[3,4)"));
        expectIAE("1,2147483648,2", "Unable to parse '1,2147483648,2', expected number");
        assertThat(RangeSet.fromString("1,2,2147483648", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,2,2147483648", "Unable to parse '1,2,2147483648', expected number");
        // Invalid number
        assertThat(RangeSet.fromString("1a,2,3", true).toString(), equalTo("[2,3),[3,4)"));
        expectIAE("1a,1,2", "Unable to parse '1a,1,2', expected number");
        assertThat(RangeSet.fromString("1,2a,3", true).toString(), equalTo("[1,2),[3,4)"));
        expectIAE("1,2a,2", "Unable to parse '1,2a,2', expected number");
        assertThat(RangeSet.fromString("1,2,3a", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,2,3a", "Unable to parse '1,2,3a', expected number");
        assertThat(RangeSet.fromString("aa,2,3", true).toString(), equalTo("[2,3),[3,4)"));
        expectIAE("aa,1,2", "Unable to parse 'aa,1,2', expected number");
        assertThat(RangeSet.fromString("1,aa,3", true).toString(), equalTo("[1,2),[3,4)"));
        expectIAE("1,aa,2", "Unable to parse '1,aa,2', expected number");
        assertThat(RangeSet.fromString("1,2,aa", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,2,aa", "Unable to parse '1,2,aa', expected number");
        //Empty
        assertThat(RangeSet.fromString(",1,2", true).toString(), equalTo(""));
        expectIAE(",1,2", "Unable to parse ',1,2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1,,2", true).toString(), equalTo(""));
        expectIAE("1,,2", "Unable to parse '1,,2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1,2,", true).toString(), equalTo(""));
        expectIAE("1,2,", "Unable to parse '1,2,', expected correct notation M,N or M-N");
        // Space
        assertThat(RangeSet.fromString(" ,1,2", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE(" ,1,2", "Unable to parse ' ,1,2', expected number");
        assertThat(RangeSet.fromString("1, ,2", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1, ,2", "Unable to parse '1, ,2', expected number");
        assertThat(RangeSet.fromString("1,2, ", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,2, ", "Unable to parse '1,2, ', expected number");
        // Comma
        assertThat(RangeSet.fromString(",,1,2", true).toString(), equalTo(""));
        expectIAE(",,1,2", "Unable to parse ',,1,2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1,,,2", true).toString(), equalTo(""));
        expectIAE("1,,,2", "Unable to parse '1,,,2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1,2,,", true).toString(), equalTo(""));
        expectIAE("1,2,,", "Unable to parse '1,2,,', expected correct notation M,N or M-N");
        // Hyphen
        assertThat(RangeSet.fromString("-,1,2", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("-,1,2", "Unable to parse '-,1,2', expected string with a range M-N");
        assertThat(RangeSet.fromString("1,-,2", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,-,2", "Unable to parse '1,-,2', expected string with a range M-N");
        assertThat(RangeSet.fromString("1,2,-", true).toString(), equalTo("[1,2),[2,3)"));
        expectIAE("1,2,-", "Unable to parse '1,2,-', expected string with a range M-N");

        //
        // Single range
        //
        // Numbers
        assertThat(RangeSet.fromString("1-2", true).toString(), equalTo("[1,3)"));
        assertThat(RangeSet.fromString("1-2", false).toString(), equalTo("[1,3)"));
        assertThat(RangeSet.fromString("+1-+2", true).toString(), equalTo("[1,3)"));
        assertThat(RangeSet.fromString("+1-+2", false).toString(), equalTo("[1,3)"));
        assertThat(RangeSet.fromString("1-1", true).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("1-1", false).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("+1-+1", true).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("+1-+1", false).toString(), equalTo("[1,2)"));
        assertThat(RangeSet.fromString("1-4", true).toString(), equalTo("[1,5)"));
        assertThat(RangeSet.fromString("1-4", false).toString(), equalTo("[1,5)"));
        assertThat(RangeSet.fromString("+1-+4", true).toString(), equalTo("[1,5)"));
        assertThat(RangeSet.fromString("+1-+4", false).toString(), equalTo("[1,5)"));
        //Zero
        assertThat(RangeSet.fromString("0-1", true).toString(), equalTo("[0,2)"));
        assertThat(RangeSet.fromString("0-1", false).toString(), equalTo("[0,2)"));
        assertThat(RangeSet.fromString("+0-+1", true).toString(), equalTo("[0,2)"));
        assertThat(RangeSet.fromString("+0-+1", false).toString(), equalTo("[0,2)"));
        assertThat(RangeSet.fromString("0-2", true).toString(), equalTo("[0,3)"));
        assertThat(RangeSet.fromString("0-2", false).toString(), equalTo("[0,3)"));
        assertThat(RangeSet.fromString("+0-+2", true).toString(), equalTo("[0,3)"));
        assertThat(RangeSet.fromString("+0-+2", false).toString(), equalTo("[0,3)"));
        assertThat(RangeSet.fromString("0--1", true).toString(), equalTo(""));
        expectIAE("0--1", "Unable to parse '0--1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("+0--1", true).toString(), equalTo(""));
        expectIAE("+0--1", "Unable to parse '+0--1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("0--2", true).toString(), equalTo(""));
        expectIAE("0--2", "Unable to parse '0--2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("+0--2", true).toString(), equalTo(""));
        expectIAE("+0--2", "Unable to parse '+0--2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1-0", true).toString(), equalTo(""));
        expectIAE("1-0", "Unable to parse '1-0', expected string with a range M-N where M<N");
        assertThat(RangeSet.fromString("+1-+0", true).toString(), equalTo(""));
        expectIAE("+1-+0", "Unable to parse '+1-+0', expected string with a range M-N where M<N");
        assertThat(RangeSet.fromString("2-0", true).toString(), equalTo(""));
        expectIAE("2-0", "Unable to parse '2-0', expected string with a range M-N where M<N");
        assertThat(RangeSet.fromString("+2-+0", true).toString(), equalTo(""));
        expectIAE("+2-+0", "Unable to parse '+2-+0', expected string with a range M-N where M<N");
        assertThat(RangeSet.fromString("-1-0", true).toString(), equalTo(""));
        expectIAE("-1-0", "Unable to parse '-1-0', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-1-+0", true).toString(), equalTo(""));
        expectIAE("-1-+0", "Unable to parse '-1-+0', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-2-0", true).toString(), equalTo(""));
        expectIAE("-2-0", "Unable to parse '-2-0', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-2-+0", true).toString(), equalTo(""));
        expectIAE("-2-+0", "Unable to parse '-2-+0', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("0-0", true).toString(), equalTo("[0,1)"));
        assertThat(RangeSet.fromString("0-0", false).toString(), equalTo("[0,1)"));
        assertThat(RangeSet.fromString("+0-+0", true).toString(), equalTo("[0,1)"));
        assertThat(RangeSet.fromString("+0-+0", false).toString(), equalTo("[0,1)"));
        // Negative number
        assertThat(RangeSet.fromString("-1-1", true).toString(), equalTo(""));
        expectIAE("-1-1", "Unable to parse '-1-1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-1-+1", true).toString(), equalTo(""));
        expectIAE("-1-+1", "Unable to parse '-1-+1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-1-2", true).toString(), equalTo(""));
        expectIAE("-1-2", "Unable to parse '-1-2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-1-+2", true).toString(), equalTo(""));
        expectIAE("-1-+2", "Unable to parse '-1-+2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1--1", true).toString(), equalTo(""));
        expectIAE("1--1", "Unable to parse '1--1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("+1--1", true).toString(), equalTo(""));
        expectIAE("+1--1", "Unable to parse '+1--1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1--2", true).toString(), equalTo(""));
        expectIAE("1--2", "Unable to parse '1--2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("+1--2", true).toString(), equalTo(""));
        expectIAE("+1--2", "Unable to parse '+1--2', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-1--1", true).toString(), equalTo(""));
        expectIAE("-1--1", "Unable to parse '-1--1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("-2--1", true).toString(), equalTo(""));
        expectIAE("-2--1", "Unable to parse '-2--1', expected correct notation M,N or M-N");
        // Exceeded int number
        assertThat(RangeSet.fromString("0-2147483648", true).toString(), equalTo(""));
        expectIAE("0-2147483648", "Unable to parse '0-2147483648', expected number");
        assertThat(RangeSet.fromString("2147483648-0", true).toString(), equalTo(""));
        expectIAE("2147483648-0", "Unable to parse '2147483648-0', expected number");
        assertThat(RangeSet.fromString("2147483648-2147483648", true).toString(), equalTo(""));
        expectIAE("2147483648-2147483648", "Unable to parse '2147483648-2147483648', expected number");
        // Invalid number
        assertThat(RangeSet.fromString("1-2a", true).toString(), equalTo(""));
        expectIAE("1-2a", "Unable to parse '1-2a', expected number");
        assertThat(RangeSet.fromString("2a-2", true).toString(), equalTo(""));
        expectIAE("2a-2", "Unable to parse '2a-2', expected number");
        assertThat(RangeSet.fromString("2a-2a", true).toString(), equalTo(""));
        expectIAE("2a-2a", "Unable to parse '2a-2a', expected number");
        assertThat(RangeSet.fromString("aa-2", true).toString(), equalTo(""));
        expectIAE("aa-2", "Unable to parse 'aa-2', expected number");
        assertThat(RangeSet.fromString("1-aa", true).toString(), equalTo(""));
        expectIAE("1-aa", "Unable to parse '1-aa', expected number");
        assertThat(RangeSet.fromString("aa-aa", true).toString(), equalTo(""));
        expectIAE("aa-aa", "Unable to parse 'aa-aa', expected number");
        // Empty
        assertThat(RangeSet.fromString("-1", true).toString(), equalTo(""));
        expectIAE("-1", "Unable to parse '-1', expected string with a range M-N");
        assertThat(RangeSet.fromString("1-", true).toString(), equalTo(""));
        expectIAE("1-", "Unable to parse '1-', expected string with a range M-N");
        assertThat(RangeSet.fromString("-", true).toString(), equalTo(""));
        expectIAE("-", "Unable to parse '-', expected string with a range M-N");
        // Space
        assertThat(RangeSet.fromString(" -1", true).toString(), equalTo(""));
        expectIAE(" -1", "Unable to parse ' -1', expected string with a range M-N");
        assertThat(RangeSet.fromString("1- ", true).toString(), equalTo(""));
        expectIAE("1- ", "Unable to parse '1- ', expected string with a range M-N");
        assertThat(RangeSet.fromString(" - ", true).toString(), equalTo(""));
        expectIAE(" - ", "Unable to parse ' - ', expected string with a range M-N");
        // Comma
        assertThat(RangeSet.fromString(",-1", true).toString(), equalTo(""));
        expectIAE(",-1", "Unable to parse ',-1', expected string with a range M-N");
        assertThat(RangeSet.fromString("1-,", true).toString(), equalTo(""));
        expectIAE("1-,", "Unable to parse '1-,', expected string with a range M-N");
        assertThat(RangeSet.fromString(",-,", true).toString(), equalTo(""));
        expectIAE(",-,", "Unable to parse ',-,', expected string with a range M-N");
        // Hyphen
        assertThat(RangeSet.fromString("--1", true).toString(), equalTo(""));
        expectIAE("--1", "Unable to parse '--1', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("1--", true).toString(), equalTo(""));
        expectIAE("1--", "Unable to parse '1--', expected correct notation M,N or M-N");
        assertThat(RangeSet.fromString("---", true).toString(), equalTo(""));
        expectIAE("---", "Unable to parse '---', expected correct notation M,N or M-N");
        // Inverse range
        assertThat(RangeSet.fromString("2-1", true).toString(), equalTo(""));
        expectIAE("2-1", "Unable to parse '2-1', expected string with a range M-N where M<N");
        assertThat(RangeSet.fromString("10-1", true).toString(), equalTo(""));
        expectIAE("10-1", "Unable to parse '10-1', expected string with a range M-N where M<N");
        assertThat(RangeSet.fromString("-1--2", true).toString(), equalTo(""));
        expectIAE("-1--2", "Unable to parse '-1--2', expected correct notation M,N or M-N");
        // Invalid range
        assertThat(RangeSet.fromString("1-3-", true).toString(), equalTo(""));
        expectIAE("1-3-", "Unable to parse '1-3-', expected correct notation M,N or M-N");

        //
        // Multiple ranges
        //
        assertThat(RangeSet.fromString("1-3,3-5", true).toString(), equalTo("[1,4),[3,6)"));
        assertThat(RangeSet.fromString("1-3,4-6", true).toString(), equalTo("[1,4),[4,7)"));
        assertThat(RangeSet.fromString("1-3,5-7", true).toString(), equalTo("[1,4),[5,8)"));
        assertThat(RangeSet.fromString("1-3,2-3", true).toString(), equalTo("[1,4),[2,4)"));
        assertThat(RangeSet.fromString("1-5,2-3", true).toString(), equalTo("[1,6),[2,4)"));
    }

    private void expectIAE(final String expr, final String msg) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> RangeSet.fromString(expr, false));
        assertThat(e.getMessage(), containsString(msg));
    }
}
