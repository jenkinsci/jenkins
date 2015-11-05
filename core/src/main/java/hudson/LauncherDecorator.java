package hudson;

import jenkins.model.Jenkins;
import hudson.model.Node;
import hudson.model.Executor;
import hudson.tasks.BuildWrapper;

/**
 * Decorates {@link Launcher} so that one can intercept executions of commands
 * and alters the command being executed, such as doing this in fakeroot, sudo, pfexec, etc.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.299
 * @see BuildWrapper#decorateLauncher(AbstractBuild, Launcher, BuildListener)
 */
public abstract class LauncherDecorator implements ExtensionPoint {
    /**
     * Called from {@link Node#createLauncher(TaskListener)} to decorate the launchers.
     *
     * <p>
     * This method should perform node-specific decoration. For job-specific decoration,
     * {@link BuildWrapper#decorateLauncher(AbstractBuild, Launcher, BuildListener)} might
     * fit your needs better.
     *
     * <p>
     * If the implementation wants to do something differently if the launcher is
     * for a build, call {@link Executor#currentExecutor()}. If it returns non-null
     * you can figure out the current build in progress from there. Note that
     * {@link Launcher}s are also created for doing things other than builds,
     * so {@link Executor#currentExecutor()} may return null. Also, for job-specific
     * decoration, see {@link BuildWrapper#decorateLauncher(AbstractBuild, Launcher, BuildListener)} as well.
     *
     * @param launcher
     *      The base launcher that you can decorate. Never null.
     * @param node
     *      Node for which this launcher is created. Never null.
     * @return
     *      Never null. Return the 'launcher' parameter to do no-op.
     * @see Launcher#decorateFor(Node)
     * @see Launcher#decorateByPrefix(String[])
     */
    public abstract Launcher decorate(Launcher launcher, Node node);

    /**
     * Returns all the registered {@link LauncherDecorator}s.
     */
    public static ExtensionList<LauncherDecorator> all() {
        return ExtensionList.lookup(LauncherDecorator.class);
    }
}
