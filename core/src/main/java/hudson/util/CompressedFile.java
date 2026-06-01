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

import hudson.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Represents write-once read-many file that can be optionally compressed
 * to save disk space. This is used for console output and other bulky data.
 *
 * <p>
 * In this class, the data on the disk can be one of two states:
 * <ol>
 * <li>Uncompressed, in which case the original data is available in the specified file name.
 * <li>Compressed, in which case the gzip-compressed data is available in the specified file name + ".gz" extension.
 * </ol>
 *
 * Once the file is written and completed, it can be compressed asynchronously
 * by {@link #compress()}.
 *
 * @author Kohsuke Kawaguchi
 */
public class CompressedFile {
    /**
     * The name of the raw file.
     */
    private final File file;

    /**
     * The name of the compressed file.
     */
    private final File gz;

    public CompressedFile(File file) {
        this.file = file;
        this.gz = new File(file.getParentFile(), file.getName() + ".gz");
    }

    /**
     * Gets the OutputStream to write to the file.
     */
    public OutputStream write() throws IOException {
        Files.deleteIfExists(Util.fileToPath(gz));
        return Files.newOutputStream(Util.fileToPath(file));
    }

    /**
     * Reads the contents of a file.
     */
    public InputStream read() throws IOException {
        if (Files.exists(Util.fileToPath(file))) {
            return Files.newInputStream(Util.fileToPath(file));
        }

        // check if the compressed file exists
        if (Files.exists(Util.fileToPath(gz))) {
            return new GZIPInputStream(Files.newInputStream(Util.fileToPath(gz)));
        }

        // no such file
        throw new FileNotFoundException(file.getName());
    }

    /**
     * Loads the file content as a string.
     * @deprecated removed without replacement
     */
    @Deprecated
    public String loadAsString() throws IOException {
        long sizeGuess;
        if (file.exists())
            sizeGuess = file.length();
        else
        if (gz.exists())
            sizeGuess = gz.length() * 2;
        else
            return "";

        StringBuilder str = new StringBuilder((int) sizeGuess);

        try (InputStream is = read();
             Reader r = new InputStreamReader(is, Charset.defaultCharset())) {
            char[] buf = new char[8192];
            int len;
            while ((len = r.read(buf, 0, buf.length)) > 0)
                str.append(buf, 0, len);
        }

        return str.toString();
    }

    /**
     * Asynchronously schedules the compression of this file.
     *
     * <p>
     * Once the file is compressed, the original will be removed and
     * the further reading will be done from the compressed stream.
     */
    public void compress() {
        compressionThread.submit(() -> {
            boolean success;
            try (InputStream in = read();
                 OutputStream os = Files.newOutputStream(gz.toPath());
                 OutputStream out = new GZIPOutputStream(os)) {
                org.apache.commons.io.IOUtils.copy(in, out);
                out.flush();
                success = true;
            } catch (IOException | InvalidPathException e) {
                LOGGER.log(Level.WARNING, "Failed to compress " + file, e);
                success = false;
            }

            File fileToDelete;
            if (success) {
                // if the compressed file is created successfully, remove the original
                fileToDelete = file;
            } else {
                // in case a processing is left in the middle
                fileToDelete = gz;
            }
            try {
                Files.deleteIfExists(fileToDelete.toPath());
            } catch (IOException | InvalidPathException e) {
                LOGGER.log(Level.WARNING, "Failed to delete " + fileToDelete, e);
            }
        });
    }

    /**
     * Executor used for compression. Limited up to one thread since
     * this should be a fairly low-priority task.
     */
    private static final ExecutorService compressionThread = new ThreadPoolExecutor(
        0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
        new ExceptionCatchingThreadFactory(new NamingThreadFactory(new DaemonThreadFactory(), "CompressedFile")));

    private static final Logger LOGGER = Logger.getLogger(CompressedFile.class.getName());
}
