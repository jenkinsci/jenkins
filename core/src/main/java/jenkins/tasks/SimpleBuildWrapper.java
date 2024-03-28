/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package jenkins.tasks;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A generalization of {@link BuildWrapper} that, like {@link SimpleBuildStep}, may be called at various points within a build.
 * <p>Such a build wrapper would typically be written according to make few assumptions about how it is being used.
 * Some hints about this refactoring:
 * <ul>
 * <li>Replace {@link AbstractBuild#getWorkspace} with the provided path.
 * <li>Replace {@link AbstractBuild#getProject} with {@link Run#getParent}.
 * <li>Use {@link FilePath#toComputer} rather than {@link Computer#currentComputer}.
 * <li>Do not bother with {@link AbstractBuild#getBuildVariables} if you are not passed an {@link AbstractBuild} (treat it like an empty map).
 * <li>The {@link Disposer} must be safely serializable. This means it should be a {@code static} class if nested, and define a {@code serialVersionUID}.
 * </ul>
 * @since 1.599
 */
@SuppressWarnings("rawtypes") // inherited
public abstract class SimpleBuildWrapper extends BuildWrapper {

    /**
     * Determines whether or not this wrapper requires a workspace context (working directory and launcher).
     * <p>
     * When such a context is required (the default), {@link #setUp(Context, Run, FilePath, Launcher, TaskListener, EnvVars)} applies.
     * Otherwise, {@link #setUp(Context, Run, TaskListener, EnvVars)} applies.
     *
     * @return {@code true} if this wrapper requires a workspace context; {@code false} otherwise.
     * @since 2.258
     */
    public boolean requiresWorkspace() {
        return true;
    }

    /**
     * Called when a segment of a build is started that is to be enhanced with this wrapper.
     * <p>
     * This method <strong>must</strong> be overridden when this wrapper requires a workspace context. If such a context
     * is <em>not</em> required, it does not need to be overridden; it will then forward to
     * {@link #setUp(Context, Run, TaskListener, EnvVars)}.
     *
     * @param context a way of collecting modifications to the environment for nested steps
     * @param build a build being run
     * @param workspace a workspace of the build
     * @param launcher a way to start commands
     * @param listener a way to report progress
     * @param initialEnvironment the environment variables set at the outset
     * @throws AbstractMethodError if this wrapper requires a workspace context, and this method is not overridden
     * @throws IOException if something fails; {@link AbortException} for user errors
     * @throws InterruptedException if setup is interrupted
     */
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // If this does not require a workspace, defer to the version that does not take a workspace and launcher.
        if (!this.requiresWorkspace()) {
            this.setUp(context, build, listener, initialEnvironment);
            return;
        }
        throw new AbstractMethodError("Unless a build wrapper is marked as not requiring a workspace context, you must implement the overload of the setUp() method that takes both a workspace and a launcher.");
    }

    /**
     * Called when a segment of a build is started that is to be enhanced with this wrapper.
     * <p>
     * This method <strong>must</strong> be overridden when this wrapper does not require a workspace context, and will
     * not be called when such a context <em>is</em> required.
     *
     * @param context a way of collecting modifications to the environment for nested steps
     * @param build a build being run
     * @param listener a way to report progress
     * @param initialEnvironment the environment variables set at the outset
     * @throws AbstractMethodError if this method is not overridden
     * @throws IllegalStateException if this wrapper requires a workspace context
     * @throws IOException if something fails; {@link AbortException} for user errors
     * @throws InterruptedException if setup is interrupted
     * @since 2.258
     */
    public void setUp(Context context, Run<?, ?> build, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // If this wrapper requires a workspace, this is the wrong method to call.
        if (this.requiresWorkspace()) {
            throw new IllegalStateException("This build wrapper requires a workspace context, but none was provided.");
        }
        // Otherwise, this method must have an implementation.
        throw new AbstractMethodError("When a build wrapper is marked as not requiring a workspace context, you must implement the overload of the setUp() method that does not take a workspace or launcher.");
    }

    /**
     * Creates a new {@link Context} for use with this wrapper.
     *
     * @return a new {@link Context} instance
     */
    @Restricted(Beta.class) // to indicate it is to be called by Jenkins internals only; not part of the normal API
    public Context createContext() {
        return new Context(this.requiresWorkspace());
    }

    /**
     * Parameter passed to {@link #setUp} to allow an implementation to specify its behavior after the initial setup.
     */
    public static final class Context {
        private Disposer disposer;
        private final Map<String, String> env = new HashMap<>();
        private final @CheckForNull Boolean wrapperRequiresWorkspace;

        /**
         * Creates a new context.
         *
         * @deprecated Use {@link SimpleBuildWrapper#createContext()} instead, so that this context can tell whether or
         * not a disposer will require a workspace context.
         */
        @Deprecated
        public Context() {
            this.wrapperRequiresWorkspace = null;
        }

        /**
         * Creates a context.
         *
         * @param wrapperRequiresWorkspace Indicates whether the wrapper for which this context was created requires a
         * workspace context, which in turn determines the same for any {@link Disposer} set on this context.
         */
        private Context(boolean wrapperRequiresWorkspace) {
            this.wrapperRequiresWorkspace = wrapperRequiresWorkspace;
        }

        /**
         * Specify an environment variable override to apply to processes launched within the block.
         * If unspecified, environment variables will be inherited unmodified.
         * @param key handles the special {@code PATH+SOMETHING} syntax as in {@link EnvVars#override}
         */
        public void env(String key, String value) {
            if (env.containsKey(key)) {
                throw new IllegalStateException("just one binding for " + key);
            }
            env.put(key, value);
        }

        public @CheckForNull Disposer getDisposer() {
            return disposer;
        }

        public @NonNull Map<String, String> getEnv() {
            return env;
        }

        /**
         * Specify an action to take when the block ends.
         * If not specified, nothing special happens.
         */
        public void setDisposer(@NonNull Disposer disposer) {
            if (this.disposer != null) {
                throw new IllegalStateException("just one disposer");
            }
            // Assumption: the provided Disposer instance is only associated with one wrapper at a time, or at least
            // only with wrappers that agree on whether or not a workspace is required.
            disposer.wrapperRequiresWorkspace = this.wrapperRequiresWorkspace;
            this.disposer = disposer;
        }
    }

    /**
     * An optional callback to run at the end of the wrapped block.
     * Must be safely serializable, so it receives runtime context comparable to that of the original setup.
     */
    public abstract static class Disposer implements Serializable {

        private static final long serialVersionUID = 1L;
        @CheckForNull
        private Boolean wrapperRequiresWorkspace;

        /**
         * Determines whether or not this end-of-wrapped-block callback requires a workspace context (working
         * directory and launcher).
         * <p>
         * When such a context is required (the default), then {@link #tearDown(Run, FilePath, Launcher, TaskListener)} applies.
         * Otherwise, {@link #tearDown(Run, TaskListener)} applies.
         *
         * @return {@code true} when this end-of-wrapped-block callback requires a workspace context; {@code false} otherwise.
         * @since 2.258
         */
        @Restricted(Beta.class) // to indicate it is to be called by Jenkins internals only; not part of the normal API
        public final boolean requiresWorkspace() {
            return this.wrapperRequiresWorkspace == null || this.wrapperRequiresWorkspace;
        }

        /**
         * Attempt to clean up anything that was done in the initial setup.
         * <p>
         * This method <strong>must</strong> be overridden when this end-of-wrapped-block callback requires a workspace
         * context. If such a context is <em>not</em> required, it does not need to be overridden; it will then forward
         * to {@link #tearDown(Run, TaskListener)}.
         *
         * @param build a build being run
         * @param workspace a workspace of the build
         * @param launcher a way to start commands
         * @param listener a way to report progress
         * @throws AbstractMethodError if this end-of-wrapped-block callback requires a workspace and this method is not overridden.
         * @throws IOException if something fails; {@link AbortException} for user errors
         * @throws InterruptedException if tear down is interrupted
         */
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            // If this does not require a workspace, defer to the version that does not take a workspace and launcher.
            if (!this.requiresWorkspace()) {
                this.tearDown(build, listener);
                return;
            }
            throw new AbstractMethodError("Unless an end-of-wrapped-block callback is marked as not requiring a workspace context, you must implement the overload of the tearDown() method that takes both a workspace and a launcher.");
        }

        /**
         * Attempt to clean up anything that was done in the initial setup.
         * <p>
         * This method <strong>must</strong> be overridden when this end-of-wrapped-block callback does not require a
         * workspace context, and will not be called when such a context <em>is</em> required.
         *
         * @param build a build being run
         * @param listener a way to report progress
         * @throws AbstractMethodError if this this method is not overridden
         * @throws IllegalStateException if this end-of-wrapped-block callback requires a workspace
         * @throws IOException if something fails; {@link AbortException} for user errors
         * @throws InterruptedException if tear down is interrupted
         * @since 2.258
         */
        public void tearDown(Run<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
            // If this callback requires a workspace, this is the wrong method to call.
            if (this.requiresWorkspace()) {
                throw new IllegalStateException("This end-of-wrapped-block callback requires a workspace context, but none was provided.");
            }
            // Otherwise, this method must have an implementation.
            throw new AbstractMethodError("When an end-of-wrapped-block callback is marked as not requiring a workspace context, you must implement the overload of the tearDown() method that does not take a workspace or launcher.");
        }

    }

    /**
     * By default, when run as part of an {@link AbstractBuild}, will run late, in the {@link #setUp(AbstractBuild, Launcher, BuildListener)} phase.
     * May be overridden to return true, in which case this will run earlier, in the {@link #preCheckout} phase.
     * Ignored when not run as part of an {@link AbstractBuild}.
     */
    protected boolean runPreCheckout() {
        return false;
    }

    @Override public final Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (runPreCheckout()) {
            return new Environment() {};
        } else {
            final Context c = this.createContext();
            setUp(c, build, build.getWorkspace(), launcher, listener, build.getEnvironment(listener));
            return new EnvironmentWrapper(c, launcher);
        }
    }

    @Override public final void preCheckout(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        if (runPreCheckout()) {
            final Context c = this.createContext();
            setUp(c, build, build.getWorkspace(), launcher, listener, build.getEnvironment(listener));
            build.getEnvironments().add(new EnvironmentWrapper(c, launcher));
        }
    }

    private class EnvironmentWrapper extends Environment {
        private final Context c;
        private final Launcher launcher;

        EnvironmentWrapper(Context c, Launcher launcher) {
            this.c = c;
            this.launcher = launcher;
        }

        @Override public void buildEnvVars(Map<String, String> env) {
            if (env instanceof EnvVars) {
                ((EnvVars) env).overrideAll(c.env);
            } else { // ?
                env.putAll(c.env);
            }
        }

        @Override public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
            if (c.disposer != null) {
                c.disposer.tearDown(build, build.getWorkspace(), launcher, listener);
            }
            return true;
        }
    }

    /**
     * Allows this wrapper to decorate log output.
     * @param build as is passed to {@link #setUp(Context, Run, FilePath, Launcher, TaskListener, EnvVars)}
     * @return a filter which ignores its {@code build} parameter and is {@link Serializable}; or null (the default)
     * @since 1.608
     */
    public @CheckForNull ConsoleLogFilter createLoggerDecorator(@NonNull Run<?, ?> build) {
        return null;
    }

    @Override public final OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException, Run.RunnerAbortedException {
        ConsoleLogFilter filter = createLoggerDecorator(build);
        return filter != null ? filter.decorateLogger(build, logger) : logger;
    }

    /**
     * May be overridden but this will only take effect when used as a {@link BuildWrapper} on an {@link AbstractProject}.
     * <p>{@inheritDoc}
     * @since 1.608
     */
    @Override public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        return super.decorateLauncher(build, launcher, listener);
        // TODO reasonable to decorate Launcher within a dynamic scope, but this signature does not mix well with Context pattern.
        // Called from AbstractBuildExecution.createLauncher; how do we track what is decorating what?
        // Would have to keep something like a LaunchedDecorator, not an actual Launcher, in Context.
        // And createLauncher is called before even preCheckout, so much too early for the Context to have been prepared.
        // Could perhaps create a proxy Launcher whose launch method checks some field in the Context remembered for the build.
    }

    /**
     * May be overridden but this will only take effect when used as a {@link BuildWrapper} on an {@link AbstractProject}.
     * <p>{@inheritDoc}
     * @since 1.608
     */
    @Override public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
        super.makeBuildVariables(build, variables);
    }

    /**
     * May be overridden but this will only take effect when used as a {@link BuildWrapper} on an {@link AbstractProject}.
     * <p>{@inheritDoc}
     * @since 1.608
     */
    @Override public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        super.makeSensitiveBuildVariables(build, sensitiveVariables);
        // TODO determine if there is a meaningful way to generalize this; perhaps as a new [Run]Action recording sensitiveVariables?
        // Complicated by the fact that in principle someone could call getSensitiveBuildVariables *before* the wrapper starts and actually sets those variables,
        // though in practice the likely use cases would come later, and perhaps it is acceptable to omit the names of variables which are yet to be set.
        // Also unclear if there is any use case for calling this method after the build is done (or Jenkins is restarted);
        // most likely it is only used during the build itself.
        // Would be much cleaner if EnvVars itself recorded which keys had sensitive values.
    }

    /**
     * @return an empty set; this might never be called if the step is not part of the static configuration of a project; instead, add a {@link SimpleBuildStep.LastBuildAction} to a build when run
     */
    @Override public final Collection<? extends Action> getProjectActions(AbstractProject job) {
        return Collections.emptySet();
    }

}
