package hudson.maven.agent;

import org.apache.maven.lifecycle.DefaultLifecycleExecutor;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.BuildFailureException;

/**
 * @author Kohsuke Kawaguchi
 */
public class LifecycleExecutorInterceptor extends DefaultLifecycleExecutor {
    /**
     * {@link LifecycleExecutorListener} that receives events.
     * There's no way external code can connect to a running instance of
     * {@link LifecycleExecutorInterceptor}, so this cannot be made instance fields.
     */
    private static LifecycleExecutorListener listener;


    public static void setListener(LifecycleExecutorListener listener) {
        LifecycleExecutorInterceptor.listener = listener;
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
