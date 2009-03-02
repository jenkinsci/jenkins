/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import hudson.ExtensionPoint;
import hudson.ExtensionListView;
import hudson.Extension;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.scm.RepositoryBrowser;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
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
     * Called after a build is moved to the {@link Run.State#COMPLETED} state.
     *
     * <p>
     * At this point, all the records related to a build is written down to the disk. As such,
     * {@link TaskListener} is no longer available. This happens later than {@link #onCompleted(Run, TaskListener)}.
     */
    public void onFinalized(R r) {}

    /**
     * Called when a build is started (i.e. it was in the queue, and will now start running
     * on an executor)
     *
     * @param r
     *      The started build.
     * @param listener
     *      The listener for this build. This can be used to produce log messages, for example,
     *      which becomes a part of the "console output" of this build.
     */
    public void onStarted(R r, TaskListener listener) {}

    /**
     * Called right before a build is going to be deleted.
     *
     * @param r The build.
     */
    public void onDeleted(R r) {}

    /**
     * Registers this object as an active listener so that it can start getting
     * callbacks invoked.
     *
     * @deprecated
     *      Put {@link Extension} on your class to get it auto-registered.
     */
    public void register() {
        all().add(this);
    }

    /**
     * Reverse operation of {@link #register()}.
     */
    public void unregister() {
        all().remove(this);
    }

    /**
     * List of registered listeners.
     * @deprecated as of 1.281
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    public static final CopyOnWriteList<RunListener> LISTENERS = ExtensionListView.createCopyOnWriteList(RunListener.class);

    /**
     * Fires the {@link #onCompleted} event.
     */
    public static void fireCompleted(Run r, TaskListener listener) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                l.onCompleted(r,listener);
        }
    }

    /**
     * Fires the {@link #onStarted} event.
     */
    public static void fireStarted(Run r, TaskListener listener) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                l.onStarted(r,listener);
        }
    }

    /**
     * Fires the {@link #onFinalized(Run)} event.
     */
    public static void fireFinalized(Run r) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                l.onFinalized(r);
        }
    }

    /**
     * Fires the {@link #onFinalized(Run)} event.
     */
    public static void fireDeleted(Run r) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                l.onDeleted(r);
        }
    }

    /**
     * Returns all the registered {@link RunListener} descriptors.
     */
    public static ExtensionList<RunListener> all() {
        return Hudson.getInstance().getExtensionList(RunListener.class);
    }
}
