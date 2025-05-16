/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

package hudson.model;

import static java.util.logging.Level.FINEST;
import static jenkins.model.lazy.AbstractLazyLoadRunMap.Direction.ASC;
import static jenkins.model.lazy.AbstractLazyLoadRunMap.Direction.DESC;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.listeners.RunListener;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SortedMap;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.lazy.AbstractLazyLoadRunMap;
import jenkins.model.lazy.BuildReference;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link Map} from build number to {@link Run}.
 *
 * <p>
 * This class is multi-thread safe by using copy-on-write technique,
 * and it also updates the bi-directional links within {@link Run}
 * accordingly.
 *
 * @author Kohsuke Kawaguchi
 */
public final class RunMap<R extends Run<?, R>> extends AbstractLazyLoadRunMap<R> implements Iterable<R> {
    /**
     * Read-only view of this map.
     */
    private final SortedMap<Integer, R> view = Collections.unmodifiableSortedMap(this);

    private final @CheckForNull Job<?, ?> job;

    private Constructor<R> cons;

    // TODO: before first complete build
    // patch up next/previous build link


    /**
     * @deprecated as of 1.485
     *      Use {@link #RunMap(Job, Constructor)}.
     */
    @Deprecated
    public RunMap() {
        job = null;
        initBaseDir(null); // will be set later
    }

    @Restricted(NoExternalUse.class)
    public RunMap(@NonNull Job<?, ?> job) {
        this.job = Objects.requireNonNull(job);
    }

    /**
     * @param cons
     *      Used to create new instance of {@link Run}.
     * @since 2.451
     */
    public RunMap(@NonNull Job<?, ?> job, Constructor cons) {
        this.job = Objects.requireNonNull(job);
        this.cons = cons;
        initBaseDir(job.getBuildDir());
    }

    /**
     * @deprecated Use {@link #RunMap(Job, Constructor)}.
     */
    @Deprecated
    public RunMap(File baseDir, Constructor cons) {
        job = null;
        this.cons = cons;
        initBaseDir(baseDir);
    }

    public boolean remove(R run) {
        return removeValue(run);
    }

    /**
     * Walks through builds, newer ones first.
     */
    @Override
    public Iterator<R> iterator() {
        return new Iterator<>() {
            R last = null;
            R next = newestBuild();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public R next() {
                last = next;
                if (last != null)
                    next = last.getPreviousBuild();
                else
                    throw new NoSuchElementException();
                return last;
            }

            @Override
            public void remove() {
                if (last == null)
                    throw new UnsupportedOperationException();
                removeValue(last);
            }
        };
    }

    @Override
    public boolean removeValue(R run) {
        run.dropLinks();
        return super.removeValue(run);
    }

    /**
     * Gets the read-only view of this map.
     */
    public SortedMap<Integer, R> getView() {
        return view;
    }

    /**
     * This is the newest build (with the biggest build number)
     */
    public R newestValue() {
        return search(Integer.MAX_VALUE, DESC);
    }

    /**
     * This is the oldest build (with the smallest build number)
     */
    public R oldestValue() {
        return search(Integer.MIN_VALUE, ASC);
    }

    /**
     * @deprecated  as of 1.485
     *      Use {@link Comparator#reverseOrder}
     */
    @Deprecated
    public static final Comparator<Comparable> COMPARATOR = Comparator.reverseOrder();

    /**
     * {@link Run} factory.
     */
    public interface Constructor<R extends Run<?, R>> {
        R create(File dir) throws IOException;
    }

    @Override
    protected int getNumberOf(R r) {
        return r.getNumber();
    }

    @Override
    protected String getIdOf(R r) {
        return r.getId();
    }

    /**
     * Add a <em>new</em> build to the map.
     * Do not use when loading existing builds (use {@link #put(Integer, Object)}).
     */
    @Override
    public R put(R r) {
        // Defense against JENKINS-23152 and its ilk.
        File rootDir = r.getRootDir();
        if (Files.isDirectory(rootDir.toPath())) {
            throw new IllegalStateException("JENKINS-23152: " + rootDir + " already existed; will not overwrite with " + r);
        }
        if (!r.getClass().getName().equals("hudson.matrix.MatrixRun")) { // JENKINS-26739: grandfathered in
            proposeNewNumber(r.getNumber());
        }
        try {
            Util.createDirectories(rootDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return super._put(r);
    }

    @CheckForNull
    @Override public R getById(String id) {
        try {
            return getByNumber(Integer.parseInt(id));
        } catch (NumberFormatException e) { // see https://issues.jenkins.io/browse/JENKINS-75476
            return null;
        }
    }

    /**
     * Reuses the same reference as much as we can.
     * <p>
     * If concurrency ends up creating a few extra, that's OK, because
     * we are really just trying to reduce the # of references we create.
     */
    @Override
    protected BuildReference<R> createReference(R r) {
        return r.createReference();
    }

    @Restricted(NoExternalUse.class)
    @Override
    protected boolean allowLoad(int buildNumber) {
        if (job == null) {
            LOGGER.fine(() -> "deprecated constructor without Job used on " + dir);
            return true;
        }
        for (RunListener<?> l : RunListener.all()) {
            if (!l.allowLoad(job, buildNumber)) {
                LOGGER.finer(() -> l + " declined to load " + buildNumber + " in " + job);
                return false;
            }
        }
        LOGGER.finest(() -> "no RunListener declined to load " + buildNumber + " in " + job + " so proceeding");
        return true;
    }

    @Restricted(NoExternalUse.class)
    @Override
    protected IntPredicate createLoadAllower() {
        if (job == null) {
            LOGGER.fine(() -> "deprecated constructor without Job used on " + dir);
            return buildNumber -> true;
        }
        @SuppressWarnings("unchecked")
        var allowers = RunListener.all().stream().map(l -> l.createLoadAllower(job)).toList();
        return buildNumber -> {
            for (var allower : allowers) {
                if (!allower.test(buildNumber)) {
                    LOGGER.finer(() -> allower + " declined to load " + buildNumber + " in " + job);
                    return false;
                }
            }
            LOGGER.finest(() -> "no RunListener declined to load " + buildNumber + " in " + job + " so proceeding");
            return true;
        };
    }

    @Override
    protected R retrieve(File d) throws IOException {
        if (new File(d, "build.xml").exists()) {
            // if the build result file isn't in the directory, ignore it.
            try {
                R b = cons.create(d);
                b.onLoad();
                if (LOGGER.isLoggable(FINEST)) {
                    LOGGER.log(FINEST, "Loaded " + b.getFullDisplayName() + " in " + Thread.currentThread().getName(), new ThisIsHowItsLoaded());
                }
                return b;
            } catch (Exception | InstantiationError e) {
                LOGGER.log(Level.WARNING, "could not load " + d, e);
            }
        }
        LOGGER.fine(() -> "no config.xml in " + d);
        return null;
    }

    /**
     * Backward compatibility method that notifies {@link RunMap} of who the owner is.
     *
     * Traditionally, this method blocked and loaded all the build records on the disk,
     * but now all the actual loading happens lazily.
     *
     * @param job
     *      Job that owns this map.
     * @param cons
     *      Used to create new instance of {@link Run}.
     * @deprecated as of 1.485
     *      Use {@link #RunMap(File, Constructor)}
     */
    @Deprecated
    public void load(Job job, Constructor<R> cons) {
        this.cons = cons;
        initBaseDir(job.getBuildDir());
    }

    private static final Logger LOGGER = Logger.getLogger(RunMap.class.getName());

    private static class ThisIsHowItsLoaded extends Exception {}
}
