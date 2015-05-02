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

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.ResourceList;

/**
 * Partial default implementation of {@link SubTask} to avoid
 * {@link AbstractMethodError} with future additions to {@link SubTask}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractSubTask implements SubTask {
    public Label getAssignedLabel() {
        return null;
    }

    public Node getLastBuiltOn() {
        return null;
    }

    public long getEstimatedDuration() {
        return -1;
    }

    public Object getSameNodeConstraint() {
        return null;
    }

    public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }
}
