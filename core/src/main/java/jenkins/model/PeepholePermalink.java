package jenkins.model;

import com.google.common.base.Predicate;
import hudson.Extension;
import hudson.Util;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.AtomicFileWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

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
public abstract class PeepholePermalink extends Permalink implements Predicate<Run<?,?>> {

    /**
     * JENKINS-22822: avoids rereading caches.
     * Top map keys are {@code builds} directories.
     * Inner maps are from permalink name to build number.
     * Synchronization is first on the outer map, then on the inner.
     */
    private static final Map<File, Map<String, Integer>> caches = new HashMap<>();

    /**
     * Checks if the given build satisfies the peep-hole criteria.
     *
     * This is the "G(B)" as described in the class javadoc.
     */
    public abstract boolean apply(Run<?,?> run);

    /** @deprecated No longer used. */
    @Deprecated
    protected File getPermalinkFile(Job<?,?> job) {
        return new File(job.getBuildDir(),getId());
    }

    /**
     * Resolves the permalink by using the cache if possible.
     */
    @Override
    public Run<?, ?> resolve(Job<?, ?> job) {
        Map<String, Integer> cache = cacheFor(job.getBuildDir());
        int n;
        synchronized (cache) {
            n = cache.getOrDefault(getId(), 0);
        }
        if (n == RESOLVES_TO_NONE) {
            return null;
        }
        Run<?, ?> b;
        if (n > 0) {
            b = job.getBuildByNumber(n);
            if (b != null && apply(b)) {
                return b; // found it (in the most efficient way possible)
            }
        } else {
            b = null;
        }

        // the cache is stale. start the search
        if (b == null) {
            b = job.getNearestOldBuild(n);
        }

        if (b==null) {
            // no cache
            b = job.getLastBuild();
        }

        // start from the build 'b' and locate the build that matches the criteria going back in time
        b = find(b);

        updateCache(job,b);
        return b;
    }

    /**
     * Start from the build 'b' and locate the build that matches the criteria going back in time
     */
    private Run<?,?> find(Run<?,?> b) {
        //noinspection StatementWithEmptyBody
        for ( ; b!=null && !apply(b); b=b.getPreviousBuild())
            ;
        return b;
    }

    private static @NonNull Map<String, Integer> cacheFor(@NonNull File buildDir) {
        synchronized (caches) {
            Map<String, Integer> cache = caches.get(buildDir);
            if (cache == null) {
                cache = load(buildDir);
                caches.put(buildDir, cache);
            }
            return cache;
        }
    }

    private static @NonNull Map<String, Integer> load(@NonNull File buildDir) {
        Map<String, Integer> cache = new TreeMap<>();
        File storage = storageFor(buildDir);
        if (storage.isFile()) {
            try (Stream<String> lines = Files.lines(storage.toPath(), StandardCharsets.UTF_8)) {
                lines.forEach(line -> {
                    int idx = line.indexOf(' ');
                    if (idx == -1) {
                        return;
                    }
                    try {
                        cache.put(line.substring(0, idx), Integer.parseInt(line.substring(idx + 1)));
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

    /**
     * Remembers the value 'n' in the cache for future {@link #resolve(Job)}.
     */
    protected void updateCache(@NonNull Job<?,?> job, @CheckForNull Run<?,?> b) {
        File buildDir = job.getBuildDir();
        Map<String, Integer> cache = cacheFor(buildDir);
        synchronized (cache) {
            cache.put(getId(), b == null ? RESOLVES_TO_NONE : b.getNumber());
            File storage = storageFor(buildDir);
            LOGGER.fine(() -> "saving to " + storage + ": " + cache);
            try (AtomicFileWriter cw = new AtomicFileWriter(storage)) {
                try {
                    for (Map.Entry<String, Integer> entry : cache.entrySet()) {
                        cw.write(entry.getKey());
                        cw.write(' ');
                        cw.write(Integer.toString(entry.getValue()));
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

    @Extension
    public static class RunListenerImpl extends RunListener<Run<?,?>> {
        /**
         * If any of the peephole permalink points to the build to be deleted, update it to point to the new location.
         */
        @Override
        public void onDeleted(Run run) {
            Job<?, ?> j = run.getParent();
            for (PeepholePermalink pp : Util.filter(j.getPermalinks(), PeepholePermalink.class)) {
                if (pp.resolve(j)==run) {
                    Run<?,?> r = pp.find(run.getPreviousBuild());
                    LOGGER.fine(() -> "Updating " + pp.getId() + " permalink from deleted " + run + " to " + (r == null ? -1 : r.getNumber()));
                    pp.updateCache(j,r);
                }
            }
        }

        /**
         * See if the new build matches any of the peephole permalink.
         */
        @Override
        public void onCompleted(Run<?,?> run, @NonNull TaskListener listener) {
            Job<?, ?> j = run.getParent();
            for (PeepholePermalink pp : Util.filter(j.getPermalinks(), PeepholePermalink.class)) {
                if (pp.apply(run)) {
                    Run<?, ?> cur = pp.resolve(j);
                    if (cur==null || cur.getNumber()<run.getNumber()) {
                        LOGGER.fine(() -> "Updating " + pp.getId() + " permalink to completed " + run);
                        pp.updateCache(j,run);
                    }
                }
            }
        }
    }

    private static final int RESOLVES_TO_NONE = -1;

    private static final Logger LOGGER = Logger.getLogger(PeepholePermalink.class.getName());
}
