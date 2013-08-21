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
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Abstraction over {@link File}, {@link FilePath}, or other items such as network resources or ZIP entries.
 * Assumed to be read-only and makes very limited assumptions, just enough to display content and traverse directories.
 * @since TODO
 */
public abstract class VirtualFile implements Comparable<VirtualFile> {
    
    /**
     * Gets the base getName.
     * For a “root directory” this may be the empty string.
     * @return a simple getName (no slashes)
     */
    public abstract @Nonnull String getName();

    public abstract URI toURI();

    public abstract VirtualFile getParent();

    public abstract boolean isDirectory() throws IOException;

    public abstract boolean isFile() throws IOException;

    public abstract boolean exists() throws IOException;

    /**
     * Lists children of this directory.
     * @return a list of children (files and subdirectories)
     * @throws IOException if this is not a directory, or listing was not possible for some other reason
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

    public abstract long length() throws IOException;

    public abstract long lastModified() throws IOException;

    public abstract boolean canRead() throws IOException;

    public abstract InputStream open() throws IOException;

    /**
     * Does case-insensitive comparison.
     * @inheritDoc
     */
    @Override public final int compareTo(VirtualFile o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    @Override public final boolean equals(Object obj) {
        return obj instanceof VirtualFile && toURI().equals(((VirtualFile) obj).toURI());
    }

    @Override public final int hashCode() {
        return toURI().hashCode();
    }

    @Override public final String toString() {
        return toURI().toString();
    }

    public static VirtualFile forFile(final File f) {
        return new VirtualFile() {
            @Override public String getName() {
                return f.getName();
            }
            @Override public URI toURI() {
                return f.toURI();
            }
            @Override public VirtualFile getParent() {
                return forFile(f.getParentFile());
            }
            @Override public boolean isDirectory() throws IOException {
                return f.isDirectory();
            }
            @Override public boolean isFile() throws IOException {
                return f.isFile();
            }
            @Override public boolean exists() throws IOException {
                return f.exists();
            }
            @Override public VirtualFile[] list() throws IOException {
                File[] kids = f.listFiles();
                if (kids == null) {
                    throw new IOException();
                }
                VirtualFile[] vfs = new VirtualFile[kids.length];
                for (int i = 0; i < kids.length; i++) {
                    vfs[i] = forFile(kids[i]);
                }
                return vfs;
            }
            @Override public String[] list(String glob) throws IOException {
                return new Scanner(glob).invoke(f, null);
            }
            @Override public VirtualFile child(String name) {
                return forFile(new File(f, name));
            }
            @Override public long length() throws IOException {
                return f.length();
            }
            @Override public long lastModified() throws IOException {
                return f.lastModified();
            }
            @Override public boolean canRead() throws IOException {
                return f.canRead();
            }
            @Override public InputStream open() throws IOException {
                return new FileInputStream(f);
            }
        };
    }

    public static VirtualFile forFilePath(final FilePath f) {
        return new VirtualFile() {
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
                return forFilePath(f.getParent());
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
                return f.read();
            }
        };
    }
    private static final class Scanner implements FilePath.FileCallable<String[]> {
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
    private static final class Readable implements FilePath.FileCallable<Boolean> {
        @Override public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return f.canRead();
        }
    }

}
