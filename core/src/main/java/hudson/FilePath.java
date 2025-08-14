/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Eric Lefevre-Ardant, Erik Ramfelt, Michael B. Donohue, Alan Harder,
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
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

import static hudson.Util.fileToPath;
import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Launcher.LocalLauncher;
import hudson.Launcher.RemoteLauncher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.Future;
import hudson.remoting.LocalChannel;
import hudson.remoting.Pipe;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteInputStream.Flag;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Which;
import hudson.security.AccessControlled;
import hudson.slaves.WorkspaceList;
import hudson.tasks.ArtifactArchiver;
import hudson.util.DaemonThreadFactory;
import hudson.util.DirScanner;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.NamingThreadFactory;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import jenkins.MasterToSlaveFileCallable;
import jenkins.agents.ControllerToAgentFileCallable;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.ContextResettingExecutorService;
import jenkins.util.SystemProperties;
import jenkins.util.VirtualFile;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

/**
 * {@link File} like object with remoting support.
 *
 * <p>
 * Unlike {@link File}, which always implies a file path on the current computer,
 * {@link FilePath} represents a file path on a specific agent or the controller.
 *
 * Despite that, {@link FilePath} can be used much like {@link File}. It exposes
 * a bunch of operations (and we should add more operations as long as they are
 * generally useful), and when invoked against a file on a remote node, {@link FilePath}
 * executes the necessary code remotely, thereby providing semi-transparent file
 * operations.
 *
 * <h2>Using {@link FilePath} smartly</h2>
 * <p>
 * The transparency makes it easy to write plugins without worrying too much about
 * remoting, by making it works like NFS, where remoting happens at the file-system
 * layer.
 *
 * <p>
 * But one should note that such use of remoting may not be optional. Sometimes,
 * it makes more sense to move some computation closer to the data, as opposed to
 * move the data to the computation. For example, if you are just computing a MD5
 * digest of a file, then it would make sense to do the digest on the host where
 * the file is located, as opposed to send the whole data to the controller and do MD5
 * digesting there.
 *
 * <p>
 * {@link FilePath} supports this "code migration" by in the
 * {@link #act(FileCallable)} method. One can pass in a custom implementation
 * of {@link FileCallable}, to be executed on the node where the data is located.
 * The following code shows the example:
 *
 * <pre>
 * void someMethod(FilePath file) {
 *     // make 'file' a fresh empty directory.
 *     file.act(new Freshen());
 * }
 * // if 'file' is on a different node, this FileCallable will
 * // be transferred to that node and executed there.
 * private static final class Freshen implements FileCallable&lt;Void&gt; {
 *     private static final long serialVersionUID = 1;
 *     &#64;Override public Void invoke(File f, VirtualChannel channel) {
 *         // f and file represent the same thing
 *         f.deleteContents();
 *         f.mkdirs();
 *         return null;
 *     }
 * }
 * </pre>
 *
 * <p>
 * When {@link FileCallable} is transferred to a remote node, it will be done so
 * by using the same Java serialization scheme that the remoting module uses.
 * See {@link Channel} for more about this.
 *
 * <p>
 * {@link FilePath} itself can be sent over to a remote node as a part of {@link Callable}
 * serialization. For example, sending a {@link FilePath} of a remote node to that
 * node causes {@link FilePath} to become "local". Similarly, sending a
 * {@link FilePath} that represents the local computer causes it to become "remote."
 *
 * @author Kohsuke Kawaguchi
 * @see VirtualFile
 */
public final class FilePath implements SerializableOnlyOverRemoting {

    public enum DisplayOption implements OpenOption, CopyOption {
        IGNORE_TMP_DIRS
    }

    /**
     * Maximum http redirects we will follow. This defaults to the same number as Firefox/Chrome tolerates.
     */
    private static final int MAX_REDIRECTS = 20;

    /**
     * When this {@link FilePath} represents the remote path,
     * this field is always non-null on the controller (the field represents
     * the channel to the remote agent.) When transferred to an agent via remoting,
     * this field reverts to null, since it's transient.
     *
     * When this {@link FilePath} represents a path on the controller,
     * this field is null on the controller. When transferred to an agent via remoting,
     * this field becomes non-null, representing the {@link Channel}
     * back to the controller.
     *
     * This is used to determine whether we are running on the controller / the built-in node, or an agent.
     */
    private transient VirtualChannel channel;

    /**
     * Represent the path to the file in the controller or the agent
     * Since the platform of the agent might be different, can't use java.io.File
     *
     * The field could not be final since it's modified in {@link #readResolve()}
     */
    private /*final*/ String remote;

    /**
     * Creates a {@link FilePath} that represents a path on the given node.
     *
     * @param channel
     *      To create a path that represents a remote path, pass in a {@link Channel}
     *      that's connected to that machine. If {@code null}, that means the local file path.
     */
    public FilePath(@CheckForNull VirtualChannel channel, @NonNull String remote) {
        this.channel = channel instanceof LocalChannel ? null : channel;
        this.remote = normalize(remote);
    }

    /**
     * To create {@link FilePath} that represents a "local" path.
     *
     * <p>
     * A "local" path means a file path on the computer where the
     * constructor invocation happened.
     */
    public FilePath(@NonNull File localPath) {
        this.channel = null;
        this.remote = normalize(localPath.getPath());
    }

    /**
     * Construct a path starting with a base location.
     * @param base starting point for resolution, and defines channel
     * @param rel a path that when relative will be resolved against base
     */
    public FilePath(@NonNull FilePath base, @NonNull String rel) {
        this.channel = base.channel;
        this.remote = normalize(resolvePathIfRelative(base, rel));
    }

    private Object readResolve() {
        this.remote = normalize(this.remote);
        return this;
    }

    private String resolvePathIfRelative(@NonNull FilePath base, @NonNull String rel) {
        if (isAbsolute(rel)) return rel;
        if (base.isUnix()) {
            // shouldn't need this replace, but better safe than sorry
            return base.remote + '/' + rel.replace('\\', '/');
        } else {
            // need this replace, see Slave.getWorkspaceFor and AbstractItem.getFullName, nested jobs on Windows
            // agents will always have a rel containing at least one '/' character. JENKINS-13649
            return base.remote + '\\' + rel.replace('/', '\\');
        }
    }

    /**
     * Is the given path name an absolute path?
     */
    private static boolean isAbsolute(@NonNull String rel) {
        return rel.startsWith("/") || DRIVE_PATTERN.matcher(rel).matches() || UNC_PATTERN.matcher(rel).matches();
    }

    private static final Pattern DRIVE_PATTERN = Pattern.compile("[A-Za-z]:[\\\\/].*"),
            UNC_PATTERN = Pattern.compile("^\\\\\\\\.*"),
            ABSOLUTE_PREFIX_PATTERN = Pattern.compile("^(\\\\\\\\|(?:[A-Za-z]:)?[\\\\/])[\\\\/]*");

    /**
     * {@link File#getParent()} etc cannot handle ".." and "." in the path component very well,
     * so remove them.
     */
    @Restricted(NoExternalUse.class)
    public static String normalize(@NonNull String path) {
        StringBuilder buf = new StringBuilder();
        // Check for prefix designating absolute path
        Matcher m = ABSOLUTE_PREFIX_PATTERN.matcher(path);
        if (m.find()) {
            buf.append(m.group(1));
            path = path.substring(m.end());
        }
        boolean isAbsolute = !buf.isEmpty();
        // Split remaining path into tokens, trimming any duplicate or trailing separators
        List<String> tokens = new ArrayList<>();
        int s = 0, end = path.length();
        for (int i = 0; i < end; i++) {
            char c = path.charAt(i);
            if (c == '/' || c == '\\') {
                tokens.add(path.substring(s, i));
                s = i;
                // Skip any extra separator chars
                //noinspection StatementWithEmptyBody
                while (++i < end && ((c = path.charAt(i)) == '/' || c == '\\'))
                    ;
                // Add token for separator unless we reached the end
                if (i < end) tokens.add(path.substring(s, s + 1));
                s = i;
            }
        }
        if (s < end) tokens.add(path.substring(s));
        // Look through tokens for "." or ".."
        for (int i = 0; i < tokens.size();) {
            String token = tokens.get(i);
            if (token.equals(".")) {
                tokens.remove(i);
                if (!tokens.isEmpty())
                    tokens.remove(i > 0 ? i - 1 : i);
            } else if (token.equals("..")) {
                if (i == 0) {
                    // If absolute path, just remove: /../something
                    // If relative path, not collapsible so leave as-is
                    tokens.remove(0);
                    if (!tokens.isEmpty()) token += tokens.remove(0);
                    if (!isAbsolute) buf.append(token);
                } else {
                    // Normalize: remove something/.. plus separator before/after
                    i -= 2;
                    for (int j = 0; j < 3; j++) tokens.remove(i);
                    if (i > 0) tokens.remove(i - 1);
                    else if (!tokens.isEmpty()) tokens.remove(0);
                }
            } else
                i += 2;
        }
        // Recombine tokens
        for (String token : tokens) buf.append(token);
        if (buf.isEmpty()) buf.append('.');
        return buf.toString();
    }

    /**
     * Checks if the remote path is Unix.
     */
    boolean isUnix() {
        // if the path represents a local path, there's no need to guess.
        if (!isRemote())
            return File.pathSeparatorChar != ';';

        // note that we can't use the usual File.pathSeparator etc., as the OS of
        // the machine where this code runs and the OS that this FilePath refers to may be different.

        // Windows absolute path is 'X:\...', so this is usually a good indication of Windows path
        if (remote.length() > 3 && remote.charAt(1) == ':' && remote.charAt(2) == '\\')
            return false;
        // Windows can handle '/' as a path separator but Unix can't,
        // so err on Unix side
        return !remote.contains("\\");
    }

    /**
     * Gets the full path of the file on the remote machine.
     *
     */
    public String getRemote() {
        return remote;
    }

    /**
     * Creates a zip file from this directory or a file and sends that to the given output stream.
     *
     * @deprecated as of 1.315. Use {@link #zip(OutputStream)} that has more consistent name.
     */
    @Deprecated
    public void createZipArchive(OutputStream os) throws IOException, InterruptedException {
        zip(os);
    }

    /**
     * Creates a zip file from this directory or a file and sends that to the given output stream.
     */
    public void zip(OutputStream os) throws IOException, InterruptedException {
        zip(os, (FileFilter) null);
    }

    public void zip(FilePath dst) throws IOException, InterruptedException {
        try (OutputStream os = dst.write()) {
            zip(os);
        }
    }

    /**
     * Creates a zip file from this directory by using the specified filter,
     * and sends the result to the given output stream.
     *
     * @param filter
     *      Must be serializable since it may be executed remotely. Can be null to add all files.
     *
     * @since 1.315
     */
    public void zip(OutputStream os, FileFilter filter) throws IOException, InterruptedException {
        archive(ArchiverFactory.ZIP, os, filter);
    }

    /**
     * Creates a zip file from this directory by only including the files that match the given glob.
     *
     * @param glob
     *      Ant style glob, like "**&#x2F;*.xml". If empty or null, this method
     *      works like {@link #createZipArchive(OutputStream)}
     *
     * @since 1.129
     * @deprecated as of 1.315
     *      Use {@link #zip(OutputStream,String)} that has more consistent name.
     */
    @Deprecated
    public void createZipArchive(OutputStream os, final String glob) throws IOException, InterruptedException {
        archive(ArchiverFactory.ZIP, os, glob);
    }

    /**
     * Creates a zip file from this directory by only including the files that match the given glob.
     *
     * @param glob
     *      Ant style glob, like "**&#x2F;*.xml". If empty or null, this method
     *      works like {@link #createZipArchive(OutputStream)}, inserting a top-level directory into the ZIP.
     *
     * @since 1.315
     */
    public void zip(OutputStream os, final String glob) throws IOException, InterruptedException {
        archive(ArchiverFactory.ZIP, os, glob);
    }

    /**
     * Uses the given scanner on 'this' directory to list up files and then archive it to a zip stream.
     */
    public int zip(OutputStream out, DirScanner scanner) throws IOException, InterruptedException {
        return archive(ArchiverFactory.ZIP, out, scanner);
    }

    /**
     * Uses the given scanner on 'this' directory to list up files and then archive it to a zip stream.
     *
     * @param out The OutputStream to write the zip into.
     * @param scanner A DirScanner for scanning the directory and filtering its contents.
     * @param verificationRoot A root or base directory for checking for any symlinks in this files parentage.
     *             Any symlinks between a file and root should be ignored.
     *             Symlinks in the parentage outside root will not be checked.
     * @param prefix The portion of file path that will be added at the beginning of the relative path inside the archive.
     *               If non-empty, a trailing forward slash will be enforced.
     * @param openOptions the options to apply when opening.
     * @return The number of files/directories archived.
     *          This is only really useful to check for a situation where nothing
     */
    public int zip(OutputStream out, DirScanner scanner, String verificationRoot, String prefix, OpenOption... openOptions) throws IOException, InterruptedException {
        ArchiverFactory archiverFactory = prefix == null ? ArchiverFactory.ZIP : ArchiverFactory.createZipWithPrefix(prefix, openOptions);
        return archive(archiverFactory, out, scanner, verificationRoot, openOptions);
    }

    /**
     * Archives this directory into the specified archive format, to the given {@link OutputStream}, by using
     * {@link DirScanner} to choose what files to include.
     *
     * @return
     *      number of files/directories archived. This is only really useful to check for a situation where nothing
     *      is archived.
     */
    public int archive(final ArchiverFactory factory, OutputStream os, final DirScanner scanner) throws IOException, InterruptedException {
        return archive(factory, os, scanner, null);
    }

    /**
     * Archives this directory into the specified archive format, to the given {@link OutputStream}, by using
     * {@link DirScanner} to choose what files to include.
     *
     * @param factory The ArchiverFactory for creating the archive.
     * @param os The OutputStream to write the zip into.
     * @param verificationRoot A root or base directory for checking for any symlinks in this files parentage.
     *             Any symlinks between a file and root should be ignored.
     *             Symlinks in the parentage outside root will not be checked.
     * @param openOptions options to apply when opening.
     *
     * @return The number of files/directories archived.
     *          This is only really useful to check for a situation where nothing
     */
    public int archive(final ArchiverFactory factory, OutputStream os, final DirScanner scanner,
                       String verificationRoot, OpenOption... openOptions) throws IOException, InterruptedException {
        final OutputStream out = channel != null ? new RemoteOutputStream(os) : os;
        return act(new Archive(factory, out, scanner, verificationRoot, openOptions));
    }

    private record Archive(ArchiverFactory factory, OutputStream out, DirScanner scanner, String verificationRoot, OpenOption... openOptions) implements ControllerToAgentFileCallable<Integer> {
        @Override
            public Integer invoke(File f, VirtualChannel channel) throws IOException {
                try (Archiver a = factory.create(out)) {
                    scanner.scan(f, ignoringTmpDirs(ignoringSymlinks(a, verificationRoot, openOptions), verificationRoot, openOptions));
                    return a.countEntries();
                }
            }
    }

    public int archive(final ArchiverFactory factory, OutputStream os, final FileFilter filter) throws IOException, InterruptedException {
        return archive(factory, os, new DirScanner.Filter(filter));
    }

    public int archive(final ArchiverFactory factory, OutputStream os, final String glob) throws IOException, InterruptedException {
        return archive(factory, os, new DirScanner.Glob(glob, null));
    }

    /**
     * When this {@link FilePath} represents a zip file, extracts that zip file.
     *
     * @param target
     *      Target directory to expand files to. All the necessary directories will be created.
     * @since 1.248
     * @see #unzipFrom(InputStream)
     */
    public void unzip(final FilePath target) throws IOException, InterruptedException {
        // TODO: post release, re-unite two branches by introducing FileStreamCallable that resolves InputStream
        if (channel != target.channel) { // local -> remote or remote->local
            final RemoteInputStream in = new RemoteInputStream(read(), Flag.GREEDY);
            target.act(new UnzipRemote(in));
        } else { // local -> local or remote->remote
            target.act(new UnzipLocal(this));
        }
    }

    private static class UnzipRemote extends MasterToSlaveFileCallable<Void> {
        private final RemoteInputStream in;

        UnzipRemote(RemoteInputStream in) {
            this.in = in;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            unzip(dir, in);
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    private static class UnzipLocal extends MasterToSlaveFileCallable<Void> {

        private final FilePath filePath;

        private UnzipLocal(FilePath filePath) {
            this.filePath = filePath;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            if (this.filePath.isRemote()) {
                throw new IllegalStateException("Expected local path for file: " + filePath); // this.channel==target.channel above
            }
            unzip(dir, new File(this.filePath.getRemote())); // shortcut to local file
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * When this {@link FilePath} represents a tar file, extracts that tar file.
     *
     * @param target
     *      Target directory to expand files to. All the necessary directories will be created.
     * @param compression
     *      Compression mode of this tar file.
     * @since 1.292
     * @see #untarFrom(InputStream, TarCompression)
     */
    public void untar(final FilePath target, final TarCompression compression) throws IOException, InterruptedException {
        final FilePath source = FilePath.this;
        // TODO: post release, re-unite two branches by introducing FileStreamCallable that resolves InputStream
        if (source.channel != target.channel) { // local -> remote or remote->local
            final RemoteInputStream in = new RemoteInputStream(source.read(), Flag.GREEDY);
            target.act(new UntarRemote(source.getName(), compression, in));
        } else { // local -> local or remote->remote
            target.act(new UntarLocal(source, compression));
        }
    }

    private static class UntarRemote extends MasterToSlaveFileCallable<Void> {
        private final TarCompression compression;
        private final RemoteInputStream in;
        private final String name;

        UntarRemote(String name, TarCompression compression, RemoteInputStream in) {
            this.compression = compression;
            this.in = in;
            this.name = name;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            readFromTar(name, dir, compression.extract(in));
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    private static class UntarLocal extends MasterToSlaveFileCallable<Void> {
        private final TarCompression compression;
        private final FilePath filePath;

        UntarLocal(FilePath source, TarCompression compression) {
            this.filePath = source;
            this.compression = compression;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            readFromTar(this.filePath.getName(), dir, compression.extract(this.filePath.read()));
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Reads the given InputStream as a zip file and extracts it into this directory.
     *
     * @param _in
     *      The stream will be closed by this method after it's fully read.
     * @since 1.283
     * @see #unzip(FilePath)
     */
    public void unzipFrom(InputStream _in) throws IOException, InterruptedException {
        final InputStream in = new RemoteInputStream(_in, Flag.GREEDY);
        act(new UnzipFrom(in));
    }

    private static class UnzipFrom extends MasterToSlaveFileCallable<Void> {
        private final InputStream in;

        UnzipFrom(InputStream in) {
            this.in = in;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException {
            unzip(dir, in);
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    private static void unzip(File dir, InputStream in) throws IOException {
        File tmpFile = File.createTempFile("tmpzip", null); // uses java.io.tmpdir
        try {
            // TODO why does this not simply use ZipInputStream?
            IOUtils.copy(in, tmpFile);
            unzip(dir, tmpFile);
        }
        finally {
            Files.delete(Util.fileToPath(tmpFile));
        }
    }

    private static void unzip(File dir, File zipFile) throws IOException {
        dir = dir.getAbsoluteFile();    // without absolutization, getParentFile below seems to fail

        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<ZipEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                File f = new File(dir, e.getName());
                if (!f.getCanonicalFile().toPath().startsWith(dir.getCanonicalPath())) {
                    throw new IOException(
                        "Zip " + zipFile.getPath() + " contains illegal file name that breaks out of the target directory: " + e.getName());
                }
                if (e.isDirectory()) {
                    mkdirs(f);
                } else {
                    File p = f.getParentFile();
                    if (p != null) {
                        mkdirs(p);
                    }
                    try (InputStream input = zip.getInputStream(e)) {
                        IOUtils.copy(input, f);
                    }
                    try {
                        FilePath target = new FilePath(f);
                        int mode = e.getUnixMode();
                        if (mode != 0)    // Ant returns 0 if the archive doesn't record the access mode
                            target.chmod(mode);
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.WARNING, "unable to set permissions", ex);
                    }
                    Files.setLastModifiedTime(Util.fileToPath(f), e.getLastModifiedTime());
                }
            }
        }
    }

    /**
     * Absolutizes this {@link FilePath} and returns the new one.
     */
    public FilePath absolutize() throws IOException, InterruptedException {
        return new FilePath(channel, act(new Absolutize()));
    }

    private static class Absolutize extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String invoke(File f, VirtualChannel channel) throws IOException {
            return f.getAbsolutePath();
        }
    }

    public boolean hasSymlink(FilePath verificationRoot, OpenOption... openOptions) throws IOException, InterruptedException {
        return act(new HasSymlink(verificationRoot == null ? null : verificationRoot.remote, openOptions));
    }

    private static class HasSymlink extends MasterToSlaveFileCallable<Boolean> {
        private static final long serialVersionUID = 1L;
        private final String verificationRoot;
        private OpenOption[] openOptions;

        HasSymlink(String verificationRoot, OpenOption... openOptions) {
            this.verificationRoot = verificationRoot;
            this.openOptions = openOptions;
        }

        @Override
        public Boolean invoke(File f, VirtualChannel channel) throws IOException {
            return isSymlink(f, verificationRoot, openOptions);
        }
    }

    public boolean containsSymlink(FilePath verificationRoot, OpenOption... openOptions) throws IOException, InterruptedException {
        return !list(new SymlinkRetainingFileFilter(verificationRoot, openOptions)).isEmpty();
    }

    private static class SymlinkRetainingFileFilter implements FileFilter, Serializable {

        private final String verificationRoot;
        private OpenOption[] openOptions;

        SymlinkRetainingFileFilter(FilePath verificationRoot, OpenOption... openOptions) {
            this.verificationRoot = verificationRoot == null ? null : verificationRoot.remote;
            this.openOptions = openOptions;
        }

        @Override
        public boolean accept(File file) {
            return isSymlink(file, verificationRoot, openOptions);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Creates a symlink to the specified target.
     *
     * @param target
     *      The file that the symlink should point to.
     * @param listener
     *      If symlink creation requires a help of an external process, the error will be reported here.
     * @since 1.456
     */
    public void symlinkTo(final String target, final TaskListener listener) throws IOException, InterruptedException {
        act(new SymlinkTo(target, listener));
    }

    private static class SymlinkTo extends MasterToSlaveFileCallable<Void> {
        private final String target;
        private final TaskListener listener;

        SymlinkTo(String target, TaskListener listener) {
            this.target = target;
            this.listener = listener;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            Util.createSymlink(f.getParentFile(), target, f.getName(), listener);
            return null;
        }
    }

    /**
     * Resolves symlink, if the given file is a symlink. Otherwise, return null.
     * <p>
     * If the resolution fails, report an error.
     *
     * @since 1.456
     */
    public String readLink() throws IOException, InterruptedException {
        return act(new ReadLink());
    }

    private static class ReadLink extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return Util.resolveSymlink(f);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilePath that = (FilePath) o;

        if (!Objects.equals(channel, that.channel)) return false;
        return remote.equals(that.remote);

    }

    @Override
    public int hashCode() {
        return 31 * (channel != null ? channel.hashCode() : 0) + remote.hashCode();
    }

    /**
     * Supported tar file compression methods.
     */
    public enum TarCompression {
        NONE {
            @Override
            public InputStream extract(InputStream in) {
                return new BufferedInputStream(in);
            }

            @Override
            public OutputStream compress(OutputStream out) {
                return new BufferedOutputStream(out);
            }
        },
        GZIP {
            @Override
            public InputStream extract(InputStream in) throws IOException {
                return new GZIPInputStream(new BufferedInputStream(in));
            }

            @Override
            public OutputStream compress(OutputStream out) throws IOException {
                return new GZIPOutputStream(new BufferedOutputStream(out));
            }
        };

        public abstract InputStream extract(InputStream in) throws IOException;

        public abstract OutputStream compress(OutputStream in) throws IOException;
    }

    /**
     * Reads the given InputStream as a tar file and extracts it into this directory.
     *
     * @param _in
     *      The stream will be closed by this method after it's fully read.
     * @param compression
     *      The compression method in use.
     * @since 1.292
     */
    public void untarFrom(InputStream _in, final TarCompression compression) throws IOException, InterruptedException {
        try (_in) {
            final InputStream in = new RemoteInputStream(_in, Flag.GREEDY);
            act(new UntarFrom(compression, in));
        }
    }

    private static class UntarFrom extends MasterToSlaveFileCallable<Void> {
        private final TarCompression compression;
        private final InputStream in;

        UntarFrom(TarCompression compression, InputStream in) {
            this.compression = compression;
            this.in = in;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException {
            readFromTar("input stream", dir, compression.extract(in));
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Given a tgz/zip file, extracts it to the given target directory, if necessary.
     *
     * <p>
     * This method is a convenience method designed for installing a binary package to a location
     * that supports upgrade and downgrade. Specifically,
     *
     * <ul>
     * <li>If the target directory doesn't exist {@linkplain #mkdirs() it will be created}.
     * <li>The timestamp of the archive is left in the installation directory upon extraction.
     * <li>If the timestamp left in the directory does not match the timestamp of the current archive file,
     *     the directory contents will be discarded and the archive file will be re-extracted.
     * <li>If the connection is refused but the target directory already exists, it is left alone.
     * </ul>
     *
     * @param archive
     *      The resource that represents the tgz/zip file. This URL must support the {@code Last-Modified} header or the {@code ETag} header.
     *      (For example, you could use {@link ClassLoader#getResource}.)
     * @param listener
     *      If non-null, a message will be printed to this listener once this method decides to
     *      extract an archive, or if there is any issue.
     * @param message a message to be printed in case extraction will proceed.
     * @return
     *      true if the archive was extracted. false if the extraction was skipped because the target directory
     *      was considered up to date.
     * @since 1.299
     */
    public boolean installIfNecessaryFrom(@NonNull URL archive, @CheckForNull TaskListener listener, @NonNull String message) throws IOException, InterruptedException {
        if (listener == null) {
            listener = TaskListener.NULL;
        }
        return installIfNecessaryFrom(archive, listener, message, MAX_REDIRECTS);
    }

    private boolean installIfNecessaryFrom(@NonNull URL archive, @NonNull TaskListener listener, @NonNull String message, int maxRedirects) throws InterruptedException, IOException {
        try {
            FilePath timestamp = this.child(".timestamp");
            long lastModified = timestamp.lastModified();
            // https://httpwg.org/specs/rfc9110.html#field.etag is the ETag specification
            // Read previously stored ETag if timestamp is available
            String etag = timestamp.exists() ? fixEmptyAndTrim(timestamp.readToString()) : null;
            URLConnection con;
            try {
                con = ProxyConfiguration.open(archive);
                if (lastModified != 0) {
                    con.setIfModifiedSince(lastModified);
                }
                if (etag != null) {
                    con.setRequestProperty("If-None-Match", etag);
                }
                con.connect();
            } catch (IOException x) {
                if (this.exists()) {
                    // Cannot connect now, so assume whatever was last unpacked is still OK.
                    listener.getLogger().println("Skipping installation of " + archive + " to " + remote + ": " + x);
                    return false;
                } else {
                    throw x;
                }
            }

            if (con instanceof HttpURLConnection httpCon) {
                int responseCode = httpCon.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    // follows redirect
                    if (maxRedirects > 0) {
                        String location = httpCon.getHeaderField("Location");
                        listener.getLogger().println("Following redirect " + archive.toExternalForm() + " -> " + location);
                        return installIfNecessaryFrom(getUrlFactory().newURL(location), listener, message, maxRedirects - 1);
                    } else {
                        listener.getLogger().println("Skipping installation of " + archive + " to " + remote + " due to too many redirects.");
                        return false;
                    }
                }
                if (lastModified != 0 || etag != null) {
                    if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        return false;
                    } else if (responseCode != HttpURLConnection.HTTP_OK) {
                        listener.getLogger().println("Skipping installation of " + archive + " to " + remote + " due to server error: " + responseCode + " " + httpCon.getResponseMessage());
                        return false;
                    }
                }
            }

            long sourceTimestamp = con.getLastModified();
            String resultEtag = fixEmptyAndTrim(con.getHeaderField("ETag"));

            if (this.exists()) {
                if (equalETags(etag, resultEtag)) {
                    return false;   // already up to date
                }
                if (lastModified != 0 && sourceTimestamp == lastModified)
                    return false;   // already up to date
                this.deleteContents();
            } else {
                this.mkdirs();
            }

            listener.getLogger().println(message);

            if (isRemote()) {
                // First try to download from the agent machine.
                try {
                    act(new Unpack(archive));
                    if (resultEtag != null && !equalETags(etag, resultEtag)) {
                        /* Store the ETag value in the timestamp file for later use */
                        timestamp.write(resultEtag, "UTF-8");
                    }
                    timestamp.touch(sourceTimestamp);
                    return true;
                } catch (IOException x) {
                    Functions.printStackTrace(x, listener.error("Failed to download " + archive + " from agent; will retry from controller"));
                }
            }

            // for HTTP downloads, enable automatic retry for added resilience
            InputStream in = archive.getProtocol().startsWith("http") ? ProxyConfiguration.getInputStream(archive) : con.getInputStream();
            CountingInputStream cis = new CountingInputStream(in);
            try {
                if (archive.toExternalForm().endsWith(".zip"))
                    unzipFrom(cis);
                else
                    untarFrom(cis, TarCompression.GZIP);
            } catch (IOException e) {
                throw new IOException(String.format("Failed to unpack %s (%d bytes read of total %d)",
                        archive, cis.getByteCount(), con.getContentLength()), e);
            }
            if (resultEtag != null && !equalETags(etag, resultEtag)) {
                /* Store the ETag value in the timestamp file for later use */
                timestamp.write(resultEtag, "UTF-8");
            }
            timestamp.touch(sourceTimestamp);
            return true;
        } catch (IOException e) {
            throw new IOException("Failed to install " + archive + " to " + remote, e);
        }
    }

    /* Return true if etag1 equals etag2 as defined by the etag specification
       https://httpwg.org/specs/rfc9110.html#field.etag
     */
    private boolean equalETags(String etag1, String etag2) {
        if (etag1 == null || etag2 == null) {
            return false;
        }
        if (etag1.equals(etag2)) {
            return true;
        }
        /* Weak tags are identified by leading characters "W/" as a marker */
        /* Weak tag marker must not be considered in tag comparison.
           This implements the weak comparison in the specification at
           https://httpwg.org/specs/rfc9110.html#field.etag */
        String opaqueTag1 = etag1.startsWith("W/") ? etag1.substring(2) : etag1;
        String opaqueTag2 = etag2.startsWith("W/") ? etag2.substring(2) : etag2;
        return opaqueTag1.equals(opaqueTag2);
    }

    // this reads from arbitrary URL
    private static final class Unpack extends MasterToSlaveFileCallable<Void> {
        private final URL archive;

        Unpack(URL archive) {
            this.archive = archive;
        }

        @Override public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            try (InputStream in = archive.openStream()) {
                CountingInputStream cis = new CountingInputStream(in);
                try {
                    if (archive.toExternalForm().endsWith(".zip")) {
                        unzip(dir, cis);
                    } else {
                        readFromTar("input stream", dir, TarCompression.GZIP.extract(cis));
                    }
                } catch (IOException x) {
                    throw new IOException(String.format("Failed to unpack %s (%d bytes read)", archive, cis.getByteCount()), x);
                }
            }
            return null;
        }
    }

    /**
     * Reads the URL on the current VM, and streams the data to this file using the Remoting channel.
     * <p>This is different from resolving URL remotely.
     * If you instead wished to open an HTTP(S) URL on the remote side,
     * prefer <a href="http://javadoc.jenkins.io/plugin/apache-httpcomponents-client-4-api/io/jenkins/plugins/httpclient/RobustHTTPClient.html#copyFromRemotely-hudson.FilePath-java.net.URL-hudson.model.TaskListener-">{@code RobustHTTPClient.copyFromRemotely}</a>.
     * @since 1.293
     */
    public void copyFrom(URL url) throws IOException, InterruptedException {
        try (InputStream in = url.openStream()) {
            copyFrom(in);
        }
    }

    /**
     * Replaces the content of this file by the data from the given {@link InputStream}.
     *
     * @since 1.293
     */
    public void copyFrom(InputStream in) throws IOException, InterruptedException {
        try (OutputStream os = write()) {
            org.apache.commons.io.IOUtils.copy(in, os);
        }
    }

    /**
     * Convenience method to call {@link FilePath#copyTo(FilePath)}.
     *
     * @since 1.311
     */
    public void copyFrom(FilePath src) throws IOException, InterruptedException {
        src.copyTo(this);
    }

    /**
     * Place the data from {@link FileItem} into the file location specified by this {@link FilePath} object.
     */
    public void copyFrom(FileItem file) throws IOException, InterruptedException {
        if (channel == null) {
            try {
                file.write(Paths.get(remote));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            try (InputStream i = file.getInputStream();
                 OutputStream o = write()) {
                org.apache.commons.io.IOUtils.copy(i, o);
            }
        }
    }

    /**
     * @deprecated use {@link #copyFrom(FileItem)}
     */
    @Deprecated
    public void copyFrom(org.apache.commons.fileupload.FileItem file) throws IOException, InterruptedException {
        copyFrom(file.toFileUpload2FileItem());
    }

    /**
     * Code that gets executed on the machine where the {@link FilePath} is local.
     * Used to act on {@link FilePath}.
     * A typical implementation would be a {@code record} implementing {@link ControllerToAgentFileCallable}.
     * @see FilePath#act(FileCallable)
     */
    public interface FileCallable<T> extends Serializable, RoleSensitive {
        /**
         * Performs the computational task on the node where the data is located.
         *
         * <p>
         * All the exceptions are forwarded to the caller.
         *
         * @param f
         *      {@link File} that represents the local file that {@link FilePath} has represented.
         * @param channel
         *      The "back pointer" of the {@link Channel} that represents the communication
         *      with the node from where the code was sent.
         */
        T invoke(File f, VirtualChannel channel) throws IOException, InterruptedException;
    }

    /**
     * Executes some program on the machine that this {@link FilePath} exists,
     * so that one can perform local file operations.
     */
    public <T> T act(final FileCallable<T> callable) throws IOException, InterruptedException {
        return act(callable, callable.getClass().getClassLoader());
    }

    private <T> T act(final FileCallable<T> callable, ClassLoader cl) throws IOException, InterruptedException {
        if (channel != null) {
            // run this on a remote system
            try {
                DelegatingCallable<T, IOException> wrapper = new FileCallableWrapper<>(callable, cl, this);
                for (FileCallableWrapperFactory factory : ExtensionList.lookup(FileCallableWrapperFactory.class)) {
                    wrapper = factory.wrap(wrapper);
                }
                return channel.call(wrapper);
            } catch (TunneledInterruptedException e) {
                throw (InterruptedException) new InterruptedException(e.getMessage()).initCause(e);
            }
        } else {
            // the file is on the local machine.
            return callable.invoke(new File(remote), localChannel);
        }
    }

    /**
     * This extension point allows to contribute a wrapper around a fileCallable so that a plugin can "intercept" a
     * call.
     * <p>The {@link #wrap(hudson.remoting.DelegatingCallable)} method itself will be executed on the controller
     * (and may collect contextual data if needed) and the returned wrapper will be executed on remote.
     *
     * @since 1.482
     * @see AbstractInterceptorCallableWrapper
     */
    public abstract static class FileCallableWrapperFactory implements ExtensionPoint {

        public abstract <T> DelegatingCallable<T, IOException> wrap(DelegatingCallable<T, IOException> callable);

    }

    /**
     * Abstract {@link DelegatingCallable} that exposes a Before/After pattern for
     * {@link hudson.FilePath.FileCallableWrapperFactory} that want to implement AOP-style interceptors
     * @since 1.482
     */
    public abstract static class AbstractInterceptorCallableWrapper<T> implements DelegatingCallable<T, IOException> {
        private static final long serialVersionUID = 1L;

        private final DelegatingCallable<T, IOException> callable;

        protected AbstractInterceptorCallableWrapper(DelegatingCallable<T, IOException> callable) {
            this.callable = callable;
        }

        @Override
        public final ClassLoader getClassLoader() {
            return callable.getClassLoader();
        }

        @Override
        public final T call() throws IOException {
            before();
            try {
                return callable.call();
            } finally {
                after();
            }
        }

        /**
         * Executed before the actual FileCallable is invoked. This code will run on remote
         */
        protected void before() {}

        /**
         * Executed after the actual FileCallable is invoked (even if this one failed). This code will run on remote
         */
        protected void after() {}
    }


    /**
     * Executes some program on the machine that this {@link FilePath} exists,
     * so that one can perform local file operations.
     */
    public <T> Future<T> actAsync(final FileCallable<T> callable) throws IOException, InterruptedException {
        try {
            DelegatingCallable<T, IOException> wrapper = new FileCallableWrapper<>(callable, this);
            for (FileCallableWrapperFactory factory : ExtensionList.lookup(FileCallableWrapperFactory.class)) {
                wrapper = factory.wrap(wrapper);
            }
            return (channel != null ? channel : localChannel)
                .callAsync(wrapper);
        } catch (IOException e) {
            // wrap it into a new IOException so that we get the caller's stack trace as well.
            throw new IOException("remote file operation failed", e);
        }
    }

    /**
     * Executes some program on the machine that this {@link FilePath} exists,
     * so that one can perform local file operations.
     */
    public <V, E extends Throwable> V act(Callable<V, E> callable) throws IOException, InterruptedException, E {
        if (channel != null) {
            // run this on a remote system
            return channel.call(callable);
        } else {
            // the file is on the local machine
            return callable.call();
        }
    }

    /**
     * Takes a {@link FilePath}+{@link FileCallable} pair and returns the equivalent {@link Callable}.
     * When executing the resulting {@link Callable}, it executes {@link FileCallable#act(FileCallable)}
     * on this {@link FilePath}.
     *
     * @since 1.522
     */
    public <V> Callable<V, IOException> asCallableWith(final FileCallable<V> task) {
        return new CallableWith<>(task);
    }

    private class CallableWith<V> implements Callable<V, IOException> {
        private final FileCallable<V> task;

        CallableWith(FileCallable<V> task) {
            this.task = task;
        }

        @Override
        public V call() throws IOException {
            try {
                return act(task);
            } catch (InterruptedException e) {
                throw (IOException) new InterruptedIOException().initCause(e);
            }
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            task.checkRoles(checker);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Converts this file to the URI, relative to the machine
     * on which this file is available.
     */
    public URI toURI() throws IOException, InterruptedException {
        return act(new ToURI());
    }

    private static class ToURI extends MasterToSlaveFileCallable<URI> {
        private static final long serialVersionUID = 1L;

        @Override
        public URI invoke(File f, VirtualChannel channel) {
            return f.toURI();
        }
    }

    /**
     * Gets the {@link VirtualFile} representation of this {@link FilePath}
     *
     * @since 1.532
     */
    public VirtualFile toVirtualFile() {
        return VirtualFile.forFilePath(this);
    }

    /**
     * If this {@link FilePath} represents a file on a particular {@link Computer}, return it.
     * Otherwise null.
     * @since 1.571
     */
    public @CheckForNull Computer toComputer() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) {
            for (Computer c : j.getComputers()) {
                if (getChannel() == c.getChannel()) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Creates this directory.
     */
    public void mkdirs() throws IOException, InterruptedException {
        if (!act(new Mkdirs())) {
            throw new IOException("Failed to mkdirs: " + remote);
        }
    }

    private static class Mkdirs extends MasterToSlaveFileCallable<Boolean> {
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            if (mkdirs(f) || f.exists())
                return true;    // OK

            // following Ant <mkdir> task to avoid possible race condition.
            Thread.sleep(10);

            return mkdirs(f) || f.exists();
        }
    }

    /**
     * Deletes all suffixes recursively.
     * @throws IOException if it exists but could not be successfully deleted
     * @since 2.244
     */
    public void deleteSuffixesRecursive() throws IOException, InterruptedException {
        act(new DeleteSuffixesRecursive());
    }

    /**
     * Deletes all suffixed directories that are separated by {@link WorkspaceList#COMBINATOR}, including all its contents recursively.
     */
    private static class DeleteSuffixesRecursive extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            for (File file : listParentFiles(f)) {
                if (file.getName().startsWith(f.getName() + WorkspaceList.COMBINATOR)) {
                    Util.deleteRecursive(file.toPath(), Path::toFile);
                }
            }

            return null;
        }
    }

    private static File[] listParentFiles(File f) {
        File parentFile = f.getParentFile();
        if (parentFile != null) {
            File[] files = parentFile.listFiles();
            if (files != null) {
                return files;
            }
        }
        return new File[0];
    }

    /**
     * Deletes this directory, including all its contents recursively.
     */
    public void deleteRecursive() throws IOException, InterruptedException {
        act(new DeleteRecursive());
    }

    private static class DeleteRecursive extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            Util.deleteRecursive(fileToPath(f), Path::toFile);
            return null;
        }
    }

    /**
     * Deletes all the contents of this directory, but not the directory itself
     */
    public void deleteContents() throws IOException, InterruptedException {
        act(new DeleteContents());
    }

    private static class DeleteContents extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            Util.deleteContentsRecursive(fileToPath(f), Path::toFile);
            return null;
        }
    }

    /**
     * Gets the file name portion except the extension.
     *
     * For example, "foo" for "foo.txt" and "foo.tar" for "foo.tar.gz".
     */
    public String getBaseName() {
        String n = getName();
        int idx = n.lastIndexOf('.');
        if (idx < 0)  return n;
        return n.substring(0, idx);
    }
    /**
     * Gets just the file name portion without directories.
     *
     * For example, "foo.txt" for "../abc/foo.txt"
     */

    public String getName() {
        String r = remote;
        if (r.endsWith("\\") || r.endsWith("/"))
            r = r.substring(0, r.length() - 1);

        int len = r.length() - 1;
        while (len >= 0) {
            char ch = r.charAt(len);
            if (ch == '\\' || ch == '/')
                break;
            len--;
        }

        return r.substring(len + 1);
    }

    /**
     * Short for {@code getParent().child(rel)}. Useful for getting other files in the same directory.
     * @return null if {@link #getParent} would have
     */
    @CheckForNull
    public FilePath sibling(String rel) {
        FilePath parent = getParent();
        return parent != null ? parent.child(rel) : null;
    }

    /**
     * Returns a {@link FilePath} by adding the given suffix to this path name.
     */
    public FilePath withSuffix(String suffix) {
        return new FilePath(channel, remote + suffix);
    }

    /**
     * The same as {@link FilePath#FilePath(FilePath,String)} but more OO.
     * @param relOrAbsolute a relative or absolute path
     * @return a file on the same channel
     */
    public @NonNull FilePath child(String relOrAbsolute) {
        return new FilePath(this, relOrAbsolute);
    }

    /**
     * Gets the parent file.
     * @return parent FilePath or null if there is no parent
     */
    @CheckForNull
    public FilePath getParent() {
        int i = remote.length() - 2;
        for (; i >= 0; i--) {
            char ch = remote.charAt(i);
            if (ch == '\\' || ch == '/')
                break;
        }

        return i >= 0 ? new FilePath(channel, remote.substring(0, i + 1)) : null;
    }

    /**
     * Creates a temporary file in the directory that this {@link FilePath} object designates.
     *
     * @param prefix
     *      The prefix string to be used in generating the file's name; must be
     *      at least three characters long
     * @param suffix
     *      The suffix string to be used in generating the file's name; may be
     *      null, in which case the suffix ".tmp" will be used
     * @return
     *      The new FilePath pointing to the temporary file
     * @see File#createTempFile(String, String)
     */
    public FilePath createTempFile(final String prefix, final String suffix) throws IOException, InterruptedException {
        try {
            return new FilePath(this, act(new CreateTempFile(prefix, suffix)));
        } catch (IOException e) {
            throw new IOException("Failed to create a temp file on " + remote, e);
        }
    }

    private static class CreateTempFile extends MasterToSlaveFileCallable<String> {
        private final String prefix;
        private final String suffix;

        CreateTempFile(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public String invoke(File dir, VirtualChannel channel) throws IOException {
            File f = File.createTempFile(prefix, suffix, dir);
            return f.getName();
        }
    }

    /**
     * Creates a temporary file in this directory and set the contents to the
     * given text (encoded in the platform default encoding)
     *
     * @param prefix
     *      The prefix string to be used in generating the file's name; must be
     *      at least three characters long
     * @param suffix
     *      The suffix string to be used in generating the file's name; may be
     *      null, in which case the suffix ".tmp" will be used
     * @param contents
     *      The initial contents of the temporary file.
     * @return
     *      The new FilePath pointing to the temporary file
     * @see File#createTempFile(String, String)
     */
    public FilePath createTextTempFile(final String prefix, final String suffix, final String contents) throws IOException, InterruptedException {
        return createTextTempFile(prefix, suffix, contents, true);
    }

    /**
     * Creates a temporary file in this directory (or the system temporary
     * directory) and set the contents to the given text (encoded in the
     * platform default encoding)
     *
     * @param prefix
     *      The prefix string to be used in generating the file's name; must be
     *      at least three characters long
     * @param suffix
     *      The suffix string to be used in generating the file's name; may be
     *      null, in which case the suffix ".tmp" will be used
     * @param contents
     *      The initial contents of the temporary file.
     * @param inThisDirectory
     *      If true, then create this temporary in the directory pointed to by
     *      this.
     *      If false, then the temporary file is created in the system temporary
     *      directory (java.io.tmpdir)
     * @return
     *      The new FilePath pointing to the temporary file
     * @see File#createTempFile(String, String)
     */
    public FilePath createTextTempFile(final String prefix, final String suffix, final String contents, final boolean inThisDirectory) throws IOException, InterruptedException {
        try {
            return new FilePath(channel, act(new CreateTextTempFile(inThisDirectory, prefix, suffix, contents)));
        } catch (IOException e) {
            throw new IOException("Failed to create a temp file on " + remote, e);
        }
    }

    private static class CreateTextTempFile extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;
        private final boolean inThisDirectory;
        private final String prefix;
        private final String suffix;
        private final String contents;

        CreateTextTempFile(boolean inThisDirectory, String prefix, String suffix, String contents) {
            this.inThisDirectory = inThisDirectory;
            this.prefix = prefix;
            this.suffix = suffix;
            this.contents = contents;
        }

        @Override
        public String invoke(File dir, VirtualChannel channel) throws IOException {
            if (!inThisDirectory)
                dir = new File(System.getProperty("java.io.tmpdir"));
            else
                mkdirs(dir);

            File f;
            try {
                f = File.createTempFile(prefix, suffix, dir);
            } catch (IOException e) {
                throw new IOException("Failed to create a temporary directory in " + dir, e);
            }

            try (Writer w = Files.newBufferedWriter(Util.fileToPath(f), Charset.defaultCharset())) {
                w.write(contents);
            }

            return f.getAbsolutePath();
        }
    }

    /**
     * Creates a temporary directory inside the directory represented by 'this'
     *
     * @param prefix
     *      The prefix string to be used in generating the directory's name;
     *      must be at least three characters long
     * @param suffix
     *      The suffix string to be used in generating the directory's name; may
     *      be null, in which case the suffix ".tmp" will be used
     * @return
     *      The new FilePath pointing to the temporary directory
     * @since 1.311
     * @see Files#createTempDirectory(Path, String, FileAttribute[])
     */
    public FilePath createTempDir(final String prefix, final String suffix) throws IOException, InterruptedException {
        try {
            String[] s;
            if (suffix == null || suffix.isBlank()) {
                s = new String[]{prefix, "tmp"}; // see File.createTempFile - tmp is used if suffix is null
            } else {
                s = new String[]{prefix, suffix};
            }
            String name = String.join(".", s);
            return new FilePath(this, act(new CreateTempDir(name)));
        } catch (IOException e) {
            throw new IOException("Failed to create a temp directory on " + remote, e);
        }
    }

    private static class CreateTempDir extends MasterToSlaveFileCallable<String> {
        private final String name;

        CreateTempDir(String name) {
            this.name = name;
        }

            private static final long serialVersionUID = 1L;

            @Override
            public String invoke(File dir, VirtualChannel channel) throws IOException {

                Path tempPath;
                final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

                if (isPosix) {
                    tempPath = Files.createTempDirectory(Util.fileToPath(dir), name,
                            PosixFilePermissions.asFileAttribute(EnumSet.allOf(PosixFilePermission.class)));
                } else {
                    tempPath = Files.createTempDirectory(Util.fileToPath(dir), name);
                }

                if (tempPath.toFile() == null) {
                    throw new IOException("Failed to obtain file from path " + dir);
                }
                return tempPath.toFile().getName();
            }
    }

    /**
     * Deletes this file.
     * @return true, for a modicum of compatibility
     * @throws IOException if it exists but could not be successfully deleted
     */
    public boolean delete() throws IOException, InterruptedException {
        act(new Delete());
        return true;
    }

    private static class Delete extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            Util.deleteFile(f);
            return null;
        }
    }

    /**
     * Checks if the file exists.
     */
    public boolean exists() throws IOException, InterruptedException {
        return act(new Exists());
    }

    private static class Exists extends MasterToSlaveFileCallable<Boolean> {
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean invoke(File f, VirtualChannel channel) throws IOException {
            return f.exists();
        }
    }

    /**
     * Gets the last modified time stamp of this file, by using the clock
     * of the machine where this file actually resides.
     *
     * @see File#lastModified()
     * @see #touch(long)
     */
    public long lastModified() throws IOException, InterruptedException {
        return act(new LastModified());
    }

    private static class LastModified extends MasterToSlaveFileCallable<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            return f.lastModified();
        }
    }

    /**
     * Creates a file (if not already exist) and sets the timestamp.
     *
     * @since 1.299
     */
    public void touch(final long timestamp) throws IOException, InterruptedException {
        act(new Touch(timestamp));
    }

    private static class Touch extends MasterToSlaveFileCallable<Void> {
        private final long timestamp;

        Touch(long timestamp) {
            this.timestamp = timestamp;
        }

            private static final long serialVersionUID = -5094638816500738429L;

            @Override
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                if (!f.exists()) {
                    Files.newOutputStream(fileToPath(f)).close();
                }
                if (!f.setLastModified(timestamp))
                    throw new IOException("Failed to set the timestamp of " + f + " to " + timestamp);
                return null;
            }
    }

    private void setLastModifiedIfPossible(final long timestamp) throws IOException, InterruptedException {
        String message = act(new SetLastModified(timestamp));

        if (message != null) {
            LOGGER.warning(message);
        }
    }

    private static class SetLastModified extends MasterToSlaveFileCallable<String> {
        private final long timestamp;

        SetLastModified(long timestamp) {
            this.timestamp = timestamp;
        }

            private static final long serialVersionUID = -828220335793641630L;

            @Override
            public String invoke(File f, VirtualChannel channel) throws IOException {
                if (!f.setLastModified(timestamp)) {
                    if (Functions.isWindows()) {
                        // On Windows this seems to fail often. See JENKINS-11073
                        // Therefore don't fail, but just log a warning
                        return "Failed to set the timestamp of " + f + " to " + timestamp;
                    } else {
                        throw new IOException("Failed to set the timestamp of " + f + " to " + timestamp);
                    }
                }
                return null;
            }
    }

    /**
     * Checks if the file is a directory.
     */
    public boolean isDirectory() throws IOException, InterruptedException {
        return act(new IsDirectory());
    }

    private static class IsDirectory extends MasterToSlaveFileCallable<Boolean> {
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean invoke(File f, VirtualChannel channel) throws IOException {
            return f.isDirectory();
        }
    }

    /**
     * Returns the file size in bytes.
     *
     * @since 1.129
     */
    public long length() throws IOException, InterruptedException {
        return act(new Length());
    }

    private static class Length extends MasterToSlaveFileCallable<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            return f.length();
        }
    }

    /**
     * Returns the number of unallocated bytes in the partition of that file.
     * @since 1.542
     */
    public long getFreeDiskSpace() throws IOException, InterruptedException {
        return act(new GetFreeDiskSpace());
    }

    private static class GetFreeDiskSpace extends MasterToSlaveFileCallable<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            return f.getFreeSpace();
        }
    }

    /**
     * Returns the total number of bytes in the partition of that file.
     * @since 1.542
     */
    public long getTotalDiskSpace() throws IOException, InterruptedException {
        return act(new GetTotalDiskSpace());
    }

    private static class GetTotalDiskSpace extends MasterToSlaveFileCallable<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            return f.getTotalSpace();
        }
    }

    /**
     * Returns the number of usable bytes in the partition of that file.
     * @since 1.542
     */
    public long getUsableDiskSpace() throws IOException, InterruptedException {
        return act(new GetUsableDiskSpace());
    }

    private static class GetUsableDiskSpace extends MasterToSlaveFileCallable<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            return f.getUsableSpace();
        }
    }

    /**
     * Sets the file permission.
     *
     * On Windows, no-op.
     *
     * @param mask
     *      File permission mask. To simplify the permission copying,
     *      if the parameter is -1, this method becomes no-op.
     *      <p>
     *      please note mask is expected to be an octal if you use <a href="http://en.wikipedia.org/wiki/Chmod">chmod command line values</a>,
     *      so preceded by a '0' in java notation, ie {@code chmod(0644)}
     *      <p>
     *      Only supports setting read, write, or execute permissions for the
     *      owner, group, or others, so the largest permissible value is 0777.
     *      Attempting to set larger values (i.e. the setgid, setuid, or sticky
     *      bits) will cause an IOException to be thrown.
     *
     * @since 1.303
     * @see #mode()
     */
    public void chmod(final int mask) throws IOException, InterruptedException {
        if (!isUnix() || mask == -1)   return;
        act(new Chmod(mask));
    }

    private static class Chmod extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;
        private final int mask;

        Chmod(int mask) {
            this.mask = mask;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            _chmod(f, mask);

            return null;
        }
    }

    /**
     * Change permissions via NIO.
     */
    private static void _chmod(File f, int mask) throws IOException {
        // TODO WindowsPosix actually does something here (WindowsLibC._wchmod); should we let it?
        // Anyway the existing calls already skip this method if on Windows.
        if (File.pathSeparatorChar == ';')  return; // noop

        Files.setPosixFilePermissions(fileToPath(f), Util.modeToPermissions(mask));
    }

    /**
     * Gets the file permission bit mask.
     *
     * @return
     *      -1 on Windows, since such a concept doesn't make sense.
     * @since 1.311
     * @see #chmod(int)
     */
    public int mode() throws IOException, InterruptedException {
        if (!isUnix())   return -1;
        return act(new Mode());
    }

    private static class Mode extends MasterToSlaveFileCallable<Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public Integer invoke(File f, VirtualChannel channel) throws IOException {
            return IOUtils.mode(f);
        }
    }

    /**
     * List up files and directories in this directory.
     *
     * <p>
     * This method returns direct children of the directory denoted by the 'this' object.
     */
    @NonNull
    public List<FilePath> list() throws IOException, InterruptedException {
        return list((FileFilter) null);
    }

    /**
     * List up files and directories in this directory.
     *
     * This is intended to allow the caller to provide {@link java.nio.file.LinkOption#NOFOLLOW_LINKS} to ignore
     * symlinks.
     * @param verificationRoot A root or base directory for checking for any symlinks in this files parentage.
     *             Any symlinks between a file and root should be ignored.
     *             Symlinks in the parentage outside root will not be checked.
     * @param openOptions the options to apply when opening.
     * @return Direct children of this directory.
     */
    @NonNull
    public List<FilePath> list(FilePath verificationRoot, OpenOption... openOptions) throws IOException, InterruptedException {
        return list(new OptionalDiscardingFileFilter(verificationRoot, openOptions));
    }

    /**
     * List up subdirectories.
     *
     * @return can be empty but never null. Doesn't contain "." and ".."
     */
    @NonNull
    public List<FilePath> listDirectories() throws IOException, InterruptedException {
        return list(new DirectoryFilter());
    }

    private static final class DirectoryFilter implements FileFilter, Serializable {
        @Override
        public boolean accept(File f) {
            return f.isDirectory();
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * List up files in this directory, just like {@link File#listFiles(FileFilter)}.
     *
     * @param filter
     *      The optional filter used to narrow down the result.
     *      If non-null, must be {@link Serializable}.
     *      If this {@link FilePath} represents a remote path,
     *      the filter object will be executed on the remote machine.
     */
    @NonNull
    public List<FilePath> list(final FileFilter filter) throws IOException, InterruptedException {
        if (filter != null && !(filter instanceof Serializable)) {
            throw new IllegalArgumentException("Non-serializable filter of " + filter.getClass());
        }
        return act(new ListFilter(filter), (filter != null ? filter : this).getClass().getClassLoader());
    }

    private static class ListFilter extends MasterToSlaveFileCallable<List<FilePath>> {
        private final FileFilter filter;

        ListFilter(FileFilter filter) {
            this.filter = filter;
        }

            private static final long serialVersionUID = 1L;

            @Override
            public List<FilePath> invoke(File f, VirtualChannel channel) throws IOException {
                File[] children = f.listFiles(filter);
                if (children == null) {
                    return Collections.emptyList();
                }

                ArrayList<FilePath> r = new ArrayList<>(children.length);
                for (File child : children)
                    r.add(new FilePath(child));

                return r;
            }
    }

    /**
     * List up files in this directory that matches the given Ant-style filter.
     *
     * @param includes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @return
     *      can be empty but always non-null.
     */
    @NonNull
    public FilePath[] list(final String includes) throws IOException, InterruptedException {
        return list(includes, null);
    }

    /**
     * List up files in this directory that matches the given Ant-style filter.
     *
     * @param includes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @param excludes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @return
     *      can be empty but always non-null.
     * @since 1.407
     */
    @NonNull
    public FilePath[] list(final String includes, final String excludes) throws IOException, InterruptedException {
        return list(includes, excludes, true);
    }

    /**
     * List up files in this directory that matches the given Ant-style filter.
     *
     * @param includes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @param excludes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @param defaultExcludes whether to use the ant default excludes
     * @return
     *      can be empty but always non-null.
     * @since 1.465
     */
    @NonNull
    public FilePath[] list(final String includes, final String excludes, final boolean defaultExcludes) throws IOException, InterruptedException {
        return act(new ListGlob(includes, excludes, defaultExcludes));
    }

    private static class ListGlob extends MasterToSlaveFileCallable<FilePath[]> {
        private final String includes;
        private final String excludes;
        private final boolean defaultExcludes;

        ListGlob(String includes, String excludes, boolean defaultExcludes) {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
        }

            private static final long serialVersionUID = 1L;

            @Override
            public FilePath[] invoke(File f, VirtualChannel channel) throws IOException {
                String[] files = glob(f, includes, excludes, defaultExcludes);

                FilePath[] r = new FilePath[files.length];
                for (int i = 0; i < r.length; i++)
                    r[i] = new FilePath(new File(f, files[i]));

                return r;
            }
    }

    /**
     * Runs Ant glob expansion.
     *
     * @return
     *      A set of relative file names from the base directory.
     */
    @NonNull
    private static String[] glob(File dir, String includes, String excludes, boolean defaultExcludes) throws IOException {
        if (isAbsolute(includes))
            throw new IOException("Expecting Ant GLOB pattern, but saw '" + includes + "'. See https://ant.apache.org/manual/Types/fileset.html for syntax");
        FileSet fs = Util.createFileSet(dir, includes, excludes);
        fs.setDefaultexcludes(defaultExcludes);
        DirectoryScanner ds;
        try {
            ds = fs.getDirectoryScanner(new Project());
        } catch (BuildException x) {
            throw new IOException(x.getMessage());
        }
        return ds.getIncludedFiles();
    }

    /**
     * Reads this file.
     */
    public InputStream read() throws IOException, InterruptedException {
        return read(null, new OpenOption[0]);
    }

    public InputStream read(FilePath rootPath, OpenOption... openOptions) throws IOException, InterruptedException {
        String rootPathString = rootPath == null ? null : rootPath.remote;
        if (channel == null) {
            File file = new File(remote);
            InputStream inputStream = newInputStreamDenyingSymlinkAsNeeded(file, rootPathString, openOptions);
            return inputStream;
        }

        final Pipe p = Pipe.createRemoteToLocal();
        actAsync(new Read(p, rootPathString, openOptions));

        return p.getIn();
    }

    public static InputStream newInputStreamDenyingSymlinkAsNeeded(File file, String verificationRoot, OpenOption... openOptions) throws IOException {
        InputStream inputStream = null;
        try {
            denyTmpDir(file, verificationRoot, openOptions);
            denySymlink(file, verificationRoot, openOptions);
            inputStream = openInputStream(file, openOptions);
            denySymlink(file, verificationRoot, openOptions);
        } catch (IOException ioe) {
            if (inputStream != null) {
                inputStream.close();
            }
            throw ioe;
        }
        return inputStream;
    }

    public static InputStream openInputStream(File file, OpenOption[] openOptions) throws IOException {
        return Files.newInputStream(fileToPath(file), stripLocalOptions(openOptions));
    }

    private static OpenOption[] stripLocalOptions(OpenOption... openOptions) {
        if (openOptions != null) {
            return Arrays.stream(openOptions).filter(option -> option != DisplayOption.IGNORE_TMP_DIRS).toArray(OpenOption[]::new);
        }
        return null;
    }

    private static void denySymlink(File file, String root, OpenOption... openOptions) throws IOException {
        /* This should be checked right before the file is opened or otherwise traversed.
           If at all possible, it should also be checked immediately afterwards.
           This narrows any possible race conditions that may exist in weird situations,
           platforms, or implementations.
           newInputStreamDenyingSymlinkAsNeeded(...) demonstrates how this would be done.

           This is useful for preventing symlink following on systems that don't support
           LinkOption.NOFOLLOW_LINK. Notable among those, is AIX. It is also important for
           prohibiting Windows Junctions, which are not considered symlinks by the
           Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS) implementation.
        */

        if (isSymlink(file, root, openOptions)) {
            throw new IOException("Symlinks are prohibited.");
        }
    }

    private static void denyTmpDir(File file, String root, OpenOption... openOptions) throws IOException {
        if (isTmpDir(file, root, openOptions)) {
            throw new IOException("Tmp directory is prohibited.");
        }
    }

    public static boolean isSymlink(File file, String root, OpenOption... openOptions) {
        if (isNoFollowLink(openOptions)) {
            if (Util.isSymlink(file.toPath())) {
                return true;
            }

            return isFileAncestorSymlink(file, root);
        }
        return false;
    }

    private static boolean isSymlink(VisitorInfo visitorInfo) {
        return isSymlink(visitorInfo.f, visitorInfo.verificationRoot, visitorInfo.openOptions);
    }

    public static boolean isTmpDir(File file, String root, OpenOption... openOptions) {
        if (isIgnoreTmpDirs(openOptions)) {
            if (isTmpDir(file)) {
                return true;
            }

            return isFileAncestorTmpDir(file, root);
        }
        return false;
    }

    public static boolean isTmpDir(String filename, OpenOption... openOptions) {
        if (isIgnoreTmpDirs(openOptions)) {
            return isTmpDir(filename);
        }
        return false;
    }

    private static boolean isTmpDir(VisitorInfo visitorInfo) {
        return isTmpDir(visitorInfo.f, visitorInfo.verificationRoot, visitorInfo.openOptions);
    }

    private static boolean isTmpDir(File file) {
        return file.isDirectory() && isTmpDir(file.getName());
    }

    private static boolean isTmpDir(String filename) {
        return filename.length() > WorkspaceList.TMP_DIR_SUFFIX.length() && filename.endsWith(WorkspaceList.TMP_DIR_SUFFIX);
    }

    public static boolean isNoFollowLink(OpenOption... openOptions) {
        return Arrays.asList(openOptions).contains(LinkOption.NOFOLLOW_LINKS);
    }

    public static boolean isIgnoreTmpDirs(OpenOption... openOptions) {
        return Arrays.asList(openOptions).contains(DisplayOption.IGNORE_TMP_DIRS);
    }

    private static boolean isFileAncestorSymlink(File file, String root) {
        return doesFileAncestorMatch(file, root, Util::isSymlink);
    }

    /**
     * Determines whether an ancestor of this file is a tmp directory, between the specified
     * file and the root path. Ancestors further up the tree are not considered.
     * @param file The base file for the beginning of the search.
     * @param root The root path for ending the search.
     * @return True if there is a tmp directory within the domain. False otherwise.
     */
    private static boolean isFileAncestorTmpDir(File file, String root) {
        return doesFileAncestorMatch(file, root, path -> isTmpDir(path.toFile()));
    }

    /**
     * Determines whether an ancestor of this file is a symlink, between the specified
     * file and the root path. Ancestors further up the tree are not considered.
     * @param file The base file for the beginning of the search.
     * @param root The root path for ending the search.
     * @return True if there is a symlink within the domain. False otherwise.
     */
     private static boolean doesFileAncestorMatch(File file, String root, Predicate<Path> matcher) {
        if (root != null) {
            Path rootPath = Paths.get(root);
            Path currPath = file.toPath();
            try {
                while (!getRealPath(currPath).equals(getRealPath(rootPath))) {
                    if (matcher.test(currPath)) {
                        return true;
                    }
                    currPath = currPath.getParent();
                    if (currPath == null) {
                        return false;
                    }
                }
            } catch (IOException ioe) {
                return false;
            }
        }
        return false;
    }

    private static class Read extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;
        private final Pipe p;
        private String verificationRoot;
        private OpenOption[] openOptions;

        Read(Pipe p, String verificationRoot, OpenOption... openOptions) {
            this.p = p;
            this.verificationRoot = verificationRoot;
            this.openOptions = openOptions;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            try (InputStream fis = newInputStreamDenyingSymlinkAsNeeded(f, verificationRoot, openOptions); OutputStream out = p.getOut()) {
                org.apache.commons.io.IOUtils.copy(fis, out);
            } catch (Exception x) {
                p.error(x);
            }
            return null;
        }
    }

    /**
     * Reads this file from the specific offset.
     * @since 1.586
     */
    public InputStream readFromOffset(final long offset) throws IOException, InterruptedException {
        if (channel == null) {
            final RandomAccessFile raf = new RandomAccessFile(new File(remote), "r");
            try {
                raf.seek(offset);
            } catch (IOException e) {
                try {
                    raf.close();
                } catch (IOException e1) {
                    // ignore
                }
                throw e;
            }
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return raf.read();
                }

                @Override
                public void close() throws IOException {
                    raf.close();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return raf.read(b, off, len);
                }

                @Override
                public int read(byte[] b) throws IOException {
                    return raf.read(b);
                }
            };
        }

        final Pipe p = Pipe.createRemoteToLocal();
        actAsync(new OffsetPipeSecureFileCallable(p, offset));
        return new java.util.zip.GZIPInputStream(p.getIn());
    }

    private static class OffsetPipeSecureFileCallable extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        private Pipe p;
        private long offset;

        private OffsetPipeSecureFileCallable(Pipe p, long offset) {
            this.p = p;
            this.offset = offset;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            try (OutputStream os = p.getOut();
                 OutputStream out = new GZIPOutputStream(os, 8192);
                 RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                raf.seek(offset);
                byte[] buf = new byte[8192];
                int len;
                while ((len = raf.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
                return null;
            }
        }
    }

    /**
     * Reads this file into a string, by using the current system encoding on the remote machine.
     */
    public String readToString() throws IOException, InterruptedException {
        return act(new ReadToString());
    }

    private static class ReadToString extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return Files.readString(fileToPath(f), Charset.defaultCharset());
        }
    }

    /**
     * Writes to this file.
     * If this file already exists, it will be overwritten.
     * If the directory doesn't exist, it will be created.
     *
     * <P>
     * I/O operation to remote {@link FilePath} happens asynchronously, meaning write operations to the returned
     * {@link OutputStream} will return without receiving a confirmation from the remote that the write happened.
     * I/O operations also happens asynchronously from the {@link Channel#call(Callable)} operations, so if
     * you write to a remote file and then execute {@link Channel#call(Callable)} and try to access the newly copied
     * file, it might not be fully written yet.
     */
    public OutputStream write() throws IOException, InterruptedException {
        if (channel == null) {
            File f = new File(remote).getAbsoluteFile();
            mkdirs(f.getParentFile());
            return Files.newOutputStream(fileToPath(f));
        }

        return act(new WritePipe());
    }

    private static class WritePipe extends MasterToSlaveFileCallable<OutputStream> {
            private static final long serialVersionUID = 1L;

            @Override
            public OutputStream invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                f = f.getAbsoluteFile();
                mkdirs(f.getParentFile());
                return new RemoteOutputStream(Files.newOutputStream(fileToPath(f)));
            }
    }

    /**
     * Overwrites this file by placing the given String as the content.
     *
     * @param encoding
     *      Null to use the platform default encoding on the remote machine.
     * @since 1.105
     */
    public void write(final String content, final String encoding) throws IOException, InterruptedException {
        act(new Write(encoding, content));
    }

    private static class Write extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;
        private final String encoding;
        private final String content;

        Write(String encoding, String content) {
            this.encoding = encoding;
            this.content = content;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            mkdirs(f.getParentFile());
            try (OutputStream fos = Files.newOutputStream(fileToPath(f));
                    Writer w = encoding != null ? new OutputStreamWriter(fos, encoding) : new OutputStreamWriter(fos, Charset.defaultCharset())) {
                w.write(content);
            }
            return null;
        }
    }

    /**
     * Computes the MD5 digest of the file in hex string.
     * @see Util#getDigestOf(File)
     */
    public String digest() throws IOException, InterruptedException {
        return act(new Digest());
    }

    private static class Digest extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String invoke(File f, VirtualChannel channel) throws IOException {
            return Util.getDigestOf(f);
        }
    }

    /**
     * Rename this file/directory to the target filepath.  This FilePath and the target must
     * be on the same host
     */
    public void renameTo(final FilePath target) throws IOException, InterruptedException {
        if (this.channel != target.channel) {
            throw new IOException("renameTo target must be on the same host");
        }
        act(new RenameTo(target));
    }

    private static class RenameTo extends MasterToSlaveFileCallable<Void> {
        private final FilePath target;

        RenameTo(FilePath target) {
            this.target = target;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            Files.move(fileToPath(f), fileToPath(new File(target.remote)), LinkOption.NOFOLLOW_LINKS);
            return null;
        }
    }

    /**
     * Moves all the contents of this directory into the specified directory, then delete this directory itself.
     *
     * @since 1.308.
     */
    public void moveAllChildrenTo(final FilePath target) throws IOException, InterruptedException {
        if (this.channel != target.channel) {
            throw new IOException("pullUpTo target must be on the same host");
        }
        act(new MoveAllChildrenTo(target));
    }

    private static class MoveAllChildrenTo extends MasterToSlaveFileCallable<Void> {
        private final FilePath target;

        MoveAllChildrenTo(FilePath target) {
            this.target = target;
        }

            private static final long serialVersionUID = 1L;

            @Override
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                // JENKINS-16846: if f.getName() is the same as one of the files/directories in f,
                // the rename op will fail
                File tmp = new File(f.getAbsolutePath() + ".__rename");
                if (!f.renameTo(tmp))
                    throw new IOException("Failed to rename " + f + " to " + tmp);

                File t = new File(target.getRemote());

                for (File child : tmp.listFiles()) {
                    File target = new File(t, child.getName());
                    if (!child.renameTo(target))
                        throw new IOException("Failed to rename " + child + " to " + target);
                }
                Files.deleteIfExists(Util.fileToPath(tmp));
                return null;
            }
    }

    /**
     * Copies this file to the specified target.
     */
    public void copyTo(FilePath target) throws IOException, InterruptedException {
        try {
            try (OutputStream out = target.write()) {
                copyTo(out);
            }
        } catch (IOException e) {
            throw new IOException("Failed to copy " + this + " to " + target, e);
        }
    }

    /**
     * Copies this file to the specified target, with file permissions and other meta attributes intact.
     * @since 1.311
     */
    public void copyToWithPermission(FilePath target) throws IOException, InterruptedException {
        // Use NIO copy with StandardCopyOption.COPY_ATTRIBUTES when copying on the same machine.
        if (this.channel == target.channel) {
            act(new CopyToWithPermission(target));
            return;
        }

        copyTo(target);
        // copy file permission
        target.chmod(mode());
        target.setLastModifiedIfPossible(lastModified());
    }

    private static class CopyToWithPermission extends MasterToSlaveFileCallable<Void> {
        private final FilePath target;

        CopyToWithPermission(FilePath target) {
            this.target = target;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            File targetFile = new File(target.remote);
            File targetDir = targetFile.getParentFile();
            Files.createDirectories(fileToPath(targetDir));
            Files.copy(fileToPath(f), fileToPath(targetFile), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return null;
        }
    }

    /**
     * Sends the contents of this file into the given {@link OutputStream}.
     */
    public void copyTo(OutputStream os) throws IOException, InterruptedException {
        final OutputStream out = new RemoteOutputStream(os);

        act(new CopyTo(out));

        // make sure the writes fully got delivered to 'os' before we return.
        // this is needed because I/O operation is asynchronous
        syncIO();
    }

    private static class CopyTo extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 4088559042349254141L;
        private final OutputStream out;

        CopyTo(OutputStream out) {
            this.out = out;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            try (InputStream fis = Files.newInputStream(fileToPath(f))) {
                org.apache.commons.io.IOUtils.copy(fis, out);
                return null;
            } finally {
                out.close();
            }
        }
    }

    /**
     * With fix to JENKINS-11251 (remoting 2.15), this is no longer necessary.
     * But I'm keeping it for a while so that users who manually deploy agent.jar has time to deploy new version
     * before this goes away.
     */
    private void syncIO() throws InterruptedException {
        try {
            if (channel != null)
                channel.syncLocalIO();
        } catch (AbstractMethodError e) {
            // legacy agent.jar. Handle this gracefully
            try {
                LOGGER.log(Level.WARNING, "Looks like an old agent.jar. Please update " + Which.jarFile(Channel.class) + " to the new version", e);
            } catch (IOException ignored) {
                // really ignore this time
            }
        }
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    private void _syncIO() throws InterruptedException {
        channel.syncLocalIO();
    }

    /**
     * Remoting interface used for {@link FilePath#copyRecursiveTo(String, FilePath)}.
     *
     * TODO: this might not be the most efficient way to do the copy.
     */
    interface RemoteCopier {
        /**
         * @param fileName
         *      relative path name to the output file. Path separator must be '/'.
         */
        void open(String fileName) throws IOException;

        void write(byte[] buf, int len) throws IOException;

        void close() throws IOException;
    }

    /**
     * Copies the contents of this directory recursively into the specified target directory.
     *
     * @return
     *      the number of files copied.
     * @since 1.312
     */
    public int copyRecursiveTo(FilePath target) throws IOException, InterruptedException {
        return copyRecursiveTo("**/*", target);
    }

    /**
     * Copies the files that match the given file mask to the specified target node.
     *
     * @param fileMask
     *      Ant GLOB pattern.
     *      String like "foo/bar/*.xml" Multiple patterns can be separated
     *      by ',', and whitespace can surround ',' so that you can write
     *      "abc, def" and "abc,def" to mean the same thing.
     * @return
     *      the number of files copied.
     */
    public int copyRecursiveTo(String fileMask, FilePath target) throws IOException, InterruptedException {
        return copyRecursiveTo(fileMask, null, target);
    }

    /**
     * Copies the files that match the given file mask to the specified target node.
     *
     * @param fileMask
     *      Ant GLOB pattern.
     *      String like "foo/bar/*.xml" Multiple patterns can be separated
     *      by ',', and whitespace can surround ',' so that you can write
     *      "abc, def" and "abc,def" to mean the same thing.
     * @param excludes
     *      Files to be excluded. Can be null.
     * @return
     *      the number of files copied.
     */
    public int copyRecursiveTo(final String fileMask, final String excludes, final FilePath target) throws IOException, InterruptedException {
        return copyRecursiveTo(new DirScanner.Glob(fileMask, excludes), target, fileMask);
    }

    /**
     * Copies files according to a specified scanner to a target node.
     * @param scanner a way of enumerating some files (must be serializable for possible delivery to remote side)
     * @param target the destination basedir
     * @param description a description of the fileset, for logging purposes
     * @return the number of files copied
     * @since 1.532
     */
    public int copyRecursiveTo(final DirScanner scanner, final FilePath target, final String description) throws IOException, InterruptedException {
        return copyRecursiveTo(scanner, target, description, TarCompression.GZIP);
    }

    /**
     * Copies files according to a specified scanner to a target node.
     * @param scanner a way of enumerating some files (must be serializable for possible delivery to remote side)
     * @param target the destination basedir
     * @param description a description of the fileset, for logging purposes
     * @param compression compression to use
     * @return the number of files copied
     * @since 2.196
     */
    public int copyRecursiveTo(final DirScanner scanner, final FilePath target, final String description, @NonNull TarCompression compression) throws IOException, InterruptedException {
        if (this.channel == target.channel) {
            // local to local copy.
            return act(new CopyRecursiveLocal(target, scanner));
        } else
        if (this.channel == null) {
            // local -> remote copy
            final Pipe pipe = Pipe.createLocalToRemote();

            Future<Void> future = target.actAsync(new ReadFromTar(target, pipe, description, compression, StandardCharsets.UTF_8));
            Future<Integer> future2 = actAsync(new WriteToTar(scanner, pipe, compression, StandardCharsets.UTF_8));
            try {
                // JENKINS-9540 in case the reading side failed, report that error first
                future.get();
                return future2.get();
            } catch (ExecutionException e) {
                throw ioWithCause(e);
            }
        } else {
            // remote -> local copy
            final Pipe pipe = Pipe.createRemoteToLocal();

            Future<Integer> future = actAsync(new CopyRecursiveRemoteToLocal(pipe, scanner, compression, StandardCharsets.UTF_8));
            try {
                readFromTar(remote + '/' + description, new File(target.remote), compression.extract(pipe.getIn()), StandardCharsets.UTF_8);
            } catch (IOException e) { // BuildException or IOException
                try {
                    future.get(3, TimeUnit.SECONDS);
                    throw e;    // the remote side completed successfully, so the error must be local
                } catch (ExecutionException x) {
                    // report both errors
                    e.addSuppressed(x);
                    throw e;
                } catch (TimeoutException ignored) {
                    // remote is hanging, just throw the original exception
                    throw e;
                }
            }
            try {
                return future.get();
            } catch (ExecutionException e) {
                throw ioWithCause(e);
            }
        }
    }

    private IOException ioWithCause(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause == null) cause = e;
        return cause instanceof IOException
                ? (IOException) cause
                : new IOException(cause)
                ;
    }

    private static class CopyRecursiveLocal extends MasterToSlaveFileCallable<Integer> {
        private final FilePath target;
        private final DirScanner scanner;

        CopyRecursiveLocal(FilePath target, DirScanner scanner) {
            this.target = target;
            this.scanner = scanner;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Integer invoke(File base, VirtualChannel channel) throws IOException {
            if (!base.exists()) {
                return 0;
            }
            if (target.channel != null) {
                throw new IllegalStateException("Expected null channel for " + target);
            }
            final File dest = new File(target.remote);
            final AtomicInteger count = new AtomicInteger();
            scanner.scan(base, new FileVisitor() {
                private boolean exceptionEncountered;
                private boolean logMessageShown;
                @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "TODO needs triage")
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    if (f.isFile()) {
                        File target = new File(dest, relativePath);
                        mkdirsE(target.getParentFile());
                        Path targetPath = fileToPath(target);
                        exceptionEncountered = exceptionEncountered || !tryCopyWithAttributes(f, targetPath);
                        if (exceptionEncountered) {
                            Files.copy(fileToPath(f), targetPath, StandardCopyOption.REPLACE_EXISTING);
                            if (!logMessageShown) {
                                LOGGER.log(Level.INFO,
                                    "JENKINS-52325: Jenkins failed to retain attributes when copying to {0}, so proceeding without attributes.",
                                    dest.getAbsolutePath());
                                logMessageShown = true;
                            }
                        }
                        count.incrementAndGet();
                    }
                }

                private boolean tryCopyWithAttributes(File f, Path targetPath) {
                    try {
                        Files.copy(fileToPath(f), targetPath,
                            StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Unable to copy: {0}", e.getMessage());
                        return false;
                    }
                    return true;
                }

                @Override
                public boolean understandsSymlink() {
                    return true;
                }

                @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "TODO needs triage")
                @Override
                public void visitSymlink(File link, String target, String relativePath) throws IOException {
                    try {
                        mkdirsE(new File(dest, relativePath).getParentFile());
                        Util.createSymlink(dest, target, relativePath, TaskListener.NULL);
                    } catch (InterruptedException x) {
                        throw new IOException(x);
                    }
                    count.incrementAndGet();
                }
            });
            return count.get();
        }
    }

    private static class ReadFromTar extends MasterToSlaveFileCallable<Void> {
        private final Pipe pipe;
        private final String description;
        private final TarCompression compression;
        private final FilePath target;
        private final String filenamesEncoding;

        ReadFromTar(FilePath target, Pipe pipe, String description, @NonNull TarCompression compression, Charset filenamesEncoding) {
            this.target = target;
            this.pipe = pipe;
            this.description = description;
            this.compression = compression;
            this.filenamesEncoding = filenamesEncoding.name();
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            try (InputStream in = pipe.getIn()) {
                readFromTar(target.remote + '/' + description, f, compression.extract(in), Charset.forName(filenamesEncoding));
                return null;
            }
        }
    }

    private static class WriteToTar extends MasterToSlaveFileCallable<Integer> {
        private final DirScanner scanner;
        private final Pipe pipe;
        private final TarCompression compression;
        private final String filenamesEncoding;

        WriteToTar(DirScanner scanner, Pipe pipe, @NonNull TarCompression compression, Charset filenamesEncoding) {
            this.scanner = scanner;
            this.pipe = pipe;
            this.compression = compression;
            this.filenamesEncoding = filenamesEncoding.name();
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Integer invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return writeToTar(f, scanner, compression.compress(pipe.getOut()), Charset.forName(filenamesEncoding));
        }
    }

    private static class CopyRecursiveRemoteToLocal extends MasterToSlaveFileCallable<Integer> {
        private static final long serialVersionUID = 1L;
        private final Pipe pipe;
        private final DirScanner scanner;
        private final TarCompression compression;
        private final String filenamesEncoding;

        CopyRecursiveRemoteToLocal(Pipe pipe, DirScanner scanner, @NonNull TarCompression compression, Charset filenamesEncoding) {
            this.pipe = pipe;
            this.scanner = scanner;
            this.compression = compression;
            this.filenamesEncoding = filenamesEncoding.name();
        }

        @Override
        public Integer invoke(File f, VirtualChannel channel) throws IOException {
            try (OutputStream out = pipe.getOut()) {
                return writeToTar(f, scanner, compression.compress(out), Charset.forName(filenamesEncoding));
            }
        }
    }

    /**
     * Writes files in 'this' directory to a tar stream.
     *
     * @param glob
     *      Ant file pattern mask, like "**&#x2F;*.java".
     */
    public int tar(OutputStream out, final String glob) throws IOException, InterruptedException {
        return archive(ArchiverFactory.TAR, out, glob);
    }

    public int tar(OutputStream out, FileFilter filter) throws IOException, InterruptedException {
        return archive(ArchiverFactory.TAR, out, filter);
    }

    /**
     * Uses the given scanner on 'this' directory to list up files and then archive it to a tar stream.
     */
    public int tar(OutputStream out, DirScanner scanner) throws IOException, InterruptedException {
        return archive(ArchiverFactory.TAR, out, scanner);
    }

    /**
     * Writes to a tar stream and stores obtained files to the base dir.
     *
     * @return
     *      number of files/directories that are written.
     */
    private static Integer writeToTar(File baseDir, DirScanner scanner, OutputStream out, Charset filenamesEncoding) throws IOException {
        Archiver tw = ArchiverFactory.TAR.create(out, filenamesEncoding);
        try (tw) {
            scanner.scan(baseDir, tw);
        }
        return tw.countEntries();
    }

    private static void readFromTar(String name, File baseDir, InputStream in) throws IOException {
        readFromTar(name, baseDir, in, Charset.defaultCharset());
    }

    /**
     * Reads from a tar stream and stores obtained files to the base dir.
     * Supports large files &gt; 10 GB since 1.627.
     */
    private static void readFromTar(String name, File baseDir, InputStream in, Charset filenamesEncoding) throws IOException {

        try (TarInputStream t = new TarInputStream(in, filenamesEncoding.name())) {
            TarEntry te;
            while ((te = t.getNextEntry()) != null) {
                File f = new File(baseDir, te.getName());
                if (!f.toPath().normalize().startsWith(baseDir.toPath())) {
                    throw new IOException(
                            "Tar " + name + " contains illegal file name that breaks out of the target directory: " + te.getName());
                }
                if (te.isDirectory()) {
                    mkdirs(f);
                } else {
                    File parent = f.getParentFile();
                    if (parent != null) mkdirs(parent);

                    if (te.isSymbolicLink()) {
                        new FilePath(f).symlinkTo(te.getLinkName(), TaskListener.NULL);
                    } else {
                        IOUtils.copy(t, f);

                        Files.setLastModifiedTime(Util.fileToPath(f), FileTime.from(te.getModTime().toInstant()));
                        int mode = te.getMode() & 0777;
                        if (mode != 0 && !Functions.isWindows()) // be defensive
                            _chmod(f, mode);
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException("Failed to extract " + name, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // process this later
            throw new IOException("Failed to extract " + name, e);
        }
    }

    /**
     * Creates a {@link Launcher} for starting processes on the node
     * that has this file.
     * @since 1.89
     */
    public Launcher createLauncher(TaskListener listener) throws IOException, InterruptedException {
        if (channel == null)
            return new LocalLauncher(listener);
        else
            return new RemoteLauncher(listener, channel, channel.call(new IsUnix()));
    }

    private static final class IsUnix extends MasterToSlaveCallable<Boolean, IOException> {
        @Override
        @NonNull
        public Boolean call() throws IOException {
            return File.pathSeparatorChar == ':';
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Same as {@link #validateAntFileMask(String, int)} with (practically) unbounded number of operations.
     *
     * @return
     *      null if no error was found. Otherwise, returns a human-readable error message.
     * @since 1.90
     * @see #validateFileMask(FilePath, String)
     * @deprecated use {@link #validateAntFileMask(String, int)} instead
     */
    @Deprecated
    public String validateAntFileMask(final String fileMasks) throws IOException, InterruptedException {
        return validateAntFileMask(fileMasks, Integer.MAX_VALUE);
    }

    /**
     * Same as {@link #validateAntFileMask(String, int, boolean)} with caseSensitive set to true.
     */
    public String validateAntFileMask(final String fileMasks, final int bound) throws IOException, InterruptedException {
        return validateAntFileMask(fileMasks, bound, true);
    }

    /**
     * Same as {@link #validateAntFileMask(String, int, boolean)} with the default number of operations.
     * @see #VALIDATE_ANT_FILE_MASK_BOUND
     * @since 2.325
     */
    public String validateAntFileMask(final String fileMasks, final boolean caseSensitive) throws IOException, InterruptedException {
        return validateAntFileMask(fileMasks, VALIDATE_ANT_FILE_MASK_BOUND, caseSensitive);
    }

    /**
     * Default bound for {@link #validateAntFileMask(String, int, boolean)}.
     * @since 1.592
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static int VALIDATE_ANT_FILE_MASK_BOUND = SystemProperties.getInteger(FilePath.class.getName() + ".VALIDATE_ANT_FILE_MASK_BOUND", 10000);

    /**
     * A dedicated subtype of {@link InterruptedException} for when no matching Ant file mask
     * matches are found.
     *
     * @see ArtifactArchiver
     */
    @Restricted(NoExternalUse.class)
    public static class FileMaskNoMatchesFoundException extends InterruptedException {
        private FileMaskNoMatchesFoundException(String message) {
            super(message);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Validates the ant file mask (like "foo/bar/*.txt, zot/*.jar") against this directory, and try to point out the problem.
     * This performs only a bounded number of operations.
     *
     * <p>Whereas the unbounded overload is appropriate for calling from cancelable, long-running tasks such as build steps,
     * this overload should be used when an answer is needed quickly, such as for {@link #validateFileMask(String)}
     * or anything else returning {@link FormValidation}.
     *
     * <p>If a positive match is found, {@code null} is returned immediately.
     * A message is returned in case the file pattern can definitely be determined to not match anything in the directory within the alloted time.
     * If the time runs out without finding a match but without ruling out the possibility that there might be one, {@link InterruptedException} is thrown,
     * in which case the calling code should give the user the benefit of the doubt and use {@link hudson.util.FormValidation.Kind#OK} (with or without a message).
     *
     * <p>While this can be used in conjunction with {@link FormValidation}, it's generally better to use {@link #validateFileMask(String)} and
     * its overloads for use in {@code doCheck} form validation methods related to workspaces, as that performs an appropriate permission check.
     * Callers of this method or its overloads from web methods should ensure permissions are checked before this method is invoked.
     *
     * @param bound a maximum number of negative operations (deliberately left vague) to perform before giving up on a precise answer; try {@link #VALIDATE_ANT_FILE_MASK_BOUND}
     * @throws InterruptedException not only in case of a channel failure, but also if too many operations were performed without finding any matches
     * @since 1.484
     */
    public @CheckForNull String validateAntFileMask(final String fileMasks, final int bound, final boolean caseSensitive) throws IOException, InterruptedException {
        return act(new ValidateAntFileMask(fileMasks, caseSensitive, bound));
    }

    private static class ValidateAntFileMask extends MasterToSlaveFileCallable<String> {
        private final String fileMasks;
        private final boolean caseSensitive;
        private final int bound;

        ValidateAntFileMask(String fileMasks, boolean caseSensitive, int bound) {
            this.fileMasks = fileMasks;
            this.caseSensitive = caseSensitive;
            this.bound = bound;
        }

            private static final long serialVersionUID = 1;

            @Override
            public String invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                if (fileMasks.startsWith("~"))
                    return Messages.FilePath_TildaDoesntWork();

                StringTokenizer tokens = new StringTokenizer(fileMasks, ",");

                while (tokens.hasMoreTokens()) {
                    final String fileMask = tokens.nextToken().trim();
                    if (hasMatch(dir, fileMask, caseSensitive))
                        continue;   // no error on this portion

                    // JENKINS-5253 - if we can get some match in case-insensitive mode
                    // and user requested case-sensitive match, notify the user
                    if (caseSensitive && hasMatch(dir, fileMask, false)) {
                        return Messages.FilePath_validateAntFileMask_matchWithCaseInsensitive(fileMask);
                    }

                    // in 1.172 we introduced an incompatible change to stop using ' ' as the separator
                    // so see if we can match by using ' ' as the separator
                    if (fileMask.contains(" ")) {
                        boolean matched = true;
                        for (String token : Util.tokenize(fileMask))
                            matched &= hasMatch(dir, token, caseSensitive);
                        if (matched)
                            return Messages.FilePath_validateAntFileMask_whitespaceSeparator();
                    }

                    // a common mistake is to assume the wrong base dir, and there are two variations
                    // to this: (1) the user gave us aa/bb/cc/dd where cc/dd was correct
                    // and (2) the user gave us cc/dd where aa/bb/cc/dd was correct.

                    { // check the (1) above first
                        String f = fileMask;
                        while (true) {
                            int idx = findSeparator(f);
                            if (idx == -1)     break;
                            f = f.substring(idx + 1);

                            if (hasMatch(dir, f, caseSensitive))
                                return Messages.FilePath_validateAntFileMask_doesntMatchAndSuggest(fileMask, f);
                        }
                    }

                    { // check the (2) above next as this is more expensive.
                        // Try prepending "**/" to see if that results in a match
                        FileSet fs = Util.createFileSet(dir, "**/" + fileMask);
                        fs.setCaseSensitive(caseSensitive);
                        DirectoryScanner ds = fs.getDirectoryScanner(new Project());
                        if (ds.getIncludedFilesCount() != 0) {
                            // try shorter name first so that the suggestion results in the least amount of changes
                            String[] names = ds.getIncludedFiles();
                            Arrays.sort(names, SHORTER_STRING_FIRST);
                            for (String f : names) {
                                // now we want to decompose f to the leading portion that matched "**"
                                // and the trailing portion that matched the file mask, so that
                                // we can suggest the user error.
                                //
                                // this is not a very efficient/clever way to do it, but it's relatively simple

                                StringBuilder prefix = new StringBuilder();
                                while (true) {
                                    int idx = findSeparator(f);
                                    if (idx == -1)     break;

                                    prefix.append(f, 0, idx).append('/');
                                    f = f.substring(idx + 1);
                                    if (hasMatch(dir, prefix + fileMask, caseSensitive))
                                        return Messages.FilePath_validateAntFileMask_doesntMatchAndSuggest(fileMask, prefix + fileMask);
                                }
                            }
                        }
                    }

                    { // finally, see if we can identify any sub portion that's valid. Otherwise, bail out
                        String previous = null;
                        String pattern = fileMask;

                        while (true) {
                            if (hasMatch(dir, pattern, caseSensitive)) {
                                // found a match
                                if (previous == null)
                                    return Messages.FilePath_validateAntFileMask_portionMatchAndSuggest(fileMask, pattern);
                                else
                                    return Messages.FilePath_validateAntFileMask_portionMatchButPreviousNotMatchAndSuggest(fileMask, pattern, previous);
                            }

                            int idx = findSeparator(pattern);
                            if (idx < 0) { // no more path component left to go back
                                if (pattern.equals(fileMask))
                                    return Messages.FilePath_validateAntFileMask_doesntMatchAnything(fileMask);
                                else
                                    return Messages.FilePath_validateAntFileMask_doesntMatchAnythingAndSuggest(fileMask, pattern);
                            }

                            // cut off the trailing component and try again
                            previous = pattern;
                            pattern = pattern.substring(0, idx);
                        }
                    }
                }

                return null; // no error
            }

            private boolean hasMatch(File dir, String pattern, boolean bCaseSensitive) throws InterruptedException {
                class Cancel extends RuntimeException {}

                DirectoryScanner ds = bound == Integer.MAX_VALUE ? new DirectoryScanner() : new DirectoryScanner() {
                    int ticks;
                    long start = System.currentTimeMillis();
                    @Override public synchronized boolean isCaseSensitive() {
                        if (!filesIncluded.isEmpty() || !dirsIncluded.isEmpty() || ticks++ > bound || System.currentTimeMillis() - start > 5000) {
                            throw new Cancel();
                        }
                        filesNotIncluded.clear();
                        dirsNotIncluded.clear();
                        // notFollowedSymlinks might be large, but probably unusual
                        // scannedDirs will typically be largish, but seems to be needed
                        return super.isCaseSensitive();
                    }
                };
                ds.setBasedir(dir);
                ds.setIncludes(new String[] {pattern});
                ds.setCaseSensitive(bCaseSensitive);
                try {
                    ds.scan();
                } catch (Cancel c) {
                    if (ds.getIncludedFilesCount() != 0 || ds.getIncludedDirsCount() != 0) {
                        return true;
                    } else {
                        throw (FileMaskNoMatchesFoundException) new FileMaskNoMatchesFoundException("no matches found within " + bound).initCause(c);
                    }
                }
                return ds.getIncludedFilesCount() != 0 || ds.getIncludedDirsCount() != 0;
            }

            /**
             * Finds the position of the first path separator.
             */
            private int findSeparator(String pattern) {
                int idx1 = pattern.indexOf('\\');
                int idx2 = pattern.indexOf('/');
                if (idx1 == -1)    return idx2;
                if (idx2 == -1)    return idx1;
                return Math.min(idx1, idx2);
            }
    }

    private static final UrlFactory DEFAULT_URL_FACTORY = new UrlFactory();

    @Restricted(NoExternalUse.class)
    static class UrlFactory {
        public URL newURL(String location) throws MalformedURLException {
            return new URL(location);
        }
    }

    private UrlFactory urlFactory;

    @VisibleForTesting
    @Restricted(NoExternalUse.class)
    void setUrlFactory(UrlFactory urlFactory) {
        this.urlFactory = urlFactory;
    }

    private UrlFactory getUrlFactory() {
        if (urlFactory != null) {
            return urlFactory;
        } else {
            return DEFAULT_URL_FACTORY;
        }
    }

    /**
     * Short for {@code validateFileMask(path, value, true)}
     */
    public static FormValidation validateFileMask(@CheckForNull FilePath path, String value) throws IOException {
        return FilePath.validateFileMask(path, value, true);
    }

    /**
     * Shortcut for {@link #validateFileMask(String,boolean,boolean)} with {@code errorIfNotExist} true, as the left-hand side can be null.
     */
    public static FormValidation validateFileMask(@CheckForNull FilePath path, String value, boolean caseSensitive) throws IOException {
        if (path == null) return FormValidation.ok();
        return path.validateFileMask(value, true, caseSensitive);
    }

    /**
     * Short for {@code validateFileMask(value, true, true)}
     */
    public FormValidation validateFileMask(String value) throws IOException {
        return validateFileMask(value, true, true);
    }

    /**
     * Short for {@code validateFileMask(value, errorIfNotExist, true)}
     */
    public FormValidation validateFileMask(String value, boolean errorIfNotExist) throws IOException {
        return validateFileMask(value, errorIfNotExist, true);
    }

    /**
     * Checks the GLOB-style file mask. See {@link #validateAntFileMask(String)}.
     * Requires configure permission on ancestor {@link AbstractProject} object in request,
     * or {@link Jenkins#MANAGE} permission if no such ancestor is found.
     *
     * <p>Note that this permission check may not always make sense based on the directory provided;
     * callers should consider using {@link #validateFileMask(FilePath, String, boolean)} and its overloads instead
     * (once appropriate permission checks have succeeded).
     *
     * @since 1.294
     */
    public FormValidation validateFileMask(String value, boolean errorIfNotExist, boolean caseSensitive) throws IOException {
        checkPermissionForValidate();

        value = fixEmpty(value);
        if (value == null)
            return FormValidation.ok();

        try {
            if (!exists()) // no workspace. can't check
                return FormValidation.ok();

            String msg = validateAntFileMask(value, VALIDATE_ANT_FILE_MASK_BOUND, caseSensitive);
            if (errorIfNotExist)     return FormValidation.error(msg);
            else                    return FormValidation.warning(msg);
        } catch (InterruptedException e) {
            return FormValidation.ok(Messages.FilePath_did_not_manage_to_validate_may_be_too_sl(value));
        }
    }

    /**
     * Validates a relative file path from this {@link FilePath}.
     * Requires configure permission on ancestor {@link AbstractProject} object in request,
     * or {@link Jenkins#MANAGE} permission if no such ancestor is found.
     *
     * <p>Note that this permission check may not always make sense based on the directory provided;
     * callers should consider using {@link #validateFileMask(FilePath, String, boolean)} and its overloads instead
     * (once appropriate permission checks have succeeded).
     *
     * @param value
     *      The relative path being validated.
     * @param errorIfNotExist
     *      If true, report an error if the given relative path doesn't exist. Otherwise, it's a warning.
     * @param expectingFile
     *      If true, we expect the relative path to point to a file.
     *      Otherwise, the relative path is expected to be pointing to a directory.
     */
    public FormValidation validateRelativePath(String value, boolean errorIfNotExist, boolean expectingFile) throws IOException {
        checkPermissionForValidate();

        value = fixEmpty(value);

        // none entered yet, or something is seriously wrong
        if (value == null) return FormValidation.ok();

        // a common mistake is to use wildcard
        if (value.contains("*")) return FormValidation.error(Messages.FilePath_validateRelativePath_wildcardNotAllowed());

        try {
            if (!exists())    // no base directory. can't check
                return FormValidation.ok();

            FilePath path = child(value);
            if (path.exists()) {
                if (expectingFile) {
                    if (!path.isDirectory())
                        return FormValidation.ok();
                    else
                        return FormValidation.error(Messages.FilePath_validateRelativePath_notFile(value));
                } else {
                    if (path.isDirectory())
                        return FormValidation.ok();
                    else
                        return FormValidation.error(Messages.FilePath_validateRelativePath_notDirectory(value));
                }
            }

            String msg = expectingFile ? Messages.FilePath_validateRelativePath_noSuchFile(value) :
                Messages.FilePath_validateRelativePath_noSuchDirectory(value);
            if (errorIfNotExist)     return FormValidation.error(msg);
            else                    return FormValidation.warning(msg);
        } catch (InterruptedException e) {
            return FormValidation.ok();
        }
    }

    private static void checkPermissionForValidate() {
        AccessControlled subject = Stapler.getCurrentRequest2().findAncestorObject(AbstractProject.class);
        if (subject == null)
            Jenkins.get().checkPermission(Jenkins.MANAGE);
        else
            subject.checkPermission(Item.CONFIGURE);
    }

    /**
     * A convenience method over {@link #validateRelativePath(String, boolean, boolean)}.
     */
    public FormValidation validateRelativeDirectory(String value, boolean errorIfNotExist) throws IOException {
        return validateRelativePath(value, errorIfNotExist, false);
    }

    public FormValidation validateRelativeDirectory(String value) throws IOException {
        return validateRelativeDirectory(value, true);
    }

    @Deprecated @Override
    public String toString() {
        // to make writing JSPs easily, return local
        return remote;
    }

    public VirtualChannel getChannel() {
        if (channel != null)   return channel;
        else                return localChannel;
    }

    /**
     * Returns true if this {@link FilePath} represents a remote file.
     */
    public boolean isRemote() {
        return channel != null;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        Channel target = _getChannelForSerialization();
        if (channel != null && channel != target) {
            throw new IllegalStateException("Can't send a remote FilePath to a different remote channel (current=" + channel + ", target=" + target + ")");
        }

        oos.defaultWriteObject();
        oos.writeBoolean(channel == null);
    }

    private Channel _getChannelForSerialization() {
        try {
            return getChannelForSerialization();
        } catch (NotSerializableException x) {
            LOGGER.log(Level.WARNING, "A FilePath object is being serialized when it should not be, indicating a bug in a plugin. See https://www.jenkins.io/redirect/filepath-serialization for details.", x);
            return null;
        }
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        Channel channel = _getChannelForSerialization();

        ois.defaultReadObject();
        if (ois.readBoolean()) {
            this.channel = channel;
        } else {
            this.channel = null;
            // If the remote channel wants us to create a FilePath that points to a local file,
            // we need to make sure the access control takes place.
            // Any FileCallables acting on a deserialized FilePath need to ensure they're subjecting it to
            // access control checks like #reading(File) etc.
        }
    }

    private static final long serialVersionUID = 1L;

    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.328")
    public static final int SIDE_BUFFER_SIZE = 1024;

    private static final Logger LOGGER = Logger.getLogger(FilePath.class.getName());

    /**
     * Adapts {@link FileCallable} to {@link Callable}.
     */
    private static class FileCallableWrapper<T> implements DelegatingCallable<T, IOException> {
        private final FileCallable<T> callable;
        private transient ClassLoader classLoader;
        private final FilePath filePath;

        FileCallableWrapper(FileCallable<T> callable, FilePath filePath) {
            this.callable = callable;
            this.classLoader = callable.getClass().getClassLoader();
            this.filePath = filePath;
        }

        private FileCallableWrapper(FileCallable<T> callable, ClassLoader classLoader, FilePath filePath) {
            this.callable = callable;
            this.classLoader = classLoader;
            this.filePath = filePath;
        }

        @Override
        public T call() throws IOException {
            try {
                return callable.invoke(new File(filePath.remote), filePath.channel);
            } catch (InterruptedException e) {
                throw new TunneledInterruptedException(e);
            }
        }

        /**
         * Role check comes from {@link FileCallable}s.
         */
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            callable.checkRoles(checker);
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public String toString() {
            return callable.toString();
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Used to tunnel {@link InterruptedException} over a Java signature that only allows {@link IOException}
     */
    private static class TunneledInterruptedException extends IOException {
        private TunneledInterruptedException(InterruptedException cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final Comparator<String> SHORTER_STRING_FIRST = Comparator.comparingInt(String::length);

    /**
     * Gets the {@link FilePath} representation of the "~" directory
     * (User's home directory in the Unix sense) of the given channel.
     */
    public static FilePath getHomeDirectory(VirtualChannel ch) throws InterruptedException, IOException {
        return ch.call(new GetHomeDirectory());
    }

    private static class GetHomeDirectory extends MasterToSlaveCallable<FilePath, IOException> {
        @Override
        public FilePath call() throws IOException {
            return new FilePath(new File(System.getProperty("user.home")));
        }
    }

    /**
     * Helper class to make it easy to send an explicit list of files using {@link FilePath} methods.
     * @since 1.532
     */
    public static final class ExplicitlySpecifiedDirScanner extends DirScanner {

        private static final long serialVersionUID = 1;

        private final Map<String, String> files;

        /**
         * Create a “scanner” (it actually does no scanning).
         * @param files a map from logical relative paths as per {@link FileVisitor#visit}, to actual relative paths within the scanned directory
         */
        public ExplicitlySpecifiedDirScanner(Map<String, String> files) {
            this.files = files;
        }

        @Override public void scan(File dir, FileVisitor visitor) throws IOException {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String archivedPath = entry.getKey();
                assert archivedPath.indexOf('\\') == -1;
                String workspacePath = entry.getValue();
                assert workspacePath.indexOf('\\') == -1;
                scanSingle(new File(dir, workspacePath), archivedPath, visitor);
            }
        }
    }

    private static final ExecutorService threadPoolForRemoting = new ContextResettingExecutorService(
            Executors.newCachedThreadPool(
                    new ExceptionCatchingThreadFactory(
                            new NamingThreadFactory(new DaemonThreadFactory(), "FilePath.localPool"))
            ));


    /**
     * Channel to the current instance.
     */
    @NonNull
    public static final LocalChannel localChannel = new LocalChannel(threadPoolForRemoting);

    /**
     * Wraps {@link FileVisitor} to ignore symlinks.
     */
    public static FileVisitor ignoringSymlinks(final FileVisitor v, String verificationRoot, OpenOption... openOptions) {
        return validatingVisitor(FilePath::isNoFollowLink,
                visitorInfo -> !isSymlink(visitorInfo),
                v, verificationRoot, openOptions);
    }

    /**
     * Wraps {@link FileVisitor} to ignore tmp directories.
     */
    public static FileVisitor ignoringTmpDirs(final FileVisitor v, String verificationRoot, OpenOption... openOptions) {
        return validatingVisitor(FilePath::isIgnoreTmpDirs,
                visitorInfo -> !isTmpDir(visitorInfo),
                v, verificationRoot, openOptions);
    }

    private static FileVisitor validatingVisitor(Predicate<OpenOption[]> gater, Predicate<VisitorInfo> matcher,
                                                 final FileVisitor v, String verificationRoot, OpenOption... openOptions) {
        if (gater.test(openOptions)) {
            return new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    VisitorInfo visitorInfo = new VisitorInfo(f, verificationRoot, openOptions);
                    if (verificationRoot == null || matcher.test(visitorInfo)) {
                        v.visit(f, relativePath);
                    }
                }

                @Override
                public boolean understandsSymlink() {
                    return false;
                }
            };
        }
        return v;
    }

    private static boolean mkdirs(File dir) throws IOException {
        if (dir.exists())   return false;
        Files.createDirectories(fileToPath(dir));
        return true;
    }

    private static File mkdirsE(File dir) throws IOException {
        if (dir.exists()) {
            return dir;
        }
        return IOUtils.mkdirs(dir);
    }

    /**
     * Check if the relative child is really a descendant after symlink resolution if any.
     */
    public boolean isDescendant(@NonNull String potentialChildRelativePath) throws IOException, InterruptedException {
        return act(new IsDescendant(potentialChildRelativePath));
    }

    private static class IsDescendant extends MasterToSlaveFileCallable<Boolean> {
        private static final long serialVersionUID = 1L;
        private String potentialChildRelativePath;

        private IsDescendant(@NonNull String potentialChildRelativePath) {
            this.potentialChildRelativePath = potentialChildRelativePath;
        }

        @Override
        public Boolean invoke(@NonNull File parentFile, @NonNull VirtualChannel channel) throws IOException, InterruptedException {
            if (new File(potentialChildRelativePath).isAbsolute()) {
                throw new IllegalArgumentException("Only a relative path is supported, the given path is absolute: " + potentialChildRelativePath);
            }

            Path parentAbsolutePath = Util.fileToPath(parentFile.getAbsoluteFile());
            Path parentRealPath;
            try {
                if (Functions.isWindows()) {
                    parentRealPath = this.windowsToRealPath(parentAbsolutePath);
                } else {
                    parentRealPath = parentAbsolutePath.toRealPath();
                }
            }
            catch (NoSuchFileException e) {
                LOGGER.log(Level.FINE, String.format("Cannot find the real path to the parentFile: %s", parentAbsolutePath), e);
                return false;
            }

            // example: "a/b/c" that will become "b/c" then just "c", and finally an empty string
            String remainingPath = potentialChildRelativePath;

            Path currentFilePath = parentFile.toPath();
            while (!remainingPath.isEmpty()) {
                Path directChild = this.getDirectChild(currentFilePath, remainingPath);
                Path childUsingFullPath = currentFilePath.resolve(remainingPath);
                String childUsingFullPathAbs = childUsingFullPath.toAbsolutePath().toString();
                String directChildAbs = directChild.toAbsolutePath().toString();

                if (childUsingFullPathAbs.length() == directChildAbs.length()) {
                    remainingPath = "";
                } else {
                    // +1 to avoid the last slash
                    remainingPath = childUsingFullPathAbs.substring(directChildAbs.length() + 1);
                }

                File childFileSymbolic = Util.resolveSymlinkToFile(directChild.toFile());
                if (childFileSymbolic == null) {
                    currentFilePath = directChild;
                } else {
                    currentFilePath = childFileSymbolic.toPath();
                }

                Path currentFileAbsolutePath = currentFilePath.toAbsolutePath();
                try {
                    Path child = currentFileAbsolutePath.toRealPath();
                    if (!child.startsWith(parentRealPath)) {
                        LOGGER.log(Level.FINE, "Child [{0}] does not start with parent [{1}] => not descendant", new Object[]{ child, parentRealPath });
                        return false;
                    }
                } catch (NoSuchFileException e) {
                    // nonexistent file / Windows Server 2016 + MSFT docker
                    // in case this folder / file will be copied somewhere else,
                    // it becomes the responsibility of that system to check the isDescendant with the existing links
                    // we are not taking the parentRealPath to avoid possible problem
                    Path child = currentFileAbsolutePath.normalize();
                    Path parent = parentAbsolutePath.normalize();
                    return child.startsWith(parent);
                } catch (FileSystemException e) {
                    LOGGER.log(Level.WARNING, String.format("Problem during call to the method toRealPath on %s", currentFileAbsolutePath), e);
                    return false;
                }
            }

            return true;
        }

        private @CheckForNull Path getDirectChild(Path parentPath, String childPath) {
            Path current = parentPath.resolve(childPath);
            while (current != null && !parentPath.equals(current.getParent())) {
                current = current.getParent();
            }
            return current;
        }

        private @NonNull Path windowsToRealPath(@NonNull Path path) throws IOException {
            try {
                return path.toRealPath();
            }
            catch (IOException e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, String.format("relaxedToRealPath cannot use the regular toRealPath on %s, trying with toRealPath(LinkOption.NOFOLLOW_LINKS)", path), e);
                }
            }

            // that's required for specific environment like Windows Server 2016, running MSFT docker
            // where the root is a <SYMLINKD>
            return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static Path getRealPath(Path path) throws IOException {
        return Functions.isWindows() ? windowsToRealPath(path) : path.toRealPath();
    }

    private static @NonNull Path windowsToRealPath(@NonNull Path path) throws IOException {
        try {
            return path.toRealPath();
        }
        catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, String.format("relaxedToRealPath cannot use the regular toRealPath on %s, trying with toRealPath(LinkOption.NOFOLLOW_LINKS)", path), e);
            }
        }

        // that's required for specific environment like Windows Server 2016, running MSFT docker
        // where the root is a <SYMLINKD>
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static class OptionalDiscardingFileFilter implements FileFilter, Serializable {

        private final String verificationRoot;
        private OpenOption[] openOptions;

        OptionalDiscardingFileFilter(FilePath verificationRoot, OpenOption... openOptions) {
            this.verificationRoot = verificationRoot == null ? null : verificationRoot.remote;
            this.openOptions = openOptions;
        }

        @Override
        public boolean accept(File file) {
            return !isSymlink(file, verificationRoot, openOptions) && !isTmpDir(file, verificationRoot, openOptions);
        }

        private static final long serialVersionUID = 1L;
    }

    private static class VisitorInfo {
        File f;
        String verificationRoot;
        OpenOption[] openOptions;

        VisitorInfo(File f, String verificationRoot, OpenOption[] openOptions) {
            this.f = f;
            this.verificationRoot = verificationRoot;
            this.openOptions = openOptions;
        }

    }
}
