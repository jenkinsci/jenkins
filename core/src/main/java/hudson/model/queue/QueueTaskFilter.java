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
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.ResourceList;
import java.io.IOException;
import java.util.Collection;

/**
 * Base class for defining filter {@link hudson.model.Queue.Task}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.360
 */
public abstract class QueueTaskFilter implements Queue.Task {
    private final Queue.Task base;

    protected QueueTaskFilter(Task base) {
        this.base = base;
    }

    @Override
    public Label getAssignedLabel() {
        return base.getAssignedLabel();
    }

    @Deprecated
    @Override
    public Node getLastBuiltOn() {
        return base.getLastBuiltOn();
    }

    @Deprecated
    @Override
    public boolean isBuildBlocked() {
        return base.isBuildBlocked();
    }

    @Deprecated
    @Override
    public String getWhyBlocked() {
        return base.getWhyBlocked();
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        return base.getCauseOfBlockage();
    }

    @Override
    public String getName() {
        return base.getName();
    }

    @Override
    public String getFullDisplayName() {
        return base.getFullDisplayName();
    }

    @Override
    public long getEstimatedDuration() {
        return base.getEstimatedDuration();
    }

    @Override
    public @CheckForNull Executable createExecutable() throws IOException {
        return base.createExecutable();
    }

    @Override
    public void checkAbortPermission() {
        base.checkAbortPermission();
    }

    @Override
    public boolean hasAbortPermission() {
        return base.hasAbortPermission();
    }

    @Override
    public String getUrl() {
        return base.getUrl();
    }

    @Override
    public boolean isConcurrentBuild() {
        return base.isConcurrentBuild();
    }

    @Override
    public String getDisplayName() {
        return base.getDisplayName();
    }

    @Override
    public ResourceList getResourceList() {
        return base.getResourceList();
    }

    @Override
    public Collection<? extends SubTask> getSubTasks() {
        return base.getSubTasks();
    }

    @Override
    public Object getSameNodeConstraint() {
        return base.getSameNodeConstraint();
    }
}
