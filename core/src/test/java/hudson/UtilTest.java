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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.model.TaskListener;
import hudson.os.WindowsUtil;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
class UtilTest {

    @TempDir
    private File tmp;

    @Test
    void testReplaceMacro() {
        Map<String, String> m = new HashMap<>();
        m.put("A", "a");
        m.put("A.B", "a-b");
        m.put("AA", "aa");
        m.put("B", "B");
        m.put("DOLLAR", "$");
        m.put("ENCLOSED", "a${A}");

        // longest match
        assertEquals("aa", Util.replaceMacro("$AA", m));

        // invalid keys are ignored
        assertEquals("$AAB", Util.replaceMacro("$AAB", m));

        assertEquals("aaB", Util.replaceMacro("${AA}B", m));
        assertEquals("${AAB}", Util.replaceMacro("${AAB}", m));

        // $ escaping
        assertEquals("asd$${AA}dd", Util.replaceMacro("asd$$$${AA}dd", m));
        assertEquals("$", Util.replaceMacro("$$", m));
        assertEquals("$$", Util.replaceMacro("$$$$", m));

        // dots
        assertEquals("a.B", Util.replaceMacro("$A.B", m));
        assertEquals("a-b", Util.replaceMacro("${A.B}", m));

        // test that more complex scenarios work
        assertEquals("/a/B/aa", Util.replaceMacro("/$A/$B/$AA", m));
        assertEquals("a-aa", Util.replaceMacro("$A-$AA", m));
        assertEquals("/a/foo/can/B/you-believe_aa~it?", Util.replaceMacro("/$A/foo/can/$B/you-believe_$AA~it?", m));
        assertEquals("$$aa$Ba${A}$it", Util.replaceMacro("$$$DOLLAR${AA}$$B${ENCLOSED}$it", m));
    }

    @Test
    void testTimeSpanString() {
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
        assertEquals(Messages.Util_year(9) + " " + Messages.Util_month(3), Util.getTimeSpanString(291708000000L));
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
        // Test JENKINS-2843 (locale with comma as fraction separator got exception for <10 sec)
        Locale saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            // Just verifying no exception is thrown:
            assertNotNull(Util.getTimeSpanString(1234), "German locale");
            assertNotNull(Util.getTimeSpanString(123), "German locale <1 sec");
        }
        finally { Locale.setDefault(saveLocale); }
    }


    /**
     * Test that Strings that contain spaces are correctly URL encoded.
     */
    @Test
    void testEncodeSpaces() {
        final String urlWithSpaces = "http://hudson/job/Hudson Job";
        String encoded = Util.encode(urlWithSpaces);
        assertEquals("http://hudson/job/Hudson%20Job", encoded);
    }

    /**
     * Test the rawEncode() method.
     */
    @Test
    void testRawEncode() {
        String[] data = {  // Alternating raw,encoded
            "abcdefghijklmnopqrstuvwxyz",
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "01234567890!@$&*()-_=+',.",
            "01234567890!@$&*()-_=+',.",
            " \"#%/:;<>?",
            "%20%22%23%25%2F%3A%3B%3C%3E%3F",
            "[\\]^`{|}~",
            "%5B%5C%5D%5E%60%7B%7C%7D%7E",
            "d\u00E9velopp\u00E9s",
            "d%C3%A9velopp%C3%A9s",
            "Foo \uD800\uDF98 Foo",
            "Foo%20%F0%90%8E%98%20Foo",
            "\u00E9 ",
            "%C3%A9%20",
        };
        for (int i = 0; i < data.length; i += 2) {
            assertEquals(data[i + 1], Util.rawEncode(data[i]), "test " + i);
        }
    }

    /**
     * Test the fullEncode() method.
     */
    @Test
    void testFullEncode() {
        String[] data = {
                "abcdefghijklmnopqrstuvwxyz",
                "abcdefghijklmnopqrstuvwxyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                "01234567890!@$&*()-_=+',.",
                "01234567890%21%40%24%26%2A%28%29%2D%5F%3D%2B%27%2C%2E",
                " \"#%/:;<>?",
                "%20%22%23%25%2F%3A%3B%3C%3E%3F",
                "[\\]^`{|}~",
                "%5B%5C%5D%5E%60%7B%7C%7D%7E",
                "d\u00E9velopp\u00E9s",
                "d%C3%A9velopp%C3%A9s",
                "Foo \uD800\uDF98 Foo",
                "Foo%20%F0%90%8E%98%20Foo",
                "\u00E9 ",
                "%C3%A9%20",
        };
        for (int i = 0; i < data.length; i += 2) {
            assertEquals(data[i + 1], Util.fullEncode(data[i]), "test " + i);
        }
    }

    /**
     * Test the tryParseNumber() method.
     */
    @Test
    void testTryParseNumber() {
        assertEquals(20, Util.tryParseNumber("20", 10).intValue(), "Successful parse did not return the parsed value");
        assertEquals(10, Util.tryParseNumber("ss", 10).intValue(), "Failed parse did not return the default value");
        assertEquals(10, Util.tryParseNumber("", 10).intValue(), "Parsing empty string did not return the default value");
        assertEquals(10, Util.tryParseNumber(null, 10).intValue(), "Parsing null string did not return the default value");
    }

    @Test
    void testSymlink() throws Exception {
        assumeFalse(Functions.isWindows());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener l = new StreamTaskListener(baos, Charset.defaultCharset());
        File d = tmp;
        try {
            new FilePath(new File(d, "a")).touch(0);
            assertNull(Util.resolveSymlink(new File(d, "a")));
            Util.createSymlink(d, "a", "x", l);
            assertEquals("a", Util.resolveSymlink(new File(d, "x")));

            // test a long name
            StringBuilder buf = new StringBuilder(768);
            for (int i = 0; i < 768; i++)
                buf.append((char) ('0' + (i % 10)));
            Util.createSymlink(d, buf.toString(), "x", l);

            String log = baos.toString(Charset.defaultCharset());
            if (!log.isEmpty())
                System.err.println("log output: " + log);

            assertEquals(buf.toString(), Util.resolveSymlink(new File(d, "x")));


            // test linking from another directory
            File anotherDir = new File(d, "anotherDir");
            assertTrue(anotherDir.mkdir(), "Couldn't create " + anotherDir);

            Util.createSymlink(d, "a", "anotherDir/link", l);
            assertEquals("a", Util.resolveSymlink(new File(d, "anotherDir/link")));

            // JENKINS-12331: either a bug in createSymlink or this isn't supposed to work:
            //assertTrue(Util.isSymlink(new File(d,"anotherDir/link")));

            File external = Files.createTempFile("something", "").toFile();
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
    void testIsSymlink() throws IOException, InterruptedException {
        assumeFalse(Functions.isWindows());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener l = new StreamTaskListener(baos, Charset.defaultCharset());
        File d = tmp;
        try {
            new FilePath(new File(d, "original")).touch(0);
            assertFalse(Util.isSymlink(new File(d, "original")));
            Util.createSymlink(d, "original", "link", l);

            assertTrue(Util.isSymlink(new File(d, "link")));

            // test linking to another directory
            File dir = new File(d, "dir");
            assertTrue(dir.mkdir(), "Couldn't create " + dir);
            assertFalse(Util.isSymlink(new File(d, "dir")));

            File anotherDir = new File(d, "anotherDir");
            assertTrue(anotherDir.mkdir(), "Couldn't create " + anotherDir);

            Util.createSymlink(d, "dir", "anotherDir/symlinkDir", l);
            // JENKINS-12331: either a bug in createSymlink or this isn't supposed to work:
            // assertTrue(Util.isSymlink(new File(d,"anotherDir/symlinkDir")));
        } finally {
            Util.deleteRecursive(d);
        }
    }

    @Test
    void testIsSymlink_onWindows_junction() throws Exception {
        assumeTrue(Functions.isWindows(), "Uses Windows-specific features");
        File targetDir = newFolder(tmp, "targetDir");
        File d = newFolder(tmp, "dir");
        File junction = WindowsUtil.createJunction(new File(d, "junction"), targetDir);
        assertTrue(Util.isSymlink(junction));
    }

    @Test
    @Issue("JENKINS-55448")
    void testIsSymlink_ParentIsJunction() throws IOException, InterruptedException {
        assumeTrue(Functions.isWindows(), "Uses Windows-specific features");
        File targetDir = newFolder(tmp, "junit");
        File file = new File(targetDir, "test-file");
        new FilePath(file).touch(System.currentTimeMillis());
        File dir = newFolder(tmp, "junit");
        File junction = WindowsUtil.createJunction(new File(dir, "junction"), targetDir);

        assertTrue(Util.isSymlink(junction));
        assertFalse(Util.isSymlink(file));
    }

    @Test
    @Issue("JENKINS-55448")
    void testIsSymlink_ParentIsSymlink() throws IOException, InterruptedException {
        assumeFalse(Functions.isWindows());
        File folder = newFolder(tmp, "junit");
        File file = new File(folder, "test-file");
        new FilePath(file).touch(System.currentTimeMillis());
        Path link = tmp.toPath().resolve("sym-link");
        Path pathWithSymlinkParent = Files.createSymbolicLink(link, folder.toPath()).resolve("test-file");
        assertTrue(Util.isSymlink(link));
        assertFalse(Util.isSymlink(pathWithSymlinkParent));
    }

    @Test
    void testHtmlEscape() {
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
    void testDigestThreadSafety() throws InterruptedException {
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

        DigesterThread(String string, String expectedDigest) {
            this.string = string;
            this.expectedDigest = expectedDigest;
        }

        @Override
        public void run() {
            for (int i = 0; i < 1000; i++) {
                String digest = Util.getDigestOf(this.string);
                if (!this.expectedDigest.equals(digest)) {
                    this.error = "Expected " + this.expectedDigest + ", but got " + digest;
                    break;
                }
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void testIsAbsoluteUri() {
        assertTrue(Util.isAbsoluteUri("http://foobar/"));
        assertTrue(Util.isAbsoluteUri("mailto:kk@kohsuke.org"));
        assertTrue(Util.isAbsoluteUri("d123://test/"));
        assertFalse(Util.isAbsoluteUri("foo/bar/abc:def"));
        assertFalse(Util.isAbsoluteUri("foo?abc:def"));
        assertFalse(Util.isAbsoluteUri("foo#abc:def"));
        assertFalse(Util.isAbsoluteUri("foo/bar"));
    }

    @Test
    @Issue({"SECURITY-276", "SECURITY-3501"})
    void testIsSafeToRedirectTo() {
        assertFalse(Util.isSafeToRedirectTo("http://foobar/"));
        assertFalse(Util.isSafeToRedirectTo("mailto:kk@kohsuke.org"));
        assertFalse(Util.isSafeToRedirectTo("d123://test/"));
        assertFalse(Util.isSafeToRedirectTo("//google.com"));
        assertFalse(Util.isSafeToRedirectTo("\\\\google.com"));
        assertFalse(Util.isSafeToRedirectTo("\\/google.com"));
        assertFalse(Util.isSafeToRedirectTo("/\\google.com"));
        assertFalse(Util.isSafeToRedirectTo("\\google.com"));

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
    void loadFile() throws IOException {
        // Standard character sets
        assertEquals(
                "Iñtërnâtiônàlizætiøn",
                Util.loadFile(FileUtils.toFile(getClass().getResource("internationalization-utf-8.txt")), StandardCharsets.UTF_8));
        assertEquals(
                "Iñtërnâtiônàlizætiøn",
                Util.loadFile(FileUtils.toFile(getClass().getResource("internationalization-iso-8859-1.txt")), StandardCharsets.ISO_8859_1));
        assertEquals(
                "Iñtërnâtiônàlizætiøn",
                Util.loadFile(FileUtils.toFile(getClass().getResource("internationalization-windows-1252.txt")), Charset.forName("windows-1252")));

        // Malformed input is replaced without throwing an exception
        assertEquals(
                "Itrntinliztin",
                Util.loadFile(FileUtils.toFile(getClass().getResource("internationalization-utf-8.txt")), StandardCharsets.US_ASCII).replaceAll("�", ""));
        assertEquals(
                "Itrntinliztin",
                Util.loadFile(FileUtils.toFile(getClass().getResource("internationalization-iso-8859-1.txt")), StandardCharsets.US_ASCII).replaceAll("�", ""));
        assertEquals(
                "Itrntinliztin",
                Util.loadFile(FileUtils.toFile(getClass().getResource("internationalization-windows-1252.txt")), StandardCharsets.US_ASCII).replaceAll("�", ""));

        // Unmappable character is replaced without throwing an exception
        assertEquals(
                "foobar",
                Util.loadFile(FileUtils.toFile(getClass().getResource("foo-0x81-bar.txt")), Charset.forName("windows-1252")).replaceAll("�", ""));

        // Nonexistent file is returned as an empty string without throwing an exception
        assertEquals("", Util.loadFile(new File("i-do-not-exist"), StandardCharsets.UTF_8));
    }

    @Test
    void loadProperties() throws IOException {

        assertEquals(0, Util.loadProperties("").size());

        Properties p = Util.loadProperties("k.e.y=va.l.ue");
        assertEquals("va.l.ue", p.get("k.e.y"), p.toString());
        assertEquals(1, p.size(), p.toString());
    }

    @Test
    void isRelativePathUnix() {
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
    void isRelativePathWindows() {
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

    @Test
    void testIsDescendant() throws IOException {
        File root;
        File other;
        if (Functions.isWindows()) {
            root = new File("C:\\Temp");
            other = new File("C:\\Windows");
        } else {
            root = new File("/tmp");
            other = new File("/usr");

        }
        assertTrue(Util.isDescendant(root, new File(root, "child")));
        assertTrue(Util.isDescendant(root, new File(new File(root, "child"), "grandchild")));
        assertFalse(Util.isDescendant(root, other));
        assertFalse(Util.isDescendant(root, new File(other, "child")));

        assertFalse(Util.isDescendant(new File(root, "child"), root));
        assertFalse(Util.isDescendant(new File(new File(root, "child"), "grandchild"), root));

        //.. whithin root
        File convoluted = new File(root, "child");
        convoluted = new File(convoluted, "..");
        convoluted = new File(convoluted, "child");
        assertTrue(Util.isDescendant(root, convoluted));

        //.. going outside of root
        convoluted = new File(root, "..");
        convoluted = new File(convoluted, other.getName());
        convoluted = new File(convoluted, "child");
        assertFalse(Util.isDescendant(root, convoluted));

        //. on root
        assertTrue(Util.isDescendant(new File(root, "."), new File(root, "child")));
        //. on both
        assertTrue(Util.isDescendant(new File(root, "."), new File(new File(root, "child"), ".")));
    }

    @Test
    void testModeToPermissions() throws Exception {
        assertEquals(PosixFilePermissions.fromString("rwxrwxrwx"), Util.modeToPermissions(0777));
        assertEquals(PosixFilePermissions.fromString("rwxr-xrwx"), Util.modeToPermissions(0757));
        assertEquals(PosixFilePermissions.fromString("rwxr-x---"), Util.modeToPermissions(0750));
        assertEquals(PosixFilePermissions.fromString("r-xr-x---"), Util.modeToPermissions(0550));
        assertEquals(PosixFilePermissions.fromString("r-xr-----"), Util.modeToPermissions(0540));
        assertEquals(PosixFilePermissions.fromString("--xr-----"), Util.modeToPermissions(0140));
        assertEquals(PosixFilePermissions.fromString("--xr---w-"), Util.modeToPermissions(0142));
        assertEquals(PosixFilePermissions.fromString("--xr--rw-"), Util.modeToPermissions(0146));
        assertEquals(PosixFilePermissions.fromString("-wxr--rw-"), Util.modeToPermissions(0346));
        assertEquals(PosixFilePermissions.fromString("---------"), Util.modeToPermissions(0000));

        assertEquals(PosixFilePermissions.fromString("r-xr-----"), Util.modeToPermissions(0100540), "Non-permission bits should be ignored");

        Exception e = assertThrows(Exception.class, () -> Util.modeToPermissions(01777));
        assertThat(e.getMessage(), startsWith("Invalid mode"));
    }

    @Test
    void testPermissionsToMode() {
        assertEquals(0777, Util.permissionsToMode(PosixFilePermissions.fromString("rwxrwxrwx")));
        assertEquals(0757, Util.permissionsToMode(PosixFilePermissions.fromString("rwxr-xrwx")));
        assertEquals(0750, Util.permissionsToMode(PosixFilePermissions.fromString("rwxr-x---")));
        assertEquals(0550, Util.permissionsToMode(PosixFilePermissions.fromString("r-xr-x---")));
        assertEquals(0540, Util.permissionsToMode(PosixFilePermissions.fromString("r-xr-----")));
        assertEquals(0140, Util.permissionsToMode(PosixFilePermissions.fromString("--xr-----")));
        assertEquals(0142, Util.permissionsToMode(PosixFilePermissions.fromString("--xr---w-")));
        assertEquals(0146, Util.permissionsToMode(PosixFilePermissions.fromString("--xr--rw-")));
        assertEquals(0346, Util.permissionsToMode(PosixFilePermissions.fromString("-wxr--rw-")));
        assertEquals(0000, Util.permissionsToMode(PosixFilePermissions.fromString("---------")));
    }

    @Test
    void testDifferenceDays() throws Exception {
        Date may_6_10am = parseDate("2018-05-06 10:00:00");
        Date may_6_11pm55 = parseDate("2018-05-06 23:55:00");
        Date may_7_01am = parseDate("2018-05-07 01:00:00");
        Date may_7_11pm = parseDate("2018-05-07 11:00:00");
        Date may_8_08am = parseDate("2018-05-08 08:00:00");
        Date june_3_08am = parseDate("2018-06-03 08:00:00");
        Date june_9_08am = parseDate("2018-06-09 08:00:00");
        Date june_9_08am_nextYear = parseDate("2019-06-09 08:00:00");

        assertEquals(0, Util.daysBetween(may_6_10am, may_6_11pm55));
        assertEquals(1, Util.daysBetween(may_6_10am, may_7_01am));
        assertEquals(1, Util.daysBetween(may_6_11pm55, may_7_01am));
        assertEquals(2, Util.daysBetween(may_6_10am, may_8_08am));
        assertEquals(1, Util.daysBetween(may_7_11pm, may_8_08am));

        // larger scale
        assertEquals(28, Util.daysBetween(may_6_10am, june_3_08am));
        assertEquals(34, Util.daysBetween(may_6_10am, june_9_08am));
        assertEquals(365 + 34, Util.daysBetween(may_6_10am, june_9_08am_nextYear));

        // reverse order
        assertEquals(-1, Util.daysBetween(may_8_08am, may_7_11pm));
    }

    private Date parseDate(String dateString) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(dateString);
    }

    @Test
    @Issue("SECURITY-904")
    void resolveSymlinkToFile() throws Exception {
        assumeFalse(Functions.isWindows());
        //  root
        //      /a
        //          /aa
        //              aa.txt
        //          /_b => symlink to /root/b
        //      /b
        //          /_a => symlink to /root/a
        File root = tmp;
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        aa.mkdirs();
        File aaTxt = new File(aa, "aa.txt");
        Files.writeString(aaTxt.toPath(), "aa", StandardCharsets.US_ASCII);

        File b = new File(root, "b");
        b.mkdir();

        File _a = new File(b, "_a");
        Util.createSymlink(_a.getParentFile(), a.getAbsolutePath(), _a.getName(), TaskListener.NULL);

        File _b = new File(a, "_b");
        Util.createSymlink(_b.getParentFile(), b.getAbsolutePath(), _b.getName(), TaskListener.NULL);

        assertTrue(Files.isSymbolicLink(_a.toPath()));
        assertTrue(Files.isSymbolicLink(_b.toPath()));

        // direct symlinks are resolved
        assertEquals(Util.resolveSymlinkToFile(_a), a);
        assertEquals(Util.resolveSymlinkToFile(_b), b);

        // intermediate symlinks are NOT resolved
        assertNull(Util.resolveSymlinkToFile(new File(_a, "aa")));
        assertNull(Util.resolveSymlinkToFile(new File(_a, "aa/aa.txt")));
    }

    @Test
    @Issue("JENKINS-67372")
    void createDirectories() throws Exception {
        assumeFalse(Functions.isWindows());
        //  root
        //      /a
        //          /a1
        //          /a2 => symlink to a1
        //      /b => symlink to a
        Path root = tmp.toPath().toRealPath();
        Path a = root.resolve("a");
        Path a1 = a.resolve("a1");
        Files.createDirectories(a1);

        Path a2 = a.resolve("a2");
        Util.createSymlink(a2.getParent().toFile(), a1.getFileName().toString(), a2.getFileName().toString(), TaskListener.NULL);

        Path b = root.resolve("b");
        Util.createSymlink(b.getParent().toFile(), a.getFileName().toString(), b.getFileName().toString(), TaskListener.NULL);

        assertTrue(Files.isSymbolicLink(a2));
        assertTrue(Files.isSymbolicLink(b));

        assertEquals(a.resolve("new1"), Util.createDirectories(a.resolve("new1")).toRealPath());
        assertEquals(a1.resolve("new2"), Util.createDirectories(a1.resolve("new2")).toRealPath());
        assertEquals(a1.resolve("new3"), Util.createDirectories(a2.resolve("new3")).toRealPath());
        assertEquals(a.resolve("new4"), Util.createDirectories(b.resolve("new4")).toRealPath());
        assertEquals(a1.resolve("new5"), Util.createDirectories(b.resolve("a1").resolve("new5")).toRealPath());
        assertEquals(a1.resolve("new6"), Util.createDirectories(b.resolve("a2").resolve("new6")).toRealPath());
    }

    @Test
    @Issue("JENKINS-67372")
    void createDirectoriesInRoot() throws Exception {
        assumeFalse(Functions.isWindows());
        Path newDirInRoot = Paths.get("/new-dir-in-root");
        Path newSymlinkInRoot = Paths.get("/new-symlink-in-root");
        try {
            assertEquals(newDirInRoot.resolve("new1"), Util.createDirectories(newDirInRoot.resolve("new1")).toRealPath());
            Util.createSymlink(newSymlinkInRoot.getParent().toFile(), newDirInRoot.getFileName().toString(), newSymlinkInRoot.getFileName().toString(), TaskListener.NULL);
            assertEquals(newDirInRoot.resolve("new2"), Util.createDirectories(newSymlinkInRoot.resolve("new2")).toRealPath());
        } catch (FileSystemException e) {
            // Not running as root
            assumeTrue(false, e.toString());
        }
    }

    @Test
    void ifOverriddenSuccess() {
        assertTrue(Util.ifOverridden(() -> true, BaseClass.class, DerivedClassSuccess.class, "method"));
    }

    @Test
    void ifOverriddenFailure() {
        AbstractMethodError error = assertThrows(AbstractMethodError.class, () -> Util.ifOverridden(() -> true, BaseClass.class, DerivedClassFailure.class, "method"));
        assertEquals("The class " + DerivedClassFailure.class.getName() + " must override at least one of the BaseClass.method methods", error.getMessage());
    }

    @Test
    void testGetHexOfSHA256DigestOf() {
        byte[] input = new byte[] {12, 34, 16};
        String str = Util.getHexOfSHA256DigestOf(input);
        assertEquals("134fefbd329986726407a5208107ef07c9e33da779f5068bff191733268fe997", str);
    }

    @Test
    void testGetSHA256DigestOf() {
        byte[] input = new byte[] {12, 34, 16};
        byte[] sha256DigestActual = Util.getSHA256DigestOf(input);

        byte[] expected = new byte[]
                { 19, 79, -17, -67, 50, -103, -122, 114, 100, 7, -91, 32, -127, 7, -17, 7, -55, -29, 61, -89, 121, -11,
                6, -117, -1, 25, 23, 51, 38, -113, -23, -105};
        assertArrayEquals(expected, sha256DigestActual);
    }

    public static class BaseClass {
        protected String method() {
            return "base";
        }

    }

    public static class DerivedClassFailure extends BaseClass {

    }

    public static class DerivedClassSuccess extends BaseClass {
        @Override
        protected String method() {
            return DerivedClassSuccess.class.getName();
        }

    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.exists() && !result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
