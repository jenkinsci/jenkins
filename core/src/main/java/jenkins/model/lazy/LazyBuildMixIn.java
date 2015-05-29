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

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.SubTask;
import hudson.widgets.BuildHistoryWidget;
import hudson.widgets.HistoryWidget;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import static java.util.logging.Level.FINER;
import jenkins.model.RunIdMigrator;

/**
 * Makes it easier to use a lazy {@link RunMap} from a {@link Job} implementation.
 * Provides method implementations for some abstract {@link Job} methods,
 * as well as some methods which are not abstract but which you should override.
 * <p>Should be kept in a {@code transient} field in the job.
 * @since 1.556
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // BuildHistoryWidget, and AbstractItem.getParent
public abstract class LazyBuildMixIn<JobT extends Job<JobT,RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT,RunT>, RunT extends Run<JobT,RunT> & LazyBuildMixIn.LazyLoadingRun<JobT,RunT>> {

    private static final Logger LOGGER = Logger.getLogger(LazyBuildMixIn.class.getName());

    @SuppressWarnings("deprecation") // [JENKINS-15156] builds accessed before onLoad or onCreatedFromScratch called
    private @Nonnull RunMap<RunT> builds = new RunMap<RunT>();

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
    public final @Nonnull RunMap<RunT> getRunMap() {
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
            for (RunT r : currentBuilds.getLoadedBuilds().values()) {
                if (r.isBuilding()) {
                    // Do not use RunMap.put(Run):
                    _builds.put(r.getNumber(), r);
                }
            }
        }
        this.builds = _builds;
    }

    private RunMap<RunT> createBuildRunMap() {
        RunMap<RunT> r = new RunMap<RunT>(asJob().getBuildDir(), new RunMap.Constructor<RunT>() {
            @Override public RunT create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });
        RunIdMigrator runIdMigrator = asJob().runIdMigrator;
        assert runIdMigrator != null;
        r.runIdMigrator = runIdMigrator;
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
     * The default implementation just calls the ({@link Job}, {@link File}) constructor of {@link #getBuildClass}.
     */
    public RunT loadBuild(File dir) throws IOException {
        try {
            return getBuildClass().getConstructor(asJob().getClass(), File.class).newInstance(asJob(), dir);
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }

    /**
     * Creates a new build of this project for immediate execution.
     * Calls the ({@link Job}) constructor of {@link #getBuildClass}.
     * Suitable for {@link SubTask#createExecutable}.
     */
    public final synchronized RunT newBuild() throws IOException {
        try {
            RunT lastBuild = getBuildClass().getConstructor(asJob().getClass()).newInstance(asJob());
            builds.put(lastBuild);
            lastBuild.getPreviousBuild(); // JENKINS-20662: create connection to previous build
            return lastBuild;
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw handleInvocationTargetException(e);
        } catch (NoSuchMethodException e) {
            throw new Error(e);
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
     * Suitable for {@link Job#createHistoryWidget}.
     */
    public final HistoryWidget createHistoryWidget() {
        return new BuildHistoryWidget(asJob(), builds, Job.HISTORY_ADAPTER);
    }

    /**
     * Marker for a {@link Job} which uses this mixin.
     */
    public interface LazyLoadingJob<JobT extends Job<JobT,RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT,RunT>, RunT extends Run<JobT,RunT> & LazyLoadingRun<JobT,RunT>> {
        LazyBuildMixIn<JobT,RunT> getLazyBuildMixIn();
    }

    public interface LazyLoadingRun<JobT extends Job<JobT,RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT,RunT>, RunT extends Run<JobT,RunT> & LazyLoadingRun<JobT,RunT>> {
        RunMixIn<JobT,RunT> getRunMixIn();
    }

    /**
     * Accompanying helper for the run type.
     * Stateful but should be held in a {@code transient final} field.
     */
    public static abstract class RunMixIn<JobT extends Job<JobT,RunT> & Queue.Task & LazyBuildMixIn.LazyLoadingJob<JobT,RunT>, RunT extends Run<JobT,RunT> & LazyLoadingRun<JobT,RunT>> {

        /**
         * Pointers to form bi-directional link between adjacent runs using
         * {@link LazyBuildMixIn}.
         *
         * <p>
         * Some {@link Run}s do lazy-loading, so we don't use
         * {@link #previousBuildR} and {@link #nextBuildR}, and instead use these
         * fields and point to {@link #selfReference} (or {@link #none}) of
         * adjacent builds.
         */
        private volatile BuildReference<RunT> previousBuildR, nextBuildR;

        /**
         * Used in {@link #previousBuildR} and {@link #nextBuildR} to indicate
         * that we know there is no next/previous build (as opposed to {@code null},
         * which is used to indicate we haven't determined if there is a next/previous
         * build.)
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static final BuildReference NONE = new BuildReference("NONE", null);

        @SuppressWarnings("unchecked")
        private BuildReference<RunT> none() {
            return NONE;
        }

        private BuildReference<RunT> selfReference;

        protected RunMixIn() {}

        protected abstract RunT asRun();

        /**
         * To implement {@link Run#createReference}.
         */
        public final synchronized BuildReference<RunT> createReference() {
            if (selfReference == null) {
                selfReference = new BuildReference<RunT>(asRun().getId(), asRun());
            }
            return selfReference;
        }

        /**
         * To implement {@link Run#dropLinks}.
         */
        public final void dropLinks() {
            if (nextBuildR != null) {
                RunT nb = nextBuildR.get();
                if (nb != null) {
                    nb.getRunMixIn().previousBuildR = previousBuildR;
                }
            }
            if (previousBuildR != null) {
                RunT pb = previousBuildR.get();
                if (pb != null) {
                    pb.getRunMixIn().nextBuildR = nextBuildR;
                }
            }

            // make this build object unreachable by other Runs
            createReference().clear();
        }

        /**
         * To implement {@link Run#getPreviousBuild}.
         */
        public final RunT getPreviousBuild() {
            while (true) {
                BuildReference<RunT> r = previousBuildR;    // capture the value once

                if (r == null) {
                    // having two neighbors pointing to each other is important to make RunMap.removeValue work
                    JobT _parent = asRun().getParent();
                    if (_parent == null) {
                        throw new IllegalStateException("no parent for " + asRun().number);
                    }
                    RunT pb = _parent.getLazyBuildMixIn()._getRuns().search(asRun().number - 1, AbstractLazyLoadRunMap.Direction.DESC);
                    if (pb != null) {
                        pb.getRunMixIn().nextBuildR = createReference();   // establish bi-di link
                        this.previousBuildR = pb.getRunMixIn().createReference();
                        LOGGER.log(FINER, "Linked {0}<->{1} in getPreviousBuild()", new Object[]{this, pb});
                        return pb;
                    } else {
                        this.previousBuildR = none();
                        return null;
                    }
                }
                if (r == none()) {
                    return null;
                }

                RunT referent = r.get();
                if (referent != null) {
                    return referent;
                }

                // the reference points to a GC-ed object, drop the reference and do it again
                this.previousBuildR = null;
            }
        }

        /**
         * To implement {@link Run#getNextBuild}.
         */
        public final RunT getNextBuild() {
            while (true) {
                BuildReference<RunT> r = nextBuildR;    // capture the value once

                if (r == null) {
                    // having two neighbors pointing to each other is important to make RunMap.removeValue work
                    RunT nb = asRun().getParent().getLazyBuildMixIn()._getRuns().search(asRun().number + 1, AbstractLazyLoadRunMap.Direction.ASC);
                    if (nb != null) {
                        nb.getRunMixIn().previousBuildR = createReference();   // establish bi-di link
                        this.nextBuildR = nb.getRunMixIn().createReference();
                        LOGGER.log(FINER, "Linked {0}<->{1} in getNextBuild()", new Object[]{this, nb});
                        return nb;
                    } else {
                        this.nextBuildR = none();
                        return null;
                    }
                }
                if (r == none()) {
                    return null;
                }

                RunT referent = r.get();
                if (referent != null) {
                    return referent;
                }

                // the reference points to a GC-ed object, drop the reference and do it again
                this.nextBuildR = null;
            }
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
