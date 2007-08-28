package org.apache.maven.lifecycle;

import hudson.maven.agent.AbortException;
import org.apache.maven.BuildFailureException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.event.EventMonitor;
import org.apache.maven.monitor.event.MavenEvents;

import java.io.IOException;

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
        try {
            session.getEventDispatcher().addEventMonitor(new EventMonitorImpl());
            if(listener!=null)
                listener.preBuild(session,rm,dispatcher);
            try {
                super.execute(session, rm, dispatcher);
            } finally {
                if(listener!=null)
                    listener.postBuild(session,rm,dispatcher);
            }
        } catch (InterruptedException e) {
            throw new BuildFailureException("aborted",e);
        } catch (IOException e) {
            throw new BuildFailureException(e.getMessage(),e);
        } catch (AbortException e) {
            throw new BuildFailureException("aborted",e);
        }
    }

    /**
     * {@link EventMonitor} offers mostly useless events, but this offers
     * the most accurate "end of module" event.
     */
    private final class EventMonitorImpl implements EventMonitor {
        public void startEvent(String eventName, String target, long timestamp) {
            // TODO
        }

        public void endEvent(String eventName, String target, long timestamp) {
            if(eventName.equals(MavenEvents.PROJECT_EXECUTION)) {
                if(listener!=null) {
                    try {
                        listener.endModule();
                    } catch (InterruptedException e) {
                        // can't interrupt now
                        Thread.currentThread().interrupt();
                    } catch (IOException e) {
                        throw new Error(e);
                    }
                }
            }
        }

        public void errorEvent(String eventName, String target, long timestamp, Throwable cause) {
            // TODO
        }
    }
}
