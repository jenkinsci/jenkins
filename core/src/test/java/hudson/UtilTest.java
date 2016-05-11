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

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assume;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import hudson.util.StreamTaskListener;

import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.Lists;

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

    @Test
    public void testDeleteFile() throws Exception {
        File f = tmp.newFile();
        // Test: File is deleted
        mkfiles(f);
        Util.deleteFile(f);
        assertFalse("f exists after calling Util.deleteFile", f.exists());
    }

    @Test
    public void testDeleteFile_onWindows() throws Exception {
        Assume.assumeTrue(Functions.isWindows());
        Class<?> c;
        try {
            c = Class.forName("java.nio.file.FileSystemException");
        } catch (ClassNotFoundException x) {
            throw new AssumptionViolatedException("prior to JDK 7", x);
        }
        final int defaultDeletionMax = Util.DELETION_MAX;
        try {
            File f = tmp.newFile();
            // Test: If we cannot delete a file, we throw explaining why
            mkfiles(f);
            lockFileForDeletion(f);
            Util.DELETION_MAX = 1;
            try {
                Util.deleteFile(f);
                fail("should not have been deletable");
            } catch (IOException x) {
                assertThat(calcExceptionHierarchy(x), hasItem(c));
                assertThat(x.getMessage(), containsString(f.getPath()));
            }
        } finally {
            Util.DELETION_MAX = defaultDeletionMax;
            unlockFilesForDeletion();
        }
    }

    @Test
    public void testDeleteContentsRecursive() throws Exception {
        final File dir = tmp.newFolder();
        final File d1 = new File(dir, "d1");
        final File d2 = new File(dir, "d2");
        final File f1 = new File(dir, "f1");
        final File d1f1 = new File(d1, "d1f1");
        final File d2f2 = new File(d2, "d1f2");
        // Test: Files and directories are deleted
        mkdirs(dir, d1, d2);
        mkfiles(f1, d1f1, d2f2);
        Util.deleteContentsRecursive(dir);
        assertTrue("dir exists", dir.exists());
        assertFalse("d1 exists", d1.exists());
        assertFalse("d2 exists", d2.exists());
        assertFalse("f1 exists", f1.exists());
    }

    @Test
    public void testDeleteContentsRecursive_onWindows() throws Exception {
        Assume.assumeTrue(Functions.isWindows());
        final File dir = tmp.newFolder();
        final File d1 = new File(dir, "d1");
        final File d2 = new File(dir, "d2");
        final File f1 = new File(dir, "f1");
        final File d1f1 = new File(d1, "d1f1");
        final File d2f2 = new File(d2, "d1f2");
        final int defaultDeletionMax = Util.DELETION_MAX;
        final int defaultDeletionWait = Util.WAIT_BETWEEN_DELETION_RETRIES;
        final boolean defaultDeletionGC = Util.GC_AFTER_FAILED_DELETE;
        try {
            // Test: If we cannot delete a file, we throw
            // but still deletes everything it can
            // even if we are not retrying deletes.
            mkdirs(dir, d1, d2);
            mkfiles(f1, d1f1, d2f2);
            lockFileForDeletion(d1f1);
            Util.GC_AFTER_FAILED_DELETE = false;
            Util.DELETION_MAX = 2;
            Util.WAIT_BETWEEN_DELETION_RETRIES = 0;
            try {
                Util.deleteContentsRecursive(dir);
                fail("Expected IOException");
            } catch (IOException x) {
                assertFalse("d2 should not exist", d2.exists());
                assertFalse("f1 should not exist", f1.exists());
                assertFalse("d1f2 should not exist", d2f2.exists());
                assertThat(x.getMessage(), containsString(dir.getPath()));
                assertThat(x.getMessage(), allOf(not(containsString("interrupted")), containsString("Tried 2 times (of a maximum of 2)."), not(containsString("garbage-collecting")), not(containsString("wait"))));
            }
        } finally {
            Util.DELETION_MAX = defaultDeletionMax;
            Util.WAIT_BETWEEN_DELETION_RETRIES = defaultDeletionWait;
            Util.GC_AFTER_FAILED_DELETE = defaultDeletionGC;
            unlockFilesForDeletion();
        }
    }

    @Test
    public void testDeleteRecursive() throws Exception {
        final File dir = tmp.newFolder();
        final File d1 = new File(dir, "d1");
        final File d2 = new File(dir, "d2");
        final File f1 = new File(dir, "f1");
        final File d1f1 = new File(d1, "d1f1");
        final File d2f2 = new File(d2, "d1f2");
        // Test: Files and directories are deleted
        mkdirs(dir, d1, d2);
        mkfiles(f1, d1f1, d2f2);
        Util.deleteRecursive(dir);
        assertFalse("dir exists", dir.exists());
    }

    @Test
    public void testDeleteRecursive_onWindows() throws Exception {
        Assume.assumeTrue(Functions.isWindows());
        final File dir = tmp.newFolder();
        final File d1 = new File(dir, "d1");
        final File d2 = new File(dir, "d2");
        final File f1 = new File(dir, "f1");
        final File d1f1 = new File(d1, "d1f1");
        final File d2f2 = new File(d2, "d1f2");
        final int defaultDeletionMax = Util.DELETION_MAX;
        final int defaultDeletionWait = Util.WAIT_BETWEEN_DELETION_RETRIES;
        final boolean defaultDeletionGC = Util.GC_AFTER_FAILED_DELETE;
        try {
            // Test: If we cannot delete a file, we throw
            // but still deletes everything it can
            // even if we are not retrying deletes.
        	// (And when we are not retrying deletes,
        	// we do not do the "between retries" delay)
            mkdirs(dir, d1, d2);
            mkfiles(f1, d1f1, d2f2);
            lockFileForDeletion(d1f1);
            Util.GC_AFTER_FAILED_DELETE = false;
            Util.DELETION_MAX = 1;
            Util.WAIT_BETWEEN_DELETION_RETRIES = 10000; // long enough to notice
            long timeWhenDeletionStarted = System.currentTimeMillis();
            try {
                Util.deleteRecursive(dir);
                fail("Expected IOException");
            } catch (IOException x) {
                long timeWhenDeletionEnded = System.currentTimeMillis();
                assertTrue("dir exists", dir.exists());
                assertTrue("d1 exists", d1.exists());
                assertTrue("d1f1 exists", d1f1.exists());
                assertFalse("d2 should not exist", d2.exists());
                assertFalse("f1 should not exist", f1.exists());
                assertFalse("d1f2 should not exist", d2f2.exists());
                assertThat(x.getMessage(), containsString(dir.getPath()));
                assertThat(x.getMessage(), allOf(not(containsString("interrupted")), not(containsString("maximum of")), not(containsString("garbage-collecting"))));
                long actualTimeSpentDeleting = timeWhenDeletionEnded - timeWhenDeletionStarted;
                assertTrue("did not wait - took " + actualTimeSpentDeleting + "ms", actualTimeSpentDeleting<1000L);
            }
            unlockFileForDeletion(d1f1);
            // Deletes get retried if they fail 1st time around,
            // allowing the operation to succeed on subsequent attempts.
            // Note: This is what bug JENKINS-15331 is all about.
            mkdirs(dir, d1, d2);
            mkfiles(f1, d1f1, d2f2);
            lockFileForDeletion(d2f2);
            Util.DELETION_MAX=4;
            Util.WAIT_BETWEEN_DELETION_RETRIES = 100;
            Thread unlockAfterDelay = new Thread("unlockFileAfterDelay") {
                public void run() {
                    try {
                        Thread.sleep(Util.WAIT_BETWEEN_DELETION_RETRIES);
                        unlockFileForDeletion(d2f2);
                    } catch( Exception x ) { /* ignored */ }
                }
            };
            unlockAfterDelay.start();
            Util.deleteRecursive(dir);
            assertFalse("dir should have been deleted", dir.exists());
            unlockAfterDelay.join();
            // An interrupt aborts the delete and makes it fail, even
            // if we had been told to retry a lot.
            mkdirs(dir, d1, d2);
            mkfiles(f1, d1f1, d2f2);
            lockFileForDeletion(d1f1);
            Util.DELETION_MAX=10;
            Util.WAIT_BETWEEN_DELETION_RETRIES = -1000;
            Util.GC_AFTER_FAILED_DELETE = true;
            final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
            Thread deleteToBeInterupted = new Thread("deleteToBeInterupted") {
                public void run() {
                    try { Util.deleteRecursive(dir); }
                    catch( Throwable x ) { thrown.set(x); }
                }
            };
            deleteToBeInterupted.start();
            deleteToBeInterupted.interrupt();
            deleteToBeInterupted.join(500);
            assertFalse("deletion stopped", deleteToBeInterupted.isAlive());
            assertTrue("d1f1 still exists", d1f1.exists());
            unlockFileForDeletion(d1f1);
            Throwable deletionInterruptedEx = thrown.get();
            assertThat(deletionInterruptedEx, instanceOf(IOException.class));
            assertThat(deletionInterruptedEx.getMessage(), allOf(containsString("interrupted"), containsString("maximum of " + Util.DELETION_MAX), containsString("garbage-collecting")));
        } finally {
            Util.DELETION_MAX = defaultDeletionMax;
            Util.WAIT_BETWEEN_DELETION_RETRIES = defaultDeletionWait;
            Util.GC_AFTER_FAILED_DELETE = defaultDeletionGC;
            unlockFilesForDeletion();
        }
    }

    /** Creates multiple directories. */
    private static void mkdirs(File... dirs) {
        for( File d : dirs ) {
            d.mkdir();
            assertTrue(d.getPath(), d.isDirectory());
        }
    }

    /** Creates multiple files, each containing their filename as text content. */
    private static void mkfiles(File... files) throws IOException {
        for( File f : files )
            FileUtils.write(f, f.getName());
    }

    /** Means of unlocking all the files we have locked, indexed by {@link File}. */
    private final Map<File, Callable<Void>> unlockFileCallables = new HashMap<File, Callable<Void>>();

    /** Prevents a file from being deleted, so we can stress the deletion code's retries. */
    private void lockFileForDeletion(File f) throws IOException, InterruptedException {
        assert !unlockFileCallables.containsKey(f) : f + " is already locked." ;
        // Limitation: Only works on Windows. On unix we can delete anything we can create.
        // On unix, can't use "chmod a-w" on the dir as the code-under-test undoes that.
        // On unix, can't use "chattr +i" because that needs root.
        // On unix, can't use "chattr +u" because ext fs ignores it.
        // On Windows, we can't delete files that are open for reading, so we use that.
        assert Functions.isWindows();
        final InputStream s = new FileInputStream(f);
        unlockFileCallables.put(f, new Callable<Void>() {
            public Void call() throws IOException { s.close(); return null; };
        });
    }

    /** Undoes a call to {@link #lockFileForDeletion(File)}. */
    private void unlockFileForDeletion(File f) throws Exception {
        unlockFileCallables.remove(f).call();
    }

    /** Undoes all calls to {@link #lockFileForDeletion(File)}. */
    private void unlockFilesForDeletion() throws Exception {
        while( !unlockFileCallables.isEmpty() ) {
            unlockFileForDeletion(unlockFileCallables.keySet().iterator().next());
        }
    }

    /** Returns all classes in the exception hierarchy. */
    private static Iterable<Class<?>> calcExceptionHierarchy(Throwable t) {
        final List<Class<?>> result = Lists.newArrayList();
        for( ; t!=null ; t = t.getCause())
            result.add(t.getClass());
        return result;
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
        assertTrue(Util.isSafeToRedirectTo("/"));
        assertTrue(Util.isSafeToRedirectTo("/foo"));
        assertTrue(Util.isSafeToRedirectTo(".."));
        assertTrue(Util.isSafeToRedirectTo("../.."));
        assertTrue(Util.isSafeToRedirectTo("/#foo"));
        assertTrue(Util.isSafeToRedirectTo("/?foo"));
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
