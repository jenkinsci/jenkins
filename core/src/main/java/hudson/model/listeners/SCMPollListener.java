/*
 * The MIT License
 *
 * Copyright (c) 2011, Christian Wolfgang
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
package hudson.model.listeners;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.scm.PollingResult;
import jenkins.model.Jenkins;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * A hook for listening to polling activities in Jenkins.
 *
 * @author Christian Wolfgang
 * @author Kohsuke Kawaguchi
 * @since 1.474
 */
public abstract class SCMPollListener implements ExtensionPoint {
    /**
     * Called before the polling execution.
     *
     * @param project
     *      Project that's about to run polling.
     * @param listener
     *      Connected to the polling log.
     */
    // TODO switch to Job
	public void onBeforePolling( AbstractProject<?, ?> project, TaskListener listener ) {}

    /**
     * Called when the polling successfully concluded.
     *
     * @param result
     *      The result of the polling.
     */
	public void onPollingSuccess( AbstractProject<?, ?> project, TaskListener listener, PollingResult result) {}

    /**
     * Called when the polling concluded with an error.
     *
     * @param exception
     *      The problem reported. This can include {@link InterruptedException} (that corresponds to the user cancelling it),
     *      some anticipated problems like {@link IOException}, or bug in the code ({@link RuntimeException})
     */
    public void onPollingFailed( AbstractProject<?, ?> project, TaskListener listener, Throwable exception) {}

	public static void fireBeforePolling( AbstractProject<?, ?> project, TaskListener listener ) {
        for (SCMPollListener l : all()) {
            try {
                l.onBeforePolling(project, listener);
            } catch (Exception e) {
                /* Make sure, that the listeners do not have any impact on the actual poll */
            }
        }
    }

	public static void firePollingSuccess( AbstractProject<?, ?> project, TaskListener listener, PollingResult result ) {
		for( SCMPollListener l : all() ) {
            try {
                l.onPollingSuccess(project, listener, result);
            } catch (Exception e) {
                /* Make sure, that the listeners do not have any impact on the actual poll */
            }
		}
	}

    public static void firePollingFailed( AbstractProject<?, ?> project, TaskListener listener, Throwable exception ) {
   		for( SCMPollListener l : all() ) {
               try {
                   l.onPollingFailed(project, listener, exception);
               } catch (Exception e) {
                   /* Make sure, that the listeners do not have any impact on the actual poll */
               }
   		}
   	}

	/**
	 * Returns all the registered {@link SCMPollListener}s.
	 */
	public static ExtensionList<SCMPollListener> all() {
		return ExtensionList.lookup( SCMPollListener.class );
	}
}
