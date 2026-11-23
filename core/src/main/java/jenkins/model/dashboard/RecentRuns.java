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
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Remembers the last completed build of every job, for {@link RecentTab}.
 *
 * <p>Each job has one entry, so a few busy jobs cannot push out everything else, and users who can only see
 * a few jobs still find their recent builds however busy the rest of the instance is. Like
 * {@link Job#getLastCompletedBuild()}, the entry is the job's highest-numbered completed build, also when builds
 * complete out of order. Runs are held
 * by job full name and build number rather than by reference, so this does not keep builds in memory, and an
 * entry takes about a hundred bytes.
 * The index starts empty. The first read fills it in the background from each job's last completed
 * build, so startup does not load any builds.
 */
@Extension
@Restricted(NoExternalUse.class)
public final class RecentRuns extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(RecentRuns.class.getName());

    record Ref(String job, int number, long endTime) {
        static Ref of(Run<?, ?> run) {
            return new Ref(run.getParent().getFullName(), run.getNumber(), run.getStartTimeInMillis() + run.getDuration());
        }
    }

    private static final Comparator<Ref> NEWEST_FIRST = Comparator.comparingLong(Ref::endTime)
            .reversed()
            .thenComparing(Ref::job)
            .thenComparingInt(Ref::number);

    /** Keyed by job full name. Changes to it and {@link #newestFirst} are made together, holding its lock. */
    private final Map<String, Ref> refs = new ConcurrentHashMap<>();

    /**
     * The same entries in the order they are read, so that reads neither copy nor sort the index. Reads
     * iterate without the lock, and skip entries that {@link #refs} no longer holds.
     */
    private final NavigableSet<Ref> newestFirst = new ConcurrentSkipListSet<>(NEWEST_FIRST);

    private final AtomicBoolean seeded = new AtomicBoolean();

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
            String prefix = fullName + '/';
            for (Ref ref : List.copyOf(refs.values())) {
                if (ref.job().startsWith(prefix)) {
                    remove(ref);
                }
            }
        }
    }

    /**
     * The most recently completed runs that the current user can see, newest first, one per job.
     *
     * @param limit the maximum number of runs to return
     */
    @NonNull
    public List<Run<?, ?>> getCompleted(int limit) {
        seedOnce();
        Jenkins jenkins = Jenkins.get();
        // Whether the current user can read each folder, so that the jobs of a folder the user cannot read are
        // skipped after one check. Users who can only see a few folders would otherwise check every job.
        Map<String, Boolean> readableFolders = new HashMap<>();
        List<Run<?, ?>> result = new ArrayList<>();
        for (Ref ref : newestFirst) {
            if (result.size() >= limit) {
                break;
            }
            if (refs.get(ref.job()) != ref) {
                // Replaced or removed since the iteration started
                continue;
            }
            int slash = ref.job().lastIndexOf('/');
            if (slash > 0
                    && !readableFolders.computeIfAbsent(
                            ref.job().substring(0, slash), folder -> jenkins.getItemByFullNameOrNull(folder) != null)) {
                continue;
            }
            // Null when the current user cannot read a folder containing the job. Not every item group checks
            // the permission of the items it returns, so the job's own is checked too.
            Job<?, ?> job = jenkins.getItemByFullNameOrNull(ref.job(), Job.class);
            if (job == null || !job.hasPermission(Item.READ)) {
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

    private void seedOnce() {
        if (seeded.compareAndSet(false, true)) {
            Timer.get().submit(this::seed);
        }
    }

    /**
     * Fills the index from the last completed build of every job. Runs as the system user, because the
     * index is shared by all users and each read filters by the reader's permissions.
     */
    @SuppressWarnings("rawtypes")
    void seed() {
        List<Candidate> candidates = new ArrayList<>();
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            Jenkins jenkins = Jenkins.get();
            for (Job job : jenkins.allItems(Job.class)) {
                Run<?, ?> run = lastCompletedBuild(job);
                if (run != null) {
                    candidates.add(new Candidate(job, Ref.of(run)));
                }
            }
            // Jobs may have been renamed, moved or deleted since they were read. Holding the lock while checking
            // means that a later change is applied by the item listener to what is added here.
            synchronized (refs) {
                for (Candidate candidate : candidates) {
                    String name = candidate.job().getFullName();
                    if (jenkins.getItemByFullName(name, Job.class) == candidate.job()) {
                        add(new Ref(name, candidate.ref().number(), candidate.ref().endTime()));
                    }
                }
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to load recent runs", e);
        }
    }

    private record Candidate(Job<?, ?> job, Ref ref) {}

    private static @CheckForNull Run<?, ?> lastCompletedBuild(Job<?, ?> job) {
        try {
            return job.getLastCompletedBuild();
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Failed to load the last build of " + job.getFullName(), e);
            return null;
        }
    }

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
