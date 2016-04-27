/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Erik Ramfelt, Richard Bair, id:cactusman
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
package hudson;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assume;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import hudson.util.StreamTaskListener;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.TemporaryFolder;

/**
 * @author Kohsuke Kawaguchi
 */
public class UtilTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testReplaceMacro() {
        Map<String,String> m = new HashMap<String,String>();
        m.put("A","a");
        m.put("A.B","a-b");
        m.put("AA","aa");
        m.put("B","B");
        m.put("DOLLAR", "$");
        m.put("ENCLOSED", "a${A}");

        // longest match
        assertEquals("aa",Util.replaceMacro("$AA",m));

        // invalid keys are ignored
        assertEquals("$AAB",Util.replaceMacro("$AAB",m));

        assertEquals("aaB",Util.replaceMacro("${AA}B",m));
        assertEquals("${AAB}",Util.replaceMacro("${AAB}",m));

        // $ escaping
        assertEquals("asd$${AA}dd", Util.replaceMacro("asd$$$${AA}dd",m));
        assertEquals("$", Util.replaceMacro("$$",m));
        assertEquals("$$", Util.replaceMacro("$$$$",m));

        // dots
        assertEquals("a.B", Util.replaceMacro("$A.B", m));
        assertEquals("a-b", Util.replaceMacro("${A.B}", m));

    	// test that more complex scenarios work
        assertEquals("/a/B/aa", Util.replaceMacro("/$A/$B/$AA",m));
        assertEquals("a-aa", Util.replaceMacro("$A-$AA",m));
        assertEquals("/a/foo/can/B/you-believe_aa~it?", Util.replaceMacro("/$A/foo/can/$B/you-believe_$AA~it?",m));
        assertEquals("$$aa$Ba${A}$it", Util.replaceMacro("$$$DOLLAR${AA}$$B${ENCLOSED}$it",m));
    }

    @Test
    public void testTimeSpanString() {
        // Check that amounts less than 365 days are not rounded up to a whole year.
        // In the previous implementation there were 360 days in a year.
        // We're still working on the assumption that a month is 30 days, so there will
        // be 5 days at the end of the year that will be "12 months" but not "1 year".
        // First check 359 days.
        assertEquals(Messages.Util_month(11), Util.getTimeSpanString(31017600000L));
        // And 362 days.
        assertEquals(Messages.Util_month(12), Util.getTimeSpanString(31276800000L));

        // 11.25 years - Check that if the first unit has 2 or more digits, a second unit isn't used.
        assertEquals(Messages.Util_year(11), Util.getTimeSpanString(354780000000L));
        // 9.25 years - Check that if the first unit has only 1 digit, a second unit is used.
        assertEquals(Messages.Util_year(9)+ " " + Messages.Util_month(3), Util.getTimeSpanString(291708000000L));
        // 67 seconds
        assertEquals(Messages.Util_minute(1) + " " + Messages.Util_second(7), Util.getTimeSpanString(67000L));
        // 17 seconds - Check that times less than a minute only use seconds.
        assertEquals(Messages.Util_second(17), Util.getTimeSpanString(17000L));
        // 1712ms -> 1.7sec
        assertEquals(Messages.Util_second(1.7), Util.getTimeSpanString(1712L));
        // 171ms -> 0.17sec
        assertEquals(Messages.Util_second(0.17), Util.getTimeSpanString(171L));
        // 101ms -> 0.10sec
        assertEquals(Messages.Util_second(0.1), Util.getTimeSpanString(101L));
        // 17ms
        assertEquals(Messages.Util_millisecond(17), Util.getTimeSpanString(17L));
        // 1ms
        assertEquals(Messages.Util_millisecond(1), Util.getTimeSpanString(1L));
        // Test HUDSON-2843 (locale with comma as fraction separator got exception for <10 sec)
        Locale saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            // Just verifying no exception is thrown:
            assertNotNull("German locale", Util.getTimeSpanString(1234));
            assertNotNull("German locale <1 sec", Util.getTimeSpanString(123));
        }
        finally { Locale.setDefault(saveLocale); }
    }


    /**
     * Test that Strings that contain spaces are correctly URL encoded.
     */
    @Test
    public void testEncodeSpaces() {
        final String urlWithSpaces = "http://hudson/job/Hudson Job";
        String encoded = Util.encode(urlWithSpaces);
        assertEquals(encoded, "http://hudson/job/Hudson%20Job");
    }

    /**
     * Test the rawEncode() method.
     */
    @Test
    public void testRawEncode() {
        String[] data = {  // Alternating raw,encoded
            "abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "01234567890!@$&*()-_=+',.", "01234567890!@$&*()-_=+',.",
            " \"#%/:;<>?", "%20%22%23%25%2F%3A%3B%3C%3E%3F",
            "[\\]^`{|}~", "%5B%5C%5D%5E%60%7B%7C%7D%7E",
            "d\u00E9velopp\u00E9s", "d%C3%A9velopp%C3%A9s",
        };
        for (int i = 0; i < data.length; i += 2) {
            assertEquals("test " + i, data[i + 1], Util.rawEncode(data[i]));
        }
    }

    /**
     * Test the tryParseNumber() method.
     */
    @Test
    public void testTryParseNumber() {
        assertEquals("Successful parse did not return the parsed value", 20, Util.tryParseNumber("20", 10).intValue());
        assertEquals("Failed parse did not return the default value", 10, Util.tryParseNumber("ss", 10).intValue());
        assertEquals("Parsing empty string did not return the default value", 10, Util.tryParseNumber("", 10).intValue());
        assertEquals("Parsing null string did not return the default value", 10, Util.tryParseNumber(null, 10).intValue());
    }

    @Test
    public void testSymlink() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener l = new StreamTaskListener(baos);
        File d = tmp.getRoot();
        try {
            new FilePath(new File(d, "a")).touch(0);
            assertNull(Util.resolveSymlink(new File(d, "a")));
            Util.createSymlink(d,"a","x", l);
            assertEquals("a",Util.resolveSymlink(new File(d,"x")));

            // test a long name
            StringBuilder buf = new StringBuilder(768);
            for( int i=0; i<768; i++)
                buf.append((char)('0'+(i%10)));
            Util.createSymlink(d,buf.toString(),"x", l);

            String log = baos.toString();
            if (log.length() > 0)
                System.err.println("log output: " + log);

            assertEquals(buf.toString(),Util.resolveSymlink(new File(d,"x")));


            // test linking from another directory
            File anotherDir = new File(d,"anotherDir");
            assertTrue("Couldn't create "+anotherDir,anotherDir.mkdir());

            Util.createSymlink(d,"a","anotherDir/link",l);
            assertEquals("a",Util.resolveSymlink(new File(d,"anotherDir/link")));

            // JENKINS-12331: either a bug in createSymlink or this isn't supposed to work:
            //assertTrue(Util.isSymlink(new File(d,"anotherDir/link")));

            File external = File.createTempFile("something", "");
            try {
                Util.createSymlink(d, external.getAbsolutePath(), "outside", l);
                assertEquals(external.getAbsolutePath(), Util.resolveSymlink(new File(d, "outside")));
            } finally {
                assertTrue(external.delete());
            }
        } finally {
            Util.deleteRecursive(d);
        }
    }

    @Test
    public void testIsSymlink() throws IOException, InterruptedException {
        Assume.assumeTrue(!Functions.isWindows());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener l = new StreamTaskListener(baos);
        File d = tmp.getRoot();
        try {
            new FilePath(new File(d, "original")).touch(0);
            assertFalse(Util.isSymlink(new File(d, "original")));
            Util.createSymlink(d,"original","link", l);

            assertTrue(Util.isSymlink(new File(d, "link")));

            // test linking to another directory
            File dir = new File(d,"dir");
            assertTrue("Couldn't create "+dir,dir.mkdir());
            assertFalse(Util.isSymlink(new File(d,"dir")));

            File anotherDir = new File(d,"anotherDir");
            assertTrue("Couldn't create "+anotherDir,anotherDir.mkdir());

            Util.createSymlink(d,"dir","anotherDir/symlinkDir",l);
            // JENKINS-12331: either a bug in createSymlink or this isn't supposed to work:
            // assertTrue(Util.isSymlink(new File(d,"anotherDir/symlinkDir")));
        } finally {
            Util.deleteRecursive(d);
        }
    }

    @Test public void deleteFile() throws Exception {
        Assume.assumeTrue(Functions.isWindows());
        Class<?> c;
        try {
            c = Class.forName("java.nio.file.FileSystemException");
        } catch (ClassNotFoundException x) {
            throw new AssumptionViolatedException("prior to JDK 7", x);
        }
        File d = tmp.getRoot();
        try {
            File f = new File(d, "f");
            OutputStream os = new FileOutputStream(f);
            try {
                Util.deleteFile(f);
                fail("should not have been deletable");
            } catch (IOException x) {
                assertEquals(c, x.getClass());
            } finally {
                os.close();
            }
        } finally {
            Util.deleteRecursive(d);
        }
    }

    @Test
    public void testHtmlEscape() {
        assertEquals("<br>", Util.escape("\n"));
        assertEquals("&lt;a&gt;", Util.escape("<a>"));
        assertEquals("&#039;&quot;", Util.escape("'\""));
        assertEquals("&nbsp; ", Util.escape("  "));
    }

    /**
     * Compute 'known-correct' digests and see if I still get them when computed concurrently
     * to another digest.
     */
    @Issue("JENKINS-10346")
    @Test
    public void testDigestThreadSafety() throws InterruptedException {
    	String a = "abcdefgh";
    	String b = "123456789";

    	String digestA = Util.getDigestOf(a);
    	String digestB = Util.getDigestOf(b);

    	DigesterThread t1 = new DigesterThread(a, digestA);
    	DigesterThread t2 = new DigesterThread(b, digestB);

    	t1.start();
    	t2.start();

    	t1.join();
    	t2.join();

    	if (t1.error != null) {
    		fail(t1.error);
    	}
    	if (t2.error != null) {
    		fail(t2.error);
    	}
    }

    private static class DigesterThread extends Thread {
    	private String string;
		private String expectedDigest;

		private String error;

		public DigesterThread(String string, String expectedDigest) {
    		this.string = string;
    		this.expectedDigest = expectedDigest;
    	}

		public void run() {
			for (int i=0; i < 1000; i++) {
				String digest = Util.getDigestOf(this.string);
				if (!this.expectedDigest.equals(digest)) {
					this.error = "Expected " + this.expectedDigest + ", but got " + digest;
					break;
				}
			}
		}
    }

    @Test
    public void testIsAbsoluteUri() {
        assertTrue(Util.isAbsoluteUri("http://foobar/"));
        assertTrue(Util.isAbsoluteUri("mailto:kk@kohsuke.org"));
        assertTrue(Util.isAbsoluteUri("d123://test/"));
        assertFalse(Util.isAbsoluteUri("foo/bar/abc:def"));
        assertFalse(Util.isAbsoluteUri("foo?abc:def"));
        assertFalse(Util.isAbsoluteUri("foo#abc:def"));
        assertFalse(Util.isAbsoluteUri("foo/bar"));
    }

    @Test
    @Issue("SECURITY-276")
    public void testIsSafeToRedirectTo() {
        assertFalse(Util.isSafeToRedirectTo("http://foobar/"));
        assertFalse(Util.isSafeToRedirectTo("mailto:kk@kohsuke.org"));
        assertFalse(Util.isSafeToRedirectTo("d123://test/"));
        assertFalse(Util.isSafeToRedirectTo("//google.com"));

        assertTrue(Util.isSafeToRedirectTo("foo/bar/abc:def"));
        assertTrue(Util.isSafeToRedirectTo("foo?abc:def"));
        assertTrue(Util.isSafeToRedirectTo("foo#abc:def"));
        assertTrue(Util.isSafeToRedirectTo("foo/bar"));
    }

    @Test
    public void loadProperties() throws IOException {

        assertEquals(0, Util.loadProperties("").size());

        Properties p = Util.loadProperties("k.e.y=va.l.ue");
        assertEquals(p.toString(), "va.l.ue", p.get("k.e.y"));
        assertEquals(p.toString(), 1, p.size());
    }

    @Test
    public void isRelativePathUnix() {
        assertThat("/", not(aRelativePath()));
        assertThat("/foo/bar", not(aRelativePath()));
        assertThat("/foo/../bar", not(aRelativePath()));
        assertThat("", aRelativePath());
        assertThat(".", aRelativePath());
        assertThat("..", aRelativePath());
        assertThat("./foo", aRelativePath());
        assertThat("./foo/bar", aRelativePath());
        assertThat("./foo/bar/", aRelativePath());
    }

    @Test
    public void isRelativePathWindows() {
        assertThat("\\", aRelativePath());
        assertThat("\\foo\\bar", aRelativePath());
        assertThat("\\foo\\..\\bar", aRelativePath());
        assertThat("", aRelativePath());
        assertThat(".", aRelativePath());
        assertThat(".\\foo", aRelativePath());
        assertThat(".\\foo\\bar", aRelativePath());
        assertThat(".\\foo\\bar\\", aRelativePath());
        assertThat("\\\\foo", aRelativePath());
        assertThat("\\\\foo\\", not(aRelativePath()));
        assertThat("\\\\foo\\c", not(aRelativePath()));
        assertThat("C:", aRelativePath());
        assertThat("z:", aRelativePath());
        assertThat("0:", aRelativePath());
        assertThat("c:.", aRelativePath());
        assertThat("c:\\", not(aRelativePath()));
        assertThat("c:/", not(aRelativePath()));
    }

    private static RelativePathMatcher aRelativePath() {
        return new RelativePathMatcher();
    }

    private static class RelativePathMatcher extends BaseMatcher<String> {

        @Override
        public boolean matches(Object item) {
            return Util.isRelativePath((String) item);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a relative path");
        }
    }

}
