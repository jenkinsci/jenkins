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
import jenkins.model.Jenkins;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;

public abstract class SCMPollListener implements ExtensionPoint {

	public void onBeforePolling( AbstractProject<?, ?> project, TaskListener listener ) {}

	public void onAfterPolling( AbstractProject<?, ?> project, TaskListener listener ) {}

	public static void fireBeforePolling( AbstractProject<?, ?> project, TaskListener listener ) {
		for( SCMPollListener l : all() ) {
			l.onBeforePolling( project, listener );
		}
	}

	public static void fireAfterPolling( AbstractProject<?, ?> project, TaskListener listener ) {
		for( SCMPollListener l : all() ) {
			l.onAfterPolling( project, listener );
		}
	}

	/**
	 * Returns all the registered {@link SCMPollListener}s.
	 */
	public static ExtensionList<SCMPollListener> all() {
		return Jenkins.getInstance().getExtensionList( SCMPollListener.class );
	}
}
