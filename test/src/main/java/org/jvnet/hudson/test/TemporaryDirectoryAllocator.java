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
        File f = File.createTempFile("hudson", "test", base);
        f.delete();
        f.mkdirs();
        tmpDirectories.add(f);
        return f;
    }

    /**
     * Deletes all allocated temporary directories.
     */
    public synchronized void dispose() throws IOException, InterruptedException {
        for (File dir : tmpDirectories)
            new FilePath(dir).deleteRecursive();
        tmpDirectories.clear();
    }

    /**
     * Deletes all allocated temporary directories asynchronously.
     */
    public synchronized void disposeAsync() {
        final Set<File> tbr = new HashSet<File>(tmpDirectories);
        tmpDirectories.clear();

        new Thread("Disposing "+base) {
            public void run() {
                try {
                    for (File dir : tbr)
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
