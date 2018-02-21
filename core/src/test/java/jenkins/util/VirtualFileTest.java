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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

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

}
