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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.TaskListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class VirtualFileTest {

    @TempDir
    private File tmp;

    @Issue("SECURITY-162")
    @Test
    void outsideSymlinks() throws Exception {
        assumeFalse(Functions.isWindows());
        File ws = newFolder(tmp, "ws");
        Files.writeString(ws.toPath().resolve("safe"), "safe", StandardCharsets.US_ASCII);
        Util.createSymlink(ws, "safe", "supported", TaskListener.NULL);
        File other = newFolder(tmp, "other");
        Files.writeString(other.toPath().resolve("secret"), "s3cr3t", StandardCharsets.US_ASCII);
        Util.createSymlink(ws, "../other/secret", "hack", TaskListener.NULL);
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile supported = root.child("supported");
        assertTrue(supported.isFile());
        assertTrue(supported.exists());
        assertEquals("safe", IOUtils.toString(supported.open(), (String) null));
        VirtualFile hack = root.child("hack");
        assertFalse(hack.isFile());
        assertFalse(hack.exists());
        assertThrows(FileNotFoundException.class, hack::open);
    }

    @Issue("JENKINS-26810")
    @Test
    void mode() throws Exception {
        File f = File.createTempFile("junit", null, tmp);
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
    @Test
    void list() throws Exception {
        File root = tmp;
        FilePath rootF = new FilePath(root);
        Set<String> paths = new HashSet<>(Arrays.asList("top.txt", "sub/mid.txt", "sub/subsub/lowest.txt", ".hg/config.txt", "very/deep/path/here"));
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
        public boolean isDirectory() {
            return paths.stream().anyMatch(p -> p.startsWith(path + "/"));
        }

        @Override
        public boolean isFile() {
            return paths.contains(path);
        }

        @Override
        public boolean exists() throws IOException {
            return isFile() || isDirectory();
        }

        @Override
        public VirtualFile[] list() {
            return paths.stream().filter(p -> p.startsWith(path + "/")).map(p -> new Ram(paths, p.replaceFirst("(\\Q" + path + "\\E/[^/]+)/.+", "$1"))).toArray(VirtualFile[]::new);
        }

        @Override
        public VirtualFile child(String name) {
            return new Ram(paths, path + "/" + name);
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public long lastModified() {
            return 0;
        }

        @Override
        public boolean canRead() throws IOException {
            return isFile();
        }

        @Override
        public InputStream open() {
            return InputStream.nullInputStream();
        }
    }

    @Test
    @Issue("SECURITY-1452")
    void list_IllegalSymlink_FileVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        File a = new File(root, "a");
        VirtualFile virtualRoot = VirtualFile.forFile(a);
        VirtualFile virtualChild = virtualRoot.child("_b");
        Collection<String> children = virtualChild.list("**", null, true);
        assertThat(children, empty());
    }

    @Test
    @Issue("SECURITY-1452")
    void list_Glob_NoFollowLinks_FileVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        Collection<String> children = virtualRoot.list("**", null, true, LinkOption.NOFOLLOW_LINKS);
        assertThat(children, containsInAnyOrder(
                "a/aa/aa.txt",
                "a/ab/ab.txt",
                "b/ba/ba.txt"
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_Glob_NoFollowLinks_FilePathVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        Collection<String> children = virtualRoot.list("**", null, true, LinkOption.NOFOLLOW_LINKS);
        assertThat(children, containsInAnyOrder(
                "a/aa/aa.txt",
                "a/ab/ab.txt",
                "b/ba/ba.txt"
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void zip_NoFollowLinks_FilePathVF() throws Exception {
        File zipFile = new File(tmp, "output.zip");
        File root = tmp;
        File source = new File(root, "source");
        prepareFileStructureForIsDescendant(source);

        VirtualFile sourcePath = VirtualFile.forFilePath(new FilePath(source));
        try (FileOutputStream outputStream = new FileOutputStream(zipFile)) {
            sourcePath.zip(outputStream, "**", null, true, "", LinkOption.NOFOLLOW_LINKS);
        }
        FilePath zipPath = new FilePath(zipFile);
        assertTrue(zipPath.exists());
        assertFalse(zipPath.isDirectory());
        FilePath unzipPath = new FilePath(new File(tmp, "unzip"));
        zipPath.unzip(unzipPath);
        assertTrue(unzipPath.exists());
        assertTrue(unzipPath.isDirectory());
        assertTrue(unzipPath.child("a").child("aa").child("aa.txt").exists());
        assertTrue(unzipPath.child("a").child("ab").child("ab.txt").exists());
        assertFalse(unzipPath.child("a").child("aa").child("aaa").exists());
        assertFalse(unzipPath.child("a").child("_b").exists());
        assertTrue(unzipPath.child("b").child("ba").child("ba.txt").exists());
        assertFalse(unzipPath.child("b").child("_a").exists());
        assertFalse(unzipPath.child("b").child("_aatxt").exists());
    }

    @Test
    @Issue({"JENKINS-19947", "JENKINS-61473"})
    void zip_NoFollowLinks_FilePathVF_withPrefix() throws Exception {
        File zipFile = new File(tmp, "output.zip");
        File root = tmp;
        File source = new File(root, "source");
        prepareFileStructureForIsDescendant(source);

        VirtualFile sourcePath = VirtualFile.forFilePath(new FilePath(source));
        String prefix = "test1";
        try (FileOutputStream outputStream = new FileOutputStream(zipFile)) {
            sourcePath.zip(outputStream, "**", null, true, prefix + "/", LinkOption.NOFOLLOW_LINKS);
        }
        FilePath zipPath = new FilePath(zipFile);
        assertTrue(zipPath.exists());
        assertFalse(zipPath.isDirectory());
        FilePath unzipPath = new FilePath(new File(tmp, "unzip"));
        zipPath.unzip(unzipPath);
        assertTrue(unzipPath.exists());
        assertTrue(unzipPath.isDirectory());
        assertTrue(unzipPath.child(prefix).isDirectory());
        assertTrue(unzipPath.child(prefix).child("a").child("aa").child("aa.txt").exists());
        assertTrue(unzipPath.child(prefix).child("a").child("ab").child("ab.txt").exists());
        assertFalse(unzipPath.child(prefix).child("a").child("aa").child("aaa").exists());
        assertFalse(unzipPath.child(prefix).child("a").child("_b").exists());
        assertTrue(unzipPath.child(prefix).child("b").child("ba").child("ba.txt").exists());
        assertFalse(unzipPath.child(prefix).child("b").child("_a").exists());
        assertFalse(unzipPath.child(prefix).child("b").child("_aatxt").exists());
    }

    @Test
    @Issue("SECURITY-1452")
    void zip_NoFollowLinks_FileVF() throws Exception {
        File zipFile = new File(tmp, "output.zip");
        File root = tmp;
        File source = new File(root, "source");
        prepareFileStructureForIsDescendant(source);

        VirtualFile sourcePath = VirtualFile.forFile(source);
        try (FileOutputStream outputStream = new FileOutputStream(zipFile)) {
            sourcePath.zip(outputStream, "**", null, true, "", LinkOption.NOFOLLOW_LINKS);
        }
        FilePath zipPath = new FilePath(zipFile);
        assertTrue(zipPath.exists());
        assertFalse(zipPath.isDirectory());
        FilePath unzipPath = new FilePath(new File(tmp, "unzip"));
        zipPath.unzip(unzipPath);
        assertTrue(unzipPath.exists());
        assertTrue(unzipPath.isDirectory());
        assertTrue(unzipPath.child("a").child("aa").child("aa.txt").exists());
        assertTrue(unzipPath.child("a").child("ab").child("ab.txt").exists());
        assertFalse(unzipPath.child("a").child("aa").child("aaa").exists());
        assertFalse(unzipPath.child("a").child("_b").exists());
        assertTrue(unzipPath.child("b").child("ba").child("ba.txt").exists());
        assertFalse(unzipPath.child("b").child("_a").exists());
        assertFalse(unzipPath.child("b").child("_aatxt").exists());
    }

    @Test
    @Issue({"JENKINS-19947", "JENKINS-61473"})
    void zip_NoFollowLinks_FileVF_withPrefix() throws Exception {
        File zipFile = new File(tmp, "output.zip");
        File root = tmp;
        File source = new File(root, "source");
        prepareFileStructureForIsDescendant(source);

        String prefix = "test1";
        VirtualFile sourcePath = VirtualFile.forFile(source);
        try (FileOutputStream outputStream = new FileOutputStream(zipFile)) {
            sourcePath.zip(outputStream, "**", null, true, prefix + "/", LinkOption.NOFOLLOW_LINKS);
        }
        FilePath zipPath = new FilePath(zipFile);
        assertTrue(zipPath.exists());
        assertFalse(zipPath.isDirectory());
        FilePath unzipPath = new FilePath(new File(tmp, "unzip"));
        zipPath.unzip(unzipPath);
        assertTrue(unzipPath.exists());
        assertTrue(unzipPath.isDirectory());
        assertTrue(unzipPath.child(prefix).isDirectory());
        assertTrue(unzipPath.child(prefix).child("a").child("aa").child("aa.txt").exists());
        assertTrue(unzipPath.child(prefix).child("a").child("ab").child("ab.txt").exists());
        assertFalse(unzipPath.child(prefix).child("a").child("aa").child("aaa").exists());
        assertFalse(unzipPath.child(prefix).child("a").child("_b").exists());
        assertTrue(unzipPath.child(prefix).child("b").child("ba").child("ba.txt").exists());
        assertFalse(unzipPath.child(prefix).child("b").child("_a").exists());
        assertFalse(unzipPath.child(prefix).child("b").child("_aatxt").exists());
    }

    @Issue("JENKINS-26810")
    @Test
    void readLink() throws Exception {
        assumeFalse(Functions.isWindows());
        File root = tmp;
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

    @Test
    void simpleList_FileVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, hasSize(2));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_FileVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, hasSize(2));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_FilePathVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        List<VirtualFile> children = Arrays.asList(virtualRoot.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, hasSize(2));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void simpleList_WithSymlink_FileVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        List<VirtualFile> children = Arrays.asList(virtualRootChildA.list());
        assertThat(children, hasSize(3));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab"),
                VFMatcher.hasName("_b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_ExternalSymlink_FileVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);
        File root = tmp;
        String symlinkName = "symlink";
        Util.createSymlink(root, "a", symlinkName, null);
        File symlinkFile = new File(root, symlinkName);
        VirtualFile virtualRootSymlink = VirtualFile.forFile(symlinkFile);
        List<VirtualFile> children = Arrays.asList(virtualRootSymlink.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_ExternalSymlink_FilePathVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);
        File root = tmp;
        String symlinkName = "symlink";
        Util.createSymlink(root, "a", symlinkName, null);
        File symlinkFile = new File(root, symlinkName);
        VirtualFile virtualRootSymlink = VirtualFile.forFilePath(new FilePath(symlinkFile));
        List<VirtualFile> children = Arrays.asList(virtualRootSymlink.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_Glob_NoFollowLinks_ExternalSymlink_FilePathVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);
        File root = tmp;
        String symlinkName = "symlink";
        Util.createSymlink(root, "a", symlinkName, null);
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        File symlinkFile = new File(root, symlinkName);
        FilePath symlinkPath = new FilePath(symlinkFile);
        VirtualFile symlinkVirtualPath = VirtualFile.forFilePath(symlinkPath);
        VirtualFile symlinkChildVirtualPath = symlinkVirtualPath.child("aa");
        Collection<String> children = symlinkChildVirtualPath.list("**", null, true, LinkOption.NOFOLLOW_LINKS);
        assertThat(children, contains("aa.txt"));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_Glob_NoFollowLinks_ExternalSymlink_FileVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);
        File root = tmp;
        String symlinkName = "symlink";
        Util.createSymlink(root, "a", symlinkName, null);
        File symlinkFile = new File(root, symlinkName);
        VirtualFile symlinkVirtualFile = VirtualFile.forFile(symlinkFile);
        VirtualFile symlinkChildVirtualFile = symlinkVirtualFile.child("aa");
        Collection<String> children = symlinkChildVirtualFile.list("**", null, true, LinkOption.NOFOLLOW_LINKS);
        assertThat(children, contains("aa.txt"));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_InternalSymlink_FileVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile rootVirtualFile = VirtualFile.forFile(root);
        VirtualFile virtualRootChildA = rootVirtualFile.child("a");
        List<VirtualFile> children = Arrays.asList(virtualRootChildA.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_InternalSymlink_FilePathVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        FilePath rootPath = new FilePath(root);
        VirtualFile rootVirtualPath = VirtualFile.forFilePath(rootPath);
        VirtualFile virtualRootChildA = rootVirtualPath.child("a");
        List<VirtualFile> children = Arrays.asList(virtualRootChildA.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_NoKids_FileVF() throws Exception {
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, empty());
    }

    @Test
    @Issue("SECURITY-1452")
    void list_Glob_NoFollowLinks_NoKids_FileVF() throws Exception {
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        Collection<String> children = virtualRoot.list("**", null, true, LinkOption.NOFOLLOW_LINKS);
        assertThat(children, empty());
    }

    @Test
    @Issue("SECURITY-1452")
    void list_Glob_NoFollowLinks_NoKids_FilePathVF() throws Exception {
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        Collection<String> children = virtualRoot.list("**", null, true, LinkOption.NOFOLLOW_LINKS);
        assertThat(children, empty());
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_NoKids_FilePathVF() throws Exception {
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        List<VirtualFile> children = Arrays.asList(virtualRoot.list(new OpenOption[0]));
        assertThat(children, empty());
    }

    @Test
    void simpleList_NoKids_FileVF() throws Exception {
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, empty());
    }

    @Test
    @Issue("SECURITY-1452")
    void simpleList_IllegalSymlink_FileVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        File a = new File(root, "a");
        VirtualFile virtualRoot = VirtualFile.forFile(a);
        VirtualFile virtualChild = virtualRoot.child("_b");
        List<VirtualFile> children = Arrays.asList(virtualChild.list());
        assertThat(children, empty());
    }

    @Test
    void simpleList_FilePathVF() throws Exception {
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, hasSize(2));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void simpleList_WithSymlink_FilePathVF() throws Exception {
        assumeFalse(Functions.isWindows());
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        List<VirtualFile> children = Arrays.asList(virtualRootChildA.list());
        assertThat(children, hasSize(3));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab"),
                VFMatcher.hasName("_b")
        ));
    }

    @Test
    void simpleList_NoKids_FilePathVF() throws Exception {
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, empty());
    }

    @Test
    void simpleList_AbstractBase() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which has limited behavior.
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, hasSize(2));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_AbstractBase() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which has limited behavior.
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, hasSize(2));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void simpleList_WithSymlink_AbstractBase() throws Exception {
        assumeFalse(Functions.isWindows());
        // This test checks the method's behavior in the abstract base class,
        // which has limited behavior.
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(root);
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        List<VirtualFile> children = Arrays.asList(virtualRootChildA.list());
        assertThat(children, hasSize(3));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab"),
                VFMatcher.hasName("_b")
        ));
    }

    @Test
    @Issue("SECURITY-1452")
    void list_NoFollowLinks_WithSymlink_AbstractBase() throws Exception {
        assumeFalse(Functions.isWindows());
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(root);
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        List<VirtualFile> children = Arrays.asList(virtualRootChildA.list(LinkOption.NOFOLLOW_LINKS));
        assertThat(children, hasSize(3));
        assertThat(children, containsInAnyOrder(
                VFMatcher.hasName("aa"),
                VFMatcher.hasName("ab"),
                VFMatcher.hasName("_b")
        ));
    }

    @Test
    void simpleList_NoKids_AbstractBase() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        File root = tmp;
        FileUtils.touch(root);
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(root);
        List<VirtualFile> children = Arrays.asList(virtualRoot.list());
        assertThat(children, empty());
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
    private void prepareFileStructureForIsDescendant(File root) throws Exception {
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        File aaa = new File(aa, "aaa");
        aaa.mkdirs();
        File aaTxt = new File(aa, "aa.txt");
        Files.writeString(aaTxt.toPath(), "aa", StandardCharsets.US_ASCII);

        File ab = new File(a, "ab");
        ab.mkdirs();
        File abTxt = new File(ab, "ab.txt");
        Files.writeString(abTxt.toPath(), "ab", StandardCharsets.US_ASCII);

        File b = new File(root, "b");

        File ba = new File(b, "ba");
        ba.mkdirs();
        File baTxt = new File(ba, "ba.txt");
        Files.writeString(baTxt.toPath(), "ba", StandardCharsets.US_ASCII);

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
    @Test
    void forFile_isDescendant() throws Exception {
        assumeFalse(Functions.isWindows());
        this.prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
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
    void forFilePath_isDescendant() throws Exception {
        assumeFalse(Functions.isWindows());
        this.prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        // keep the root information for isDescendant
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        VirtualFile virtualFromA = VirtualFile.forFilePath(new FilePath(a));

        checkCommonAssertionForIsDescendant(virtualRoot, virtualRootChildA, virtualFromA, aa.getAbsolutePath());
    }

    private void checkCommonAssertionForIsDescendant(VirtualFile virtualRoot, VirtualFile virtualRootChildA, VirtualFile virtualFromA, String absolutePath) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> virtualRootChildA.isDescendant(absolutePath), "isDescendant should have refused the absolute path");

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
    void forFile_listOnlyDescendants_withoutIllegal() throws Exception {
        assumeFalse(Functions.isWindows());
        this.prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
        File a = new File(root, "a");
        File b = new File(root, "b");
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        VirtualFile virtualFromA = VirtualFile.forFile(a);
        VirtualFile virtualFromB = VirtualFile.forFile(b);

        checkCommonAssertionForList(virtualRoot, virtualFromA, virtualFromB);
    }

    @Test
    @Issue("SECURITY-904")
    void forFilePath_listOnlyDescendants_withoutIllegal() throws Exception {
        assumeFalse(Functions.isWindows());
        this.prepareFileStructureForIsDescendant(tmp);

        File root = tmp;
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

    @Test
    void forAbstractBase_listOnlyDescendants_withoutIllegal() throws Exception {
        File root = tmp;
        FileUtils.touch(new File(root, "a"));
        FileUtils.touch(new File(root, "b"));
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(root);

        assertThat(virtualRoot.listOnlyDescendants(), empty());
    }

    @Test
    void forAbstractBase_WithAllDescendants_listOnlyDescendants_withoutIllegal() throws Exception {
        File root = tmp;
        FileUtils.touch(new File(root, "a"));
        FileUtils.touch(new File(root, "b"));
        VirtualFile virtualRoot = new VirtualFileMinimalImplementationWithDescendants(root);

        List<VirtualFile> descendants = virtualRoot.listOnlyDescendants();
        assertThat(descendants, hasSize(2));
        assertThat(descendants, containsInAnyOrder(
                VFMatcher.hasName("a"),
                VFMatcher.hasName("b")
        ));
    }

    private abstract static class VFMatcher extends TypeSafeMatcher<VirtualFile> {
        private final String description;

        private VFMatcher(String description) {
            this.description = description;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(this.description);
        }

        public static VFMatcher hasName(String expectedName) {
            return new VFMatcher("Has name: " + expectedName) {
                @Override
                protected boolean matchesSafely(VirtualFile vf) {
                    return expectedName.equals(vf.getName());
                }
            };
        }
    }

    @Test
    void testGetParent_FileVF() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child = "child";
        File childFile = new File(parentFile, child);
        VirtualFile vf = VirtualFile.forFile(childFile);
        assertThat(vf.getParent().getName(), is(parentFolder));
    }

    @Test
    void testGetUri_FileVF() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child = "child";
        File childFile = new File(parentFile, child);
        VirtualFile vf = VirtualFile.forFile(childFile);
        URI uri = vf.toURI();
        assertThat(uri.getScheme(), is("file"));
        assertThat(uri.getPath(), endsWith(parentFolder + "/" + child));
    }

    @Test
    @Issue("SECURITY-1452")
    void testIsDirectory_IllegalSymLink_FileVF() throws IOException, InterruptedException {
        String invalidSymlinkName = "invalidSymlink";
        File ws = createInvalidDirectorySymlink(invalidSymlinkName);
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile invalidSymlink = root.child(invalidSymlinkName);
        assertFalse(invalidSymlink.isDirectory());
    }

    @Test
    @Issue("SECURITY-1452")
    void testReadLink_IllegalSymLink_FileVF() throws IOException, InterruptedException {
        String invalidSymlinkName = "invalidSymlink";
        File ws = createInvalidDirectorySymlink(invalidSymlinkName);
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile invalidSymlink = root.child(invalidSymlinkName);
        assertThat(invalidSymlink.readLink(), nullValue());
    }

    @Test
    void testLength_FileVF() throws IOException {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        VirtualFile child = VirtualFile.forFile(ws).child(childString);
        assertThat(child.length(), is((long) childString.length()));
    }

    @Test
    @Issue("SECURITY-1452")
    void testLength_IllegalSymLink_FileVF() throws IOException, InterruptedException {
        File ws = createInvalidFileSymlink();
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile invalidSymlink = root.child("invalidSymlink");
        assertThat(invalidSymlink.length(), is(0L));
    }

    @Test
    @Issue("SECURITY-1452")
    void testMode_IllegalSymLink_FileVF() throws Exception {
        File ws = createInvalidFileSymlink();
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile invalidSymlink = root.child("invalidSymlink");
        assertThat(invalidSymlink.mode(), is(-1));
    }

    @Test
    void testLastModified_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        FileUtils.touch(new File(ws, childString));
        VirtualFile child = VirtualFile.forFile(ws).child(childString);
        long earlierSystemTime = computeEarlierSystemTime();
        assertThat(child.lastModified(), greaterThan(earlierSystemTime));
    }

    @Test
    void testLastModified_IllegalSymLink_FileVF() throws Exception {
        File ws = createInvalidFileSymlink();
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile invalidSymlink = root.child("invalidSymlink");
        assertThat(invalidSymlink.lastModified(), is(0L));
    }

    @Test
    void testCanRead_True_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        FileUtils.touch(new File(ws, childString));
        VirtualFile child = VirtualFile.forFile(ws).child(childString);
        assertTrue(child.canRead());
    }

    @Test
    @Disabled("TODO doesn't pass on ci.jenkins.io due to root user being used in container tests")
    void testCanRead_False_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        File childFile = new File(ws, childString);
        FileUtils.touch(childFile);
        childFile.setReadable(false);
        VirtualFile child = VirtualFile.forFile(ws).child(childString);
        // Windows ignores this setting. On Unix, setting this flag means it cannot be read.
        assertEquals(Functions.isWindows(), child.canRead());
    }

    @Test
    @Issue("SECURITY-1452")
    void testCanRead_IllegalSymlink_FileVF() throws Exception {
        File ws = createInvalidFileSymlink();
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile invalidSymlink = root.child("invalidSymlink");
        assertFalse(invalidSymlink.canRead());
    }

    @Test
    @Issue("SECURITY-1452")
    void testOpenNoFollowLinks_AbstractBase() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        VirtualFile child = new VirtualFileMinimalImplementation(ws).child(childString);
        String fileContents = IOUtils.toString(child.open());
        assertThat(childString, is(fileContents));
    }

    @Test
    @Issue("SECURITY-1452")
    void testOpenNoFollowLinks_FollowsLink_AbstractBase() throws Exception {
        assumeFalse(Functions.isWindows());
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        String linkString = "link";
        Util.createSymlink(ws, childString, linkString, TaskListener.NULL);

        VirtualFile link = new VirtualFileMinimalImplementation(ws).child(linkString);
        String fileContents = IOUtils.toString(link.open(LinkOption.NOFOLLOW_LINKS));
        assertThat(childString, is(fileContents));
    }

    @Test
    @Issue("SECURITY-1452")
    void testOpenNoFollowLinks_NoFollowsLink_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        String linkString = "link";
        Util.createSymlink(ws, childString, linkString, TaskListener.NULL);

        VirtualFile link = VirtualFile.forFile(ws).child(linkString);
        assertThrows(IOException.class, () -> link.open(LinkOption.NOFOLLOW_LINKS), "Should have not followed links");
    }

    @Test
    @Issue("SECURITY-1452")
    void testOpenNoFollowLinks_NoFollowsLinkInternalSymlink_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        VirtualFile rootVirtualFile = VirtualFile.forFile(tmp);
        String symlinkName = "symlink";
        Util.createSymlink(tmp, ws.getName(), symlinkName, null);
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        File childThroughSymlink = new File(tmp, "/" + symlinkName + "/" + childString);
        VirtualFile child = rootVirtualFile.child(symlinkName).child(childString);
        assertThrows(IOException.class, () -> child.open(LinkOption.NOFOLLOW_LINKS), "Should have not followed links");
    }

    @Test
    @Issue("SECURITY-1452")
    void testOpenNoFollowLinks_NoFollowsLinkInternalSymlink_FilePathVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String symlinkName = "symlink";
        Util.createSymlink(tmp, ws.getName(), symlinkName, null);
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        VirtualFile rootVirtualPath = VirtualFile.forFilePath(new FilePath(tmp));
        VirtualFile childVirtualPath = rootVirtualPath.child(symlinkName).child(childString);
        assertThrows(IOException.class, () -> childVirtualPath.open(LinkOption.NOFOLLOW_LINKS), "Should have not followed links");
    }

    @Test
    @Issue("SECURITY-1452")
    void testOpenNoFollowLinks_NoFollowsLink_FilePathVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        String linkString = "link";
        Util.createSymlink(ws, childString, linkString, TaskListener.NULL);

        VirtualFile link = VirtualFile.forFilePath(new FilePath(ws)).child(linkString);
        assertThrows(IOException.class, () -> link.open(LinkOption.NOFOLLOW_LINKS), "Should have not followed links");
    }

    @Test
    void testSupportIsDescendant_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        VirtualFile root = VirtualFile.forFile(ws);
        assertTrue(root.supportIsDescendant());
    }

    @Test
    void testSupportsQuickRecursiveListing_FileVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        VirtualFile root = VirtualFile.forFile(ws);
        assertTrue(root.supportsQuickRecursiveListing());
    }

    @Test
    void testGetParent_FilePathVF() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child = "child";
        File childFile = new File(parentFile, child);
        VirtualFile vf = VirtualFile.forFilePath(new FilePath(childFile));
        assertThat(vf.getParent().getName(), is(parentFolder));
    }

    @Test
    void testGetUri_FilePathVF() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child = "child";
        File childFile = new File(parentFile, child);
        VirtualFile vf = VirtualFile.forFilePath(new FilePath(childFile));
        URI uri = vf.toURI();
        assertThat(uri.getScheme(), is("file"));
        assertThat(uri.getPath(), endsWith(parentFolder + "/" + child));
    }

    @Test
    void testLength_FilePathVF() throws IOException {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        Files.writeString(ws.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        VirtualFile child = VirtualFile.forFilePath(new FilePath(ws)).child(childString);
        assertThat(child.length(), is((long) childString.length()));
    }

    @Test
    void testLastModified_FilePathVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        FileUtils.touch(new File(ws, childString));
        VirtualFile child = VirtualFile.forFilePath(new FilePath(ws)).child(childString);
        long earlierSystemTime = computeEarlierSystemTime();
        assertThat(child.lastModified(), greaterThan(earlierSystemTime));
    }

    @Test
    void testCanRead_True_FilePathVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        FileUtils.touch(new File(ws, childString));
        VirtualFile child = VirtualFile.forFilePath(new FilePath(ws)).child(childString);
        assertTrue(child.canRead());
    }

    @Test
    @Disabled("TODO doesn't pass on ci.jenkins.io due to root user being used in container tests")
    void testCanRead_False_FilePathVF() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        File ws = newFolder(tmp, "ws");
        String childString = "child";
        File childFile = new File(ws, childString);
        FileUtils.touch(childFile);
        childFile.setReadable(false);
        VirtualFile child = VirtualFile.forFilePath(new FilePath(ws)).child(childString);
        assertEquals(Functions.isWindows(), child.canRead());
    }

    @Test
    void testSupportIsDescendant_FilePathVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        VirtualFile root = VirtualFile.forFilePath(new FilePath(ws));
        assertTrue(root.supportIsDescendant());
    }

    @Test
    void testSupportsQuickRecursiveListing_FilePathVF() throws Exception {
        File ws = newFolder(tmp, "ws");
        VirtualFile root = VirtualFile.forFilePath(new FilePath(ws));
        assertTrue(root.supportsQuickRecursiveListing());
    }

    @Test
    void testSupportIsDescendant_AbstractBase() {
        VirtualFile root = new VirtualFileMinimalImplementation();
        assertFalse(root.supportIsDescendant());
    }

    @Test
    void testSupportsQuickRecursiveListing_AbstractBase() {
        VirtualFile root = new VirtualFileMinimalImplementation();
        assertFalse(root.supportsQuickRecursiveListing());
    }

    @Test
    void testReadLink_AbstractBase() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        VirtualFile root = new VirtualFileMinimalImplementation();
        assertThat(root.readLink(), nullValue());
    }

    @Test
    void testMode_AbstractBase() throws Exception {
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        VirtualFile root = new VirtualFileMinimalImplementation();
        assertThat(root.mode(), is(-1));
    }

    @Test
    void testIsDescendant_AbstractBase() throws Exception {
        VirtualFile root = new VirtualFileMinimalImplementation();
        assertFalse(root.isDescendant("anything"));
    }

    @Test
    void testExternalUrl() throws Exception {
        VirtualFile root = new VirtualFileMinimalImplementation();
        assertThat(root.toExternalURL(), nullValue());
    }

    @Test
    void testToString() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child = "child";
        File childFile = new File(parentFile, child);
        VirtualFile vf = new VirtualFileMinimalImplementation(childFile);
        String vfString = vf.toString();
        assertThat(vfString, startsWith("file:/"));
        assertThat(vfString, endsWith(child));
    }

    @Test
    void testHashCode() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child = "child";
        File childFile = new File(parentFile, child);
        VirtualFile vf = new VirtualFileMinimalImplementation(childFile);
        assertThat(vf.hashCode(), is(childFile.toURI().hashCode()));
    }

    @Test
    void testEquals_Null() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        VirtualFile vf2 = null;
        assertNotEquals(vf2, vf1);
    }

    @Test
    void testEquals_Different() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        String child2 = "child2";
        File childFile2 = new File(parentFile, child2);
        VirtualFile vf2 = new VirtualFileMinimalImplementation(childFile2);
        assertNotEquals(vf1, vf2);
    }

    @Test
    void testEquals_Same() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        String child2 = child1;
        File childFile2 = new File(parentFile, child2);
        VirtualFile vf2 = new VirtualFileMinimalImplementation(childFile2);
        assertEquals(vf1, vf2);
    }

    @Test
    void testEquals_OtherType() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        assertNotEquals(child1, vf1);
    }

    @Test
    void testCompareTo_Same() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        String child2 = child1;
        File childFile2 = new File(parentFile, child2);
        VirtualFile vf2 = new VirtualFileMinimalImplementation(childFile2);
        assertThat(vf1.compareTo(vf2), is(0));
    }

    @Test
    void testCompareTo_LessThan() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        String child2 = "child2";
        File childFile2 = new File(parentFile, child2);
        VirtualFile vf2 = new VirtualFileMinimalImplementation(childFile2);
        assertThat(vf1.compareTo(vf2), lessThan(0));
    }

    @Test
    void testCompareTo_GreaterThan() throws IOException {
        String parentFolder = "parentFolder";
        File parentFile = newFolder(tmp, parentFolder);
        String child1 = "child1";
        File childFile1 = new File(parentFile, child1);
        VirtualFile vf1 = new VirtualFileMinimalImplementation(childFile1);
        String child2 = "child2";
        File childFile2 = new File(parentFile, child2);
        VirtualFile vf2 = new VirtualFileMinimalImplementation(childFile2);
        assertThat(vf2.compareTo(vf1), greaterThan(0));
    }

    @Test
    void hasSymlink_AbstractBase() throws IOException {
        // This test checks the method's behavior in the abstract base class,
        // which generally does nothing.
        VirtualFile virtualRoot = new VirtualFileMinimalImplementation(tmp);
        assertFalse(virtualRoot.hasSymlink(LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void hasSymlink_False_FilePathVF() throws IOException {
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(tmp));
        assertFalse(virtualRoot.hasSymlink(LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void hasSymlink_True_FilePathVF() throws IOException, InterruptedException {
        assumeFalse(Functions.isWindows());
        FilePath rootPath = new FilePath(tmp);
        FilePath childPath = rootPath.child("child");
        childPath.touch(0);
        FilePath symlinkPath = rootPath.child("symlink");
        symlinkPath.symlinkTo(childPath.getName(), null);
        VirtualFile virtualFile = VirtualFile.forFilePath(symlinkPath);
        assertTrue(virtualFile.hasSymlink(LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void hasSymlink_False_FileVF() throws IOException {
        VirtualFile virtualRoot = VirtualFile.forFile(tmp);
        assertFalse(virtualRoot.hasSymlink(LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void hasSymlink_True_FileVF() throws IOException, InterruptedException {
        assumeFalse(Functions.isWindows());
        FilePath rootPath = new FilePath(tmp);
        FilePath childPath = rootPath.child("child");
        childPath.touch(0);
        FilePath symlinkPath = rootPath.child("symlink");
        symlinkPath.symlinkTo(childPath.getName(), null);
        VirtualFile virtualFile = VirtualFile.forFile(new File(symlinkPath.toURI()));
        assertTrue(virtualFile.hasSymlink(LinkOption.NOFOLLOW_LINKS));
    }

    private File createInvalidDirectorySymlink(String invalidSymlinkName) throws IOException, InterruptedException {
        File ws = newFolder(tmp, "ws");
        String externalFolderName = "external";
        newFolder(tmp, externalFolderName);
        Util.createSymlink(ws, "../" + externalFolderName, invalidSymlinkName, TaskListener.NULL);
        return ws;
    }

    private File createInvalidFileSymlink() throws IOException, InterruptedException {
        File ws = newFolder(tmp, "ws");
        String externalFolderName = "external";
        File externalFile = newFolder(tmp, externalFolderName);
        String childString = "child";
        Files.writeString(externalFile.toPath().resolve(childString), childString, StandardCharsets.US_ASCII);
        Util.createSymlink(ws, "../" + externalFolderName, "invalidSymlink", TaskListener.NULL);
        return ws;
    }

    private long computeEarlierSystemTime() {
        long earlierSystemTime = 0L;
        if (Functions.isWindows()) {
            return 0L;
        }
        Date date = new GregorianCalendar(2018, Calendar.JANUARY, 1).getTime();
        return date.getTime();
    }

    private static class VirtualFileMinimalImplementation extends VirtualFile {

        private File file;
        private File root;

        VirtualFileMinimalImplementation() {
        }

        VirtualFileMinimalImplementation(File file) {
            this(file, file);
        }

        VirtualFileMinimalImplementation(File file, File root) {
            this.file = file;
            this.root = root;
        }

        @NonNull
        @Override
        public String getName() {
            return file.getName();
        }

        @NonNull
        @Override
        public URI toURI() {
            return file.toURI();
        }

        @Override
        public VirtualFile getParent() {
            return null;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public boolean exists() {
            return false;
        }

        @NonNull
        @Override
        public VirtualFile[] list() {
            File[] kids = file.listFiles();
            if (kids == null) {
                return new VirtualFile[0];
            }
            VirtualFile[] vfs = new VirtualFile[kids.length];
            for (int i = 0; i < kids.length; i++) {
                vfs[i] = child(kids[i], root);
            }
            return vfs;
        }

        protected VirtualFile child(File kid, File root) {
            return new VirtualFileMinimalImplementation(kid, root);
        }

        @NonNull
        @Override
        public VirtualFile child(@NonNull String name) {
            return child(new File(file, name), root);
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public long lastModified() {
            return 0;
        }

        @Override
        public boolean canRead() {
            return false;
        }

        @Override
        public InputStream open() throws IOException {
            return Files.newInputStream(file.toPath());
        }

    }

    private static class VirtualFileMinimalImplementationWithDescendants extends VirtualFileMinimalImplementation {

        VirtualFileMinimalImplementationWithDescendants(File file) {
            super(file);
        }

        VirtualFileMinimalImplementationWithDescendants(File file, File root) {
            super(file, root);
        }

        @Override
        public boolean supportIsDescendant() {
            return true;
        }

        @Override
        public boolean isDescendant(String childRelativePath) {
            return true;
        }

        @Override
        protected VirtualFile child(File kid, File root) {
            return new VirtualFileMinimalImplementationWithDescendants(kid, root);
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
