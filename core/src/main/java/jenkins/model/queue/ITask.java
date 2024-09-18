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

package jenkins.model.queue;

import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.AccessControlled;

/**
 * A task that can be displayed in the executors widget.
 *
 * @since TODO
 */
public interface ITask extends ModelObject {
    /**
     * @return {@code true} if the current user can cancel the current task.
     *
     * NOTE: If you have implemented {@link AccessControlled} this returns by default
     * {@code hasPermission(Item.CANCEL)}
     */
    default boolean hasAbortPermission() {
        if (this instanceof AccessControlled ac) {
            return ac.hasPermission(Item.CANCEL);
        }
        return true;
    }

    /**
     * @return {@code true} if the current user has read access on the task.
     */
    @SuppressWarnings("unused") // jelly
    default boolean hasReadPermission() {
        if (this instanceof AccessControlled ac) {
            return ac.hasPermission(Item.READ);
        }
        return true;
    }

    /**
     * @return the full display name of the task.
     * <p>
     * Defaults to the same as {@link #getDisplayName()}.
     */
    default String getFullDisplayName() {
        return getDisplayName();
    }

    /**
     * @return the URL where to reach specifically this task, relative to Jenkins URL.
     * <p>
     * Can be {@code null} if the task can't be reached directly. Otherwise, it must end with '/'.
     */
    default String getUrl() {
        return null;
    }
}
