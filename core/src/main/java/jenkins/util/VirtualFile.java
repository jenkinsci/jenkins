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
import hudson.Util;
import hudson.model.DirectoryBrowserSupport;
import hudson.os.PosixException;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.IOUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import jenkins.MasterToSlaveFileCallable;
import jenkins.model.ArtifactManager;
import jenkins.security.MasterToSlaveCallable;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.AbstractFileSet;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.apache.tools.ant.types.selectors.TokenizedPath;
import org.apache.tools.ant.types.selectors.TokenizedPattern;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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
 * <h2>Opening files from other machines</h2>
 *
 * While {@link VirtualFile} is marked {@link Serializable},
 * it is <em>not</em> safe in general to transfer over a Remoting channel.
 * (For example, an implementation from {@link #forFilePath} could be sent on the <em>same</em> channel,
 * but an implementation from {@link #forFile} will not.)
 * Thus callers should assume that methods such as {@link #open} will work
 * only on the node on which the object was created.
 *
 * <p>Since some implementations may in fact use external file storage,
 * callers may request optional APIs to access those services more efficiently.
 * Otherwise, for example, a plugin copying a file
 * previously saved by {@link ArtifactManager} to an external storage service
 * which tunneled a stream from {@link #open} using {@link RemoteInputStream}
 * would wind up transferring the file from the service to the Jenkins master and then on to an agent.
 * Similarly, if {@link DirectoryBrowserSupport} rendered a link to an in-Jenkins URL,
 * a large file could be transferred from the service to the Jenkins master and then on to the browser.
 * To avoid this overhead, callers may check whether an implementation supports {@link #toExternalURL}.
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
    public abstract @NonNull String getName();

    /**
     * Gets a URI.
     * Should at least uniquely identify this virtual file within its root, but not necessarily globally.
     * <p>When {@link #toExternalURL} is implemented, that same value could be used here,
     * unless some sort of authentication is also embedded.
     * @return a URI (need not be absolute)
     */
    public abstract @NonNull URI toURI();

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
     * If this file is a symlink, returns the link target.
     * <p>The default implementation always returns null.
     * Some implementations may not support symlinks under any conditions.
     * @return a target (typically a relative path in some format), or null if this is not a link
     * @throws IOException if reading the link, or even determining whether this file is a link, failed
     * @since 2.118
     */
    public @CheckForNull String readLink() throws IOException {
        return null;
    }

    /**
     * Checks whether this file exists.
     * The behavior is undefined for symlinks; if in doubt, check {@link #readLink} first.
     * @return true if it is a plain file or directory, false if nonexistent
     * @throws IOException in case checking status failed
     */
    public abstract boolean exists() throws IOException;

    /**
     * Lists children of this directory. Only one level deep.
     * 
     * @return a list of children (files and subdirectories); empty for a file or nonexistent directory
     * @throws IOException if this directory exists but listing was not possible for some other reason
     */
    public abstract @NonNull VirtualFile[] list() throws IOException;

    @Restricted(NoExternalUse.class)
    public boolean supportsQuickRecursiveListing() {
        return false;
    }
    
    /**
     * Lists only the children that are descendant of the root directory (not necessarily the current VirtualFile). 
     * Only one level deep.
     * 
     * @return a list of descendant children (files and subdirectories); empty for a file or nonexistent directory
     * @throws IOException if this directory exists but listing was not possible for some other reason
     */
    @Restricted(NoExternalUse.class)
    public @NonNull List<VirtualFile> listOnlyDescendants() throws IOException {
        VirtualFile[] children = list();
        List<VirtualFile> result = new ArrayList<>();
        for (VirtualFile child : children) {
            if (child.isDescendant("")) {
                result.add(child);
            }
        }        
        return result;
    }

    /**
     * @deprecated use {@link #list(String, String, boolean)} instead
     */
    @Deprecated
    public @NonNull String[] list(String glob) throws IOException {
        return list(glob.replace('\\', '/'), null, true).toArray(MemoryReductionUtil.EMPTY_STRING_ARRAY);
    }

    /**
     * Lists recursive files of this directory with pattern matching.
     * <p>The default implementation calls {@link #list()} recursively inside {@link #run} and applies filtering to the result.
     * Implementations may wish to override this more efficiently.
     * @param includes comma-separated Ant-style globs as per {@link Util#createFileSet(File, String, String)} using {@code /} as a path separator;
     *                 the empty string means <em>no matches</em> (use {@link SelectorUtils#DEEP_TREE_MATCH} if you want to match everything except some excludes)
     * @param excludes optional excludes in similar format to {@code includes}
     * @param useDefaultExcludes as per {@link AbstractFileSet#setDefaultexcludes}
     * @return a list of {@code /}-separated relative names of children (files directly inside or in subdirectories)
     * @throws IOException if this is not a directory, or listing was not possible for some other reason
     * @since 2.118
     */
    public @NonNull Collection<String> list(@NonNull String includes, @CheckForNull String excludes, boolean useDefaultExcludes) throws IOException {
        Collection<String> r = run(new CollectFiles(this));
        List<TokenizedPattern> includePatterns = patterns(includes);
        List<TokenizedPattern> excludePatterns = patterns(excludes);
        if (useDefaultExcludes) {
            for (String patt : DirectoryScanner.getDefaultExcludes()) {
                excludePatterns.add(new TokenizedPattern(patt.replace('/', File.separatorChar)));
            }
        }
        return r.stream().filter(p -> {
            TokenizedPath path = new TokenizedPath(p.replace('/', File.separatorChar));
            return includePatterns.stream().anyMatch(patt -> patt.matchPath(path, true)) && excludePatterns.stream().noneMatch(patt -> patt.matchPath(path, true));
        }).collect(Collectors.toSet());
    }
    private static final class CollectFiles extends MasterToSlaveCallable<Collection<String>, IOException> {
        private static final long serialVersionUID = 1;
        private final VirtualFile root;
        CollectFiles(VirtualFile root) {
            this.root = root;
        }
        @Override
        public Collection<String> call() throws IOException {
            List<String> r = new ArrayList<>();
            collectFiles(root, r, "");
            return r;
        }
        private static void collectFiles(VirtualFile d, Collection<String> names, String prefix) throws IOException {
            for (VirtualFile child : d.list()) {
                if (child.isFile()) {
                    names.add(prefix + child.getName());
                } else if (child.isDirectory()) {
                    collectFiles(child, names, prefix + child.getName() + "/");
                }
            }
        }
    }
    private List<TokenizedPattern> patterns(String patts) {
        List<TokenizedPattern> r = new ArrayList<>();
        if (patts != null) {
            for (String patt : patts.split(",")) {
                if (patt.endsWith("/")) {
                    patt += SelectorUtils.DEEP_TREE_MATCH;
                }
                r.add(new TokenizedPattern(patt.replace('/', File.separatorChar)));
            }
        }
        return r;
    }

    /**
     * Obtains a child file.
     * @param name a relative path, possibly including {@code /} (but not {@code ..})
     * @return a representation of that child, whether it actually exists or not
     */
    public abstract @NonNull VirtualFile child(@NonNull String name);

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
     * Gets the file’s Unix mode, if meaningful.
     * If the file is symlink (see {@link #readLink}), the mode is that of the link target, not the link itself.
     * @return for example, 0644 ~ {@code rw-r--r--}; -1 by default, meaning unknown or inapplicable
     * @throws IOException if checking the mode failed
     * @since 2.118
     */
    public int mode() throws IOException {
        return -1;
    }

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
     * {@inheritDoc}
     */
    @Override public final int compareTo(VirtualFile o) {
        return getName().compareToIgnoreCase(o.getName());
    }

    /**
     * Compares according to {@link #toURI}.
     * {@inheritDoc}
     */
    @Override public final boolean equals(Object obj) {
        return obj instanceof VirtualFile && toURI().equals(((VirtualFile) obj).toURI());
    }

    /**
     * Hashes according to {@link #toURI}.
     * {@inheritDoc}
     */
    @Override public final int hashCode() {
        return toURI().hashCode();
    }

    /**
     * Displays {@link #toURI}.
     * {@inheritDoc}
     */
    @Override public final String toString() {
        return toURI().toString();
    }

    /**
     * Does some calculations in batch.
     * For a remote file, this can be much faster than doing the corresponding operations one by one as separate requests.
     * The default implementation just calls the block directly.
     * @param <V> a value type
     * @param callable something to run all at once (only helpful if any mentioned files are on the same system)
     * @return the callable result
     * @throws IOException if remote communication failed
     * @since 1.554
     */
    public <V> V run(Callable<V,IOException> callable) throws IOException {
        return callable.call();
    }

    /**
     * Optionally obtains a URL which may be used to retrieve file contents from any process on any node.
     * For example, given cloud storage this might produce a permalink to the file.
     * <p>Only {@code http} and {@code https} protocols are permitted.
     * It is recommended to use <a href="http://javadoc.jenkins.io/plugin/apache-httpcomponents-client-4-api/io/jenkins/plugins/httpclient/RobustHTTPClient.html#downloadFile-java.io.File-java.net.URL-hudson.model.TaskListener-">{@code RobustHTTPClient.downloadFile}</a> to work with these URLs.
     * <p>This is only meaningful for {@link #isFile}:
     * no ZIP etc. archiving protocol is defined to allow bulk access to directory trees.
     * <p>Any necessary authentication must be encoded somehow into the URL itself;
     * do not include any tokens or other authentication which might allow access to unrelated files
     * (for example {@link ArtifactManager} builds from a different job).
     * Authentication should be limited to download, not upload or any other modifications.
     * <p>The URL might be valid for only a limited amount of time or even only a single use;
     * this method should be called anew every time an external URL is required.
     * @return an externally usable URL like {@code https://gist.githubusercontent.com/ACCT/GISTID/raw/COMMITHASH/FILE}, or null if there is no such support
     * @since 2.118
     * @see #toURI
     */
    public @CheckForNull URL toExternalURL() throws IOException {
        return null;
    }

    /**
     * Determine if the implementation supports the {@link #isDescendant(String)} method
     *
     * TODO un-restrict it in a weekly after the patch
     */
    @Restricted(NoExternalUse.class)
    public boolean supportIsDescendant() {
        return false;
    }

    /**
     * Check if the relative path is really a descendant of this folder, following the symbolic links.
     * Meant to be used in coordination with {@link #child(String)}.
     *
     * TODO un-restrict it in a weekly after the patch
     */
    @Restricted(NoExternalUse.class)
    public boolean isDescendant(String childRelativePath) throws IOException {
        return false;
    }
    
    String joinWithForwardSlashes(Collection<String> relativePath){
        // instead of File.separator that is specific to the master, the / has the advantage to be supported
        // by either Windows AND Linux for the Path.toRealPath() used in isDescendant
        return String.join("/", relativePath) + "/";
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
        private boolean cacheDescendant = false;
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
            @Override public String readLink() throws IOException {
                if (isIllegalSymlink()) {
                    return null; // best to just ignore link -> ../whatever
                }
                return Util.resolveSymlink(f);
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

            @Override public boolean supportsQuickRecursiveListing() {
                return true;
            }

            @Override public @NonNull List<VirtualFile> listOnlyDescendants() throws IOException {
                if (isIllegalSymlink()) {
                    return Collections.emptyList();
                }
                File[] children = f.listFiles();
                if (children == null) {
                    return Collections.emptyList();
                }
                List<VirtualFile> legalChildren = new ArrayList<>(children.length);
                for (File child : children) {
                    if (isDescendant(child.getName())) {
                        FileVF legalChild = new FileVF(child, root);
                        legalChild.cacheDescendant = true;
                        legalChildren.add(legalChild);
                    }
                }
                return legalChildren;
            }
            @Override
            public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) throws IOException {
                if (isIllegalSymlink()) {
                    return Collections.emptySet();
                }
                return new Scanner(includes, excludes, useDefaultExcludes).invoke(f, null);
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
            @Override public int mode() throws IOException {
                if (isIllegalSymlink()) {
                    return -1;
                }
                return IOUtils.mode(f);
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
                try {
                    return Files.newInputStream(f.toPath());
                } catch (InvalidPathException e) {
                    throw new IOException(e);
                }
            }

        private boolean isIllegalSymlink() {
            try {
                String myPath = f.toPath().toRealPath().toString();
                String rootPath = root.toPath().toRealPath().toString();
                if (!myPath.equals(rootPath) && !myPath.startsWith(rootPath + File.separatorChar)) {
                    return true;
                }
            } catch (IOException x) {
                Logger.getLogger(VirtualFile.class.getName()).log(Level.FINE, "could not determine symlink status of " + f, x);
            } catch (InvalidPathException x2) {
                // if this cannot be converted to a path, it cannot be an illegal symlink, as it cannot exist
                // it's the case when we are calling it with *zip*
                Logger.getLogger(VirtualFile.class.getName()).log(Level.FINE, "Could not convert " + f + " to path", x2);
            }
            return false;
        }

        /**
         * TODO un-restrict it in a weekly after the patch
         */
        @Override
        @Restricted(NoExternalUse.class)
        public boolean supportIsDescendant() {
            return true;
        }

        /**
         * TODO un-restrict it in a weekly after the patch
         */
        @Override
        @Restricted(NoExternalUse.class)
        public boolean isDescendant(String potentialChildRelativePath) throws IOException {
            if (potentialChildRelativePath.isEmpty() && cacheDescendant) {
                return true;
            }

            if (new File(potentialChildRelativePath).isAbsolute()) {
                throw new IllegalArgumentException("Only a relative path is supported, the given path is absolute: " + potentialChildRelativePath);
            }

            // shortcut for direct child to avoid the complexity of the whole computation
            // as we know that a file that is a direct descendant of its parent can only be descendant of the root
            // if the parent is descendant AND the file itself is not symbolic
            File directChild = new File(f, potentialChildRelativePath);
            if (directChild.getParentFile().equals(f)) {
                // potential shortcut for "simple" / direct child
                if (!Util.isSymlink(directChild)) {
                    return true;
                }
            }

            FilePath root = new FilePath(this.root);
            String relativePath = computeRelativePathToRoot();
            
            try {
                boolean isDescendant = root.isDescendant(relativePath + potentialChildRelativePath);
                if (isDescendant && potentialChildRelativePath.isEmpty()) {
                    // in DirectoryBrowserSupport#zip, multiple calls to isDescendant are done for the same VirtualFile
                    cacheDescendant = true;
                }
                return isDescendant;
            }
            catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * To be kept in sync with {@link FilePathVF#computeRelativePathToRoot()}
         */
        private String computeRelativePathToRoot(){
            if (this.root.equals(this.f)) {
                return "";
            }
            
            Deque<String> relativePath = new LinkedList<>();
            File current = this.f;
            while (current != null && !current.equals(this.root)) {
                relativePath.addFirst(current.getName());
                current = current.getParentFile();
            }

            return joinWithForwardSlashes(relativePath);
        }
    }

    /**
     * Creates a virtual file wrapper for a remotable file.
     * @param f a local or remote file (need not exist)
     * @return a wrapper
     */
    public static VirtualFile forFilePath(final FilePath f) {
        return new FilePathVF(f, f);
    }
    private static final class FilePathVF extends VirtualFile {
        private final FilePath f;
        private final FilePath root;
        private boolean cacheDescendant = false;
        FilePathVF(FilePath f, FilePath root) {
            this.f = f;
            this.root = root;
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
                    throw new IOException(x);
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
                    throw new IOException(x);
                }
            }
            @Override public String readLink() throws IOException {
                try {
                    return f.readLink();
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }
            @Override public VirtualFile[] list() throws IOException {
                try {
                    List<FilePath> kids = f.list();
                    VirtualFile[] vfs = new VirtualFile[kids.size()];
                    for (int i = 0; i < vfs.length; i++) {
                        vfs[i] = new FilePathVF(kids.get(i), this.root);
                    }
                    return vfs;
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }

            @Override public boolean supportsQuickRecursiveListing() {
                return this.f.getChannel() == FilePath.localChannel;
            }

            @Override public @NonNull List<VirtualFile> listOnlyDescendants() throws IOException {
                try {
                    if (!isDescendant("")) {
                        return Collections.emptyList();
                    }

                    List<FilePath> children = f.list();
                    List<VirtualFile> legalChildren = new ArrayList<>(children.size());
                    for (FilePath child : children){
                        if (isDescendant(child.getName())) {
                            FilePathVF legalChild = new FilePathVF(child, this.root);
                            legalChild.cacheDescendant = true;
                            legalChildren.add(legalChild);
                        }
                    }

                    return legalChildren;
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }

        @Override public Collection<String> list(String includes, String excludes, boolean useDefaultExcludes) throws IOException {
            try {
                return f.act(new Scanner(includes, excludes, useDefaultExcludes));
            } catch (InterruptedException x) {
                throw new IOException(x);
            }
        }
            @Override public VirtualFile child(String name) {
                return new FilePathVF(f.child(name), this.root);
            }
            @Override public long length() throws IOException {
                try {
                    return f.length();
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }
            @Override public int mode() throws IOException {
                try {
                    return f.mode();
                } catch (InterruptedException | PosixException x) {
                    throw new IOException(x);
                }
            }
            @Override public long lastModified() throws IOException {
                try {
                    return f.lastModified();
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }
            @Override public boolean canRead() throws IOException {
                try {
                    return f.act(new Readable());
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }
            @Override public InputStream open() throws IOException {
                try {
                    return f.read();
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }
            @Override public <V> V run(Callable<V,IOException> callable) throws IOException {
                try {
                    return f.act(callable);
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
            }

        /**
         * TODO un-restrict it in a weekly after the patch
         */
        @Override
        @Restricted(NoExternalUse.class)
        public boolean supportIsDescendant() {
            return true;
        }

        /**
         * TODO un-restrict it in a weekly after the patch
         */
        @Override
        @Restricted(NoExternalUse.class)
        public boolean isDescendant(String potentialChildRelativePath) throws IOException {
            if (potentialChildRelativePath.equals("") && cacheDescendant) {
                return true;
            }

            if (new File(potentialChildRelativePath).isAbsolute()) {
                throw new IllegalArgumentException("Only a relative path is supported, the given path is absolute: " + potentialChildRelativePath);
            }

            // shortcut for direct child to avoid the complexity of the whole computation
            // as we know that a file that is a direct descendant of its parent can only be descendant of the root
            // if the parent is descendant
            FilePath directChild = this.f.child(potentialChildRelativePath);
            if (Objects.equals(directChild.getParent(), this.f)) {
                try {
                    boolean isDirectDescendant = this.f.isDescendant(potentialChildRelativePath);
                    if (isDirectDescendant) {
                        return true;
                    }
                    // not a return false because you can be a non-descendant of your parent but still
                    // inside the root directory
                }
                catch (InterruptedException e) {
                    return false;
                }
            }
            
            String relativePath = computeRelativePathToRoot();
            
            try {
                return this.root.isDescendant(relativePath + potentialChildRelativePath);
            }
            catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * To be kept in sync with {@link FileVF#computeRelativePathToRoot()}
         */
        private String computeRelativePathToRoot(){
            if (this.root.equals(this.f)) {
                return "";
            }

            LinkedList<String> relativePath = new LinkedList<>();
            FilePath current = this.f;
            while (current != null && !current.equals(this.root)) {
                relativePath.addFirst(current.getName());
                current = current.getParent();
            }

            return joinWithForwardSlashes(relativePath);
        }
    }
    private static final class Scanner extends MasterToSlaveFileCallable<List<String>> {
        private final String includes, excludes;
        private final boolean useDefaultExcludes;
        Scanner(String includes, String excludes, boolean useDefaultExcludes) {
            this.includes = includes;
            this.excludes = excludes;
            this.useDefaultExcludes = useDefaultExcludes;
        }
        @Override public List<String> invoke(File f, VirtualChannel channel) throws IOException {
            if (includes.isEmpty()) { // see Glob class Javadoc, and list(String, String, boolean) note
                return Collections.emptyList();
            }
            final List<String> paths = new ArrayList<>();
            new DirScanner.Glob(includes, excludes, useDefaultExcludes).scan(f, new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    paths.add(relativePath.replace('\\', '/'));
                }
            });
            return paths;
        }

    }
    private static final class Readable extends MasterToSlaveFileCallable<Boolean> {
        @Override public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return f.canRead();
        }
    }

}
