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
import org.junit.rules.ExternalResource;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Helper class for tracking which files are locked.
 * Only useful in Windows environments as POSIX seems to allow locked files to be deleted.
 */
public class FileLockerRule extends ExternalResource {
    private final Map<File, Closeable> locks = new HashMap<>();

    @Override
    protected void after() {
        List<IOException> exceptions = new ArrayList<>();
        Iterator<Closeable> it = locks.values().iterator();
        while (it.hasNext()) {
            try (Closeable ignored = it.next()) {
                it.remove();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty())
            throw new CompositeIOException("Could not unlock all files", exceptions).asUncheckedIOException();
    }

    public synchronized void acquireLock(@NonNull File file) throws IOException {
        assertTrue(Functions.isWindows());
        assertThat(file + " is already locked.", locks, not(hasKey(file)));
        Closeable lock = new FileInputStream(file);
        locks.put(file, lock);
    }

    public synchronized void releaseLock(@NonNull File file) throws Exception {
        assertTrue(Functions.isWindows());
        assertThat(file + " is not locked.", locks, hasKey(file));
        locks.remove(file).close();
    }
}
