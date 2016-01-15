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
package org.jvnet.hudson.test;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Allocates temporary directories and cleans it up at the end.
 * @author Kohsuke Kawaguchi
 */
public class TemporaryDirectoryAllocator {
    /**
     * Remember allocated directories to delete them later.
     */
    private final Set<File> tmpDirectories = new HashSet<File>();

    /**
     * Directory in which we allocate temporary directories.
     */
    private final File base;

    public TemporaryDirectoryAllocator(File base) {
        this.base = base;
    }

    public TemporaryDirectoryAllocator() {
        this(new File(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Allocates a new empty temporary directory and returns it.
     *
     * This directory will be wiped out when {@link TemporaryDirectoryAllocator} gets disposed.
     * When this method returns, the directory already exists. 
     */
    public synchronized File allocate() throws IOException {
        try {
            File f = File.createTempFile("hudson", "test", base);
            f.delete();
            f.mkdirs();
            tmpDirectories.add(f);
            return f;
        } catch (IOException e) {
            throw new IOException("Failed to create a temporary directory in "+base,e);
        }
    }

    /**
     * Deletes all allocated temporary directories.
     */
    public synchronized void dispose() throws IOException, InterruptedException {
        IOException x = null;
        for (File dir : tmpDirectories)
            try {
                new FilePath(dir).deleteRecursive();
            } catch (IOException e) {
                x = e;
            }
        tmpDirectories.clear();
        if (x!=null)    throw new IOException("Failed to clean up temp dirs",x);
    }

    /**
     * Deletes all allocated temporary directories asynchronously.
     */
    public synchronized void disposeAsync() {
        final Set<File> tbr = new HashSet<File>(tmpDirectories);
        tmpDirectories.clear();

        new Thread("Disposing "+base) {
            public void run() {
                for (File dir : tbr)
                    try {
                        new FilePath(dir).deleteRecursive();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }
        }.start();
    }
}
