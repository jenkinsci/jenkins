/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model.dashboard;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.Terminator;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.access.AccessDeniedException;

/**
 * Remembers the last completed build of every job, for {@link RecentTab}.
 *
 * <p>Each job has one entry, so a few busy jobs cannot push out everything else, and users who can only see
 * a few jobs still find their recent builds however busy the rest of the instance is. Like
 * {@link Job#getLastCompletedBuild()}, the entry is the job's highest-numbered completed build, also when builds
 * complete out of order. Runs are held by job full name and build number rather than by reference, so this does not
 * keep builds in memory, and an entry takes about a hundred bytes.
 *
 * <p>The index starts empty. The first read fills it in the background from each job's last completed
 * build, so startup does not load any builds. Reads see the jobs read so far while it fills.
 */
@Extension
@Restricted(NoExternalUse.class)
public final class RecentRuns extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(RecentRuns.class.getName());

    /**
     * An entry of the index, ordered newest first, so that a {@link PriorityQueue} of entries can be built in linear
     * time.
     */
    record Ref(String job, int number, long endTime) implements Comparable<Ref> {
        static Ref of(Run<?, ?> run) {
            return new Ref(run.getParent().getFullName(), run.getNumber(), run.getStartTimeInMillis() + run.getDuration());
        }

        @Override
        public int compareTo(Ref other) {
            return NEWEST_FIRST.compare(this, other);
        }
    }

    private static final Comparator<Ref> NEWEST_FIRST = Comparator.comparingLong(Ref::endTime)
            .reversed()
            .thenComparing(Ref::job)
            .thenComparingInt(Ref::number);

    /**
     * Keyed by job full name, in name order, so that the jobs inside an item group are next to each other. Changes to
     * it and {@link #newestFirst} are made together, holding its lock.
     */
    private final ConcurrentNavigableMap<String, Ref> refs = new ConcurrentSkipListMap<>();

    /**
     * The same entries in the order they are read, so that reads neither copy nor sort the index. Reads
     * iterate without the lock, and skip entries that {@link #refs} no longer holds.
     */
    private final NavigableSet<Ref> newestFirst = new ConcurrentSkipListSet<>(NEWEST_FIRST);

    private final AtomicBoolean seeded = new AtomicBoolean();

    /** Whether the index is being filled from existing builds. */
    private volatile boolean loading;

    /** Set when Jenkins shuts down, to stop filling the index and not start again. */
    private volatile boolean stopped;

    /** The thread filling the index, if any. */
    private volatile Thread seeder;

    public static RecentRuns get() {
        return ExtensionList.lookupSingleton(RecentRuns.class);
    }

    @Override
    public void onCompleted(Run<?, ?> run, @NonNull TaskListener listener) {
        add(run);
    }

    @Override
    public void onDeleted(Run<?, ?> run) {
        String job = run.getParent().getFullName();
        synchronized (refs) {
            Ref ref = refs.get(job);
            if (ref == null || ref.number() != run.getNumber()) {
                return;
            }
            remove(ref);
        }
        Run<?, ?> previous = run.getPreviousCompletedBuild();
        if (previous != null) {
            add(previous);
        }
    }

    /**
     * Records a completed run. The job's name is read holding the lock, so that a rename that completes first has
     * already moved the job's entry and the run is recorded under the new name.
     */
    private void add(Run<?, ?> run) {
        synchronized (refs) {
            add(Ref.of(run));
        }
    }

    void add(Ref ref) {
        synchronized (refs) {
            Ref existing = refs.get(ref.job());
            if (existing != null && existing.number() > ref.number()) {
                return;
            }
            if (existing != null) {
                newestFirst.remove(existing);
            }
            refs.put(ref.job(), ref);
            newestFirst.add(ref);
        }
    }

    /** Call holding the lock on {@link #refs}. */
    private void remove(Ref ref) {
        if (refs.remove(ref.job(), ref)) {
            newestFirst.remove(ref);
        }
    }

    /**
     * Follows a renamed or moved item. Moving a folder notifies each item inside it too, so only the entry of the
     * item itself is moved.
     */
    private void moved(String oldFullName, String newFullName) {
        synchronized (refs) {
            Ref ref = refs.get(oldFullName);
            if (ref != null) {
                remove(ref);
                // A build may already be recorded under the new name, so keep whichever is newer
                add(new Ref(newFullName, ref.number(), ref.endTime()));
            }
        }
    }

    private void deleted(Item item) {
        String fullName = item.getFullName();
        synchronized (refs) {
            if (item instanceof Job) {
                Ref ref = refs.get(fullName);
                if (ref != null) {
                    remove(ref);
                }
                return;
            }
            // Folders usually delete their items one by one first, but not every item group does
            for (Ref ref : List.copyOf(inside(fullName + '/'))) {
                remove(ref);
            }
        }
    }

    /** The entries of the jobs whose full names start with a prefix. */
    private Collection<Ref> inside(String prefix) {
        return refs.subMap(prefix, true, prefix + Character.MAX_VALUE, false).values();
    }

    /**
     * The entries inside an item group, newest first. Rather than sorting them all, they are put in a queue, which
     * takes linear time, and each is taken from it as it is read, as reading usually stops after the first few.
     */
    private Iterable<Ref> newestFirstInside(String prefix) {
        // Built from the collection in one go, in the natural order of the entries, rather than one entry at a time
        PriorityQueue<Ref> queue = new PriorityQueue<>(inside(prefix));
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }

            @Override
            public Ref next() {
                Ref next = queue.poll();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                return next;
            }
        };
    }

    /**
     * The most recently completed runs of jobs inside an item group that the current user can see, newest first,
     * one per job.
     *
     * @param limit the maximum number of runs to return
     * @param prefix the prefix of the full names of the jobs to include, such as {@code team/}, or empty for
     *     every job
     */
    @NonNull
    public List<Run<?, ?>> getCompleted(int limit, @NonNull String prefix) {
        seedOnce();
        Iterable<Ref> candidates = prefix.isEmpty() ? newestFirst : newestFirstInside(prefix);
        ReadableFolders folders = new ReadableFolders();
        List<Run<?, ?>> result = new ArrayList<>();
        for (Ref ref : candidates) {
            if (result.size() >= limit) {
                break;
            }
            // The folder first, as it is remembered, and most entries are in folders the user cannot read or that
            // they have seen already
            int slash = ref.job().lastIndexOf('/');
            ItemGroup<?> parent = slash < 0 ? Jenkins.get() : folders.get(ref.job().substring(0, slash));
            if (parent == null) {
                continue;
            }
            if (refs.get(ref.job()) != ref) {
                // Replaced or removed since the iteration started
                continue;
            }
            if (!(ReadableFolders.readableItem(parent, ref.job().substring(slash + 1)) instanceof Job<?, ?> job)) {
                continue;
            }
            Run<?, ?> run = job.getBuildByNumber(ref.number());
            if (run == null) {
                // The build is gone, for example deleted while the index was being filled. The job's current last
                // completed build takes its place from the next read.
                synchronized (refs) {
                    remove(ref);
                }
                Run<?, ?> last = lastCompletedBuild(job);
                if (last != null) {
                    add(last);
                }
            } else if (!run.isBuilding()) {
                result.add(run);
            }
        }
        return result;
    }

    /**
     * The folders the current user can read, by full name, each found in the folder containing it and remembered,
     * so that a user who can only see a few folders checks each folder once, and the folders inside a folder they
     * cannot read not at all.
     */
    private static final class ReadableFolders {
        /** Null for a folder the user cannot read. */
        private final Map<String, ItemGroup<?>> folders = new HashMap<>();

        @CheckForNull
        ItemGroup<?> get(String fullName) {
            if (folders.containsKey(fullName)) {
                return folders.get(fullName);
            }
            int slash = fullName.lastIndexOf('/');
            ItemGroup<?> parent = slash < 0 ? Jenkins.get() : get(fullName.substring(0, slash));
            ItemGroup<?> folder = parent != null
                    && readableItem(parent, fullName.substring(slash + 1)) instanceof ItemGroup<?> group ? group : null;
            folders.put(fullName, folder);
            return folder;
        }

        /**
         * An item in a group, if the current user can read it. Checks the item's own permission, as not every item
         * group checks it for the items it returns.
         */
        static @CheckForNull Item readableItem(ItemGroup<?> group, String name) {
            try {
                Item item = group.getItem(name);
                return item != null && item.hasPermission(Item.READ) ? item : null;
            } catch (AccessDeniedException e) {
                return null;
            }
        }
    }

    /**
     * Whether the index is still being filled from existing builds, so that it may be missing older builds.
     */
    public boolean isLoading() {
        return loading;
    }

    private void seedOnce() {
        if (!stopped && seeded.compareAndSet(false, true)) {
            loading = true;
            Thread thread = new Thread(this::seed, "Dashboard recent builds");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            seeder = thread;
            thread.start();
        }
    }

    /**
     * Stops filling the index when Jenkins shuts down, before plugins are stopped.
     */
    @Terminator
    public static void stop() {
        RecentRuns recentRuns = ExtensionList.lookupSingleton(RecentRuns.class);
        recentRuns.stopped = true;
        Thread thread = recentRuns.seeder;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * Fills the index from the last completed build of every job, adding each job as it is read. Runs as the
     * system user, because the index is shared by all users and each read filters by the reader's permissions.
     * If it fails, the next read tries again.
     */
    @SuppressWarnings("rawtypes")
    void seed() {
        boolean done = false;
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            Jenkins jenkins = Jenkins.get();
            for (Job job : jenkins.allItems(Job.class)) {
                if (stopped) {
                    return;
                }
                beforeSeeding.accept(job);
                Run<?, ?> run = lastCompletedBuild(job);
                if (run == null) {
                    continue;
                }
                Ref ref = Ref.of(run);
                // The job may have been renamed, moved or deleted since it was read. Holding the lock while
                // checking means that a later change is applied by the item listener to what is added here.
                synchronized (refs) {
                    String name = job.getFullName();
                    if (jenkins.getItemByFullName(name, Job.class) == job) {
                        add(new Ref(name, ref.number(), ref.endTime()));
                    }
                }
            }
            done = true;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to load recent runs", e);
        } finally {
            // Before another attempt can start, which sets it again
            loading = false;
            seeder = null;
            if (!done) {
                seeded.set(false);
            }
        }
    }

    private static @CheckForNull Run<?, ?> lastCompletedBuild(Job<?, ?> job) {
        try {
            return job.getLastCompletedBuild();
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Failed to load the last build of " + job.getFullName(), e);
            return null;
        }
    }

    /**
     * Called with each job before it is read while the index fills. For tests, to pause or fail the fill.
     */
    static volatile Consumer<Job<?, ?>> beforeSeeding = job -> {};

    /**
     * Forgets everything. For tests.
     *
     * @param seed whether the next read fills the index from existing builds
     */
    void reset(boolean seed) {
        synchronized (refs) {
            refs.clear();
            newestFirst.clear();
        }
        seeded.set(!seed);
    }

    /**
     * Follows renamed, moved and deleted jobs and folders.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static final class ItemTracker extends ItemListener {
        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            get().moved(oldFullName, newFullName);
        }

        @Override
        public void onDeleted(Item item) {
            get().deleted(item);
        }
    }
}
