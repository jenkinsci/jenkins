package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.AtomicFileWriter;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Convenient base implementation for {@link Permalink}s that satisfy
 * certain properties.
 *
 * <p>
 * For a permalink to be able to use this, it has to satisfy the following:
 *
 * <blockquote>
 *     Given a job J, permalink is a function F that computes a build B.
 *     A peephole permalink is a subset of this function that can be
 *     deduced to the "peep-hole" function G(B)→bool:
 *
 *     <pre>
 *         F(J) = { newest B | G(B)==true }
 *     </pre>
 * </blockquote>
 *
 * <p>
 * Intuitively speaking, a peep-hole permalink resolves to the latest build that
 * satisfies a certain characteristics that can be determined solely by looking
 * at the build and nothing else (most commonly its build result.)
 *
 * <p>
 * This base class provides a file-based caching mechanism that avoids
 * walking the long build history.
 *
 * <p>
 * The implementation transparently tolerates G(B) that goes from true to false over time
 * (it simply scans the history till find the new matching build.) To tolerate G(B)
 * that goes from false to true, you need to be able to intercept whenever G(B) changes
 * from false to true, then call {@link #resolve(Job)} to check the current permalink target
 * is up to date, then call {@link #updateCache(Job, Run)} if it needs updating.
 *
 * @since 1.507
 */
public abstract class PeepholePermalink extends Permalink implements Predicate<Run<?, ?>> {

    /**
     * Checks if the given build satisfies the peep-hole criteria.
     *
     * This is the "G(B)" as described in the class javadoc.
     */
    public abstract boolean apply(Run<?, ?> run);

    @Override
    public boolean test(Run<?, ?> run) {
        return apply(run);
    }

    /** @deprecated No longer used. */
    @Deprecated
    protected File getPermalinkFile(Job<?, ?> job) {
        return new File(job.getBuildDir(), getId());
    }

    /**
     * Resolves the permalink by using the cache if possible.
     */
    @Override
    public Run<?, ?> resolve(Job<?, ?> job) {
        return get(job).resolve(this, job, getId());
    }

    Cache.PermalinkTarget get(Job<?, ?> job) {
        return ExtensionList.lookupFirst(Cache.class).get(job, getId());
    }

    int resolveNumber(Job<?, ?> job) {
        var pt = get(job);
        if (pt instanceof Cache.Some(int number)) {
            return number;
        } else if (pt instanceof Cache.None) {
            return 0;
        } else { // Unknown
            var b = pt.resolve(this, job, getId());
            return b != null ? b.number : 0;
        }
    }

    /**
     * Start from the build 'b' and locate the build that matches the criteria going back in time
     */
    @CheckForNull
    private Run<?, ?> find(@CheckForNull Run<?, ?> b) {
        while (b != null && !apply(b)) {
            b = b.getPreviousBuild();
        }
        return b;
    }

    /**
     * Remembers the value 'n' in the cache for future {@link #resolve(Job)}.
     */
    protected void updateCache(@NonNull Job<?, ?> job, @CheckForNull Run<?, ?> b) {
        ExtensionList.lookupFirst(Cache.class).put(job, getId(), b != null ? new Cache.Some(b.getNumber()) : Cache.NONE);
    }

    /**
     * Persistable cache of peephole permalink targets.
     */
    @Restricted(Beta.class)
    public interface Cache extends ExtensionPoint {

        /** Cacheable target of a permalink. */
        sealed interface PermalinkTarget extends Serializable {

            /**
             * Implementation of {@link #resolve(Job)}.
             * This may update the cache if it was missing or found to be invalid.
             */
            @Restricted(NoExternalUse.class)
            @CheckForNull
            Run<?, ?> resolve(@NonNull PeepholePermalink pp, @NonNull Job<?, ?> job, @NonNull String id);

            /**
             * Partial implementation of {@link #resolve(PeepholePermalink, Job, String)} when searching.
             * @param b if set, the newest build to even consider when searching
             */
            @Restricted(NoExternalUse.class)
            @CheckForNull
            default Run<?, ?> search(@NonNull PeepholePermalink pp, @NonNull Job<?, ?> job, @NonNull String id, @CheckForNull Run<?, ?> b) {
                if (b == null) {
                    // no cache
                    b = job.getLastBuild();
                }
                // start from the build 'b' and locate the build that matches the criteria going back in time
                b = pp.find(b);
                pp.updateCache(job, b);
                return b;
            }

        }

        /**
         * The cache entry for this target is missing.
         */
        record Unknown() implements PermalinkTarget {
            @Override
            public Run<?, ?> resolve(PeepholePermalink pp, Job<?, ?> job, String id) {
                return search(pp, job, id, null);
            }
        }

        Unknown UNKNOWN = new Unknown();

        /**
         * The cache entry for this target is present.
         */
        sealed interface Known extends PermalinkTarget {}

        /** There is known to be no matching build. */
        record None() implements Known {
            @Override
            public Run<?, ?> resolve(PeepholePermalink pp, Job<?, ?> job, String id) {
                return null;
            }
        }

        /** Singleton of {@link None}. */
        None NONE = new None();

        /** A matching build, indicated by {@link Run#getNumber}. */
        record Some(int number) implements Known {
            @Override
            public Run<?, ?> resolve(PeepholePermalink pp, Job<?, ?> job, String id) {
                Run<?, ?> b = job.getBuildByNumber(number);
                if (b != null && pp.apply(b)) {
                    return b; // found it (in the most efficient way possible)
                }
                // the cache is stale. start the search
                if (b == null) {
                    b = job.getNearestOldBuild(number);
                }
                return search(pp, job, id, b);
            }
        }

        /**
         * Looks for any existing cache hit.
         * @param id {@link #getId}
         * @return {@link Some} or {@link #NONE} or {@link #UNKNOWN}
         */
        @NonNull PermalinkTarget get(@NonNull Job<?, ?> job, @NonNull String id);

        /**
         * Updates the cache.
         * Note that this may be called not just when a build completes or is deleted
         * (meaning that the logical value of the cache has changed),
         * but also when {@link #resolve} has failed to find a cached value
         * (or determined that a previously cached value is in fact invalid).
         * @param id {@link #getId}
         * @param target {@link Some} or {@link #NONE}
         */
        void put(@NonNull Job<?, ?> job, @NonNull String id, @NonNull Known target);
    }

    /**
     * Default cache based on a {@code permalinks} file in the build directory.
     * There is one line per cached permalink, in the format {@code lastStableBuild 123}
     * or (for a negative cache) {@code lastFailedBuild -1}.
     */
    @Restricted(NoExternalUse.class)
    @Extension(ordinal = -1000)
    public static final class DefaultCache implements Cache {

        /**
         * JENKINS-22822: avoids rereading caches.
         * Top map keys are {@code builds} directories.
         * Inner maps are from permalink name to target.
         * Synchronization is first on the outer map, then on the inner.
         */
        private final Map<File, Map<String, Known>> caches = new HashMap<>();

        @Override
        public PermalinkTarget get(Job<?, ?> job, String id) {
            var cache = cacheFor(job.getBuildDir());
            synchronized (cache) {
                var cached = cache.get(id);
                return cached != null ? cached : UNKNOWN;
            }
        }

        @Override
        public void put(Job<?, ?> job, String id, Known target) {
            File buildDir = job.getBuildDir();
            var cache = cacheFor(buildDir);
            synchronized (cache) {
                cache.put(id, target);
                File storage = storageFor(buildDir);
                LOGGER.fine(() -> "saving to " + storage + ": " + cache);
                try (AtomicFileWriter cw = new AtomicFileWriter(storage)) {
                    try {
                        for (var entry : cache.entrySet()) {
                            cw.write(entry.getKey());
                            cw.write(' ');
                            cw.write(Integer.toString(entry.getValue() instanceof Some(int number) ? number : -1));
                            cw.write('\n');
                        }
                        cw.commit();
                    } finally {
                        cw.abort();
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to update " + storage, x);
                }
            }
        }

        private @NonNull Map<String, Known> cacheFor(@NonNull File buildDir) {
            synchronized (caches) {
                var cache = caches.get(buildDir);
                if (cache == null) {
                    cache = load(buildDir);
                    caches.put(buildDir, cache);
                }
                return cache;
            }
        }

        private static @NonNull Map<String, Known> load(@NonNull File buildDir) {
            Map<String, Known> cache = new TreeMap<>();
            File storage = storageFor(buildDir);
            if (storage.isFile()) {
                try (Stream<String> lines = Files.lines(storage.toPath(), StandardCharsets.UTF_8)) {
                    lines.forEach(line -> {
                        int idx = line.indexOf(' ');
                        if (idx == -1) {
                            return;
                        }
                        try {
                            int number = Integer.parseInt(line.substring(idx + 1));
                            cache.put(line.substring(0, idx), number == -1 ? Cache.NONE : new Cache.Some(number));
                        } catch (NumberFormatException x) {
                            LOGGER.log(Level.WARNING, "failed to read " + storage, x);
                        }
                    });
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to read " + storage, x);
                }
                LOGGER.fine(() -> "loading from " + storage + ": " + cache);
            }
            return cache;
        }

        static @NonNull File storageFor(@NonNull File buildDir) {
            return new File(buildDir, "permalinks");
        }
    }

    @Extension
    public static class RunListenerImpl extends RunListener<Run<?, ?>> {
        /**
         * If any of the peephole permalink points to the build to be deleted, update it to point to the new location.
         */
        @Override
        public void onDeleted(Run run) {
            Job<?, ?> j = run.getParent();
            for (PeepholePermalink pp : Util.filter(j.getPermalinks(), PeepholePermalink.class)) {
                if (pp.resolveNumber(j) == run.number) {
                    Run<?, ?> r = pp.find(run.getPreviousBuild());
                    LOGGER.fine(() -> "Updating " + pp.getId() + " permalink from deleted " + run + " to " + (r == null ? -1 : r.getNumber()));
                    pp.updateCache(j, r);
                }
            }
        }

        /**
         * See if the new build matches any of the peephole permalink.
         */
        @Override
        public void onCompleted(Run<?, ?> run, @NonNull TaskListener listener) {
            Job<?, ?> j = run.getParent();
            for (PeepholePermalink pp : Util.filter(j.getPermalinks(), PeepholePermalink.class)) {
                if (pp.apply(run)) {
                    if (pp.resolveNumber(j) < run.getNumber()) {
                        LOGGER.fine(() -> "Updating " + pp.getId() + " permalink to completed " + run);
                        pp.updateCache(j, run);
                    }
                }
            }
        }
    }

    /**
     * @since 2.436
     */
    public static final Permalink LAST_STABLE_BUILD = new PeepholePermalink() {
        @Override
        public String getDisplayName() {
            return hudson.model.Messages.Permalink_LastStableBuild();
        }

        @Override
        public String getId() {
            return "lastStableBuild";
        }

        @Override
        public boolean apply(Run<?, ?> run) {
            return !run.isBuilding() && run.getResult() == Result.SUCCESS;
        }
    };

    /**
     * @since 2.436
     */
    public static final Permalink LAST_SUCCESSFUL_BUILD = new PeepholePermalink() {
        @Override
        public String getDisplayName() {
            return hudson.model.Messages.Permalink_LastSuccessfulBuild();
        }

        @Override
        public String getId() {
            return "lastSuccessfulBuild";
        }

        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "TODO needs triage")
        @Override
        public boolean apply(Run<?, ?> run) {
            return !run.isBuilding() && run.getResult().isBetterOrEqualTo(Result.UNSTABLE);
        }
    };

    /**
     * @since 2.436
     */
    public static final Permalink LAST_FAILED_BUILD = new PeepholePermalink() {
        @Override
        public String getDisplayName() {
            return hudson.model.Messages.Permalink_LastFailedBuild();
        }

        @Override
        public String getId() {
            return "lastFailedBuild";
        }

        @Override
        public boolean apply(Run<?, ?> run) {
            return !run.isBuilding() && run.getResult() == Result.FAILURE;
        }
    };

    /**
     * @since 2.436
     */
    public static final Permalink LAST_UNSTABLE_BUILD = new PeepholePermalink() {
        @Override
        public String getDisplayName() {
            return hudson.model.Messages.Permalink_LastUnstableBuild();
        }

        @Override
        public String getId() {
            return "lastUnstableBuild";
        }

        @Override
        public boolean apply(Run<?, ?> run) {
            return !run.isBuilding() && run.getResult() == Result.UNSTABLE;
        }
    };

    /**
     * @since 2.436
     */
    public static final Permalink LAST_UNSUCCESSFUL_BUILD = new PeepholePermalink() {
        @Override
        public String getDisplayName() {
            return hudson.model.Messages.Permalink_LastUnsuccessfulBuild();
        }

        @Override
        public String getId() {
            return "lastUnsuccessfulBuild";
        }

        @Override
        public boolean apply(Run<?, ?> run) {
            return !run.isBuilding() && run.getResult() != Result.SUCCESS;
        }
    };

    /**
     * @since 2.436
     */
    public static final Permalink LAST_COMPLETED_BUILD = new PeepholePermalink() {
        @Override
        public String getDisplayName() {
            return hudson.model.Messages.Permalink_LastCompletedBuild();
        }

        @Override
        public String getId() {
            return "lastCompletedBuild";
        }

        @Override
        public boolean apply(Run<?, ?> run) {
            return !run.isBuilding();
        }
    };

    static {
        BUILTIN.add(LAST_STABLE_BUILD);
        BUILTIN.add(LAST_SUCCESSFUL_BUILD);
        BUILTIN.add(LAST_FAILED_BUILD);
        BUILTIN.add(LAST_UNSTABLE_BUILD);
        BUILTIN.add(LAST_UNSUCCESSFUL_BUILD);
        BUILTIN.add(LAST_COMPLETED_BUILD);
        Permalink.LAST_STABLE_BUILD = LAST_STABLE_BUILD;
        Permalink.LAST_SUCCESSFUL_BUILD = LAST_SUCCESSFUL_BUILD;
        Permalink.LAST_FAILED_BUILD = LAST_FAILED_BUILD;
        Permalink.LAST_UNSTABLE_BUILD = LAST_UNSTABLE_BUILD;
        Permalink.LAST_UNSUCCESSFUL_BUILD = LAST_UNSUCCESSFUL_BUILD;
        Permalink.LAST_COMPLETED_BUILD = LAST_COMPLETED_BUILD;
    }

    @Restricted(NoExternalUse.class)
    public static void initialized() {}

    private static final Logger LOGGER = Logger.getLogger(PeepholePermalink.class.getName());
}
