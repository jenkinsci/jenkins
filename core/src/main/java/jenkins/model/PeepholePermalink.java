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
import hudson.util.StreamTaskListener;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 *     deduced to the "peep-hole" function G(B)->bool:
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
 * walking the long build history. The cache is a symlink to the build directory
 * where symlinks are supported, and text file that contains the build number otherwise.
 *
 * <p>
 * The implementation transparently tolerates G(B) that goes from true to false over time
 * (it simply scans the history till find the new matching build.) To tolerate G(B)
 * that goes from false to true, you need to be able to intercept whenever G(B) changes
 * from false to true, then call {@link #resolve(Job)} to check the current permalink target
 * is up to date, then call {@link #updateCache(Job, Run)} if it needs updating.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.507
 */
public abstract class PeepholePermalink extends Permalink implements Predicate<Run<?,?>> {
    /**
     * Checks if the given build satisfies the peep-hole criteria.
     *
     * This is the "G(B)" as described in the class javadoc.
     */
    public abstract boolean apply(Run<?,?> run);

    /**
     * The file in which the permalink target gets recorded.
     */
    protected File getPermalinkFile(Job<?,?> job) {
        return new File(job.getBuildDir(),getId());
    }

    /**
     * Resolves the permalink by using the cache if possible.
     */
    @Override
    public Run<?, ?> resolve(Job<?, ?> job) {
        File f = getPermalinkFile(job);
        Run<?,?> b=null;

        try {
            String target = readSymlink(f);
            if (target!=null) {
                int n = Integer.parseInt(Util.getFileName(target));
                if (n==RESOLVES_TO_NONE)  return null;

                b = job.getBuildByNumber(n);
                if (b!=null && apply(b))
                    return b;   // found it (in the most efficient way possible)

                // the cache is stale. start the search
                if (b==null)
                     b=job.getNearestOldBuild(n);
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to read permalink cache:" + f, e);
            // if we fail to read the cache, fall back to the re-computation
        } catch (IOException e) {
            // this happens when the symlink doesn't exist
            // (and it cannot be distinguished from the case when the actual I/O error happened
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
        for ( ; b!=null && !apply(b); b=b.getPreviousBuild())
            ;
        return b;
    }

    /**
     * Remembers the value 'n' in the cache for future {@link #resolve(Job)}.
     */
    protected void updateCache(@Nonnull Job<?,?> job, @Nullable Run<?,?> b) {
        final int n = b==null ? RESOLVES_TO_NONE : b.getNumber();

        File cache = getPermalinkFile(job);
        cache.getParentFile().mkdirs();

        try {
            String target = String.valueOf(n);
            if (b != null && !new File(job.getBuildDir(), target).exists()) {
                // (re)create the build Number->Id symlink
                Util.createSymlink(job.getBuildDir(),b.getId(),target,TaskListener.NULL);
            }
            writeSymlink(cache, String.valueOf(n));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update "+job+" "+getId()+" permalink for " + b, e);
            cache.delete();
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to update "+job+" "+getId()+" permalink for " + b, e);
            cache.delete();
        }
    }

    // File.exists returns false for a link with a missing target, so for Java 6 compatibility we have to use this circuitous method to see if it was created.
    private static boolean exists(File link) {
        File[] kids = link.getParentFile().listFiles();
        return kids != null && Arrays.asList(kids).contains(link);
    }

    static String readSymlink(File cache) throws IOException, InterruptedException {
        String target = Util.resolveSymlink(cache);
        if (target==null && cache.exists()) {
            // if this file isn't a symlink, it must be a regular file
            target = FileUtils.readFileToString(cache,"UTF-8").trim();
        }
        return target;
    }

    static void writeSymlink(File cache, String target) throws IOException, InterruptedException {
        StringWriter w = new StringWriter();
        StreamTaskListener listener = new StreamTaskListener(w);
        File tmp = new File(cache.getPath()+".tmp");
        try {
            Util.createSymlink(tmp.getParentFile(),target,tmp.getName(),listener);
            // Avoid calling resolveSymlink on a nonexistent file as it will probably throw an IOException:
            if (!exists(tmp) || Util.resolveSymlink(tmp)==null) {
                // symlink not supported. use a regular file
                AtomicFileWriter cw = new AtomicFileWriter(cache);
                try {
                    cw.write(target);
                    cw.commit();
                } finally {
                    cw.abort();
                }
            } else {
                cache.delete();
                tmp.renameTo(cache);
            }
        } finally {
            tmp.delete();
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
                if (pp.apply(run)) {
                    if (pp.resolve(j)==run) {
                        pp.updateCache(j,pp.find(run.getPreviousBuild()));
                    }
                }
            }
        }

        /**
         * See if the new build matches any of the peephole permalink.
         */
        @Override
        public void onCompleted(Run<?,?> run, @Nonnull TaskListener listener) {
            Job<?, ?> j = run.getParent();
            for (PeepholePermalink pp : Util.filter(j.getPermalinks(), PeepholePermalink.class)) {
                if (pp.apply(run)) {
                    Run<?, ?> cur = pp.resolve(j);
                    if (cur==null || cur.getNumber()<run.getNumber())
                        pp.updateCache(j,run);
                }
            }
        }
    }

    private static final int RESOLVES_TO_NONE = -1;

    private static final Logger LOGGER = Logger.getLogger(PeepholePermalink.class.getName());
}
