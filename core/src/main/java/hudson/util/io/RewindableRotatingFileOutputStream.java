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

import hudson.Util;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ReopenableFileOutputStream} that does log rotation upon rewind.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.18
 */
public class RewindableRotatingFileOutputStream extends RewindableFileOutputStream {
    private static final Logger LOGGER = Logger.getLogger(RewindableRotatingFileOutputStream.class.getName());

    /**
     * Number of log files to keep.
     */
    private final int size;

    public RewindableRotatingFileOutputStream(File out, int size) {
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
            if (Files.exists(Util.fileToPath(fi))) {
                File next = getNumberedFileName(i+1);
                Files.move(Util.fileToPath(fi), Util.fileToPath(next), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Deletes all the log files, including rotated files.
     */
    public void deleteAll() {
        for (int i=0; i<=size; i++) {
            try {
                Files.deleteIfExists(getNumberedFileName(i).toPath());
            } catch (IOException | InvalidPathException e) {
                LOGGER.log(Level.WARNING, null, e);
            }
        }
    }
}
