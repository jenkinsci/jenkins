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

import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.ResourceActivity;

import javax.annotation.Nonnull;
import java.io.IOException;
import javax.annotation.CheckForNull;

/**
 * A component of {@link Task} that represents a computation carried out by a single {@link Executor}.
 *
 * A {@link Task} consists of a number of {@link SubTask}.
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
     */
    Label getAssignedLabel();

    /**
     * If the previous execution of this task run on a certain node
     * and this task prefers to run on the same node, return that.
     * Otherwise null.
     */
    Node getLastBuiltOn();

    /**
     * Estimate of how long will it take to execute this task.
     * Measured in milliseconds.
     *
     * @return -1 if it's impossible to estimate.
     */
    long getEstimatedDuration();

    /**
     * Creates {@link Executable}, which performs the actual execution of the task.
     * @return {@link Executable} to be launched or null if the executable cannot be
     * created (e.g. {@link AbstractProject} is disabled)
     * @exception IOException {@link Executable} cannot be created
     */
    @CheckForNull Executable createExecutable() throws IOException;

    /**
     * Gets the {@link Task} that this subtask belongs to.
     */
    @Nonnull Task getOwnerTask();

    /**
     * If a subset of {@link SubTask}s of a {@link Task} needs to be collocated with other {@link SubTask}s,
     * those {@link SubTask}s should return the equal object here. If null, the execution unit isn't under a
     * colocation constraint.
     */
    Object getSameNodeConstraint();
}
