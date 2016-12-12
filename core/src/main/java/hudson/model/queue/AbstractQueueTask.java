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

import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import org.acegisecurity.Authentication;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * Abstract base class for {@link hudson.model.Queue.Task} to protect plugins
 * from new additions to the interface.
 *
 * <p>
 * Plugins are encouraged to implement {@link AccessControlled} otherwise
 * the tasks will be hidden from display in the queue.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.360
 */
public abstract class AbstractQueueTask implements Queue.Task {
    public Collection<? extends SubTask> getSubTasks() {
        return Collections.singleton(this);
    }

    public final Task getOwnerTask() {
        return this;
    }

    public boolean isConcurrentBuild() {
        return false;
    }

    public CauseOfBlockage getCauseOfBlockage() {
        return null;
    }

    public Object getSameNodeConstraint() {
        return null;
    }

    /**
     * This default implementation is the historical behaviour, but this is no longer desirable. Please override.
     * See {@link Task#getDefaultAuthentication()} for the contract.
     */
    @Nonnull
    public Authentication getDefaultAuthentication() {
        return ACL.SYSTEM;
    }

    @Nonnull
    @Override
    public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }
}
