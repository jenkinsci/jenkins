/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.FilePath;
import hudson.model.DirectoryBrowserSupport;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

import jenkins.MasterToSlaveFileCallable;

/**
 * Abstraction over {@link File}, {@link FilePath}, or other items such as network resources or ZIP entries.
 * Assumed to be read-only and makes very limited assumptions, just enough to display content and traverse directories.
 *
 * <p>
 * To obtain a {@link VirtualFile} representation for an existing file, use {@link #forFile(File)} or {@link FilePath#toVirtualFile()}
 *
 * <h2>How are VirtualFile and FilePath different?</h2>
 * <p>
 * FilePath abstracts away {@link File}s on machines that are connected over {@link Channel}, whereas
 * {@link VirtualFile} makes no assumption about where the actual files are, or whether there really exists
 * {@link File}s somewhere. This makes VirtualFile more abstract.
 *
 * @see DirectoryBrowserSupport
 * @see FilePath
 * @since 1.532
 */
public abstract class VirtualFile implements Comparable<VirtualFile>, Serializable {
    
    /**
     * Gets the base name, meaning just the last portion of the path name without any
     * directories.
     *
     * For a “root directory” this may be the empty string.
     * @return a simple name (no slashes)
     */
    public abstract @Nonnull String getName();

    /**
     * Gets a URI.
     * Should at least uniquely identify this virtual file within its root, but not necessarily globally.
     * @return a URI (need not be absolute)
     */
    public abstract URI toURI();

    /**
     * Gets the parent file.
     * Need only operate within the originally given root.
     * @return the parent
     */
    public abstract VirtualFile getParent();

    /**
     * Checks whether this file exists and is a directory.
     * @return true if it is a directory, false if a file or nonexistent
     * @throws IOException in case checking status failed
     */
    public abstract boolean isDirectory() throws IOException;

    /**
     * Checks whether this file exists and is a plain file.
     * @return true if it is a file, false if a directory or nonexistent
     * @throws IOException in case checking status failed
     */
    public abstract boolean isFile() throws IOException;

    /**
     * Checks whether this file exists.
     * @return true if it is a plain file or directory, false if nonexistent
     * @throws IOException in case checking status failed
     */
    public abstract boolean exists() throws IOException;

    /**
     * Lists children of this directory.
     * @return a list of children (files and subdirectories); empty for a file or nonexistent directory
     * @throws IOException if this directory exists but listing was not possible for some other reason
     */
    public abstract @Nonnull VirtualFile[] list() throws IOException;

    /**
     * Lists recursive files of this directory with pattern matching.
     * @param glob an Ant-style glob
     * @return a list of relative names of children (files directly inside or in subdirectories)
     * @throws IOException if this is not a directory, or listing was not possible for some other reason
     */
    public abstract @Nonnull String[] list(String glob) throws IOException;

    /**
     * Obtains a child file.
     * @param name a relative path, possibly including {@code /} (but not {@code ..})
     * @return a representation of that child, whether it actually exists or not
     */
    public abstract @Nonnull VirtualFile child(@Nonnull String name);

    /**
     * Gets the file length.
     * @return a length, or 0 if inapplicable (e.g. a directory)
     * @throws IOException if checking the length failed
     */
    public abstract long length() throws IOException;

    /**
     * Gets the file timestamp.
     * @return a length, or 0 if inapplicable
     * @throws IOException if checking the timestamp failed
     */
    public abstract long lastModified() throws IOException;

    /**
     * Checks whether this file can be read.
     * @return true normally
     * @throws IOException if checking status failed
     */
    public abstract boolean canRead() throws IOException;

    /**
     * Opens an input stream on the file so its contents can be read.
     * @return an open stream
     * @throws IOException if it could not be opened
     */
    public abstract InputStream open() throws IOException;

    /**
     * Does case-insensitive comparison.
     * @inheritDoc
     */
    @Override public final int compareTo(VirtualFile o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    /**
     * Compares according to {@link #toURI}.
     * @inheritDoc
     */
    @Override public final boolean equals(Object obj) {
        return obj instanceof VirtualFile && toURI().equals(((VirtualFile) obj).toURI());
    }

    /**
     * Hashes according to {@link #toURI}.
     * @inheritDoc
     */
    @Override public final int hashCode() {
        return toURI().hashCode();
    }

    /**
     * Displays {@link #toURI}.
     * @inheritDoc
     */
    @Override public final String toString() {
        return toURI().toString();
    }

    /**
     * Does some calculations in batch.
     * For a remote file, this can be much faster than doing the corresponding operations one by one as separate requests.
     * The default implementation just calls the block directly.
     * @param <V> a value type
     * @param <T> the exception type
     * @param callable something to run all at once (only helpful if any mentioned files are on the same system)
     * @return the callable result
     * @throws IOException if remote communication failed
     * @since 1.554
     */
    public <V> V run(Callable<V,IOException> callable) throws IOException {
        return callable.call();
    }

    /**
     * Creates a virtual file wrapper for a local file.
     * @param f a disk file (need not exist)
     * @return a wrapper
     */
    public static VirtualFile forFile(final File f) {
        return new FileVF(f, f);
    }
    private static final class FileVF extends VirtualFile {
        private final File f;
        private final File root;
        FileVF(File f, File root) {
            this.f = f;
            this.root = root;
        }
            @Override public String getName() {
                return f.getName();
            }
            @Override public URI toURI() {
                return f.toURI();
            }
            @Override public VirtualFile getParent() {
                return new FileVF(f.getParentFile(), root);
            }
            @Override public boolean isDirectory() throws IOException {
                if (isIllegalSymlink()) {
                    return false;
                }
                return f.isDirectory();
            }
            @Override public boolean isFile() throws IOException {
                if (isIllegalSymlink()) {
                    return false;
                }
                return f.isFile();
            }
            @Override public boolean exists() throws IOException {
                if (isIllegalSymlink()) {
                    return false;
                }
                return f.exists();
            }
            @Override public VirtualFile[] list() throws IOException {
                if (isIllegalSymlink()) {
                    return new VirtualFile[0];
                }
                File[] kids = f.listFiles();
                if (kids == null) {
                    return new VirtualFile[0];
                }
                VirtualFile[] vfs = new VirtualFile[kids.length];
                for (int i = 0; i < kids.length; i++) {
                    vfs[i] = new FileVF(kids[i], root);
                }
                return vfs;
            }
            @Override public String[] list(String glob) throws IOException {
                if (isIllegalSymlink()) {
                    return new String[0];
                }
                return new Scanner(glob).invoke(f, null);
            }
            @Override public VirtualFile child(String name) {
                return new FileVF(new File(f, name), root);
            }
            @Override public long length() throws IOException {
                if (isIllegalSymlink()) {
                    return 0;
                }
                return f.length();
            }
            @Override public long lastModified() throws IOException {
                if (isIllegalSymlink()) {
                    return 0;
                }
                return f.lastModified();
            }
            @Override public boolean canRead() throws IOException {
                if (isIllegalSymlink()) {
                    return false;
                }
                return f.canRead();
            }
            @Override public InputStream open() throws IOException {
                if (isIllegalSymlink()) {
                    throw new FileNotFoundException(f.getPath());
                }
                return new FileInputStream(f);
            }
        private boolean isIllegalSymlink() { // TODO JENKINS-26838
            try {
                String myPath = f.toPath().toRealPath(new LinkOption[0]).toString();
                String rootPath = root.toPath().toRealPath(new LinkOption[0]).toString();
                if (!myPath.equals(rootPath) && !myPath.startsWith(rootPath + File.separatorChar)) {
                    return true;
                }
            } catch (IOException x) {
                Logger.getLogger(VirtualFile.class.getName()).log(Level.FINE, "could not determine symlink status of " + f, x);
            }
            return false;
        }
    }

    /**
     * Creates a virtual file wrapper for a remotable file.
     * @param f a local or remote file (need not exist)
     * @return a wrapper
     */
    public static VirtualFile forFilePath(final FilePath f) {
        return new FilePathVF(f);
    }
    private static final class FilePathVF extends VirtualFile {
        private final FilePath f;
        FilePathVF(FilePath f) {
            this.f = f;
        }
            @Override public String getName() {
                return f.getName();
            }
            @Override public URI toURI() {
                try {
                    return f.toURI();
                } catch (Exception x) {
                    return URI.create(f.getRemote());
                }
            }
            @Override public VirtualFile getParent() {
                return f.getParent().toVirtualFile();
            }
            @Override public boolean isDirectory() throws IOException {
                try {
                    return f.isDirectory();
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public boolean isFile() throws IOException {
                // TODO should probably introduce a method for this purpose
                return exists() && !isDirectory();
            }
            @Override public boolean exists() throws IOException {
                try {
                    return f.exists();
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public VirtualFile[] list() throws IOException {
                try {
                    List<FilePath> kids = f.list();
                    if (kids == null) {
                        return new VirtualFile[0];
                    }
                    VirtualFile[] vfs = new VirtualFile[kids.size()];
                    for (int i = 0; i < vfs.length; i++) {
                        vfs[i] = forFilePath(kids.get(i));
                    }
                    return vfs;
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public String[] list(String glob) throws IOException {
                try {
                    return f.act(new Scanner(glob));
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public VirtualFile child(String name) {
                return forFilePath(f.child(name));
            }
            @Override public long length() throws IOException {
                try {
                    return f.length();
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public long lastModified() throws IOException {
                try {
                    return f.lastModified();
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public boolean canRead() throws IOException {
                try {
                    return f.act(new Readable());
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public InputStream open() throws IOException {
                try {
                    return f.read();
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
            @Override public <V> V run(Callable<V,IOException> callable) throws IOException {
                try {
                    return f.act(callable);
                } catch (InterruptedException x) {
                    throw (IOException) new IOException(x.toString()).initCause(x);
                }
            }
    }
    private static final class Scanner extends MasterToSlaveFileCallable<String[]> {
        private final String glob;
        Scanner(String glob) {
            this.glob = glob;
        }
        @Override public String[] invoke(File f, VirtualChannel channel) throws IOException {
            final List<String> paths = new ArrayList<String>();
            new DirScanner.Glob(glob, null).scan(f, new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    paths.add(relativePath);
                }
            });
            return paths.toArray(new String[paths.size()]);
        }

    }
    private static final class Readable extends MasterToSlaveFileCallable<Boolean> {
        @Override public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return f.canRead();
        }
    }

}
