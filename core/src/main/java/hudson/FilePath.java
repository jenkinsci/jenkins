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

import com.jcraft.jzlib.GZIPInputStream;
import com.jcraft.jzlib.GZIPOutputStream;
import hudson.Launcher.LocalLauncher;
import hudson.Launcher.RemoteLauncher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.org.apache.tools.tar.TarInputStream;
import hudson.os.PosixAPI;
import hudson.os.PosixException;
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
import hudson.util.DaemonThreadFactory;
import hudson.util.DirScanner;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import hudson.util.HeadBufferingStream;
import hudson.util.IOUtils;
import hudson.util.NamingThreadFactory;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import jenkins.FilePathFilter;
import jenkins.MasterToSlaveFileCallable;
import jenkins.SlaveToMasterFileCallable;
import jenkins.SoloFilePathFilter;
import jenkins.model.Jenkins;
import jenkins.util.ContextResettingExecutorService;
import jenkins.util.VirtualFile;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.kohsuke.stapler.Stapler;

import javax.annotation.CheckForNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hudson.FilePath.TarCompression.*;
import static hudson.Util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;
        
/**
 * {@link File} like object with remoting support.
 *
 * <p>
 * Unlike {@link File}, which always implies a file path on the current computer,
 * {@link FilePath} represents a file path on a specific slave or the master.
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
 * the file is located, as opposed to send the whole data to the master and do MD5
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
 * private static final class Freshen implements FileCallable&lt;Void> {
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
public final class FilePath implements Serializable {
    /**
     * When this {@link FilePath} represents the remote path,
     * this field is always non-null on master (the field represents
     * the channel to the remote slave.) When transferred to a slave via remoting,
     * this field reverts back to null, since it's transient.
     *
     * When this {@link FilePath} represents a path on the master,
     * this field is null on master. When transferred to a slave via remoting,
     * this field becomes non-null, representing the {@link Channel}
     * back to the master.
     *
     * This is used to determine whether we are running on the master or the slave.
     */
    private transient VirtualChannel channel;

    // since the platform of the slave might be different, can't use java.io.File
    private final String remote;

    /**
     * If this {@link FilePath} is deserialized to handle file access request from a remote computer,
     * this field is set to the filter that performs access control.
     *
     * <p>
     * If null, no access control is needed.
     *
     * @see #filterNonNull()
     */
    private transient @Nullable
    SoloFilePathFilter filter;

    /**
     * Creates a {@link FilePath} that represents a path on the given node.
     *
     * @param channel
     *      To create a path that represents a remote path, pass in a {@link Channel}
     *      that's connected to that machine. If null, that means the local file path.
     */
    public FilePath(VirtualChannel channel, String remote) {
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
    public FilePath(File localPath) {
        this.channel = null;
        this.remote = normalize(localPath.getPath());
    }

    /**
     * Construct a path starting with a base location.
     * @param base starting point for resolution, and defines channel
     * @param rel a path which if relative will be resolved against base
     */
    public FilePath(FilePath base, String rel) {
        this.channel = base.channel;
        this.remote = normalize(resolvePathIfRelative(base, rel));
    }

    private String resolvePathIfRelative(FilePath base, String rel) {
        if(isAbsolute(rel)) return rel;
        if(base.isUnix()) {
            // shouldn't need this replace, but better safe than sorry
            return base.remote+'/'+rel.replace('\\','/');
        } else {
            // need this replace, see Slave.getWorkspaceFor and AbstractItem.getFullName, nested jobs on Windows
            // slaves will always have a rel containing at least one '/' character. JENKINS-13649
            return base.remote+'\\'+rel.replace('/','\\');
        }
    }

    /**
     * Is the given path name an absolute path?
     */
    private static boolean isAbsolute(String rel) {
        return rel.startsWith("/") || DRIVE_PATTERN.matcher(rel).matches() || UNC_PATTERN.matcher(rel).matches();
    }

    private static final Pattern DRIVE_PATTERN = Pattern.compile("[A-Za-z]:[\\\\/].*"),
            UNC_PATTERN = Pattern.compile("^\\\\\\\\.*"),
            ABSOLUTE_PREFIX_PATTERN = Pattern.compile("^(\\\\\\\\|(?:[A-Za-z]:)?[\\\\/])[\\\\/]*");

    /**
     * {@link File#getParent()} etc cannot handle ".." and "." in the path component very well,
     * so remove them.
     */
    private static String normalize(String path) {
        StringBuilder buf = new StringBuilder();
        // Check for prefix designating absolute path
        Matcher m = ABSOLUTE_PREFIX_PATTERN.matcher(path);
        if (m.find()) {
            buf.append(m.group(1));
            path = path.substring(m.end());
        }
        boolean isAbsolute = buf.length() > 0;
        // Split remaining path into tokens, trimming any duplicate or trailing separators
        List<String> tokens = new ArrayList<String>();
        int s = 0, end = path.length();
        for (int i = 0; i < end; i++) {
            char c = path.charAt(i);
            if (c == '/' || c == '\\') {
                tokens.add(path.substring(s, i));
                s = i;
                // Skip any extra separator chars
                while (++i < end && ((c = path.charAt(i)) == '/' || c == '\\')) { }
                // Add token for separator unless we reached the end
                if (i < end) tokens.add(path.substring(s, s+1));
                s = i;
            }
        }
        if (s < end) tokens.add(path.substring(s));
        // Look through tokens for "." or ".."
        for (int i = 0; i < tokens.size();) {
            String token = tokens.get(i);
            if (token.equals(".")) {
                tokens.remove(i);
                if (tokens.size() > 0)
                    tokens.remove(i > 0 ? i - 1 : i);
            } else if (token.equals("..")) {
                if (i == 0) {
                    // If absolute path, just remove: /../something
                    // If relative path, not collapsible so leave as-is
                    tokens.remove(0);
                    if (tokens.size() > 0) token += tokens.remove(0);
                    if (!isAbsolute) buf.append(token);
                } else {
                    // Normalize: remove something/.. plus separator before/after
                    i -= 2;
                    for (int j = 0; j < 3; j++) tokens.remove(i);
                    if (i > 0) tokens.remove(i-1);
                    else if (tokens.size() > 0) tokens.remove(0);
                }
            } else
                i += 2;
        }
        // Recombine tokens
        for (String token : tokens) buf.append(token);
        if (buf.length() == 0) buf.append('.');
        return buf.toString();
    }

    /**
     * Checks if the remote path is Unix.
     */
    boolean isUnix() {
        // if the path represents a local path, there' no need to guess.
        if(!isRemote())
            return File.pathSeparatorChar!=';';
            
        // note that we can't use the usual File.pathSeparator and etc., as the OS of
        // the machine where this code runs and the OS that this FilePath refers to may be different.

        // Windows absolute path is 'X:\...', so this is usually a good indication of Windows path
        if(remote.length()>3 && remote.charAt(1)==':' && remote.charAt(2)=='\\')
            return false;
        // Windows can handle '/' as a path separator but Unix can't,
        // so err on Unix side
        return remote.indexOf("\\")==-1;
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
        zip(os,(FileFilter)null);
    }

    public void zip(FilePath dst) throws IOException, InterruptedException {
        OutputStream os = dst.write();
        try {
            zip(os);
        } finally {
            os.close();
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
        archive(ArchiverFactory.ZIP,os,filter);
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
        archive(ArchiverFactory.ZIP,os,glob);
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
        archive(ArchiverFactory.ZIP,os,glob);
    }

    /**
     * Uses the given scanner on 'this' directory to list up files and then archive it to a zip stream.
     */
    public int zip(OutputStream out, DirScanner scanner) throws IOException, InterruptedException {
        return archive(ArchiverFactory.ZIP, out, scanner);
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
        final OutputStream out = (channel!=null)?new RemoteOutputStream(os):os;
        return act(new SecureFileCallable<Integer>() {
            public Integer invoke(File f, VirtualChannel channel) throws IOException {
                Archiver a = factory.create(out);
                try {
                    scanner.scan(f,reading(a));
                } finally {
                    a.close();
                }
                return a.countEntries();
            }

            private static final long serialVersionUID = 1L;
        });
    }

    public int archive(final ArchiverFactory factory, OutputStream os, final FileFilter filter) throws IOException, InterruptedException {
        return archive(factory,os,new DirScanner.Filter(filter));
    }

    public int archive(final ArchiverFactory factory, OutputStream os, final String glob) throws IOException, InterruptedException {
        return archive(factory,os,new DirScanner.Glob(glob,null));
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
        if (this.channel!=target.channel) {// local -> remote or remote->local
            final RemoteInputStream in = new RemoteInputStream(read(), Flag.GREEDY);
            target.act(new SecureFileCallable<Void>() {
                public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                    unzip(dir, in);
                    return null;
                }

                private static final long serialVersionUID = 1L;
            });
        } else {// local -> local or remote->remote
            target.act(new SecureFileCallable<Void>() {

                public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                    assert !FilePath.this.isRemote();       // this.channel==target.channel above
                    unzip(dir, reading(new File(FilePath.this.getRemote()))); // shortcut to local file
                    return null;
                }

                private static final long serialVersionUID = 1L;
            });
        }
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
        // TODO: post release, re-unite two branches by introducing FileStreamCallable that resolves InputStream
        if (this.channel!=target.channel) {// local -> remote or remote->local
            final RemoteInputStream in = new RemoteInputStream(read(), Flag.GREEDY);
            target.act(new SecureFileCallable<Void>() {
                public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                    readFromTar(FilePath.this.getName(),dir,compression.extract(in));
                    return null;
                }

                private static final long serialVersionUID = 1L;
            });
        } else {// local -> local or remote->remote
            target.act(new SecureFileCallable<Void>() {
                public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                    readFromTar(FilePath.this.getName(),dir,compression.extract(FilePath.this.read()));
                    return null;
                }
                private static final long serialVersionUID = 1L;
            });
        }
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
        final InputStream in = new RemoteInputStream(_in);
        act(new SecureFileCallable<Void>() {
            public Void invoke(File dir, VirtualChannel channel) throws IOException {
                unzip(dir, in);
                return null;
            }
            private static final long serialVersionUID = 1L;
        });
    }

    private void unzip(File dir, InputStream in) throws IOException {
        File tmpFile = File.createTempFile("tmpzip", null); // uses java.io.tmpdir
        try {
            // TODO why does this not simply use ZipInputStream?
            IOUtils.copy(in, tmpFile);
            unzip(dir,tmpFile);
        }
        finally {
            tmpFile.delete();
        }
    }

    private void unzip(File dir, File zipFile) throws IOException {
        dir = dir.getAbsoluteFile();    // without absolutization, getParentFile below seems to fail
        ZipFile zip = new ZipFile(zipFile);
        @SuppressWarnings("unchecked")
        Enumeration<ZipEntry> entries = zip.getEntries();

        try {
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                File f = new File(dir, e.getName());
                if (e.isDirectory()) {
                    mkdirs(f);
                } else {
                    File p = f.getParentFile();
                    if (p != null) {
                        mkdirs(p);
                    }
                    InputStream input = zip.getInputStream(e);
                    try {
                        IOUtils.copy(input, writing(f));
                    } finally {
                        input.close();
                    }
                    try {
                        FilePath target = new FilePath(f);
                        int mode = e.getUnixMode();
                        if (mode!=0)    // Ant returns 0 if the archive doesn't record the access mode
                            target.chmod(mode);
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.WARNING, "unable to set permissions", ex);
                    }
                    f.setLastModified(e.getTime());
                }
            }
        } finally {
            zip.close();
        }
    }

    /**
     * Absolutizes this {@link FilePath} and returns the new one.
     */
    public FilePath absolutize() throws IOException, InterruptedException {
        return new FilePath(channel,act(new SecureFileCallable<String>() {
            private static final long serialVersionUID = 1L;
            public String invoke(File f, VirtualChannel channel) throws IOException {
                return f.getAbsolutePath();
            }
        }));
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
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                symlinking(f);
                Util.createSymlink(f.getParentFile(),target,f.getName(),listener);
                return null;
            }
        });
    }
    
    /**
     * Resolves symlink, if the given file is a symlink. Otherwise return null.
     * <p>
     * If the resolution fails, report an error.
     *
     * @since 1.456
     */
    public String readLink() throws IOException, InterruptedException {
        return act(new SecureFileCallable<String>() {
            private static final long serialVersionUID = 1L;
            public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                return Util.resolveSymlink(reading(f));
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilePath that = (FilePath) o;

        if (channel != null ? !channel.equals(that.channel) : that.channel != null) return false;
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
            public InputStream extract(InputStream in) {
                return in;
            }
            public OutputStream compress(OutputStream out) {
                return out;
            }
        },
        GZIP {
            public InputStream extract(InputStream _in) throws IOException {
                HeadBufferingStream in = new HeadBufferingStream(_in,SIDE_BUFFER_SIZE);
                try {
                    return new GZIPInputStream(in, 8192, true);
                } catch (IOException e) {
                    // various people reported "java.io.IOException: Not in GZIP format" here, so diagnose this problem better
                    in.fillSide();
                    throw new IOException(e.getMessage()+"\nstream="+Util.toHexString(in.getSideBuffer()),e);
                }
            }
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
        try {
            final InputStream in = new RemoteInputStream(_in);
            act(new SecureFileCallable<Void>() {
                public Void invoke(File dir, VirtualChannel channel) throws IOException {
                    readFromTar("input stream",dir, compression.extract(in));
                    return null;
                }
                private static final long serialVersionUID = 1L;
            });
        } finally {
            org.apache.commons.io.IOUtils.closeQuietly(_in);
        }
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
     *      The resource that represents the tgz/zip file. This URL must support the {@code Last-Modified} header.
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
    public boolean installIfNecessaryFrom(@Nonnull URL archive, @CheckForNull TaskListener listener, @Nonnull String message) throws IOException, InterruptedException {
        try {
            FilePath timestamp = this.child(".timestamp");
            long lastModified = timestamp.lastModified();
            URLConnection con;
            try {
                con = ProxyConfiguration.open(archive);
                if (lastModified != 0) {
                    con.setIfModifiedSince(lastModified);
                }
                con.connect();
            } catch (IOException x) {
                if (this.exists()) {
                    // Cannot connect now, so assume whatever was last unpacked is still OK.
                    if (listener != null) {
                        listener.getLogger().println("Skipping installation of " + archive + " to " + remote + ": " + x);
                    }
                    return false;
                } else {
                    throw x;
                }
            }

            if (lastModified != 0 && con instanceof HttpURLConnection) {
                HttpURLConnection httpCon = (HttpURLConnection) con;
                int responseCode = httpCon.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return false;
                } else if (responseCode != HttpURLConnection.HTTP_OK) {
                    listener.getLogger().println("Skipping installation of " + archive + " to " + remote + " due to server error: " + responseCode + " " + httpCon.getResponseMessage());
                    return false;
                }
            }

            long sourceTimestamp = con.getLastModified();

            if(this.exists()) {
                if (lastModified != 0 && sourceTimestamp == lastModified)
                    return false;   // already up to date
                this.deleteContents();
            } else {
                this.mkdirs();
            }

            if(listener!=null)
                listener.getLogger().println(message);

            if (isRemote()) {
                // First try to download from the slave machine.
                try {
                    act(new Unpack(archive));
                    timestamp.touch(sourceTimestamp);
                    return true;
                } catch (IOException x) {
                    if (listener != null) {
                        x.printStackTrace(listener.error("Failed to download " + archive + " from slave; will retry from master"));
                    }
                }
            }

            // for HTTP downloads, enable automatic retry for added resilience
            InputStream in = archive.getProtocol().startsWith("http") ? ProxyConfiguration.getInputStream(archive) : con.getInputStream();
            CountingInputStream cis = new CountingInputStream(in);
            try {
                if(archive.toExternalForm().endsWith(".zip"))
                    unzipFrom(cis);
                else
                    untarFrom(cis,GZIP);
            } catch (IOException e) {
                throw new IOException(String.format("Failed to unpack %s (%d bytes read of total %d)",
                        archive,cis.getByteCount(),con.getContentLength()),e);
            }
            timestamp.touch(sourceTimestamp);
            return true;
        } catch (IOException e) {
            throw new IOException("Failed to install "+archive+" to "+remote,e);
        }
    }

    // this reads from arbitrary URL
    private final class Unpack extends MasterToSlaveFileCallable<Void> {
        private final URL archive;
        Unpack(URL archive) {
            this.archive = archive;
        }
        @Override public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            InputStream in = archive.openStream();
            try {
                CountingInputStream cis = new CountingInputStream(in);
                try {
                    if (archive.toExternalForm().endsWith(".zip")) {
                        unzip(dir, cis);
                    } else {
                        readFromTar("input stream", dir, GZIP.extract(cis));
                    }
                } catch (IOException x) {
                    throw new IOException(String.format("Failed to unpack %s (%d bytes read)", archive, cis.getByteCount()), x);
                }
            } finally {
                in.close();
            }
            return null;
        }
    }

    /**
     * Reads the URL on the current VM, and writes all the data to this {@link FilePath}
     * (this is different from resolving URL remotely.)
     *
     * @since 1.293
     */
    public void copyFrom(URL url) throws IOException, InterruptedException {
        InputStream in = url.openStream();
        try {
            copyFrom(in);
        } finally {
            in.close();
        }
    }

    /**
     * Replaces the content of this file by the data from the given {@link InputStream}.
     *
     * @since 1.293
     */
    public void copyFrom(InputStream in) throws IOException, InterruptedException {
        OutputStream os = write();
        try {
            org.apache.commons.io.IOUtils.copy(in, os);
        } finally {
            os.close();
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
        if(channel==null) {
            try {
                file.write(writing(new File(remote)));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            InputStream i = file.getInputStream();
            OutputStream o = write();
            try {
                org.apache.commons.io.IOUtils.copy(i,o);
            } finally {
                try {
                    o.close();
                } finally {
                    i.close();
                }
            }
        }
    }

    /**
     * Code that gets executed on the machine where the {@link FilePath} is local.
     * Used to act on {@link FilePath}.
     * <strong>Warning:</code> implementations must be serializable, so prefer a static nested class to an inner class.
     *
     * <p>
     * Subtypes would likely want to extend from either {@link MasterToSlaveCallable}
     * or {@link SlaveToMasterFileCallable}.
     *
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
     * {@link FileCallable}s that can be executed anywhere, including the master.
     *
     * The code is the same as {@link SlaveToMasterFileCallable}, but used as a marker to
     * designate those impls that use {@link FilePathFilter}.
     */
    /*package*/ static abstract class SecureFileCallable<T> extends SlaveToMasterFileCallable<T> {
    }

    /**
     * Executes some program on the machine that this {@link FilePath} exists,
     * so that one can perform local file operations.
     */
    public <T> T act(final FileCallable<T> callable) throws IOException, InterruptedException {
        return act(callable,callable.getClass().getClassLoader());
    }

    private <T> T act(final FileCallable<T> callable, ClassLoader cl) throws IOException, InterruptedException {
        if(channel!=null) {
            // run this on a remote system
            try {
                DelegatingCallable<T,IOException> wrapper = new FileCallableWrapper<T>(callable, cl);
                for (FileCallableWrapperFactory factory : ExtensionList.lookup(FileCallableWrapperFactory.class)) {
                    wrapper = factory.wrap(wrapper);
                }
                return channel.call(wrapper);
            } catch (TunneledInterruptedException e) {
                throw (InterruptedException)new InterruptedException(e.getMessage()).initCause(e);
            } catch (AbortException e) {
                throw e;    // pass through so that the caller can catch it as AbortException
            } catch (IOException e) {
                // wrap it into a new IOException so that we get the caller's stack trace as well.
                throw new IOException("remote file operation failed: " + remote + " at " + channel + ": " + e, e);
            }
        } else {
            // the file is on the local machine.
            return callable.invoke(new File(remote), localChannel);
        }
    }

    /**
     * This extension point allows to contribute a wrapper around a fileCallable so that a plugin can "intercept" a
     * call.
     * <p>The {@link #wrap(hudson.remoting.DelegatingCallable)} method itself will be executed on master
     * (and may collect contextual data if needed) and the returned wrapper will be executed on remote.
     *
     * @since 1.482
     * @see AbstractInterceptorCallableWrapper
     */
    public static abstract class FileCallableWrapperFactory implements ExtensionPoint {

        public abstract <T> DelegatingCallable<T,IOException> wrap(DelegatingCallable<T,IOException> callable);

    }

    /**
     * Abstract {@link DelegatingCallable} that exposes an Before/After pattern for
     * {@link hudson.FilePath.FileCallableWrapperFactory} that want to implement AOP-style interceptors
     * @since 1.482
     */
    public static abstract class AbstractInterceptorCallableWrapper<T> implements DelegatingCallable<T, IOException> {
        private static final long serialVersionUID = 1L;

        private final DelegatingCallable<T, IOException> callable;

        public AbstractInterceptorCallableWrapper(DelegatingCallable<T, IOException> callable) {
            this.callable = callable;
        }

        @Override
        public final ClassLoader getClassLoader() {
            return callable.getClassLoader();
        }

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
            DelegatingCallable<T,IOException> wrapper = new FileCallableWrapper<T>(callable);
            for (FileCallableWrapperFactory factory : ExtensionList.lookup(FileCallableWrapperFactory.class)) {
                wrapper = factory.wrap(wrapper);
            }
            return (channel!=null ? channel : localChannel)
                .callAsync(wrapper);
        } catch (IOException e) {
            // wrap it into a new IOException so that we get the caller's stack trace as well.
            throw new IOException("remote file operation failed",e);
        }
    }

    /**
     * Executes some program on the machine that this {@link FilePath} exists,
     * so that one can perform local file operations.
     */
    public <V,E extends Throwable> V act(Callable<V,E> callable) throws IOException, InterruptedException, E {
        if(channel!=null) {
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
    public <V> Callable<V,IOException> asCallableWith(final FileCallable<V> task) {
        return new Callable<V,IOException>() {
            @Override
            public V call() throws IOException {
                try {
                    return act(task);
                } catch (InterruptedException e) {
                    throw (IOException)new InterruptedIOException().initCause(e);
                }
            }

            @Override
            public void checkRoles(RoleChecker checker) throws SecurityException {
                task.checkRoles(checker);
            }

            private static final long serialVersionUID = 1L;
        };
    }

    /**
     * Converts this file to the URI, relative to the machine
     * on which this file is available.
     */
    public URI toURI() throws IOException, InterruptedException {
        return act(new SecureFileCallable<URI>() {
            private static final long serialVersionUID = 1L;
            public URI invoke(File f, VirtualChannel channel) {
                return f.toURI();
            }
        });
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
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            for (Computer c : j.getComputers()) {
                if (getChannel()==c.getChannel()) {
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
        if(!act(new SecureFileCallable<Boolean>() {
            private static final long serialVersionUID = 1L;
            public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                if(mkdirs(f) || f.exists())
                    return true;    // OK

                // following Ant <mkdir> task to avoid possible race condition.
                Thread.sleep(10);

                return f.mkdirs() || f.exists();
            }
        }))
            throw new IOException("Failed to mkdirs: "+remote);
    }

    /**
     * Deletes this directory, including all its contents recursively.
     */
    public void deleteRecursive() throws IOException, InterruptedException {
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                deleteRecursive(deleting(f));
                return null;
            }
        });
    }

    /**
     * Deletes all the contents of this directory, but not the directory itself
     */
    public void deleteContents() throws IOException, InterruptedException {
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                deleteContentsRecursive(f);
                return null;
            }
        });
    }

    private void deleteRecursive(File dir) throws IOException {
        if(!isSymlink(dir))
            deleteContentsRecursive(dir);
        try {
            deleteFile(deleting(dir));
        } catch (IOException e) {
            // if some of the child directories are big, it might take long enough to delete that
            // it allows others to create new files, causing problemsl ike JENKINS-10113
            // so give it one more attempt before we give up.
            if(!isSymlink(dir))
                deleteContentsRecursive(dir);
            deleteFile(deleting(dir));
        }
    }

    private void deleteContentsRecursive(File file) throws IOException {
        File[] files = file.listFiles();
        if(files==null)
            return;     // the directory didn't exist in the first place
        for (File child : files)
            deleteRecursive(child);
    }

    /**
     * Gets the file name portion except the extension.
     *
     * For example, "foo" for "foo.txt" and "foo.tar" for "foo.tar.gz".
     */
    public String getBaseName() {
        String n = getName();
        int idx = n.lastIndexOf('.');
        if (idx<0)  return n;
        return n.substring(0,idx);
    }
    /**
     * Gets just the file name portion without directories.
     *
     * For example, "foo.txt" for "../abc/foo.txt"
     */
    public String getName() {
        String r = remote;
        if(r.endsWith("\\") || r.endsWith("/"))
            r = r.substring(0,r.length()-1);

        int len = r.length()-1;
        while(len>=0) {
            char ch = r.charAt(len);
            if(ch=='\\' || ch=='/')
                break;
            len--;
        }

        return r.substring(len+1);
    }

    /**
     * Short for {@code getParent().child(rel)}. Useful for getting other files in the same directory. 
     */
    public FilePath sibling(String rel) {
        return getParent().child(rel);
    }

    /**
     * Returns a {@link FilePath} by adding the given suffix to this path name.
     */
    public FilePath withSuffix(String suffix) {
        return new FilePath(channel,remote+suffix);
    }

    /**
     * The same as {@link FilePath#FilePath(FilePath,String)} but more OO.
     * @param relOrAbsolute a relative or absolute path
     * @return a file on the same channel
     */
    public @Nonnull FilePath child(String relOrAbsolute) {
        return new FilePath(this,relOrAbsolute);
    }

    /**
     * Gets the parent file.
     * @return parent FilePath or null if there is no parent
     */
    public FilePath getParent() {
        int i = remote.length() - 2;
        for (; i >= 0; i--) {
            char ch = remote.charAt(i);
            if(ch=='\\' || ch=='/')
                break;
        }

        return i >= 0 ? new FilePath( channel, remote.substring(0,i+1) ) : null;
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
            return new FilePath(this,act(new SecureFileCallable<String>() {
                private static final long serialVersionUID = 1L;
                public String invoke(File dir, VirtualChannel channel) throws IOException {
                    File f = writing(File.createTempFile(prefix, suffix, dir));
                    return f.getName();
                }
            }));
        } catch (IOException e) {
            throw new IOException("Failed to create a temp file on "+remote,e);
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
        return createTextTempFile(prefix,suffix,contents,true);
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
            return new FilePath(channel,act(new SecureFileCallable<String>() {
                private static final long serialVersionUID = 1L;
                public String invoke(File dir, VirtualChannel channel) throws IOException {
                    if(!inThisDirectory)
                        dir = new File(System.getProperty("java.io.tmpdir"));
                    else
                        mkdirs(dir);

                    File f;
                    try {
                        f = creating(File.createTempFile(prefix, suffix, dir));
                    } catch (IOException e) {
                        throw new IOException("Failed to create a temporary directory in "+dir,e);
                    }

                    Writer w = new FileWriter(writing(f));
                    try {
                        w.write(contents);
                    } finally {
                        w.close();
                    }

                    return f.getAbsolutePath();
                }
            }));
        } catch (IOException e) {
            throw new IOException("Failed to create a temp file on "+remote,e);
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
     * @see File#createTempFile(String, String)
     */
    public FilePath createTempDir(final String prefix, final String suffix) throws IOException, InterruptedException {
        try {
            return new FilePath(this,act(new SecureFileCallable<String>() {
                private static final long serialVersionUID = 1L;
                public String invoke(File dir, VirtualChannel channel) throws IOException {
                    File f = File.createTempFile(prefix, suffix, dir);
                    f.delete();
                    f.mkdir();
                    return f.getName();
                }
            }));
        } catch (IOException e) {
            throw new IOException("Failed to create a temp directory on "+remote,e);
        }
    }

    /**
     * Deletes this file.
     * @throws IOException if it exists but could not be successfully deleted
     * @return true, for a modicum of compatibility
     */
    public boolean delete() throws IOException, InterruptedException {
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                Util.deleteFile(deleting(f));
                return null;
            }
        });
        return true;
    }

    /**
     * Checks if the file exists.
     */
    public boolean exists() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Boolean>() {
            private static final long serialVersionUID = 1L;
            public Boolean invoke(File f, VirtualChannel channel) throws IOException {
                return stating(f).exists();
            }
        });
    }

    /**
     * Gets the last modified time stamp of this file, by using the clock
     * of the machine where this file actually resides.
     *
     * @see File#lastModified()
     * @see #touch(long)
     */
    public long lastModified() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Long>() {
            private static final long serialVersionUID = 1L;
            public Long invoke(File f, VirtualChannel channel) throws IOException {
                return stating(f).lastModified();
            }
        });
    }

    /**
     * Creates a file (if not already exist) and sets the timestamp.
     *
     * @since 1.299
     */
    public void touch(final long timestamp) throws IOException, InterruptedException {
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = -5094638816500738429L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                if(!f.exists())
                    new FileOutputStream(creating(f)).close();
                if(!stating(f).setLastModified(timestamp))
                    throw new IOException("Failed to set the timestamp of "+f+" to "+timestamp);
                return null;
            }
        });
    }
    
    private void setLastModifiedIfPossible(final long timestamp) throws IOException, InterruptedException {
        String message = act(new SecureFileCallable<String>() {
            private static final long serialVersionUID = -828220335793641630L;
            public String invoke(File f, VirtualChannel channel) throws IOException {
                if(!writing(f).setLastModified(timestamp)) {
                    if (Functions.isWindows()) {
                        // On Windows this seems to fail often. See JENKINS-11073
                        // Therefore don't fail, but just log a warning
                        return "Failed to set the timestamp of "+f+" to "+timestamp;
                    } else {
                        throw new IOException("Failed to set the timestamp of "+f+" to "+timestamp);
                    }
                }
                return null;
            }
        });

        if (message!=null) {
            LOGGER.warning(message);
        }
    }

    /**
     * Checks if the file is a directory.
     */
    public boolean isDirectory() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Boolean>() {
            private static final long serialVersionUID = 1L;
            public Boolean invoke(File f, VirtualChannel channel) throws IOException {
                return stating(f).isDirectory();
            }
        });
    }
    
    /**
     * Returns the file size in bytes.
     *
     * @since 1.129
     */
    public long length() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Long>() {
            private static final long serialVersionUID = 1L;
            public Long invoke(File f, VirtualChannel channel) throws IOException {
                return stating(f).length();
            }
        });
    }

    /**
     * Returns the number of unallocated bytes in the partition of that file.
     * @since 1.542
     */
    public long getFreeDiskSpace() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Long>() {
            private static final long serialVersionUID = 1L;
            @Override public Long invoke(File f, VirtualChannel channel) throws IOException {
                return f.getFreeSpace();
            }
        });
    }

    /**
     * Returns the total number of bytes in the partition of that file.
     * @since 1.542
     */
    public long getTotalDiskSpace() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Long>() {
            private static final long serialVersionUID = 1L;
            @Override public Long invoke(File f, VirtualChannel channel) throws IOException {
                return f.getTotalSpace();
            }
        });
    }

    /**
     * Returns the number of usable bytes in the partition of that file.
     * @since 1.542
     */
    public long getUsableDiskSpace() throws IOException, InterruptedException {
        return act(new SecureFileCallable<Long>() {
            private static final long serialVersionUID = 1L;
            @Override public Long invoke(File f, VirtualChannel channel) throws IOException {
                return f.getUsableSpace();
            }
        });
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
     *      so preceded by a '0' in java notation, ie <code>chmod(0644)</code>
     *
     * @since 1.303
     * @see #mode()
     */
    public void chmod(final int mask) throws IOException, InterruptedException {
        if(!isUnix() || mask==-1)   return;
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                // TODO first check for Java 7+ and use PosixFileAttributeView
                _chmod(writing(f), mask);

                return null;
            }
        });
    }

    /**
     * Run chmod via jnr-posix
     */
    private static void _chmod(File f, int mask) throws IOException {
        // TODO WindowsPosix actually does something here (WindowsLibC._wchmod); should we let it?
        // Anyway the existing calls already skip this method if on Windows.
        if (File.pathSeparatorChar==';')  return; // noop

        PosixAPI.jnr().chmod(f.getAbsolutePath(),mask);
    }

    private static boolean CHMOD_WARNED = false;

    /**
     * Gets the file permission bit mask.
     *
     * @return
     *      -1 on Windows, since such a concept doesn't make sense.
     * @since 1.311
     * @see #chmod(int)
     */
    public int mode() throws IOException, InterruptedException, PosixException {
        if(!isUnix())   return -1;
        return act(new SecureFileCallable<Integer>() {
            private static final long serialVersionUID = 1L;
            public Integer invoke(File f, VirtualChannel channel) throws IOException {
                return IOUtils.mode(stating(f));
            }
        });
    }

    /**
     * List up files and directories in this directory.
     *
     * <p>
     * This method returns direct children of the directory denoted by the 'this' object.
     */
    public List<FilePath> list() throws IOException, InterruptedException {
        return list((FileFilter)null);
    }

    /**
     * List up subdirectories.
     *
     * @return can be empty but never null. Doesn't contain "." and ".."
     */
    public List<FilePath> listDirectories() throws IOException, InterruptedException {
        return list(new DirectoryFilter());
    }

    private static final class DirectoryFilter implements FileFilter, Serializable {
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
    public List<FilePath> list(final FileFilter filter) throws IOException, InterruptedException {
        if (filter != null && !(filter instanceof Serializable)) {
            throw new IllegalArgumentException("Non-serializable filter of " + filter.getClass());
        }
        return act(new SecureFileCallable<List<FilePath>>() {
            private static final long serialVersionUID = 1L;
            public List<FilePath> invoke(File f, VirtualChannel channel) throws IOException {
                File[] children = reading(f).listFiles(filter);
                if(children ==null)     return null;

                ArrayList<FilePath> r = new ArrayList<FilePath>(children.length);
                for (File child : children)
                    r.add(new FilePath(child));

                return r;
            }
        }, (filter!=null?filter:this).getClass().getClassLoader());
    }

    /**
     * List up files in this directory that matches the given Ant-style filter.
     *
     * @param includes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @return
     *      can be empty but always non-null.
     */
    public FilePath[] list(final String includes) throws IOException, InterruptedException {
        return list(includes, null);
    }

    /**
     * List up files in this directory that matches the given Ant-style filter.
     *
     * @param includes
     * @param excludes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @return
     *      can be empty but always non-null.
     * @since 1.407
     */
    public FilePath[] list(final String includes, final String excludes) throws IOException, InterruptedException {
        return list(includes, excludes, true);
    }

    /**
     * List up files in this directory that matches the given Ant-style filter.
     *
     * @param includes
     * @param excludes
     *      See {@link FileSet} for the syntax. String like "foo/*.zip" or "foo/*&#42;/*.xml"
     * @param defaultExcludes whether to use the ant default excludes
     * @return
     *      can be empty but always non-null.
     * @since 1.465
     */
    public FilePath[] list(final String includes, final String excludes, final boolean defaultExcludes) throws IOException, InterruptedException {
        return act(new SecureFileCallable<FilePath[]>() {
            private static final long serialVersionUID = 1L;
            public FilePath[] invoke(File f, VirtualChannel channel) throws IOException {
                String[] files = glob(reading(f), includes, excludes, defaultExcludes);

                FilePath[] r = new FilePath[files.length];
                for( int i=0; i<r.length; i++ )
                    r[i] = new FilePath(new File(f,files[i]));

                return r;
            }
        });
    }

    /**
     * Runs Ant glob expansion.
     *
     * @return
     *      A set of relative file names from the base directory.
     */
    private static String[] glob(File dir, String includes, String excludes, boolean defaultExcludes) throws IOException {
        if(isAbsolute(includes))
            throw new IOException("Expecting Ant GLOB pattern, but saw '"+includes+"'. See http://ant.apache.org/manual/Types/fileset.html for syntax");
        FileSet fs = Util.createFileSet(dir,includes,excludes);
        fs.setDefaultexcludes(defaultExcludes);
        DirectoryScanner ds = fs.getDirectoryScanner(new Project());
        String[] files = ds.getIncludedFiles();
        return files;
    }

    /**
     * Reads this file.
     */
    public InputStream read() throws IOException, InterruptedException {
        if(channel==null)
            return new FileInputStream(reading(new File(remote)));

        final Pipe p = Pipe.createRemoteToLocal();
        actAsync(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(reading(f));
                    Util.copyStream(fis, p.getOut());
                } catch (Exception x) {
                    p.error(x);
                } finally {
                    org.apache.commons.io.IOUtils.closeQuietly(fis);
                    org.apache.commons.io.IOUtils.closeQuietly(p.getOut());
                }
                return null;
            }
        });

        return p.getIn();
    }

    /**
     * Reads this file from the specific offset.
     * @since 1.586
     */
    public InputStream readFromOffset(final long offset) throws IOException, InterruptedException {
        if(channel ==null) {
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
        actAsync(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;

            public Void invoke(File f, VirtualChannel channel) throws IOException {
                final OutputStream out = new java.util.zip.GZIPOutputStream(p.getOut(), 8192);
                RandomAccessFile raf = null;
                try {
                    raf = new RandomAccessFile(reading(f), "r");
                    raf.seek(offset);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = raf.read(buf)) >= 0) {
                        out.write(buf, 0, len);
                    }
                    return null;
                } finally {
                    IOUtils.closeQuietly(out);
                    if (raf != null) {
                        try {
                            raf.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        });

        return new java.util.zip.GZIPInputStream(p.getIn());
    }

    /**
     * Reads this file into a string, by using the current system encoding.
     */
    public String readToString() throws IOException, InterruptedException {
        InputStream in = read();
        try {
            return org.apache.commons.io.IOUtils.toString(in);
        } finally {
            in.close();
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
     *
     * <p>
     *
     */
    public OutputStream write() throws IOException, InterruptedException {
        if(channel==null) {
            File f = new File(remote).getAbsoluteFile();
            mkdirs(f.getParentFile());
            return new FileOutputStream(writing(f));
        }

        return act(new SecureFileCallable<OutputStream>() {
            private static final long serialVersionUID = 1L;
            public OutputStream invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                f = f.getAbsoluteFile();
                mkdirs(f.getParentFile());
                FileOutputStream fos = new FileOutputStream(writing(f));
                return new RemoteOutputStream(fos);
            }
        });
    }

    /**
     * Overwrites this file by placing the given String as the content.
     *
     * @param encoding
     *      Null to use the platform default encoding.
     * @since 1.105
     */
    public void write(final String content, final String encoding) throws IOException, InterruptedException {
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                mkdirs(f.getParentFile());
                FileOutputStream fos = new FileOutputStream(writing(f));
                Writer w = encoding != null ? new OutputStreamWriter(fos, encoding) : new OutputStreamWriter(fos);
                try {
                    w.write(content);
                } finally {
                    w.close();
                }
                return null;
            }
        });
    }

    /**
     * Computes the MD5 digest of the file in hex string.
     * @see Util#getDigestOf(File)
     */
    public String digest() throws IOException, InterruptedException {
        return act(new SecureFileCallable<String>() {
            private static final long serialVersionUID = 1L;
            public String invoke(File f, VirtualChannel channel) throws IOException {
                return Util.getDigestOf(reading(f));
            }
        });
    }

    /**
     * Rename this file/directory to the target filepath.  This FilePath and the target must
     * be on the some host
     */
    public void renameTo(final FilePath target) throws IOException, InterruptedException {
    	if(this.channel != target.channel) {
    		throw new IOException("renameTo target must be on the same host");
    	}
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
            	reading(f).renameTo(creating(new File(target.remote)));
                return null;
            }
        });
    }

    /**
     * Moves all the contents of this directory into the specified directory, then delete this directory itself.
     *
     * @since 1.308.
     */
    public void moveAllChildrenTo(final FilePath target) throws IOException, InterruptedException {
        if(this.channel != target.channel) {
            throw new IOException("pullUpTo target must be on the same host");
        }
        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 1L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                // JENKINS-16846: if f.getName() is the same as one of the files/directories in f,
                // then the rename op will fail
                File tmp = new File(f.getAbsolutePath()+".__rename");
                if (!f.renameTo(tmp))
                    throw new IOException("Failed to rename "+f+" to "+tmp);

                File t = new File(target.getRemote());
                
                for(File child : reading(tmp).listFiles()) {
                    File target = new File(t, child.getName());
                    if(!stating(child).renameTo(creating(target)))
                        throw new IOException("Failed to rename "+child+" to "+target);
                }
                deleting(tmp).delete();
                return null;
            }
        });
    }

    /**
     * Copies this file to the specified target.
     */
    public void copyTo(FilePath target) throws IOException, InterruptedException {
        try {
            OutputStream out = target.write();
            try {
                copyTo(out);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new IOException("Failed to copy "+this+" to "+target,e);
        }
    }

    /**
     * Copies this file to the specified target, with file permissions and other meta attributes intact.
     * @since 1.311
     */
    public void copyToWithPermission(FilePath target) throws IOException, InterruptedException {
        copyTo(target);
        // copy file permission
        target.chmod(mode());
        target.setLastModifiedIfPossible(lastModified());
    }

    /**
     * Sends the contents of this file into the given {@link OutputStream}.
     */
    public void copyTo(OutputStream os) throws IOException, InterruptedException {
        final OutputStream out = new RemoteOutputStream(os);

        act(new SecureFileCallable<Void>() {
            private static final long serialVersionUID = 4088559042349254141L;
            public Void invoke(File f, VirtualChannel channel) throws IOException {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(reading(f));
                    Util.copyStream(fis,out);
                    return null;
                } finally {
                    org.apache.commons.io.IOUtils.closeQuietly(fis);
                    org.apache.commons.io.IOUtils.closeQuietly(out);
                }
            }
        });

        // make sure the writes fully got delivered to 'os' before we return.
        // this is needed because I/O operation is asynchronous
        syncIO();
    }

    /**
     * With fix to JENKINS-11251 (remoting 2.15), this is no longer necessary.
     * But I'm keeping it for a while so that users who manually deploy slave.jar has time to deploy new version
     * before this goes away.
     */
    private void syncIO() throws InterruptedException {
        try {
            if (channel!=null)
                channel.syncLocalIO();
        } catch (AbstractMethodError e) {
            // legacy slave.jar. Handle this gracefully
            try {
                LOGGER.log(Level.WARNING,"Looks like an old slave.jar. Please update "+ Which.jarFile(Channel.class)+" to the new version",e);
            } catch (IOException _) {
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
        return copyRecursiveTo("**/*",target);
    }

    /**
     * Copies the files that match the given file mask to the specified target node.
     *
     * @param fileMask
     *      Ant GLOB pattern.
     *      String like "foo/bar/*.xml" Multiple patterns can be separated
     *      by ',', and whitespace can surround ',' (so that you can write
     *      "abc, def" and "abc,def" to mean the same thing.
     * @return
     *      the number of files copied.
     */
    public int copyRecursiveTo(String fileMask, FilePath target) throws IOException, InterruptedException {
        return copyRecursiveTo(fileMask,null,target);
    }

    /**
     * Copies the files that match the given file mask to the specified target node.
     *
     * @param fileMask
     *      Ant GLOB pattern.
     *      String like "foo/bar/*.xml" Multiple patterns can be separated
     *      by ',', and whitespace can surround ',' (so that you can write
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
        if(this.channel==target.channel) {
            // local to local copy.
            return act(new SecureFileCallable<Integer>() {
                private static final long serialVersionUID = 1L;
                public Integer invoke(File base, VirtualChannel channel) throws IOException {
                    if(!base.exists())  return 0;
                    assert target.channel==null;
                    final File dest = new File(target.remote);
                    final AtomicInteger count = new AtomicInteger();
                    scanner.scan(base, reading(new FileVisitor() {
                        @Override
                        public void visit(File f, String relativePath) throws IOException {
                            if (f.isFile()) {
                                File target = new File(dest, relativePath);
                                mkdirsE(target.getParentFile());
                                Util.copyFile(f, writing(target));
                                count.incrementAndGet();
                            }
                        }

                        @Override
                        public boolean understandsSymlink() {
                            return true;
                        }

                        @Override
                        public void visitSymlink(File link, String target, String relativePath) throws IOException {
                            try {
                                mkdirsE(new File(dest, relativePath).getParentFile());
                                writing(new File(dest, target));
                                Util.createSymlink(dest, target, relativePath, TaskListener.NULL);
                            } catch (InterruptedException x) {
                                throw (IOException) new IOException(x.toString()).initCause(x);
                            }
                            count.incrementAndGet();
                        }
                    }));
                    return count.get();
                }
            });
        } else
        if(this.channel==null) {
            // local -> remote copy
            final Pipe pipe = Pipe.createLocalToRemote();

            Future<Void> future = target.actAsync(new SecureFileCallable<Void>() {
                private static final long serialVersionUID = 1L;
                public Void invoke(File f, VirtualChannel channel) throws IOException {
                    try {
                        readFromTar(remote + '/' + description, f,TarCompression.GZIP.extract(pipe.getIn()));
                        return null;
                    } finally {
                        pipe.getIn().close();
                    }
                }
            });
            Future<Integer> future2 = actAsync(new SecureFileCallable<Integer>() {
                private static final long serialVersionUID = 1L;
                @Override public Integer invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    return writeToTar(new File(remote), scanner, TarCompression.GZIP.compress(pipe.getOut()));
                }
            });
            try {
                // JENKINS-9540 in case the reading side failed, report that error first
                future.get();
                return future2.get();
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
        } else {
            // remote -> local copy
            final Pipe pipe = Pipe.createRemoteToLocal();

            Future<Integer> future = actAsync(new SecureFileCallable<Integer>() {
                private static final long serialVersionUID = 1L;
                public Integer invoke(File f, VirtualChannel channel) throws IOException {
                    try {
                        return writeToTar(f, scanner, TarCompression.GZIP.compress(pipe.getOut()));
                    } finally {
                        pipe.getOut().close();
                    }
                }
            });
            try {
                readFromTar(remote + '/' + description,new File(target.remote),TarCompression.GZIP.extract(pipe.getIn()));
            } catch (IOException e) {// BuildException or IOException
                try {
                    future.get(3,TimeUnit.SECONDS);
                    throw e;    // the remote side completed successfully, so the error must be local
                } catch (ExecutionException x) {
                    // report both errors
                    throw new IOException(Functions.printThrowable(e),x);
                } catch (TimeoutException _) {
                    // remote is hanging
                    throw e;
                }
            }
            try {
                return future.get();
            } catch (ExecutionException e) {
                throw new IOException(e);
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
    private Integer writeToTar(File baseDir, DirScanner scanner, OutputStream out) throws IOException {
        Archiver tw = ArchiverFactory.TAR.create(out);
        try {
            scanner.scan(baseDir,reading(tw));
        } finally {
            tw.close();
        }
        return tw.countEntries();
    }

    /**
     * Reads from a tar stream and stores obtained files to the base dir.
     */
    private void readFromTar(String name, File baseDir, InputStream in) throws IOException {
        TarInputStream t = new TarInputStream(in);
        try {
            TarEntry te;
            while ((te = t.getNextEntry()) != null) {
                File f = new File(baseDir,te.getName());
                if(te.isDirectory()) {
                    mkdirs(f);
                } else {
                    File parent = f.getParentFile();
                    if (parent != null) mkdirs(parent);
                    writing(f);

                    byte linkFlag = (Byte) LINKFLAG_FIELD.get(te);
                    if (linkFlag==TarEntry.LF_SYMLINK) {
                        new FilePath(f).symlinkTo(te.getLinkName(), TaskListener.NULL);
                    } else {
                        IOUtils.copy(t,f);

                        f.setLastModified(te.getModTime().getTime());
                        int mode = te.getMode()&0777;
                        if(mode!=0 && !Functions.isWindows()) // be defensive
                            _chmod(f,mode);
                    }
                }
            }
        } catch(IOException e) {
            throw new IOException("Failed to extract "+name,e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // process this later
            throw new IOException("Failed to extract "+name,e);
        } catch (IllegalAccessException e) {
            throw new IOException("Failed to extract "+name,e);
        } finally {
            t.close();
        }
    }

    /**
     * Creates a {@link Launcher} for starting processes on the node
     * that has this file.
     * @since 1.89
     */
    public Launcher createLauncher(TaskListener listener) throws IOException, InterruptedException {
        if(channel==null)
            return new LocalLauncher(listener);
        else
            return new RemoteLauncher(listener,channel,channel.call(new IsUnix()));
    }

    private static final class IsUnix extends MasterToSlaveCallable<Boolean,IOException> {
        public Boolean call() throws IOException {
            return File.pathSeparatorChar==':';
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Validates the ant file mask (like "foo/bar/*.txt, zot/*.jar")
     * against this directory, and try to point out the problem.
     *
     * <p>
     * This is useful in conjunction with {@link FormValidation}.
     *
     * @return
     *      null if no error was found. Otherwise returns a human readable error message.
     * @since 1.90
     * @see #validateFileMask(FilePath, String)
     * @deprecated use {@link #validateAntFileMask(String, int)} instead
     */
    @Deprecated
    public String validateAntFileMask(final String fileMasks) throws IOException, InterruptedException {
        return validateAntFileMask(fileMasks, Integer.MAX_VALUE);
    }

    /**
     * Default bound for {@link #validateAntFileMask(String, int)}.
     * @since 1.592
     */
    public static int VALIDATE_ANT_FILE_MASK_BOUND = Integer.getInteger(FilePath.class.getName() + ".VALIDATE_ANT_FILE_MASK_BOUND", 10000);

    /**
     * Like {@link #validateAntFileMask(String)} but performing only a bounded number of operations.
     * <p>Whereas the unbounded overload is appropriate for calling from cancelable, long-running tasks such as build steps,
     * this overload should be used when an answer is needed quickly, such as for {@link #validateFileMask(String)}
     * or anything else returning {@link FormValidation}.
     * <p>If a positive match is found, {@code null} is returned immediately.
     * A message is returned in case the file pattern can definitely be determined to not match anything in the directory within the alloted time.
     * If the time runs out without finding a match but without ruling out the possibility that there might be one, {@link InterruptedException} is thrown,
     * in which case the calling code should give the user the benefit of the doubt and use {@link hudson.util.FormValidation.Kind#OK} (with or without a message).
     * @param bound a maximum number of negative operations (deliberately left vague) to perform before giving up on a precise answer; try {@link #VALIDATE_ANT_FILE_MASK_BOUND}
     * @throws InterruptedException not only in case of a channel failure, but also if too many operations were performed without finding any matches
     * @since 1.484
     */
    public String validateAntFileMask(final String fileMasks, final int bound) throws IOException, InterruptedException {
        return act(new MasterToSlaveFileCallable<String>() {
            private static final long serialVersionUID = 1;
            public String invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
                if(fileMasks.startsWith("~"))
                    return Messages.FilePath_TildaDoesntWork();

                StringTokenizer tokens = new StringTokenizer(fileMasks,",");

                while(tokens.hasMoreTokens()) {
                    final String fileMask = tokens.nextToken().trim();
                    if(hasMatch(dir,fileMask))
                        continue;   // no error on this portion

                    // in 1.172 we introduced an incompatible change to stop using ' ' as the separator
                    // so see if we can match by using ' ' as the separator
                    if(fileMask.contains(" ")) {
                        boolean matched = true;
                        for (String token : Util.tokenize(fileMask))
                            matched &= hasMatch(dir,token);
                        if(matched)
                            return Messages.FilePath_validateAntFileMask_whitespaceSeprator();
                    }

                    // a common mistake is to assume the wrong base dir, and there are two variations
                    // to this: (1) the user gave us aa/bb/cc/dd where cc/dd was correct
                    // and (2) the user gave us cc/dd where aa/bb/cc/dd was correct.

                    {// check the (1) above first
                        String f=fileMask;
                        while(true) {
                            int idx = findSeparator(f);
                            if(idx==-1)     break;
                            f=f.substring(idx+1);

                            if(hasMatch(dir,f))
                                return Messages.FilePath_validateAntFileMask_doesntMatchAndSuggest(fileMask,f);
                        }
                    }

                    {// check the (2) above next as this is more expensive.
                        // Try prepending "**/" to see if that results in a match
                        FileSet fs = Util.createFileSet(reading(dir),"**/"+fileMask);
                        DirectoryScanner ds = fs.getDirectoryScanner(new Project());
                        if(ds.getIncludedFilesCount()!=0) {
                            // try shorter name first so that the suggestion results in least amount of changes
                            String[] names = ds.getIncludedFiles();
                            Arrays.sort(names,SHORTER_STRING_FIRST);
                            for( String f : names) {
                                // now we want to decompose f to the leading portion that matched "**"
                                // and the trailing portion that matched the file mask, so that
                                // we can suggest the user error.
                                //
                                // this is not a very efficient/clever way to do it, but it's relatively simple

                                String prefix="";
                                while(true) {
                                    int idx = findSeparator(f);
                                    if(idx==-1)     break;

                                    prefix+=f.substring(0,idx)+'/';
                                    f=f.substring(idx+1);
                                    if(hasMatch(dir,prefix+fileMask))
                                        return Messages.FilePath_validateAntFileMask_doesntMatchAndSuggest(fileMask, prefix+fileMask);
                                }
                            }
                        }
                    }

                    {// finally, see if we can identify any sub portion that's valid. Otherwise bail out
                        String previous = null;
                        String pattern = fileMask;

                        while(true) {
                            if(hasMatch(dir,pattern)) {
                                // found a match
                                if(previous==null)
                                    return Messages.FilePath_validateAntFileMask_portionMatchAndSuggest(fileMask,pattern);
                                else
                                    return Messages.FilePath_validateAntFileMask_portionMatchButPreviousNotMatchAndSuggest(fileMask,pattern,previous);
                            }

                            int idx = findSeparator(pattern);
                            if(idx<0) {// no more path component left to go back
                                if(pattern.equals(fileMask))
                                    return Messages.FilePath_validateAntFileMask_doesntMatchAnything(fileMask);
                                else
                                    return Messages.FilePath_validateAntFileMask_doesntMatchAnythingAndSuggest(fileMask,pattern);
                            }

                            // cut off the trailing component and try again
                            previous = pattern;
                            pattern = pattern.substring(0,idx);
                        }
                    }
                }

                return null; // no error
            }

            private boolean hasMatch(File dir, String pattern) throws InterruptedException {
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
                ds.setBasedir(reading(dir));
                ds.setIncludes(new String[] {pattern});
                try {
                    ds.scan();
                } catch (Cancel c) {
                    if (ds.getIncludedFilesCount()!=0 || ds.getIncludedDirsCount()!=0) {
                        return true;
                    } else {
                        throw new InterruptedException("no matches found within " + bound);
                    }
                }
                return ds.getIncludedFilesCount()!=0 || ds.getIncludedDirsCount()!=0;
            }

            /**
             * Finds the position of the first path separator.
             */
            private int findSeparator(String pattern) {
                int idx1 = pattern.indexOf('\\');
                int idx2 = pattern.indexOf('/');
                if(idx1==-1)    return idx2;
                if(idx2==-1)    return idx1;
                return Math.min(idx1,idx2);
            }
        });
    }

    /**
     * Shortcut for {@link #validateFileMask(String)} in case the left-hand side can be null.
     */
    public static FormValidation validateFileMask(@CheckForNull FilePath path, String value) throws IOException {
        if(path==null) return FormValidation.ok();
        return path.validateFileMask(value);
    }

    /**
     * Short for {@code validateFileMask(value,true)} 
     */
    public FormValidation validateFileMask(String value) throws IOException {
        return validateFileMask(value,true);
    }

    /**
     * Checks the GLOB-style file mask. See {@link #validateAntFileMask(String)}.
     * Requires configure permission on ancestor AbstractProject object in request,
     * or admin permission if no such ancestor is found.
     * @since 1.294
     */
    public FormValidation validateFileMask(String value, boolean errorIfNotExist) throws IOException {
        checkPermissionForValidate();

        value = fixEmpty(value);
        if(value==null)
            return FormValidation.ok();

        try {
            if(!exists()) // no workspace. can't check
                return FormValidation.ok();

            String msg = validateAntFileMask(value, VALIDATE_ANT_FILE_MASK_BOUND);
            if(errorIfNotExist)     return FormValidation.error(msg);
            else                    return FormValidation.warning(msg);
        } catch (InterruptedException e) {
            return FormValidation.ok(Messages.FilePath_did_not_manage_to_validate_may_be_too_sl(value));
        }
    }

    /**
     * Validates a relative file path from this {@link FilePath}.
     * Requires configure permission on ancestor AbstractProject object in request,
     * or admin permission if no such ancestor is found.
     *
     * @param value
     *      The relative path being validated.
     * @param errorIfNotExist
     *      If true, report an error if the given relative path doesn't exist. Otherwise it's a warning.
     * @param expectingFile
     *      If true, we expect the relative path to point to a file.
     *      Otherwise, the relative path is expected to be pointing to a directory.
     */
    public FormValidation validateRelativePath(String value, boolean errorIfNotExist, boolean expectingFile) throws IOException {
        checkPermissionForValidate();

        value = fixEmpty(value);

        // none entered yet, or something is seriously wrong
        if(value==null) return FormValidation.ok();

        // a common mistake is to use wildcard
        if(value.contains("*")) return FormValidation.error(Messages.FilePath_validateRelativePath_wildcardNotAllowed());

        try {
            if(!exists())    // no base directory. can't check
                return FormValidation.ok();

            FilePath path = child(value);
            if(path.exists()) {
                if (expectingFile) {
                    if(!path.isDirectory())
                        return FormValidation.ok();
                    else
                        return FormValidation.error(Messages.FilePath_validateRelativePath_notFile(value));
                } else {
                    if(path.isDirectory())
                        return FormValidation.ok();
                    else
                        return FormValidation.error(Messages.FilePath_validateRelativePath_notDirectory(value));
                }
            }

            String msg = expectingFile ? Messages.FilePath_validateRelativePath_noSuchFile(value) : 
                Messages.FilePath_validateRelativePath_noSuchDirectory(value);
            if(errorIfNotExist)     return FormValidation.error(msg);
            else                    return FormValidation.warning(msg);
        } catch (InterruptedException e) {
            return FormValidation.ok();
        }
    }

    private static void checkPermissionForValidate() {
        AccessControlled subject = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
        if (subject == null)
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        else
            subject.checkPermission(Item.CONFIGURE);
    }

    /**
     * A convenience method over {@link #validateRelativePath(String, boolean, boolean)}.
     */
    public FormValidation validateRelativeDirectory(String value, boolean errorIfNotExist) throws IOException {
        return validateRelativePath(value,errorIfNotExist,false);
    }

    public FormValidation validateRelativeDirectory(String value) throws IOException {
        return validateRelativeDirectory(value,true);
    }

    @Deprecated @Override
    public String toString() {
        // to make writing JSPs easily, return local
        return remote;
    }

    public VirtualChannel getChannel() {
        if(channel!=null)   return channel;
        else                return localChannel;
    }

    /**
     * Returns true if this {@link FilePath} represents a remote file. 
     */
    public boolean isRemote() {
        return channel!=null;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        Channel target = Channel.current();

        if(channel!=null && channel!=target)
            throw new IllegalStateException("Can't send a remote FilePath to a different remote channel");

        oos.defaultWriteObject();
        oos.writeBoolean(channel==null);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        Channel channel = Channel.current();
        assert channel!=null;

        ois.defaultReadObject();
        if(ois.readBoolean()) {
            this.channel = channel;
            this.filter = null;
        } else {
            this.channel = null;
            // If the remote channel wants us to create a FilePath that points to a local file,
            // we need to make sure the access control takes place.
            // This covers the immediate case of FileCallables taking FilePath into reference closure implicitly,
            // but it also covers more general case of FilePath sent as a return value or argument.
            this.filter = SoloFilePathFilter.wrap(FilePathFilter.current());
        }
    }

    private static final long serialVersionUID = 1L;

    public static int SIDE_BUFFER_SIZE = 1024;

    private static final Logger LOGGER = Logger.getLogger(FilePath.class.getName());

    /**
     * Adapts {@link FileCallable} to {@link Callable}.
     */
    private class FileCallableWrapper<T> implements DelegatingCallable<T,IOException> {
        private final FileCallable<T> callable;
        private transient ClassLoader classLoader;

        public FileCallableWrapper(FileCallable<T> callable) {
            this.callable = callable;
            this.classLoader = callable.getClass().getClassLoader();
        }

        private FileCallableWrapper(FileCallable<T> callable, ClassLoader classLoader) {
            this.callable = callable;
            this.classLoader = classLoader;
        }

        public T call() throws IOException {
            try {
                return callable.invoke(new File(remote), Channel.current());
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

        public ClassLoader getClassLoader() {
            return classLoader;
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

    private static final Comparator<String> SHORTER_STRING_FIRST = new Comparator<String>() {
        public int compare(String o1, String o2) {
            return o1.length()-o2.length();
        }
    };

    private static final Field LINKFLAG_FIELD = getTarEntryLinkFlagField();

    private static Field getTarEntryLinkFlagField() {
        try {
            Field f = TarEntry.class.getDeclaredField("linkFlag");
            f.setAccessible(true);
            return f;
        } catch (SecurityException e) {
            throw new AssertionError(e);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Gets the {@link FilePath} representation of the "~" directory
     * (User's home directory in the Unix sense) of the given channel.
     */
    public static FilePath getHomeDirectory(VirtualChannel ch) throws InterruptedException, IOException {
        return ch.call(new MasterToSlaveCallable<FilePath,IOException>() {
            public FilePath call() throws IOException {
                return new FilePath(new File(System.getProperty("user.home")));
            }
        });
    }

    /**
     * Helper class to make it easy to send an explicit list of files using {@link FilePath} methods.
     * @since 1.532
     */
    public static final class ExplicitlySpecifiedDirScanner extends DirScanner {

        private static final long serialVersionUID = 1;

        private final Map<String,String> files;

        /**
         * Create a “scanner” (it actually does no scanning).
         * @param files a map from logical relative paths as per {@link FileVisitor#visit}, to actual relative paths within the scanned directory
         */
        public ExplicitlySpecifiedDirScanner(Map<String,String> files) {
            this.files = files;
        }

        @Override public void scan(File dir, FileVisitor visitor) throws IOException {
            for (Map.Entry<String,String> entry : files.entrySet()) {
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

    public static final LocalChannel localChannel = new LocalChannel(threadPoolForRemoting);

    private @Nonnull SoloFilePathFilter filterNonNull() {
        return filter!=null ? filter : UNRESTRICTED;
    }

    /**
     * Wraps {@link FileVisitor} to notify read access to {@link FilePathFilter}.
     */
    private FileVisitor reading(final FileVisitor v) {
        final FilePathFilter filter = FilePathFilter.current();
        if (filter==null)    return v;

        return new FileVisitor() {
            @Override
            public void visit(File f, String relativePath) throws IOException {
                filter.read(f);
                v.visit(f,relativePath);
            }

            @Override
            public void visitSymlink(File link, String target, String relativePath) throws IOException {
                filter.read(link);
                v.visitSymlink(link, target, relativePath);
            }

            @Override
            public boolean understandsSymlink() {
                return v.understandsSymlink();
            }
        };
    }

    /**
     * Pass through 'f' after ensuring that we can read that file.
     */
    private File reading(File f) {
        filterNonNull().read(f);
        return f;
    }

    /**
     * Pass through 'f' after ensuring that we can access the file attributes.
     */
    private File stating(File f) {
        filterNonNull().stat(f);
        return f;
    }

    /**
     * Pass through 'f' after ensuring that we can create that file/dir.
     */
    private File creating(File f) {
        filterNonNull().create(f);
        return f;
    }

    /**
     * Pass through 'f' after ensuring that we can write to that file.
     */
    private File writing(File f) {
        FilePathFilter filter = filterNonNull();
        if (!f.exists())
            filter.create(f);
        filter.write(f);
        return f;
    }

    /**
     * Pass through 'f' after ensuring that we can create that symlink.
     */
    private File symlinking(File f) {
        FilePathFilter filter = filterNonNull();
        if (!f.exists())
            filter.create(f);
        filter.symlink(f);
        return f;
    }

    /**
     * Pass through 'f' after ensuring that we can delete that file.
     */
    private File deleting(File f) {
        filterNonNull().delete(f);
        return f;
    }

    private boolean mkdirs(File dir) {
        if (dir.exists())   return false;

        filterNonNull().mkdirs(dir);
        return dir.mkdirs();
    }

    private File mkdirsE(File dir) throws IOException {
        if (dir.exists()) {
            return dir;
        }
        filterNonNull().mkdirs(dir);
        return IOUtils.mkdirs(dir);
    }

    private static final SoloFilePathFilter UNRESTRICTED = SoloFilePathFilter.wrap(FilePathFilter.UNRESTRICTED);
}
