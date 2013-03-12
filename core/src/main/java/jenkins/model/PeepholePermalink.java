package jenkins.model;

import com.google.common.base.Predicate;
import hudson.Functions;
import hudson.Util;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Run;
import hudson.util.AtomicFileWriter;
import hudson.util.StreamTaskListener;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
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
 * is up to date, then call {@link #updateCache(Job, int)} if it needs updating.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.507
 */
public abstract class PeepholePermalink extends Permalink implements Predicate<Run<?,?>> {
    /**
     * Checks if the given build satifies the peep-hole criteria.
     *
     * This is the "G(B)" as described in the class javadoc.
     */
    public abstract boolean apply(Run<?,?> run);

    /**
     * The file in which the permalink target gets recorded.
     */
    protected File getPermalinkFile(Job<?,?> job) {
        return new File(job.getRootDir(),"permalinks/"+getId());
    }

    /**
     * Resolves the permalink by using the cache if possible.
     */
    @Override
    public Run<?, ?> resolve(Job<?, ?> job) {
        File f = getPermalinkFile(job);
        Run<?,?> b=null;

        try {
            String target = null;
            if (USE_SYMLINK) { // f.exists() return false if symlink exists but point to a non-existent directory
                target = Util.resolveSymlink(f);
                if (target==null && f.exists()) {
                    // if this file isn't a symlink, it must be a regular file
                    target = FileUtils.readFileToString(f,"UTF-8").trim();
                }
            } else {
                if (f.exists()) {
                    // if this file isn't a symlink, it must be a regular file
                    target = FileUtils.readFileToString(f,"UTF-8").trim();
                }
            }

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
            LOGGER.log(Level.WARNING, "Failed to read permalink cache:" + f, e);
            // if we fail to read the cache, fall back to the re-computation
        }

        if (b==null) {
            // no cache
            b = job.getLastBuild();
        }

        int n;
        // start from the build 'b' and locate the build that matches the criteria going back in time
        while (true) {
            if (b==null) {
                n = RESOLVES_TO_NONE;
                break;
            }
            if (apply(b)) {
                n = b.getNumber();
                break;
            }

            b=b.getPreviousBuild();
        }

        updateCache(job,n);
        return b;
    }

    /**
     * Remembers the value 'n' in the cache for future {@link #resolve(Job)}.
     */
    protected void updateCache(Job<?,?> job, int n) {
        File cache = getPermalinkFile(job);
        cache.getParentFile().mkdirs();

        try {
            StringWriter w = new StringWriter();
            StreamTaskListener listener = new StreamTaskListener(w);

            if (USE_SYMLINK) {
                Util.createSymlink(cache.getParentFile(),"../builds/"+n,cache.getName(),listener);
            } else {
                // symlink not supported. use a regular
                AtomicFileWriter cw = new AtomicFileWriter(cache);
                try {
                    cw.write(String.valueOf(n));
                    cw.commit();
                } finally {
                    cw.abort();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update permalink cache for " + job, e);
            cache.delete();
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to update permalink cache for "+job,e);
            cache.delete();
        }
    }

    private static final int RESOLVES_TO_NONE = -1;

    private static final Logger LOGGER = Logger.getLogger(PeepholePermalink.class.getName());

    /**
     * True if we use the symlink as cache, false if plain text file.
     *
     * <p>
     * On Windows, even with Java7, using symlinks require one to go through quite a few hoops
     * (you need to change the security policy to specifically have this permission, then
     * you better not be in the administrator group because this token gets filtered out
     * on UAC-enabled Windows.)
     */
    public static boolean USE_SYMLINK = !Functions.isWindows();
}
