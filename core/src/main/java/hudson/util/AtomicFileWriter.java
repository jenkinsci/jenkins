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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;

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

    private final Writer core;
    private final Path tmpFile;
    private final Path destFile;

    /**
     * Writes with UTF-8 encoding.
     */
    public AtomicFileWriter(File f) throws IOException {
        this(f,"UTF-8");
    }

    /**
     * @param encoding File encoding to write. If null, platform default encoding is chosen.
     *
     * @deprecated Use {@link #AtomicFileWriter(File, Charset)}
     */
    public AtomicFileWriter(File f, String encoding) throws IOException {
        this(f, Charset.forName(encoding));
    }

        /**
         * @param charset File charset to write. If null, platform default encoding is chosen.
         */
    public AtomicFileWriter(File f, Charset charset) throws IOException {
        destFile = f.toPath();
        Path dir = destFile.getParent();
        try {
            if (Files.notExists(dir)) {
                Files.createDirectories(dir);
            }
            tmpFile = Files.createTempFile(dir, "atomic", "tmp");
        } catch (IOException e) {
            throw new IOException("Failed to create a temporary file in "+ dir,e);
        }
        if (charset==null)
            charset = Charset.defaultCharset();
        core = Files.newBufferedWriter(tmpFile, charset, StandardOpenOption.SYNC);
    }

    @Override
    public void write(int c) throws IOException {
        core.write(c);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        core.write(str,off,len);
    }

    public void write(char cbuf[], int off, int len) throws IOException {
        core.write(cbuf,off,len);
    }

    public void flush() throws IOException {
        core.flush();
    }

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
        close();
        Files.deleteIfExists(tmpFile);
    }

    public void commit() throws IOException {
        close();
        try {
            // Try to make an atomic move.
            Files.move(tmpFile, destFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // If it falls here that means that Atomic move is not supported by the OS.
            // In this case we need to fall-back to a copy option which is supported by all OSes.
            Files.move(tmpFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        // one way or the other, temporary file should be deleted.
        close();
        Files.deleteIfExists(tmpFile);
    }

    /**
     * Until the data is committed, this file captures
     * the written content.
     *
     * @deprecated Use getTemporaryPath() for JDK 7+
     */
    @Deprecated
    public File getTemporaryFile() {
        return tmpFile.toFile();
    }

    /**
     * Until the data is committed, this file captures
     * the written content.
     *
     */
    public Path getTemporaryPath() {
        return tmpFile;
    }
}
