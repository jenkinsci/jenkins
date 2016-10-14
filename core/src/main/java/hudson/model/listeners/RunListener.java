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
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.util.CopyOnWriteList;
import org.jvnet.tiger_types.Types;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

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

    protected RunListener() {
        Type type = Types.getBaseClass(getClass(), RunListener.class);
        if (type instanceof ParameterizedType)
            targetType = Types.erasure(Types.getTypeArgument(type,0));
        else
            throw new IllegalStateException(getClass()+" uses the raw type for extending RunListener");
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
     * @throws RuntimeException
     *      Any exception/error thrown from this method will be swallowed to prevent broken listeners
     *      from breaking all the builds.
     */
    public void onCompleted(R r, @Nonnull TaskListener listener) {}

    /**
     * Called after a build is moved to the {@link hudson.model.Run.State#COMPLETED} state.
     *
     * <p>
     * At this point, all the records related to a build is written down to the disk. As such,
     * {@link TaskListener} is no longer available. This happens later than {@link #onCompleted(Run, TaskListener)}.
     *
     * @throws RuntimeException
     *      Any exception/error thrown from this method will be swallowed to prevent broken listeners
     *      from breaking all the builds.
     */
    public void onFinalized(R r) {}

    /**
     * Called when a Run is entering execution.
     * @param r
     *      The started build.
     * @since 2.9
     */
    public void onInitialize(R r) {}

    /**
     * Called when a build is started (i.e. it was in the queue, and will now start running
     * on an executor)
     *
     * @param r
     *      The started build.
     * @param listener
     *      The listener for this build. This can be used to produce log messages, for example,
     *      which becomes a part of the "console output" of this build.
     * @throws RuntimeException
     *      Any exception/error thrown from this method will be swallowed to prevent broken listeners
     *      from breaking all the builds.
     */
    public void onStarted(R r, TaskListener listener) {}

    /**
     * Runs before the {@link SCM#checkout(AbstractBuild, Launcher, FilePath, BuildListener, File)} runs, and performs a set up.
     * Can contribute additional properties/env vars to the environment.
     *
     * <p>
     * A typical strategy is for implementations to check {@link JobProperty}s and other configuration
     * of the project to determine the environment to inject, which allows you to achieve the equivalent of
     * {@link BuildWrapper}, but without UI.
     *
     * @param build
     *      The build in progress for which an {@link Environment} object is created.
     *      Never null.
     * @param launcher
     *      This launcher can be used to launch processes for this build.
     *      If the build runs remotely, launcher will also run a job on that remote machine.
     *      Never null.
     * @param listener
     *      Can be used to send any message.
     * @return
     *      non-null if the build can continue, null if there was an error
     *      and the build needs to be aborted.
     * @throws IOException
     *      terminates the build abnormally. Hudson will handle the exception
     *      and reports a nice error message.
     * @throws RunnerAbortedException
     *      If a fatal error is detected and the callee handled it gracefully, throw this exception
     *      to suppress a stack trace by the receiver.
     * @since 1.410
     */
    public Environment setUpEnvironment( AbstractBuild build, Launcher launcher, BuildListener listener ) throws IOException, InterruptedException, RunnerAbortedException {
    	return new Environment() {};
    }

    /**
     * Called right before a build is going to be deleted.
     *
     * @param r The build.
     * @throws RuntimeException
     *      Any exception/error thrown from this method will be swallowed to prevent broken listeners
     *      from breaking all the builds.
     */
    public void onDeleted(R r) {}

    /**
     * Registers this object as an active listener so that it can start getting
     * callbacks invoked.
     *
     * @deprecated as of 1.281
     *      Put {@link Extension} on your class to get it auto-registered.
     */
    @Deprecated
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
    @Deprecated
    public static final CopyOnWriteList<RunListener> LISTENERS = ExtensionListView.createCopyOnWriteList(RunListener.class);

    /**
     * Fires the {@link #onCompleted(Run, TaskListener)} event.
     */
    public static void fireCompleted(Run r, @Nonnull TaskListener listener) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                try {
                    l.onCompleted(r,listener);
                } catch (Throwable e) {
                    report(e);
                }
        }
    }

    /**
     * Fires the {@link #onInitialize(Run)} event.
     */
    public static void fireInitialize(Run r) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                try {
                    l.onInitialize(r);
                } catch (Throwable e) {
                    report(e);
                }
        }
    }


    /**
     * Fires the {@link #onStarted(Run, TaskListener)} event.
     */
    public static void fireStarted(Run r, TaskListener listener) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                try {
                    l.onStarted(r,listener);
                } catch (Throwable e) {
                    report(e);
                }
        }
    }

    /**
     * Fires the {@link #onFinalized(Run)} event.
     */
    public static void fireFinalized(Run r) {
        if (Jenkins.getInstanceOrNull() == null) { // TODO use !Functions.isExtensionsAvailable() once JENKINS-33377
            return;
        }
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                try {
                    l.onFinalized(r);
                } catch (Throwable e) {
                    report(e);
                }
        }
    }

    /**
     * Fires the {@link #onDeleted} event.
     */
    public static void fireDeleted(Run r) {
        for (RunListener l : all()) {
            if(l.targetType.isInstance(r))
                try {
                    l.onDeleted(r);
                } catch (Throwable e) {
                    report(e);
                }
        }
    }

    /**
     * Returns all the registered {@link RunListener}s.
     */
    public static ExtensionList<RunListener> all() {
        return ExtensionList.lookup(RunListener.class);
    }

    private static void report(Throwable e) {
        LOGGER.log(Level.WARNING, "RunListener failed",e);
    }

    private static final Logger LOGGER = Logger.getLogger(RunListener.class.getName());

}
