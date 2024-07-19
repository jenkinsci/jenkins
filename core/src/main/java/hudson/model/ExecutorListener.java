/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

package hudson.model;

import hudson.ExtensionPoint;
import hudson.slaves.SlaveComputer;

/**
 * A listener for task related events from executors.
 * A {@link Computer#getRetentionStrategy} or {@link SlaveComputer#getLauncher} may implement this interface.
 * Or you may create an implementation as an extension (since 2.318).
* @author Stephen Connolly
* @since 1.312
*/
public interface ExecutorListener extends ExtensionPoint {

    /**
     * Called whenever a task is accepted by an executor.
     * @param executor The executor.
     * @param task The task.
     */
    default void taskAccepted(Executor executor, Queue.Task task) {}

    /**
     * Called whenever a task is started by an executor.
     * @param executor The executor.
     * @param task The task.
     * @since 2.318
     */
    default void taskStarted(Executor executor, Queue.Task task) {}

    /**
     * Called whenever a task is completed without any problems by an executor.
     * @param executor The executor.
     * @param task The task.
     * @param durationMS The number of milliseconds that the task took to complete.
     */
    default void taskCompleted(Executor executor, Queue.Task task, long durationMS) {}

    /**
     * Called whenever a task is completed with some problems by an executor.
     * @param executor The executor.
     * @param task The task.
     * @param durationMS The number of milliseconds that the task took to complete.
     * @param problems The exception that was thrown.
     */
    default void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {}
}
