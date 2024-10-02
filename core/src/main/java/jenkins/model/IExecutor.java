/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Util;
import hudson.model.Queue;
import hudson.model.queue.WorkUnit;
import jenkins.model.queue.ITask;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Interface for an executor that can be displayed in the executors widget.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public interface IExecutor {
    /**
     * Returns true if this {@link IExecutor} is ready for action.
     */
    boolean isIdle();

    /**
     * @return the {@link IComputer} that this executor belongs to.
     */
    IComputer getOwner();

    /**
     * @return the current executable, if any.
     */
    @CheckForNull Queue.Executable getCurrentExecutable();

    /**
     * Returns the current {@link WorkUnit} (of {@link #getCurrentExecutable() the current executable})
     * that this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    @CheckForNull WorkUnit getCurrentWorkUnit();

    /**
     * @return the current display name of the executor. Usually the name of the executable.
     */
    String getDisplayName();

    /**
     * @return a reference to the parent task of the current executable, if any.
     */
    @CheckForNull
    default ITask getParentTask() {
        var currentExecutable = getCurrentExecutable();
        if (currentExecutable == null) {
            var workUnit = getCurrentWorkUnit();
            if (workUnit != null) {
                return workUnit.work;
            } else {
                // Idle
                return null;
            }
        } else {
            return currentExecutable.getParent();
        }
    }

    /**
     * Checks if the current user has a permission to stop this build.
     */
    boolean hasStopPermission();

    /**
     * Gets the executor number that uniquely identifies it among
     * other {@link IExecutor}s for the same computer.
     *
     * @return
     *      a sequential number starting from 0.
     */
    int getNumber();

    /**
     * Gets the elapsed time since the build has started.
     *
     * @return
     *      the number of milliseconds since the build has started.
     */
    long getElapsedTime();

    /**
     * Gets the string that says how long since this build has started.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    default String getTimestampString() {
        return Util.getTimeSpanString(getElapsedTime());
    }

    /**
     * Computes a human-readable text that shows the expected remaining time
     * until the build completes.
     */
    String getEstimatedRemainingTime();

    /**
     * Returns true if the current build is likely stuck.
     *
     * <p>
     * This is a heuristics based approach, but if the build is suspiciously taking for a long time,
     * this method returns true.
     */
    boolean isLikelyStuck();

    /**
     * Returns the progress of the current build in the number between 0-100.
     *
     * @return -1
     *      if it's impossible to estimate the progress.
     */
    int getProgress();
}
