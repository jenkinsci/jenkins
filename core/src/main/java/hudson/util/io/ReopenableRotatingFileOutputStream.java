/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
import java.io.IOException;

/**
 * {@link ReopenableFileOutputStream} that does log rotation upon rewind.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.416
 */
public class ReopenableRotatingFileOutputStream extends ReopenableFileOutputStream {
    /**
     * Number of log files to keep.
     */
    private final int size;

    public ReopenableRotatingFileOutputStream(File out, int size) {
        super(out);
        this.size = size;
    }

    protected File getNumberedFileName(int n) {
        if (n==0)   return out;
        return new File(out.getPath()+"."+n);
    }

    @Override
    public void rewind() throws IOException {
        super.rewind();
        for (int i=size-1;i>=0;i--) {
            File fi = getNumberedFileName(i);
            if (fi.exists()) {
                File next = getNumberedFileName(i+1);
                next.delete();
                fi.renameTo(next);
            }
        }
    }

    /**
     * Deletes all the log files, including rotated files.
     */
    public void deleteAll() {
        for (int i=0; i<=size; i++) {
            getNumberedFileName(i).delete();
        }
    }
}
