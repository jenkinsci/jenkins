/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package jenkins.util;

import com.google.common.collect.ImmutableSet;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class VirtualFileTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    
    @Issue("SECURITY-162")
    @Test public void outsideSymlinks() throws Exception {
        assumeFalse("Symlinks don't work well on Windows", Functions.isWindows());
        File ws = tmp.newFolder("ws");
        FileUtils.write(new File(ws, "safe"), "safe");
        Util.createSymlink(ws, "safe", "supported", TaskListener.NULL);
        File other = tmp.newFolder("other");
        FileUtils.write(new File(other, "secret"), "s3cr3t");
        Util.createSymlink(ws, "../other/secret", "hack", TaskListener.NULL);
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile supported = root.child("supported");
        assertTrue(supported.isFile());
        assertTrue(supported.exists());
        assertEquals("safe", IOUtils.toString(supported.open(), (String) null));
        VirtualFile hack = root.child("hack");
        assertFalse(hack.isFile());
        assertFalse(hack.exists());
        try {
            hack.open();
            fail();
        } catch (FileNotFoundException | NoSuchFileException x) {
            // OK
        }
    }

    @Issue("JENKINS-26810")
    @Test public void mode() throws Exception {
        File f = tmp.newFile();
        VirtualFile vf = VirtualFile.forFile(f);
        FilePath fp = new FilePath(f);
        VirtualFile vfp = VirtualFile.forFilePath(fp);
        assertEquals(modeString(hudson.util.IOUtils.mode(f)), modeString(vf.mode()));
        assertEquals(modeString(hudson.util.IOUtils.mode(f)), modeString(vfp.mode()));
        fp.chmod(0755); // no-op on Windows, but harmless
        assertEquals(modeString(hudson.util.IOUtils.mode(f)), modeString(vf.mode()));
        assertEquals(modeString(hudson.util.IOUtils.mode(f)), modeString(vfp.mode()));
    }
    private static String modeString(int mode) throws IOException {
        return mode == -1 ? "N/A" : PosixFilePermissions.toString(Util.modeToPermissions(mode));
    }

    @Issue("JENKINS-26810")
    @Test public void list() throws Exception {
        File root = tmp.getRoot();
        FilePath rootF = new FilePath(root);
        Set<String> paths = ImmutableSet.of("top.txt", "sub/mid.txt", "sub/subsub/lowest.txt", ".hg/config.txt", "very/deep/path/here");
        for (String path : paths) {
            rootF.child(path).write("", null);
        }
        for (VirtualFile vf : new VirtualFile[] {VirtualFile.forFile(root), VirtualFile.forFilePath(rootF), new Ram(paths.stream().map(p -> "/" + p).collect(Collectors.toSet()), "")}) {
            System.err.println("testing " + vf.getClass().getName());
            assertEquals("[.hg/config.txt, sub/mid.txt, sub/subsub/lowest.txt, top.txt]", new TreeSet<>(vf.list("**/*.txt", null, false)).toString());
            assertEquals("[sub/mid.txt, sub/subsub/lowest.txt, top.txt]", new TreeSet<>(vf.list("**/*.txt", null, true)).toString());
            assertEquals("[.hg/config.txt, sub/mid.txt, sub/subsub/lowest.txt, top.txt, very/deep/path/here]", new TreeSet<>(vf.list("**", null, false)).toString());
            assertEquals("[]", new TreeSet<>(vf.list("", null, false)).toString());
            assertEquals("[sub/mid.txt, sub/subsub/lowest.txt]", new TreeSet<>(vf.list("sub/", null, false)).toString());
            assertEquals("[sub/mid.txt]", new TreeSet<>(vf.list("sub/", "sub/subsub/", false)).toString());
            assertEquals("[sub/mid.txt]", new TreeSet<>(vf.list("sub/", "sub/subsub/**", false)).toString());
            assertEquals("[sub/mid.txt]", new TreeSet<>(vf.list("sub/", "**/subsub/", false)).toString());
            assertEquals("[.hg/config.txt, sub/mid.txt]", new TreeSet<>(vf.list("**/mid*,**/conf*", null, false)).toString());
            assertEquals("[sub/mid.txt, sub/subsub/lowest.txt]", new TreeSet<>(vf.list("sub/", "**/notthere/", false)).toString());
            assertEquals("[top.txt]", new TreeSet<>(vf.list("*.txt", null, false)).toString());
            assertEquals("[sub/subsub/lowest.txt, top.txt, very/deep/path/here]", new TreeSet<>(vf.list("**", "**/mid*,**/conf*", false)).toString());
        }
    }
    /** Roughly analogous to {@code org.jenkinsci.plugins.compress_artifacts.ZipStorage}. */
    private static final class Ram extends VirtualFile {
        private final Set<String> paths; // e.g., [/very/deep/path/here]
        private final String path; // e.g., empty string or /very or /very/deep/path/here
        Ram(Set<String> paths, String path) {
            this.paths = paths;
            this.path = path;
        }
        @Override
        public String getName() {
            return path.replaceFirst(".*/", "");
        }
        @Override
        public URI toURI() {
            return URI.create("ram:" + path);
        }
        @Override
        public VirtualFile getParent() {
            return new Ram(paths, path.replaceFirst("/[^/]+$", ""));
        }
        @Override
        public boolean isDirectory() throws IOException {
            return paths.stream().anyMatch(p -> p.startsWith(path + "/"));
        }
        @Override
        public boolean isFile() throws IOException {
            return paths.contains(path);
        }
        @Override
        public boolean exists() throws IOException {
            return isFile() || isDirectory();
        }
        @Override
        public VirtualFile[] list() throws IOException {
            return paths.stream().filter(p -> p.startsWith(path + "/")).map(p -> new Ram(paths, p.replaceFirst("(\\Q" + path + "\\E/[^/]+)/.+", "$1"))).toArray(VirtualFile[]::new);
        }
        @Override
        public VirtualFile child(String name) {
            return new Ram(paths, path + "/" + name);
        }
        @Override
        public long length() throws IOException {
            return 0;
        }
        @Override
        public long lastModified() throws IOException {
            return 0;
        }
        @Override
        public boolean canRead() throws IOException {
            return isFile();
        }
        @Override
        public InputStream open() throws IOException {
            return new NullInputStream(0);
        }
    }

    @Issue("JENKINS-26810")
    @Test public void readLink() throws Exception {
        assumeFalse("Symlinks do not work well on Windows", Functions.isWindows());
        File root = tmp.getRoot();
        FilePath rootF = new FilePath(root);
        rootF.child("plain").write("", null);
        rootF.child("link").symlinkTo("physical", TaskListener.NULL);
        for (VirtualFile vf : new VirtualFile[] {VirtualFile.forFile(root), VirtualFile.forFilePath(rootF)}) {
            assertNull(vf.readLink());
            assertNull(vf.child("plain").readLink());
            VirtualFile link = vf.child("link");
            assertEquals("physical", link.readLink());
            assertFalse(link.isFile());
            assertFalse(link.isDirectory());
            // not checking .exists() for now
        }
    }

    //  root
    //      /a
    //          /aa
    //              /aaa
    //                  /_b2 => symlink to /root/b
    //              aa.txt
    //          /ab
    //              ab.txt
    //          /_b => symlink to /root/b
    //      /b
    //          /_a => symlink to /root/a
    //          /_aatxt => symlink to /root/a/aa/aa.txt
    //          /ba
    //              ba.txt
    private void prepareFileStructureForIsDescendant() throws Exception {
        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        File aaa = new File(aa, "aaa");
        aaa.mkdirs();
        File aaTxt = new File(aa, "aa.txt");
        FileUtils.write(aaTxt, "aa");

        File ab = new File(a, "ab");
        ab.mkdirs();
        File abTxt = new File(ab, "ab.txt");
        FileUtils.write(abTxt, "ab");

        File b = new File(root, "b");

        File ba = new File(b, "ba");
        ba.mkdirs();
        File baTxt = new File(ba, "ba.txt");
        FileUtils.write(baTxt, "ba");

        File _a = new File(b, "_a");
        new FilePath(_a).symlinkTo(a.getAbsolutePath(), TaskListener.NULL);

        File _aatxt = new File(b, "_aatxt");
        new FilePath(_aatxt).symlinkTo(aaTxt.getAbsolutePath(), TaskListener.NULL);

        File _b = new File(a, "_b");
        new FilePath(_b).symlinkTo(b.getAbsolutePath(), TaskListener.NULL);
        File _b2 = new File(aaa, "_b2");
        new FilePath(_b2).symlinkTo(b.getAbsolutePath(), TaskListener.NULL);
    }

    @Issue("SECURITY-904")
    @Test public void forFile_isDescendant() throws Exception {
        this.prepareFileStructureForIsDescendant();

        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        // keep the root information for isDescendant
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        VirtualFile virtualFromA = VirtualFile.forFile(a);

        checkCommonAssertionForIsDescendant(virtualRoot, virtualRootChildA, virtualFromA, aa.getAbsolutePath());
    }

    @Test
    @Issue("SECURITY-904")
    public void forFilePath_isDescendant() throws Exception {
        this.prepareFileStructureForIsDescendant();

        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        // keep the root information for isDescendant
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        VirtualFile virtualFromA = VirtualFile.forFilePath(new FilePath(a));

        checkCommonAssertionForIsDescendant(virtualRoot, virtualRootChildA, virtualFromA, aa.getAbsolutePath());
    }

    private void checkCommonAssertionForIsDescendant(VirtualFile virtualRoot, VirtualFile virtualRootChildA, VirtualFile virtualFromA, String absolutePath) throws Exception {
        try {
            virtualRootChildA.isDescendant(absolutePath);
            fail("isDescendant should have refused the absolute path");
        } catch (IllegalArgumentException e) {}

        assertTrue(virtualRootChildA.isDescendant("aa"));
        assertTrue(virtualRootChildA.isDescendant("aa/aa.txt"));
        assertTrue(virtualRootChildA.isDescendant("aa\\aa.txt"));
        assertTrue(virtualRootChildA.isDescendant("ab"));
        assertTrue(virtualRootChildA.isDescendant("ab/ab.txt"));
        assertTrue(virtualRootChildA.isDescendant("ab//ab.txt"));
        assertTrue(virtualRootChildA.isDescendant("ab/nonExistingFile.txt"));
        assertTrue(virtualRootChildA.isDescendant("nonExistingFolder"));
        assertTrue(virtualRootChildA.isDescendant("nonExistingFolder/nonExistingFile.txt"));

        assertTrue(virtualRootChildA.isDescendant("_b"));
        assertTrue(virtualRootChildA.isDescendant("_b/ba"));
        assertTrue(virtualRootChildA.isDescendant("_b/ba/ba.txt"));
        assertTrue(virtualRootChildA.isDescendant("aa/aaa/_b2"));
        assertTrue(virtualRootChildA.isDescendant("aa/aaa/_b2/ba"));
        assertTrue(virtualRootChildA.isDescendant("aa/aaa/_b2/ba/ba.txt"));

        // such approach could be used to check existence of file inside symlink
        assertTrue(virtualRootChildA.isDescendant("_b/ba/ba-unexistingFile.txt"));

        // we go outside, then inside = forbidden, could be used to check existence of symlinks
        assertTrue(virtualRootChildA.isDescendant("_b/_a"));
        assertTrue(virtualRootChildA.isDescendant("_b/_a/aa"));
        assertTrue(virtualRootChildA.isDescendant("_b/_a/aa/aa.txt"));

        assertTrue(virtualFromA.isDescendant("aa"));
        assertFalse(virtualFromA.isDescendant("_b"));
        assertFalse(virtualFromA.isDescendant("_b/ba/ba-unexistingFile.txt"));
        assertFalse(virtualFromA.isDescendant("_b/_a"));
        assertFalse(virtualFromA.isDescendant("_b/_a/aa"));
        assertFalse(virtualFromA.isDescendant("_b/_a/aa/aa.txt"));
        assertFalse(virtualFromA.isDescendant("aa/aaa/_b2"));
        assertFalse(virtualFromA.isDescendant("aa/aaa/_b2/ba"));
        assertFalse(virtualFromA.isDescendant("aa/aaa/_b2/ba/ba.txt"));

        assertTrue(virtualRoot.isDescendant("aa"));
        assertTrue(virtualRoot.isDescendant("aa/aa.txt"));
        assertTrue(virtualRoot.isDescendant("ab"));
        assertTrue(virtualRoot.isDescendant("ab/ab.txt"));
        assertTrue(virtualRoot.isDescendant("ab/nonExistingFile.txt"));
        assertTrue(virtualRoot.isDescendant("nonExistingFolder"));
        assertTrue(virtualRoot.isDescendant("nonExistingFolder/nonExistingFile.txt"));

        assertTrue(virtualRoot.isDescendant("_b"));
        assertTrue(virtualRoot.isDescendant("_b/ba"));
        assertTrue(virtualRoot.isDescendant("_b/ba/ba.txt"));
        assertTrue(virtualRoot.isDescendant("_b/_a"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa/"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa/../ab/ab.txt"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa/aa.txt"));
    }

    @Test
    @Issue("JENKINS-55050")
    public void forFile_listOnlyDescendants_withoutIllegal() throws Exception {
        this.prepareFileStructureForIsDescendant();

        File root = tmp.getRoot();
        File a = new File(root, "a");
        File b = new File(root, "b");
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        VirtualFile virtualFromA = VirtualFile.forFile(a);
        VirtualFile virtualFromB = VirtualFile.forFile(b);

        checkCommonAssertionForList(virtualRoot, virtualFromA, virtualFromB);
    }

    @Test
    @Issue("SECURITY-904")
    public void forFilePath_listOnlyDescendants_withoutIllegal() throws Exception {
        this.prepareFileStructureForIsDescendant();

        File root = tmp.getRoot();
        File a = new File(root, "a");
        File b = new File(root, "b");
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        VirtualFile virtualFromA = VirtualFile.forFilePath(new FilePath(a));
        VirtualFile virtualFromB = VirtualFile.forFilePath(new FilePath(b));

        checkCommonAssertionForList(virtualRoot, virtualFromA, virtualFromB);
    }

    private void checkCommonAssertionForList(VirtualFile virtualRoot, VirtualFile virtualFromA, VirtualFile virtualFromB) throws Exception {
        // outside link to folder is not returned
        assertThat(virtualFromA.listOnlyDescendants(), containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab")
        ));

        // outside link to file is not returned
        assertThat(virtualFromB.listOnlyDescendants(), contains(
                VFMatcher.hasName("ba")
        ));

        assertThat(virtualFromA.child("_b").listOnlyDescendants(), hasSize(0));

        assertThat(virtualFromA.child("aa").listOnlyDescendants(), containsInAnyOrder(
                VFMatcher.hasName("aaa"),
                VFMatcher.hasName("aa.txt")
        ));

        // only a outside link
        assertThat(virtualFromA.child("aa").child("aaa").listOnlyDescendants(), hasSize(0));

        // as we start from the root, the a/_b linking to root/b is legal
        assertThat(virtualRoot.child("a").listOnlyDescendants(), containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab"),
                VFMatcher.hasName("_b")
        ));

        assertThat(virtualRoot.child("a").child("_b").listOnlyDescendants(), containsInAnyOrder(
                VFMatcher.hasName("_a"),
                VFMatcher.hasName("_aatxt"),
                VFMatcher.hasName("ba")
        ));

        assertThat(virtualRoot.child("a").child("_b").child("_a").listOnlyDescendants(), containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab"),
                VFMatcher.hasName("_b")
        ));
    }

    private abstract static class VFMatcher extends TypeSafeMatcher<VirtualFile> {
        private final String description;

        private VFMatcher(String description) {
            this.description = description;
        }

        public void describeTo(Description description) {
            description.appendText(this.description);
        }

        public static VFMatcher hasName(String expectedName) {
            return new VFMatcher("Has name: " + expectedName) {
                protected boolean matchesSafely(VirtualFile vf) {
                    return expectedName.equals(vf.getName());
                }
            };
        }
    }
}
