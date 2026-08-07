/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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

package hudson.tasks;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.Run.RunnerAbortedException;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;

/**
 * Pluggability point for performing pre/post actions for the build process.
 *
 * <p>
 * This extension point enables a plugin to set up / tear down additional
 * services needed to perform a build, such as setting up local X display,
 * or launching and stopping application servers for testing.
 *
 * <p>
 * An instance of {@link BuildWrapper} is associated with a {@link Project}
 * with configuration information as its state. An instance is persisted
 * along with {@link Project}.
 *
 * <p>
 * {@link BuildWrapper}s are instantiated when the user saves the job configuration, and sticks
 * around in memory until the job configuration is overwritten.
 *
 *
 * <p>
 * The {@link #setUp(Build,Launcher,BuildListener)} method is invoked for each build.
 *
 * <p>
 * {@link BuildWrapper} requires an user consent (in terms of a checkbox) to work.
 * If this is not desirable, see {@link hudson.model.Environment} for other ways
 * to inject Environments to builds.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BuildWrapper implements Describable<BuildWrapper>, ExtensionPoint {
    /**
     * Represents the environment set up by {@link BuildWrapper#setUp(Build,Launcher,BuildListener)}.
     *
     * <p>
     * It is expected that the subclasses of {@link BuildWrapper} extends this
     * class and implements its own semantics.
     */
    public abstract class Environment extends hudson.model.Environment {
        /**
         * Runs after the {@link Builder} completes, and performs a tear down.
         *
         * <p>
         * This method is invoked even when the build failed, so that the
         * clean up operation can be performed regardless of the build result
         * (for example, you'll want to stop application server even if a build
         * fails.)  {@link Build#getResult} in this case will return Result.FAILURE
         * (since 1.339), and a null result indicates SUCCESS-so-far (post-build
         * actions may still affect the final result).
         *
         * @param build
         *      The same {@link Build} object given to the set up method.
         * @param listener
         *      The same {@link BuildListener} object given to the set up method.
         * @return
         *      true if the build can continue, false if there was an error
         *      and the build needs to be aborted.
         * @throws IOException
         *      terminates the build abnormally. Hudson will handle the exception
         *      and reports a nice error message.
         * @since 1.150
         */
        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            if (build instanceof Build)
                return tearDown((Build) build, listener);
            else
                return true;
        }

        /**
         * @deprecated since 2007-10-28.
         *      Use {@link #tearDown(AbstractBuild, BuildListener)} instead.
         */
        @Deprecated
        public boolean tearDown(Build build, BuildListener listener) throws IOException, InterruptedException {
            return true;
        }
    }

    /**
     * Runs before the {@link Builder} runs (but after the checkout has occurred), and performs a set up.
     *
     * @param build
     *      The build in progress for which an {@link Environment} object is created.
     *      Never null.
     * @param launcher
     *      This launcher can be used to launch processes for this build.
     *      If the build runs remotely, launcher will also run a job on that remote machine.
     *      Never null.
     * @param listener
     *      Can be used to send any message.
     * @return
     *      non-null if the build can continue, null if there was an error
     *      and the build needs to be aborted.
     * @throws IOException
     *      terminates the build abnormally. Jenkins will handle the exception
     *      and report a nice error message.
     * @throws UnsupportedOperationException
     *      when a plugin does not support this type of build.
     * @since 1.150
     */
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        // If it's a Build, and the plugin implements the deprecated API, use it.
        if (build instanceof Build && Util.isOverridden(BuildWrapper.class, getClass(), "setUp", Build.class, Launcher.class, BuildListener.class))
            return setUp((Build) build, launcher, listener);
        else // not a supported build type
            throw new UnsupportedOperationException("Plugin class '" + this.getClass().getName() +
                    "' does not support a build of type '" + build.getClass().getName() + "'.");
    }

    /**
     * @throws AbstractMethodError
     *     when a plugin overrides neither this method nor {@link #setUp(AbstractBuild, Launcher, BuildListener)}.
     *
     * @deprecated since 2007-10-28.
     *      Use {@link #setUp(AbstractBuild, Launcher, BuildListener)} instead.
     */
    @Deprecated
    public Environment setUp(Build build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (Util.isOverridden(BuildWrapper.class, getClass(), "setUp", AbstractBuild.class, Launcher.class, BuildListener.class)) {
            // old client calling newer implementation. forward the call.
            return setUp((AbstractBuild) build, launcher, listener);
        } else {
            // happens only if the subtype fails to override either setUp method
            throw new AbstractMethodError("Plugin class '" + this.getClass().getName() + "' does not override " +
                    "either overload of the setUp method.");
        }
    }

    /**
     * Provides an opportunity for a {@link BuildWrapper} to decorate a {@link Launcher} to be used in the build.
     *
     * <p>
     * This hook is called very early on in the build (even before {@link #setUp(AbstractBuild, Launcher, BuildListener)} is invoked.)
     * The typical use of {@link Launcher} decoration involves in modifying the environment that processes run,
     * such as the use of sudo/pfexec/chroot, or manipulating environment variables.
     *
     * <p>
     * The default implementation is no-op, which just returns the {@code launcher} parameter as-is.
     *
     * @param build
     *      The build in progress for which this {@link BuildWrapper} is called. Never null.
     * @param launcher
     *      The default launcher. Never null. This method is expected to wrap this launcher.
     *      This makes sure that when multiple {@link BuildWrapper}s attempt to decorate the same launcher
     *      it will sort of work. But if you are developing a plugin where such collision is not a concern,
     *      you can also simply discard this {@link Launcher} and create an entirely different {@link Launcher}
     *      and return it, too.
     * @param listener
     *      Connected to the build output. Never null. Can be used for error reporting.
     * @return
     *      Must not be null. If a fatal error happens, throw an exception.
     * @throws RunnerAbortedException
     *      If a fatal error is detected but the implementation handled it gracefully, throw this exception
     *      to suppress stack trace.
     * @since 1.280
     * @see LauncherDecorator
     */
    public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, RunnerAbortedException {
        return launcher;
    }

    /**
     * Provides an opportunity for a {@link BuildWrapper} to decorate the {@link BuildListener} logger to be used by the build.
     *
     * <p>
     * This hook is called very early on in the build (even before {@link #setUp(AbstractBuild, Launcher, BuildListener)} is invoked.)
     *
     * <p>
     * The default implementation is no-op, which just returns the {@code logger} parameter as-is.
     * <p>({@link ArgumentListBuilder#add(String, boolean)} is a simpler way to suppress a single password.)
     * @param build
     *      The build in progress for which this {@link BuildWrapper} is called. Never null.
     * @param logger
     *      The default logger. Never null. This method is expected to wrap this logger.
     *      This makes sure that when multiple {@link BuildWrapper}s attempt to decorate the same logger
     *      it will sort of work.
     * @return
     *      Must not be null. If a fatal error happens, throw an exception.
     * @throws RunnerAbortedException
     *      If a fatal error is detected but the implementation handled it gracefully, throw this exception
     *      to suppress stack trace.
     * @since 1.374
     * @see ConsoleLogFilter
     */
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException, RunnerAbortedException {
        return logger;
    }

    /**
     * Provides an opportunity for a {@link BuildWrapper} to perform some actions before SCM checkout.
     *
     * <p>
     * This hook is called early on in the build (before {@link #setUp(AbstractBuild, Launcher, BuildListener)},
     * but after {@link #decorateLauncher(AbstractBuild, Launcher, BuildListener)} is invoked.)
     * The typical use is delete existing workspace before new build starts etc.
     *
     * <p>
     * By the time this method is called, the workspace is assigned to the build, which can be obtained
     * via {@code build.getWorkspace()}.
     *
     * <p>
     * The default implementation is no-op.
     *
     * @param build
     *      The build in progress for which this {@link BuildWrapper} is called. Never null.
     * @param launcher
     *      The launcher. Never null.
     * @param listener
     *      Connected to the build output. Never null. Can be used for error reporting.
     * @since 1.399
     */
    public void preCheckout(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException{
    }

    /**
     * {@link Action} to be displayed in the job page.
     *
     * @param job
     *      This object owns the {@link BuildWrapper}. The returned action will be added to this object.
     * @return
     *      null if there's no such action.
     * @since 1.226
     * @deprecated
     *      Use {@link #getProjectActions(AbstractProject)} instead.
     */
    @Deprecated
    public Action getProjectAction(AbstractProject job) {
        return null;
    }

    /**
     * {@link Action}s to be displayed in the job page.
     *
     * @param job
     *      This object owns the {@link BuildWrapper}. The returned action will be added to this object.
     * @return
     *      can be empty but never null
     * @since 1.341
     */
    public Collection<? extends Action> getProjectActions(AbstractProject job) {
        // delegate to getJobAction (singular) for backward compatible behavior
        Action a = getProjectAction(job);
        if (a == null)    return Collections.emptyList();
        return List.of(a);
    }

    /**
     * Called to define {@linkplain AbstractBuild#getBuildVariables()}.
     *
     * This provides an opportunity for a BuildWrapper to append any additional
     * build variables defined for the current build.
     *
     * @param build
     *      The build in progress for which this {@link BuildWrapper} is called. Never null.
     * @param variables
     *      Contains existing build variables. Add additional build variables that you contribute
     *      to this map.
     */
    public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
        // noop
    }

    /**
     * Called to define sensitive build variables. This provides an opportunity
     * for a BuildWrapper to denote the names of variables that are sensitive in
     * nature and should not be exposed in output.
     *
     * @param build
     *      The build in progress for which this {@link BuildWrapper} is called. Never null.
     * @param sensitiveVariables
     *      Contains names of sensitive build variables. Names of sensitive variables
     *      that were added with {@link #makeBuildVariables(hudson.model.AbstractBuild, java.util.Map)}
     * @since 1.378
     */
    public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        // noop
    }

    /**
     * Returns all the registered {@link BuildWrapper} descriptors.
     */
    // for compatibility we can't use BuildWrapperDescriptor
    public static DescriptorExtensionList<BuildWrapper, Descriptor<BuildWrapper>> all() {
        // use getDescriptorList and not getExtensionList to pick up legacy instances
        return Jenkins.get().getDescriptorList(BuildWrapper.class);
    }
}
