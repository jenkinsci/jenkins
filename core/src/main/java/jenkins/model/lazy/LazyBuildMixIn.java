/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.model.lazy;


import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.SubTask;
import hudson.widgets.HistoryWidget;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Makes it easier to use a lazy {@link RunMap} from a {@link Job} implementation.
 * Provides method implementations for some abstract {@link Job} methods,
 * as well as some methods which are not abstract but which you should override.
 * <p>Should be kept in a {@code transient} field in the job.
 * @since 1.556
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // BuildHistoryWidget, and AbstractItem.getParent
public abstract class LazyBuildMixIn<JobT extends Job<JobT, RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT, RunT>, RunT extends Run<JobT, RunT> & LazyBuildMixIn.LazyLoadingRun<JobT, RunT>> {

    private static final Logger LOGGER = Logger.getLogger(LazyBuildMixIn.class.getName());

    // [JENKINS-15156] builds accessed before onLoad or onCreatedFromScratch called
    private @NonNull RunMap<RunT> builds = new RunMap<>(asJob());

    /**
     * Initializes this mixin.
     * Call this from a constructor and {@link AbstractItem#onLoad} to make sure it is always initialized.
     */
    protected LazyBuildMixIn() {}

    protected abstract JobT asJob();

    /**
     * Gets the raw model.
     * Normally should not be called as such.
     * Note that the initial value is replaced during {@link #onCreatedFromScratch} or {@link #onLoad}.
     */
    public final @NonNull RunMap<RunT> getRunMap() {
        return builds;
    }

    /**
     * Same as {@link #getRunMap} but suitable for {@link Job#_getRuns}.
     */
    public final RunMap<RunT> _getRuns() {
        assert builds.baseDirInitialized() : "neither onCreatedFromScratch nor onLoad called on " + asJob() + " yet";
        return builds;
    }

    /**
     * Something to be called from {@link Job#onCreatedFromScratch}.
     */
    public final void onCreatedFromScratch() {
        builds = createBuildRunMap();
    }

    /**
     * Something to be called from {@link Job#onLoad}.
     */
    @SuppressWarnings("unchecked")
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        RunMap<RunT> _builds = createBuildRunMap();
        int max = _builds.maxNumberOnDisk();
        int next = asJob().getNextBuildNumber();
        if (next <= max) {
            LOGGER.log(Level.FINE, "nextBuildNumber {0} detected in {1} with highest build number {2}; adjusting", new Object[] {next, asJob(), max});
            asJob().fastUpdateNextBuildNumber(max + 1);
        }
        RunMap<RunT> currentBuilds = this.builds;
        if (parent != null) {
            // are we overwriting what currently exist?
            // this is primarily when Jenkins is getting reloaded
            Item current;
            try {
                current = parent.getItem(name);
            } catch (RuntimeException x) {
                LOGGER.log(Level.WARNING, "failed to look up " + name + " in " + parent, x);
                current = null;
            }
            if (current != null && current.getClass() == asJob().getClass()) {
                currentBuilds = (RunMap<RunT>) ((LazyLoadingJob) current).getLazyBuildMixIn().builds;
            }
        }
        if (currentBuilds != null) {
            // if we are reloading, keep all those that are still building intact
            TreeMap<Integer, RunT> stillBuildingBuilds = new TreeMap<>();
            for (RunT r : currentBuilds.getLoadedBuilds().values()) {
                if (r.isBuilding()) {
                    // Do not use RunMap.put(Run):
                    stillBuildingBuilds.put(r.getNumber(), r);
                    LOGGER.log(Level.FINE, "keeping reloaded {0}", r);
                }
            }
            _builds.putAll(stillBuildingBuilds);
        }
        this.builds = _builds;
    }

    private RunMap<RunT> createBuildRunMap() {
        RunMap<RunT> r = new RunMap<>(asJob(), new RunMap.Constructor<RunT>() {
            @Override
            public RunT create(File dir) throws IOException {
                return loadBuild(dir);
            }

            @Override
            public Class<RunT> getBuildClass() {
                return LazyBuildMixIn.this.getBuildClass();
            }
        });
        return r;
    }

    /**
     * Type token for the build type.
     * The build class must have two constructors:
     * one taking the project type ({@code P});
     * and one taking {@code P}, then {@link File}.
     */
    protected abstract Class<RunT> getBuildClass();

    /**
     * Loads an existing build record from disk.
     * The default implementation just calls the ({@link Job}, {@link File}) constructor of {@link #getBuildClass},
     * which will call {@link Run#Run(Job, File)}.
     */
    public RunT loadBuild(File dir) throws IOException {
        try {
            return getBuildClass().getConstructor(asJob().getClass(), File.class).newInstance(asJob(), dir);
        } catch (InstantiationException | NoSuchMethodException | IllegalAccessException e) {
            throw new LinkageError(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        }
    }

    /**
     * Creates a new build of this project for immediate execution.
     * Calls the ({@link Job}) constructor of {@link #getBuildClass}, which will call {@link Run#Run(Job)}.
     * Suitable for {@link SubTask#createExecutable}.
     */
    public final synchronized RunT newBuild() throws IOException {
        try {
            RunT lastBuild = getBuildClass().getConstructor(asJob().getClass()).newInstance(asJob());
            var rootDir = lastBuild.getRootDir().toPath();
            if (Files.isDirectory(rootDir)) {
               LOGGER.warning(() -> "JENKINS-23152: " + rootDir + " already existed; will not overwrite with " + lastBuild + " but will create a fresh build #" + asJob().getNextBuildNumber());
               return newBuild();
            }
            builds.put(lastBuild);
            return lastBuild;
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.WARNING, String.format("A new build could not be created in job %s", asJob().getFullName()), e);
            throw handleInvocationTargetException(e);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("A new build could not be created in " + asJob().getFullName() + ": " + e, e);
        } catch (IllegalStateException e) {
            throw new IOException("A new build could not be created in " + asJob().getFullName() + ": " + e, e);
        }
    }

    private IOException handleInvocationTargetException(InvocationTargetException e) {
        Throwable t = e.getTargetException();
        if (t instanceof Error) {
            throw (Error) t;
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof IOException) {
            return (IOException) t;
        }
        throw new Error(t);
    }

    /**
     * Suitable for {@link Job#removeRun}.
     */
    public final void removeRun(RunT run) {
        if (!builds.remove(run)) {
            LOGGER.log(Level.WARNING, "{0} did not contain {1} to begin with", new Object[] {asJob(), run});
        }
    }

    /**
     * Suitable for {@link Job#getBuild}.
     */
    public final RunT getBuild(String id) {
        return builds.getById(id);
    }

    /**
     * Suitable for {@link Job#getBuildByNumber}.
     */
    public final RunT getBuildByNumber(int n) {
        return builds.getByNumber(n);
    }

    /**
     * Suitable for {@link Job#getFirstBuild}.
     */
    public final RunT getFirstBuild() {
        return builds.oldestBuild();
    }

    /**
     * Suitable for {@link Job#getLastBuild}.
     */
    public final @CheckForNull RunT getLastBuild() {
        return builds.newestBuild();
    }

    /**
     * Suitable for {@link Job#getNearestBuild}.
     */
    public final RunT getNearestBuild(int n) {
        return builds.search(n, AbstractLazyLoadRunMap.Direction.ASC);
    }

    /**
     * Suitable for {@link Job#getNearestOldBuild}.
     */
    public final RunT getNearestOldBuild(int n) {
        return builds.search(n, AbstractLazyLoadRunMap.Direction.DESC);
    }

    /**
     * Suitable for {@link Job#getEstimatedDurationCandidates}.
     * @since 2.407
     */
    public List<RunT> getEstimatedDurationCandidates() {
        var loadedBuilds = builds.getLoadedBuilds().values(); // reverse chronological order
        List<RunT> candidates = new ArrayList<>(3);
        for (Result threshold : List.of(Result.UNSTABLE, Result.FAILURE)) {
            for (RunT build : loadedBuilds) {
                if (candidates.contains(build)) {
                    continue;
                }
                if (!build.isBuilding()) {
                    Result result = build.getResult();
                    if (result != null && result.isBetterOrEqualTo(threshold)) {
                        candidates.add(build);
                        if (candidates.size() == 3) {
                            LOGGER.fine(() -> "Candidates: " + candidates);
                            return candidates;
                        }
                    }
                }
            }
        }
        LOGGER.fine(() -> "Candidates: " + candidates);
        return candidates;
    }

    /**
     * @deprecated Remove any code calling this method, history widget is now created via {@link jenkins.widgets.WidgetFactory} implementation.
     */
    @Deprecated(forRemoval = true, since = "2.459")
    public final HistoryWidget createHistoryWidget() {
        throw new IllegalStateException("HistoryWidget is now created via WidgetFactory implementation");
    }

    /**
     * Marker for a {@link Job} which uses this mixin.
     */
    public interface LazyLoadingJob<JobT extends Job<JobT, RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT, RunT>, RunT extends Run<JobT, RunT> & LazyLoadingRun<JobT, RunT>> {
        LazyBuildMixIn<JobT, RunT> getLazyBuildMixIn();
        // not offering default implementation for _getRuns(), removeRun(R), getBuild(String), getBuildByNumber(int), getFirstBuild(), getLastBuild(), getNearestBuild(int), getNearestOldBuild(int), or createHistoryWidget()
        // since they are defined in Job
        // (could allow implementations to call LazyLoadingJob.super.theMethod())
    }

    /**
     * Marker for a {@link Run} which uses this mixin.
     */
    public interface LazyLoadingRun<JobT extends Job<JobT, RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT, RunT>, RunT extends Run<JobT, RunT> & LazyLoadingRun<JobT, RunT>> {
        RunMixIn<JobT, RunT> getRunMixIn();
        // not offering default implementations for createReference() or dropLinks() since they are protected
        // (though could use @Restricted(ProtectedExternally.class))
        // nor for getPreviousBuild() or getNextBuild() since they are defined in Run
        // (though could allow implementations to call LazyLoadingRun.super.theMethod())
    }

    /**
     * Accompanying helper for the run type.
     * Stateful but should be held in a {@code final transient} field.
     */
    public abstract static class RunMixIn<JobT extends Job<JobT, RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT, RunT>, RunT extends Run<JobT, RunT> & LazyLoadingRun<JobT, RunT>> {

        private BuildReference<RunT> selfReference;

        protected RunMixIn() {}

        protected abstract RunT asRun();

        /**
         * To implement {@link Run#createReference}.
         */
        public final synchronized BuildReference<RunT> createReference() {
            if (selfReference == null) {
                selfReference = new BuildReference<>(asRun().getId(), asRun());
            }
            return selfReference;
        }

        /**
         * To implement {@link Run#dropLinks}.
         */
        public final void dropLinks() {
            // make this build object unreachable by other Runs
            createReference().clear();
        }

        /**
         * To implement {@link Run#getPreviousBuild}.
         */
        public final RunT getPreviousBuild() {
            return asRun().getParent().getLazyBuildMixIn()._getRuns().search(asRun().number - 1, AbstractLazyLoadRunMap.Direction.DESC);
        }

        /**
         * To implement {@link Run#getNextBuild}.
         */
        public final RunT getNextBuild() {
            return asRun().getParent().getLazyBuildMixIn()._getRuns().search(asRun().number + 1, AbstractLazyLoadRunMap.Direction.ASC);
        }

    }

    @Restricted(DoNotUse.class)
    @Extension public static final class ItemListenerImpl extends ItemListener {
        @Override public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            if (item instanceof LazyLoadingJob) {
                RunMap<?> builds = ((LazyLoadingJob) item).getLazyBuildMixIn().builds;
                builds.updateBaseDir(((Job) item).getBuildDir());
            }
        }
    }

}
