/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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
package hudson.util.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} that writes to a file.
 *
 * <p>
 * Unlike regular {@link FileOutputStream}, this implementation allows the caller to close,
 * and then keep writing.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated due to risk for file leak. Prefer {@link RewindableFileOutputStream}
 */
@Deprecated public class ReopenableFileOutputStream extends OutputStream {
    protected final File out;

    private OutputStream current;
    private boolean appendOnNextOpen = false;

    public ReopenableFileOutputStream(File out) {
        this.out = out;
    }

    private synchronized OutputStream current() throws IOException {
        if (current==null)
            try {
                current = new FileOutputStream(out,appendOnNextOpen);
            } catch (FileNotFoundException e) {
                throw new IOException("Failed to open "+out,e);
            }
        return current;
    }

    @Override
    public void write(int b) throws IOException {
        current().write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        current().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        current().write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        current().flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (current!=null) {
            current.close();
            appendOnNextOpen = true;
            current = null;
        }
    }

    /**
     * In addition to close, ensure that the next "open" would truncate the file.
     */
    public synchronized void rewind() throws IOException {
        close();
        appendOnNextOpen = false;
    }
}
