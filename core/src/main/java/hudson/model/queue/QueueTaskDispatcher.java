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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.Task;
import hudson.agents.Cloud;

/**
 * Vetos the execution of a task on a node
 *
 * <p>
 * To register your dispatcher implementations, put @{@link Extension} on your subtypes.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.360
 */
public abstract class QueueTaskDispatcher implements ExtensionPoint {
    /**
     * Called whenever {@link Queue} is considering to execute the given task on a given node.
     *
     * <p>
     * Implementations can return null to indicate that the assignment is fine, or it can return
     * a non-null instance to block the execution of the task on the given node.
     *
     * <p>
     * Queue doesn't remember/cache the response from dispatchers, and instead it'll keep asking.
     * The upside of this is that it's very easy to block execution for a limited time period (
     * as you just need to return null when it's ready to execute.) The downside of this is that
     * the decision needs to be made quickly.
     *
     * <p>
     * Vetos are additive. When multiple {@link QueueTaskDispatcher}s are in the system,
     * the task won't run on the given node if any one of them returns a non-null value.
     * (This relationship is also the same with built-in check logic.)
     *
     * @deprecated since 1.413
     *      Use {@link #canTake(Node, Queue.BuildableItem)}
     */
    @Deprecated
    public @CheckForNull CauseOfBlockage canTake(Node node, Task task) {
        return null;
    }

    /**
     * Called when {@link Queue} is deciding where to execute the given task.
     *
     * <p>
     * Implementations can return null to indicate that the assignment is fine, or it can return
     * a non-null instance to block the execution of the task on the given node.
     *
     * <p>
     * Queue doesn't remember/cache the response from dispatchers, and instead it'll keep asking.
     * The upside of this is that it's very easy to block execution for a limited time period (
     * as you just need to return null when it's ready to execute.) The downside of this is that
     * the decision needs to be made quickly.
     *
     * <p>
     * This method is primarily designed to fine-tune where the execution should take place. If the execution
     * shouldn't commence anywhere at all, implementation should use {@link #canRun(Queue.Item)} instead so
     * that Jenkins understands the difference between "this node isn't the right place for this work"
     * vs "the time isn't right for this work to execute." This affects the provisioning behaviour
     * with elastic Jenkins deployments.
     *
     * <p>
     * Vetos are additive. When multiple {@link QueueTaskDispatcher}s are in the system,
     * the task won't run on the given node if any one of them returns a non-null value.
     * (This relationship is also the same with built-in check logic.)
     *
     * @since 1.413
     */
    public @CheckForNull CauseOfBlockage canTake(Node node, BuildableItem item) {
        return canTake(node, item.task); // backward compatible behaviour
    }

    /**
     * Called whenever {@link Queue} is considering if {@link hudson.model.Queue.Item} is ready to execute immediately
     * (which doesn't necessarily mean that it gets executed right away &mdash; it's still subject to
     * executor availability), or if it should be considered blocked.
     *
     * <p>
     * Compared to {@link #canTake(Node, Queue.BuildableItem)}, this version tells Jenkins that the task is
     * simply not ready to execute, even if there's available executor. This is more efficient
     * than {@link #canTake(Node, Queue.BuildableItem)}, and it sends the right signal to Jenkins so that
     * it won't use {@link Cloud} to try to provision new executors.
     *
     * <p>
     * Vetos are additive. When multiple {@link QueueTaskDispatcher}s are in the system,
     * the task is considered blocked if any one of them returns a non-null value.
     * (This relationship is also the same with built-in check logic.)
     *
     * <p>
     * If a {@link QueueTaskDispatcher} returns non-null from this method, the task is placed into
     * the 'blocked' state, and generally speaking it stays in this state for a few seconds before
     * its state gets re-evaluated. If a {@link QueueTaskDispatcher} wants the blockage condition
     * to be re-evaluated earlier, call {@link Queue#scheduleMaintenance()} to initiate that process.
     *
     * @return
     *      null to indicate that the item is ready to proceed to the buildable state as far as this
     *      {@link QueueTaskDispatcher} is concerned. Otherwise return an object that indicates why
     *      the build is blocked.
     * @since 1.427
     */
    public @CheckForNull CauseOfBlockage canRun(Queue.Item item) {
        return null;
    }

    /**
     * All registered {@link QueueTaskDispatcher}s.
     */
    public static ExtensionList<QueueTaskDispatcher> all() {
        return ExtensionList.lookup(QueueTaskDispatcher.class);
    }
}
