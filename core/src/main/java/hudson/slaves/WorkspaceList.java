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
import hudson.Functions;
import hudson.model.Computer;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used by {@link Computer} to keep track of workspaces that are actively in use.
 *
 * <p>
 * SUBJECT TO CHANGE! Do not use this from plugins directly.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 * @see Computer#getWorkspaceList()
 */
public final class WorkspaceList {
    /**
     * Book keeping for workspace allocation.
     */
    public static final class Entry {
        /**
         * Who acquired this workspace?
         */
        public final Thread holder = Thread.currentThread();

        /**
         * When?
         */
        public final long time = System.currentTimeMillis();

        /**
         * From where?
         */
        public final Exception source = new Exception();

        /**
         * True makes the caller of {@link WorkspaceList#allocate(FilePath)} wait
         * for this workspace.
         */
        public final boolean quick;

        public final FilePath path;

        private Entry(FilePath path, boolean quick) {
            this.path = path;
            this.quick = quick;
        }

        @Override
        public String toString() {
            String s = path+" owned by "+holder.getName()+" from "+new Date(time);
            if(quick) s+=" (quick)";
            s+="\n"+Functions.printThrowable(source);
            return s;
        }
    }

    /**
     * Represents a leased workspace that needs to be returned later.
     */
    public static abstract class Lease {
        public final FilePath path;

        protected Lease(FilePath path) {
            this.path = path;
        }

        /**
         * Releases this lease.
         */
        public abstract void release();

        /**
         * Creates a dummy {@link Lease} object that does no-op in the release.
         */
        public static Lease createDummyLease(FilePath p) {
            return new Lease(p) {
                public void release() {
                    // noop
                }
            };
        }
    }

    private final Map<FilePath,Entry> inUse = new HashMap<FilePath,Entry>();

    public WorkspaceList() {
    }

    /**
     * Allocates a workspace by adding some variation to the given base to make it unique.
     */
    public synchronized Lease allocate(FilePath base) throws InterruptedException {
        for (int i=1; ; i++) {
            FilePath candidate = i==1 ? base : base.withSuffix(COMBINATOR+i);
            Entry e = inUse.get(candidate);
            if(e!=null && !e.quick)
                continue;
            return acquire(candidate);
        }
    }

    /**
     * Just record that this workspace is being used, without paying any attention to the sycnhronization support.
     */
    public synchronized Lease record(FilePath p) {
        log("recorded  "+p);
        Entry old = inUse.put(p, new Entry(p, false));
        if (old!=null)
            throw new AssertionError("Tried to record a workspace already owned: "+old);
        return lease(p);
    }

    /**
     * Releases an allocated or acquired workspace.
     */
    private synchronized void _release(FilePath p) {
        Entry old = inUse.remove(p);
        if (old==null)
            throw new AssertionError("Releasing unallocated workspace "+p);
        notifyAll();
    }

    /**
     * Acquires the given workspace. If necessary, this method blocks until it's made available.
     *
     * @return
     *      The same {@link FilePath} as given to this method.
     */
    public synchronized Lease acquire(FilePath p) throws InterruptedException {
        return acquire(p,false);
    }

    /**
     * See {@link #acquire(FilePath)}
     *
     * @param quick
     *      If true, indicates that the acquired workspace will be returned quickly.
     *      This makes other calls to {@link #allocate(FilePath)} to wait for the release of this workspace.
     */
    public synchronized Lease acquire(FilePath p, boolean quick) throws InterruptedException {
        while (inUse.containsKey(p))
            wait();
        log("acquired "+p);
        inUse.put(p,new Entry(p,quick));
        return lease(p);
    }

    /**
     * Wraps a path into a valid lease.
     */
    private Lease lease(FilePath p) {
        return new Lease(p) {
            public void release() {
                _release(path);
            }
        };
    }

    private void log(String msg) {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine(Thread.currentThread().getName() + " " + msg);
    }

    private static final Logger LOGGER = Logger.getLogger(WorkspaceList.class.getName());

    /**
     * The token that combines the project name and unique number to create unique workspace directory.
     */
    private static final String COMBINATOR = System.getProperty(WorkspaceList.class.getName(),"@");
}
