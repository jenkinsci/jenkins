package org.apache.maven.lifecycle;

import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.monitor.event.EventDispatcher;

/**
 * {@link LifecycleExecutor} interceptor.
 *
 * <p>
 * This class is in the same package as in {@link DefaultLifecycleExecutor},
 * because Plexus requires the class and its subordinates (like {@link Lifecycle},
 * which is referenced in <tt>components.xml</tt>
 *
 * @author Kohsuke Kawaguchi
 */
public class LifecycleExecutorInterceptor extends DefaultLifecycleExecutor {
    /**
     * {@link LifecycleExecutorListener} that receives events.
     * There's no way external code can connect to a running instance of
     * {@link LifecycleExecutorInterceptor}, so this cannot be made instance fields.
     */
    private static LifecycleExecutorListener listener;


    public static void setListener(LifecycleExecutorListener _listener) {
        listener = _listener;
    }

    public void execute(MavenSession session, ReactorManager rm, EventDispatcher dispatcher) throws BuildFailureException, LifecycleExecutionException {
        if(listener!=null)
            listener.preBuild(session,rm,dispatcher);
        try {
            super.execute(session, rm, dispatcher);
        } finally {
            if(listener!=null)
                listener.postBuild(session,rm,dispatcher);
        }
    }
}
