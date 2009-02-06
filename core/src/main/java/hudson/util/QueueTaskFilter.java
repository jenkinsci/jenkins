/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import hudson.model.Queue;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.ResourceList;

import java.io.IOException;

/**
 * Convenient base class for {@link Queue.Task} decorators.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class QueueTaskFilter implements Queue.Task {
    /**
     * Delegation target.
     */
    protected volatile Queue.Task delegate;

    protected QueueTaskFilter(Queue.Task delegate) {
        this.delegate = delegate;
    }

    protected QueueTaskFilter() {
    }

    public Label getAssignedLabel() {
        return delegate.getAssignedLabel();
    }

    public Node getLastBuiltOn() {
        return delegate.getLastBuiltOn();
    }

    public boolean isBuildBlocked() {
        return delegate.isBuildBlocked();
    }

    public String getWhyBlocked() {
        return delegate.getWhyBlocked();
    }

    public String getName() {
        return delegate.getName();
    }

    public String getFullDisplayName() {
        return delegate.getFullDisplayName();
    }

    public long getEstimatedDuration() {
        return delegate.getEstimatedDuration();
    }

    public Queue.Executable createExecutable() throws IOException {
        return delegate.createExecutable();
    }

    public void checkAbortPermission() {
        delegate.checkAbortPermission();
    }

    public boolean hasAbortPermission() {
        return delegate.hasAbortPermission();
    }

    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    public ResourceList getResourceList() {
        return delegate.getResourceList();
    }

    // Queue.Task hashCode and equals need to be implemented to provide value equality semantics
    public abstract int hashCode();

    public abstract boolean equals(Object obj);
}
