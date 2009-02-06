/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
