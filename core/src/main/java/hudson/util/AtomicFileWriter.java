/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Functions;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.ref.Cleaner;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;

/**
 * Buffered {@link FileWriter} that supports atomic operations.
 *
 * <p>
 * The write operation is atomic when used for overwriting;
 * it either leaves the original file intact, or it completely rewrites it with new contents.
 *
 * @author Kohsuke Kawaguchi
 */
public class AtomicFileWriter extends Writer {

    private static final Logger LOGGER = Logger.getLogger(AtomicFileWriter.class.getName());

    private static final Cleaner CLEANER = Cleaner.create(
            new NamingThreadFactory(new DaemonThreadFactory(), AtomicFileWriter.class.getName() + ".cleaner"));

    private static /* final */ boolean DISABLE_FORCED_FLUSH = SystemProperties.getBoolean(
            AtomicFileWriter.class.getName() + ".DISABLE_FORCED_FLUSH");

    private static /* final */ boolean REQUIRES_DIR_FSYNC = SystemProperties.getBoolean(
            AtomicFileWriter.class.getName() + ".REQUIRES_DIR_FSYNC", !Functions.isWindows());

    /**
     * Whether the platform supports atomic move.
     */
    private static boolean atomicMoveSupported = true;

    static {
        if (DISABLE_FORCED_FLUSH) {
            LOGGER.log(Level.WARNING, "DISABLE_FORCED_FLUSH flag used, this could result in dataloss if failures happen in your storage subsystem.");
        }
    }

    private final FileChannelWriter core;
    private final Path tmpPath;
    private final Path destPath;

    /**
     * Writes with UTF-8 encoding.
     */
    public AtomicFileWriter(File f) throws IOException {
        this(toPath(f), StandardCharsets.UTF_8);
    }

    /**
     * @param encoding File encoding to write. If null, platform default encoding is chosen.
     *
     * @deprecated Use {@link #AtomicFileWriter(Path, Charset)}
     */
    @Deprecated
    public AtomicFileWriter(@NonNull File f, @Nullable String encoding) throws IOException {
        this(toPath(f), encoding == null ? Charset.defaultCharset() : Charset.forName(encoding));
    }

    /**
     * Wraps potential {@link java.nio.file.InvalidPathException} thrown by {@link File#toPath()} in an
     * {@link IOException} for backward compatibility.
     *
     * @param file file to obtain the path of
     * @return the path for that file
     * @see File#toPath()
     */
    private static Path toPath(@NonNull File file) throws IOException {
        try {
            return file.toPath();
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    /**
     * @param destinationPath the destination path where to write the content when committed.
     * @param charset File charset to write.
     */
    public AtomicFileWriter(@NonNull Path destinationPath, @NonNull Charset charset) throws IOException {
        // See FileChannelWriter docs to understand why we do not cause a force() call on flush() from AtomicFileWriter.
        this(destinationPath, charset, false, true);
    }

    /**
     * <strong>DO NOT USE THIS METHOD, OR YOU WILL LOSE DATA INTEGRITY.</strong>
     *
     * @param destinationPath the destination path where to write the content when committed.
     * @param charset File charset to write.
     * @param integrityOnFlush do not force writing to disk when flushing
     * @param integrityOnClose do not force writing to disk when closing
     * @deprecated use {@link AtomicFileWriter#AtomicFileWriter(Path, Charset)}
     */
    @Deprecated
    public AtomicFileWriter(@NonNull Path destinationPath, @NonNull Charset charset, boolean integrityOnFlush, boolean integrityOnClose) throws IOException {
        if (charset == null) { // be extra-defensive if people don't care
            throw new IllegalArgumentException("charset is null");
        }
        this.destPath = destinationPath;
        Path dir = this.destPath.getParent();

        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new IOException(dir + " exists and is neither a directory nor a symlink to a directory");
        }
        else {
            if (Files.isSymbolicLink(dir)) {
                LOGGER.log(Level.CONFIG, "{0} is a symlink to a directory", dir);
            } else {
                Files.createDirectories(dir); // Cannot be called on symlink, so we are pretty defensive...
            }
        }

        try {
            // JENKINS-48407: NIO's createTempFile creates file with 0600 permissions, so we use pre-NIO for this...
            tmpPath = File.createTempFile(destPath.getFileName() + "-atomic", "tmp", dir.toFile()).toPath();
        } catch (IOException e) {
            throw new IOException("Failed to create a temporary file in " + dir, e);
        }

        if (DISABLE_FORCED_FLUSH) {
            integrityOnFlush = false;
            integrityOnClose = false;
        }

        core = new FileChannelWriter(tmpPath, charset, integrityOnFlush, integrityOnClose, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        CLEANER.register(this, new CleanupChecker(core, tmpPath, destPath));
    }

    @Override
    public void write(int c) throws IOException {
        core.write(c);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        core.write(str, off, len);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        core.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
        core.flush();
    }

    @Override
    public void close() throws IOException {
        core.close();
    }

    /**
     * When the write operation failed, call this method to
     * leave the original file intact and remove the temporary file.
     * This method can be safely invoked from the "finally" block, even after
     * the {@link #commit()} is called, to simplify coding.
     */
    public void abort() throws IOException {
        // One way or another, the temporary file should be deleted.
        try {
            close();
        } finally {
            Files.deleteIfExists(tmpPath);
        }
    }

    public void commit() throws IOException {
        close();
        try {
            move(tmpPath, destPath);
        } finally {
            try {
                // In case of prior failure, the temporary file should be deleted.
                // If the operation succeeded, the tmpPath is already deleted.
                Files.deleteIfExists(tmpPath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, e, () -> "Failed to delete temporary file " + tmpPath + " for destination file " + destPath);
            }
        }

        /*
         * From fsync(2) on Linux:
         *
         *     Calling fsync() does not necessarily ensure that the entry in the directory containing the file has also
         *     reached disk. For that an explicit fsync() on a file descriptor for the directory is also needed.
         */
        if (!DISABLE_FORCED_FLUSH && REQUIRES_DIR_FSYNC) {
            try (FileChannel parentChannel = FileChannel.open(destPath.getParent())) {
                parentChannel.force(true);
            }
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        if (atomicMoveSupported) {
            try {
                // Try to make an atomic move.
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AtomicMoveNotSupportedException e) {
                // Both files are on the same filesystem, so this should not happen.
                LOGGER.log(Level.WARNING, e, () -> "Atomic move " + source + " → " + destination + " not supported. Falling back to non-atomic move.");
                atomicMoveSupported = false;
            }
        }
        if (!atomicMoveSupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class CleanupChecker implements Runnable {
        private final FileChannelWriter core;
        private final Path tmpPath;
        private final Path destPath;

        CleanupChecker(final FileChannelWriter core, final Path tmpPath, final Path destPath) {
            this.core = core;
            this.tmpPath = tmpPath;
            this.destPath = destPath;
        }

        @Override
        public void run() {
            if (core.isOpen()) {
                LOGGER.log(Level.WARNING, "AtomicFileWriter for " + destPath + " was not closed before being released");
                try {
                    core.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to close " + tmpPath + " for destination file " + destPath, e);
                }
            }
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete temporary file " + tmpPath + " for destination file " + destPath, e);
            }
        }
    }

    /**
     * Until the data is committed, this file captures
     * the written content.
     *
     * @deprecated Use getTemporaryPath()
     */
    @Deprecated
    public File getTemporaryFile() {
        return tmpPath.toFile();
    }

    /**
     * Until the data is committed, this file captures
     * the written content.
     * @since 2.93
     */
    public Path getTemporaryPath() {
        return tmpPath;
    }
}
