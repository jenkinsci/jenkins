/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.slaves;

import hudson.FilePath;
import hudson.model.Computer;

import java.util.HashSet;
import java.util.Set;

/**
 * Used by {@link Computer} to keep track of workspaces that are actively in use.
 *
 * <p>
 * SUBJECT TO CHANGE! Do not use this from plugins directly.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.XXX
 * @see Computer#getWorkspaceList()
 */
public final class WorkspaceList {
    private final Set<FilePath> inUse = new HashSet<FilePath>();

    public WorkspaceList() {
    }

    /**
     * Allocates some workspace by adding some variation to the given base if necessary.
     */
    public synchronized FilePath allocate(FilePath base) {
        for (int i=1; ; i++) {
            FilePath candidate = i==1 ? base : base.withSuffix("@"+i);
            if(inUse.contains(candidate))
                continue;
            inUse.add(candidate);
            return candidate;
        }
    }

    /**
     * Just record that this workspace is being used, without paying any attention to the sycnhronization support.
     */
    public synchronized FilePath record(FilePath p) {
        inUse.add(p);
        return p;
    }

    /**
     * Releases an allocated or acquired workspace.
     */
    public synchronized void release(FilePath p) {
        if (!inUse.remove(p))
            throw new AssertionError("Releasing unallocated workspace "+p);
        notifyAll();
    }

    /**
     * Acquires the given workspace. If necessary, this method blocks until it's made available.
     *
     * @return
     *      The same {@link FilePath} as given to this method.
     */
    public synchronized FilePath acquire(FilePath p) throws InterruptedException {
        while (inUse.contains(p))
            wait();
        inUse.add(p);
        return p;
    }
}
