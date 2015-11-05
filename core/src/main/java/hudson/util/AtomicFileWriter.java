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
    private final File tmpFile;
    private final File destFile;

    /**
     * Writes with UTF-8 encoding.
     */
    public AtomicFileWriter(File f) throws IOException {
        this(f,"UTF-8");
    }

    /**
     * @param encoding
     *      File encoding to write. If null, platform default encoding is chosen.
     */
    public AtomicFileWriter(File f, String encoding) throws IOException {
        File dir = f.getParentFile();
        try {
            dir.mkdirs();
            tmpFile = File.createTempFile("atomic",null, dir);
        } catch (IOException e) {
            throw new IOException("Failed to create a temporary file in "+ dir,e);
        }
        destFile = f;
        if (encoding==null)
            encoding = Charset.defaultCharset().name();
        core = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile),encoding));
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
        tmpFile.delete();
    }

    public void commit() throws IOException {
        close();
        if (destFile.exists()) {
            try {
                Util.deleteFile(destFile);
            } catch (IOException x) {
                tmpFile.delete();
                throw x;
            }
        }
        tmpFile.renameTo(destFile);
    }

    @Override
    protected void finalize() throws Throwable {
        // one way or the other, temporary file should be deleted.
        close();
        tmpFile.delete();
    }

    /**
     * Until the data is committed, this file captures
     * the written content.
     */
    public File getTemporaryFile() {
        return tmpFile;
    }
}
