/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.util.io;

import hudson.Functions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Helper class for tracking which files are locked.
 * Only useful in Windows environments as POSIX seems to allow locked files to be deleted.
 */
public class FileLocker implements AutoCloseable {
    private final Map<File, AutoCloseable> locks = new HashMap<>();

    public synchronized AutoCloseable acquireLock(File file) throws IOException {
        assertTrue(Functions.isWindows());
        assertThat(file + " is already locked.", locks, not(hasKey(file)));
        AutoCloseable lock = new FileInputStream(file);
        locks.put(file, lock);
        return lock;
    }

    public synchronized void releaseLock(File file) throws Exception {
        assertThat(file + " is not locked.", locks, hasKey(file));
        locks.get(file).close();
    }

    @Override
    public synchronized void close() throws Exception {
        while (!locks.isEmpty()) {
            releaseLock(locks.keySet().iterator().next());
        }
    }
}
