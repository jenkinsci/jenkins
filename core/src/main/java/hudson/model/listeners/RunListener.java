package hudson.model.listeners;

import hudson.ExtensionPoint;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.CopyOnWriteList;

/**
 * Receives notifications about builds.
 *
 * <p>
 * Listener is always Hudson-wide, so once registered it gets notifications for every build
 * that happens in this Hudson.
 *
 * <p>
 * This is an abstract class so that methods added in the future won't break existing listeners.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.145
 */
public abstract class RunListener<R extends Run> implements ExtensionPoint {
    public final Class<R> targetType;

    protected RunListener(Class<R> targetType) {
        this.targetType = targetType;
    }

    /**
     * Called after a build is completed.
     *
     * @param r
     *      The completed build.
     * @param listener
     *      The listener for this build. This can be used to produce log messages, for example,
     *      which becomes a part of the "console output" of this build. But when this method runs,
     *      the build is considered completed, so its status cannot be changed anymore.
     */
    public void onCompleted(R r, TaskListener listener) {}

    /**
     * Registers this object as an active listener so that it can start getting
     * callbacks invoked.
     */
    public void register() {
        LISTENERS.add(this);
    }

    /**
     * Reverse operation of {@link #register()}.
     */
    public void unregister() {
        LISTENERS.remove(this);
    }

    /**
     * List of registered listeners.
     */
    public static final CopyOnWriteList<RunListener> LISTENERS = new CopyOnWriteList<RunListener>();

    /**
     * Fires the {@link #onCompleted} event.
     */
    public static void fireCompleted(Run r, TaskListener listener) {
        for (RunListener l : LISTENERS) {
            if(l.targetType.isInstance(r))
                l.onCompleted(r,listener);
        }
    }
}
