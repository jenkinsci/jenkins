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
package hudson.model.listeners;

import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Item;
import hudson.ExtensionPoint;

/**
 * Receives notifications about jobs.
 *
 * <p>
 * This is an abstract class so that methods added in the future won't break existing listeners.
 *
 * @author Kohsuke Kawaguchi
 * @see Hudson#addListener(JobListener)
 * @deprecated {@link ItemListener} is the generalized form of this.
 */
public abstract class JobListener implements ExtensionPoint {
    /**
     * Called after a new job is created and added to {@link Hudson}.
     */
    public void onCreated(Job j) {
    }

    /**
     * Called after all the jobs are loaded from disk into {@link Hudson}
     * object.
     *
     * @since 1.68
     */
    public void onLoaded() {
    }

    /**
     * Called right before a job is going to be deleted.
     *
     * At this point the data files of the job is already gone.
     */
    public void onDeleted(Job j) {
    }

    public static final class JobListenerAdapter extends ItemListener {
        private final JobListener listener;

        public JobListenerAdapter(JobListener listener) {
            this.listener = listener;
        }

        public void onCreated(Item item) {
            if(item instanceof Job)
                listener.onCreated((Job)item);
        }

        public void onLoaded() {
            listener.onLoaded();
        }

        public void onDeleted(Item item) {
            if(item instanceof Job)
                listener.onDeleted((Job)item);
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            JobListenerAdapter that = (JobListenerAdapter) o;

            return this.listener.equals(that.listener);
        }

        public int hashCode() {
            return listener.hashCode();
        }
    }
}
