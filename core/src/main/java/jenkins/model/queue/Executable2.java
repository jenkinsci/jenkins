/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package jenkins.model.queue;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.OneOffExecutor;
import hudson.model.Queue;
import hudson.model.Resource;
import hudson.model.ResourceActivity;
import hudson.model.ResourceController;
import hudson.model.ResourceList;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Extended interface for running tasks with some additional logic.
 * @since TODO
 */
public interface Executable2 extends Queue.Executable {

    /**
     * {@inheritDoc}
     * @throws AsynchronousExecution if you would like to continue without consuming a thread
     */
    @Override void run() throws AsynchronousExecution;

    /**
     * Special means of indicating that an executable will proceed in the background without consuming a native thread ({@link Executor}).
     * May be thrown from {@link Executable2#run} after doing any preparatory work synchronously.
     * <p>{@link Executor#isActive} will remain true (even though {@link Executor#isAlive} is not) until {@link #completed} is called.
     * The thrower will need to hold on to a reference to this instance as a handle to call {@link #completed}.
     * <p>The execution may not extend into another Jenkins session; if you wish to model a long-running execution, you must schedule a new task after restart.
     * This class is not serializable anyway.
     * <p>Mainly intended for use with {@link OneOffExecutor} (from a {@link hudson.model.Queue.FlyweightTask}), of which there could be many,
     * but could also be used with a heavyweight executor even though the number of executors is bounded by node configuration.
     * <p>{@link ResourceController}/{@link ResourceActivity}/{@link ResourceList}/{@link Resource} are not currently supported.
     * Nor are {@link hudson.model.Queue.Task#getSubTasks} other than the primary task.
     */
    abstract class AsynchronousExecution extends RuntimeException {

        private Executor executor;

        /** Constructor for subclasses. */
        protected AsynchronousExecution() {}

        /**
         * Called in lieu of {@link Thread#interrupt} by {@link Executor#interrupt()} and its overloads.
         * As with the standard Java method, you are requested to cease work as soon as possible, but there is no enforcement of this.
         * You might also want to call {@link Executor#recordCauseOfInterruption} on {@link #getExecutor}.
         * @param forShutdown if true, this interruption is because Jenkins is shutting down (and thus {@link Computer#interrupt} was called from {@link Jenkins#cleanUp}); otherwise, a normal interrupt such as by {@link Executor#doStop()}
         */
        public abstract void interrupt(boolean forShutdown);

        /**
         * Obtains the associated executor.
         */
        public final Executor getExecutor() {
            return executor;
        }

        @Restricted(NoExternalUse.class)
        public final void setExecutor(Executor executor) {
            this.executor = executor;
        }

        /**
         * To be called when the task is actually complete.
         * @param error normally null (preferable to handle errors yourself), but may be specified to simulate an exception from {@link Executable2#run}, as per {@link ExecutorListener#taskCompletedWithProblems}
         */
        public final void completed(@CheckForNull Throwable error) {
            executor.completedAsynchronous(error);
        }

    }

}
