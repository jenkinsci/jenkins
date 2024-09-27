/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

package hudson.model.queue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceActivity;
import java.io.IOException;

/**
 * A component of {@link Queue.Task} that represents a computation carried out by a single {@link Executor}.
 *
 * A {@link Queue.Task} consists of a number of {@link SubTask}.
 *
 * <p>
 * Plugins are encouraged to extend from {@link AbstractSubTask}
 * instead of implementing this interface directly, to maintain
 * compatibility with future changes to this interface.
 *
 * @since 1.377
 */
public interface SubTask extends ResourceActivity {
    /**
     * If this task needs to be run on a node with a particular label,
     * return that {@link Label}. Otherwise null, indicating
     * it can run on anywhere.
     * @return by default, null
     */
    default Label getAssignedLabel() {
        return null;
    }

    /**
     * If the previous execution of this task run on a certain node
     * and this task prefers to run on the same node, return that.
     * Otherwise null.
     * @return by default, null
     * @deprecated Unused.
     */
    @Deprecated
    default Node getLastBuiltOn() {
        return null;
    }

    /**
     * Estimate of how long will it take to execute this task.
     * Measured in milliseconds.
     *
     * @return -1 if it's impossible to estimate (the default)
     */
    default long getEstimatedDuration() {
        return -1;
    }

    /**
     * Creates an object which performs the actual execution of the task.
     * @return executable to be launched or null if the executable cannot be
     * created (e.g. {@link AbstractProject} is disabled)
     * @exception IOException executable cannot be created
     */
    @CheckForNull Queue.Executable createExecutable() throws IOException;

    /**
     * Gets the task that this subtask belongs to.
     * @return by default, {@code this}
     * @see #getOwnerExecutable
     */
    default @NonNull Queue.Task getOwnerTask() {
        return (Queue.Task) this;
    }

    /**
     * If this task is associated with an executable of {@link #getOwnerTask}, finds that.
     * @return by default, {@code null}
     * @see hudson.model.Queue.Executable#getParentExecutable
     * @since 2.389
     */
    default @CheckForNull Queue.Executable getOwnerExecutable() {
        return null;
    }

    /**
     * If a subset of {@link SubTask}s of a {@link Queue.Task} needs to be collocated with other {@link SubTask}s,
     * those {@link SubTask}s should return the equal object here. If null, the execution unit isn't under a
     * colocation constraint.
     * @return by default, null
     */
    default Object getSameNodeConstraint() {
        return null;
    }
}
