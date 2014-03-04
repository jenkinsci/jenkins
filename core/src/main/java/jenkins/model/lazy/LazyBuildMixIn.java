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
import hudson.model.Queue.Task;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.listeners.ItemListener;
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

/**
 * Makes it easier to use a lazy {@link RunMap} from a {@link Job} implementation.
 * Provides method implementations for some abstract {@link Job} methods,
 * as well as some methods which are not abstract but which you should override.
 * <p>Should be kept in a {@code transient} field in the job.
 * @since TODO
 */
public abstract class LazyBuildMixIn<P extends Job<P,R>,R extends Run<P,R>> {

    private static final Logger LOGGER = Logger.getLogger(LazyBuildMixIn.class.getName());

    private final Job<?,?> job;

    @SuppressWarnings("deprecation") // [JENKINS-15156] builds accessed before onLoad or onCreatedFromScratch called
    private @Nonnull RunMap<R> builds = new RunMap<R>();

    // keep track of the previous time we started a build
    private long lastBuildStartTime;

    /**
     * Initializes this mixin based on a job.
     * Call this from a constructor and {@link AbstractItem#onLoad} to make sure it is always initialized.
     * @param job the owning job (should be of type {@code P} and assignable to {@link Task})
     */
    public LazyBuildMixIn(Job<?,?> job) {
        this.job = job;
    }

    /**
     * Gets the raw model.
     * Normally should not be called as such.
     * Note that the initial value is replaced during {@link #onCreatedFromScratch} or {@link #onLoad}.
     */
    public final @Nonnull RunMap<R> getRunMap() {
        return builds;
    }

    /**
     * Same as {@link #getRunMap} but suitable for {@link Job#_getRuns}, which you <em>must override to be public</em>.
     */
    public final RunMap<R> _getRuns() {
        assert builds.baseDirInitialized() : "neither onCreatedFromScratch nor onLoad called on " + job + " yet";
        return builds;
    }

    /**
     * Something to be called from {@link AbstractItem#onCreatedFromScratch}.
     */
    public final void onCreatedFromScratch() {
        builds = createBuildRunMap();
    }

    /**
     * Something to be called from {@link AbstractItem#onLoad}.
     */
    @SuppressWarnings("unchecked")
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        RunMap<R> _builds = createBuildRunMap();
        RunMap<R> currentBuilds = this.builds;
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
            if (current != null && current.getClass() == job.getClass()) {
                try {
                    currentBuilds = (RunMap<R>) current.getClass().getMethod("_getRuns").invoke(current);
                } catch (Exception x) {
                    assert false : "you should have made _getRuns public in " + job.getClass();
                }
            }
        }
        if (currentBuilds != null) {
            // if we are reloading, keep all those that are still building intact
            for (R r : currentBuilds.getLoadedBuilds().values()) {
                if (r.isBuilding()) {
                    _builds.put(r);
                }
            }
        }
        this.builds = _builds;
    }

    private RunMap<R> createBuildRunMap() {
        return new RunMap<R>(job.getBuildDir(), new RunMap.Constructor<R>() {
            @Override public R create(File dir) throws IOException {
                return loadBuild(dir);
            }
        });
    }

    /**
     * Type token for the build type.
     * The build class must have two constructors:
     * one taking the project type ({@code P});
     * and one taking {@code P}, then {@link File}.
     */
    protected abstract Class<R> getBuildClass();

    /**
     * Loads an existing build record from disk.
     * The default implementation just calls the ({@link Job}, {@link File}) constructor of {@link #getBuildClass}.
     */
    public R loadBuild(File dir) throws IOException {
        try {
            return getBuildClass().getConstructor(job.getClass(), File.class).newInstance(job, dir);
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
    @SuppressWarnings("SleepWhileHoldingLock")
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SWL_SLEEP_WITH_LOCK_HELD")
    public final synchronized R newBuild() throws IOException {
    	// make sure we don't start two builds in the same second
    	// so the build directories will be different too
    	long timeSinceLast = System.currentTimeMillis() - lastBuildStartTime;
    	if (timeSinceLast < 1000) {
    		try {
				Thread.sleep(1000 - timeSinceLast);
			} catch (InterruptedException e) {
			}
    	}
    	lastBuildStartTime = System.currentTimeMillis();
        try {
            R lastBuild = getBuildClass().getConstructor(job.getClass()).newInstance(job);
            builds.put(lastBuild);
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
    public final void removeRun(R run) {
        if (!builds.remove(run)) {
            LOGGER.log(Level.WARNING, "{0} did not contain {1} to begin with", new Object[] {job, run});
        }
    }

    /**
     * Suitable for {@link Job#getBuild}.
     */
    public final R getBuild(String id) {
        return builds.getById(id);
    }

    /**
     * Suitable for {@link Job#getBuildByNumber}.
     */
    public final R getBuildByNumber(int n) {
        return builds.getByNumber(n);
    }

    /**
     * Suitable for {@link Job#getFirstBuild}.
     */
    public final R getFirstBuild() {
        return builds.oldestBuild();
    }

    /**
     * Suitable for {@link Job#getLastBuild}.
     */
    public final @CheckForNull R getLastBuild() {
        return builds.newestBuild();
    }

    /**
     * Suitable for {@link Job#getNearestBuild}.
     */
    public final R getNearestBuild(int n) {
        return builds.search(n, AbstractLazyLoadRunMap.Direction.ASC);
    }

    /**
     * Suitable for {@link Job#getNearestOldBuild}.
     */
    public final R getNearestOldBuild(int n) {
        return builds.search(n, AbstractLazyLoadRunMap.Direction.DESC);
    }

    /**
     * Suitable for {@link Job#createHistoryWidget}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final HistoryWidget createHistoryWidget() {
        return new BuildHistoryWidget((Task) job, builds, Job.HISTORY_ADAPTER);
    }

    @Restricted(DoNotUse.class)
    @Extension public static final class ItemListenerImpl extends ItemListener {
        @Override public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            if (item instanceof Job) {
                RunMap<?> builds;
                try {
                    builds = (RunMap<?>) item.getClass().getMethod("_getRuns").invoke(item);
                } catch (NoSuchMethodException x) {
                    // OK, did not override this to be public
                    return;
                } catch (ClassCastException x) {
                    // override it to be public but of a different type, fine
                    return;
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, null, x);
                    return;
                }
                builds.updateBaseDir(((Job) item).getBuildDir());
            }
        }
    }

}
