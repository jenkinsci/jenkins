/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import hudson.model.Queue.Task;
import hudson.model.Action;
import hudson.model.Queue;

import java.util.List;

/**
 * An action interface that allows action data to be folded together.
 *
 * <p>
 * {@link Action} can implement this optional marker interface to be notified when
 * the {@link Task} that it's added to the queue with is determined to be "already in the queue".
 *
 * <p>
 * This is useful for passing on parameters to the task that's already in the queue.
 *
 * @author mdonohue
 * @since 1.300-ish.
 */
public interface FoldableAction extends Action {
    /**
     * Notifies that the {@link Task} that "owns" this action (that is, the task for which this action is submitted)
     * is considered as a duplicate.
     *
     * @param item
     *      The existing {@link hudson.model.Queue.Item} in the queue against which we are judged as a duplicate. Never null.
     * @param owner
     *      The {@link Task} with which this action was submitted to the queue. Never null.
     * @param otherActions
     *      Other {@link Action}s that are submitted with the task. (One of them is this {@link FoldableAction}.)
     *      Never null.
     */
    void foldIntoExisting(Queue.Item item, Task owner, List<Action> otherActions);
}
