/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Marker for tasks which should perhaps “jump ahead” in the queue.
 * Ensures that this task gets scheduled ahead of regular stuff.
 * Use judiciously; an appropriate use case is a task which is intended to be the direct continuation of one currently running
 * or which was running in a previous Jenkins session and is not logically finished.
 * @since TODO
 */
public interface PrioritizedTask extends Queue.Task {

    /**
     * True if the task should actually be consider prioritized now.
     */
    boolean isPrioritized();

    @Restricted(DoNotUse.class) // implementation
    @Extension class Scheduler extends QueueTaskDispatcher {

        private static boolean isPrioritized(Queue.Task task) {
            return task instanceof PrioritizedTask && ((PrioritizedTask) task).isPrioritized();
        }

        @Override public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            if (isPrioritized(item.task)) {
                return null;
            }
            for (Queue.BuildableItem other : Queue.getInstance().getBuildableItems()) {
                if (isPrioritized(other.task)) {
                    Label label = other.task.getAssignedLabel();
                    if (label == null || label.matches(node)) { // conservative; might actually go to a different node
                        return new HoldOnPlease(other.task);
                    }
                }
            }
            return null;
        }

        private static final class HoldOnPlease extends CauseOfBlockage {

            private final Queue.Task task;

            HoldOnPlease(Queue.Task task) {
                this.task = task;
            }

            @Override public String getShortDescription() {
                return task.getFullDisplayName() + " should be allowed to run first";
            }

        }

    }

}
