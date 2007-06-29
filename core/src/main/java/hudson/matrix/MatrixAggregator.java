package hudson.matrix;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.tasks.BuildStep;
import hudson.tasks.Publisher;

import java.io.IOException;

/**
 * Performs the aggregation of {@link MatrixRun} results
 * into {@link MatrixBuild}.
 *
 * <p>
 * {@link MatrixAggregator} is a transitive stateful mutable object.
 * Unlike {@link Publisher}, it is not persisted. Instead, a fresh
 * instance is created for each {@link MatrixBuild}, and various
 * methods on this class are invoked in the event callback style
 * as the build progresses.
 *
 * <p>
 * The end result of the aggregation should be
 * {@link MatrixBuild#addAction(Action) contributed as actions}. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.115
 * @see MatrixAggregatable
 */
public abstract class MatrixAggregator implements ExtensionPoint {
    /**
     * The build in progress. Never null.
     */
    protected final MatrixBuild build;

    protected final Launcher launcher;
    /**
     * The listener to send the output to. Never null.
     */
    protected final BuildListener listener;

    protected MatrixAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
    }

    /**
     * Called before the build starts.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     * @see BuildStep#prebuild(Build,BuildListener)
     */
    public boolean startBuild() throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called whenever one run is completed.
     *
     * @param run
     *      The completed {@link MatrixRun} object. Always non-null.
     * @return
     *      See {@link #startBuild()} for the return value semantics.
     */
    public boolean endRun(MatrixRun run) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called after all the {@link MatrixRun}s have been completed
     * to indicate that the build is about to finish.
     * 
     * @return
     *      See {@link #startBuild()} for the return value semantics.
     */
    public boolean endBuild() throws InterruptedException, IOException {
        return true;
    }
}
