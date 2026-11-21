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
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Remembers the last completed build of the most recently active jobs, for {@link RecentTab}.
 *
 * <p>Each job has at most one entry, so a few busy jobs cannot push out everything else. Like
 * {@link Job#getLastCompletedBuild()}, the entry is the job's highest-numbered completed build, also when builds
 * complete out of order. Runs are held
 * by job full name and build number rather than by reference, so this does not keep builds in memory.
 * The index starts empty. The first read fills it in the background from each job's last completed
 * build, so startup does not load any builds.
 */
@Extension
@Restricted(NoExternalUse.class)
public final class RecentRuns extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(RecentRuns.class.getName());

    /**
     * The maximum number of jobs remembered.
     */
    static /* non-final for Groovy */ int CAPACITY = SystemProperties.getInteger(RecentRuns.class.getName() + ".capacity", 200);

    record Ref(String job, int number, long endTime) {
        static Ref of(Run<?, ?> run) {
            return new Ref(run.getParent().getFullName(), run.getNumber(), run.getStartTimeInMillis() + run.getDuration());
        }
    }

    /** Keyed by job full name. */
    private final Map<String, Ref> refs = new HashMap<>();

    private final AtomicBoolean seeded = new AtomicBoolean();

    public static RecentRuns get() {
        return ExtensionList.lookupSingleton(RecentRuns.class);
    }

    @Override
    public void onCompleted(Run<?, ?> run, @NonNull TaskListener listener) {
        add(Ref.of(run));
    }

    @Override
    public void onDeleted(Run<?, ?> run) {
        String job = run.getParent().getFullName();
        synchronized (refs) {
            Ref ref = refs.get(job);
            if (ref == null || ref.number() != run.getNumber()) {
                return;
            }
            refs.remove(job);
        }
        Run<?, ?> previous = run.getPreviousCompletedBuild();
        if (previous != null) {
            add(Ref.of(previous));
        }
    }

    void add(Ref ref) {
        synchronized (refs) {
            Ref existing = refs.get(ref.job());
            if (existing != null && existing.number() > ref.number()) {
                return;
            }
            refs.put(ref.job(), ref);
            if (refs.size() > CAPACITY) {
                refs.values().stream()
                        .min(Comparator.comparingLong(Ref::endTime))
                        .ifPresent(oldest -> refs.remove(oldest.job()));
            }
        }
    }

    private void moved(String oldFullName, String newFullName) {
        synchronized (refs) {
            for (Ref ref : List.copyOf(refs.values())) {
                if (isSelfOrDescendant(ref.job(), oldFullName)) {
                    refs.remove(ref.job());
                    String job = newFullName + ref.job().substring(oldFullName.length());
                    refs.put(job, new Ref(job, ref.number(), ref.endTime()));
                }
            }
        }
    }

    private void deleted(String fullName) {
        synchronized (refs) {
            refs.keySet().removeIf(job -> isSelfOrDescendant(job, fullName));
        }
    }

    private static boolean isSelfOrDescendant(String job, String fullName) {
        return job.equals(fullName) || job.startsWith(fullName + '/');
    }

    /**
     * The most recently completed runs that the current user can see, newest first, one per job.
     *
     * @param limit the maximum number of runs to return
     */
    @NonNull
    public List<Run<?, ?>> getCompleted(int limit) {
        seedOnce();
        List<Ref> snapshot;
        synchronized (refs) {
            snapshot = new ArrayList<>(refs.values());
        }
        snapshot.sort(Comparator.comparingLong(Ref::endTime).reversed());

        Jenkins jenkins = Jenkins.get();
        List<Run<?, ?>> result = new ArrayList<>();
        for (Ref ref : snapshot) {
            if (result.size() >= limit) {
                break;
            }
            // Null when the current user cannot read the job
            Job<?, ?> job = jenkins.getItemByFullNameOrNull(ref.job(), Job.class);
            if (job == null) {
                continue;
            }
            Run<?, ?> run = job.getBuildByNumber(ref.number());
            if (run == null) {
                // The build is gone, for example deleted while the index was being filled
                synchronized (refs) {
                    refs.remove(ref.job(), ref);
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
        // Keeps only the newest CAPACITY entries; the head is the oldest
        PriorityQueue<Candidate> newest = new PriorityQueue<>(Comparator.comparingLong(c -> c.ref().endTime()));
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            Jenkins jenkins = Jenkins.get();
            for (Job job : jenkins.allItems(Job.class)) {
                Run<?, ?> run = lastCompletedBuild(job);
                if (run != null) {
                    newest.add(new Candidate(job, Ref.of(run)));
                    if (newest.size() > CAPACITY) {
                        newest.poll();
                    }
                }
            }
            // Jobs may have been renamed, moved or deleted since they were read. Holding the lock while checking
            // means that a later change is applied by the item listener to what is added here.
            synchronized (refs) {
                for (Candidate candidate : newest) {
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
            get().deleted(item.getFullName());
        }
    }
}
