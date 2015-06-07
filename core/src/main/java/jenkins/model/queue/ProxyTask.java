/*
 * The MIT License
 *
 * Copyright (c) 2015 Oleg Nenashev.
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

import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import java.io.IOException;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A {@link Task}, which refers other {@link Task}.
 * This method allows to proxies all methods of the original {@link Task}.
 * @author Oleg Nenashev
 * @since TODO
 */
@ExportedBean
public class ProxyTask implements Queue.Task {
        
    private final Queue.Task upperTask;

    /**
     * Creates a new {@link ProxyTask} for the specified {@link Queue.Task}.
     * @param task A task to be proxied.
     */
    public ProxyTask(@Nonnull Queue.Task task) {
        this.upperTask = task;
    }

    /**
     * Get the task proxied by this class.
     * @return {@link Task}.
     */
    public @Nonnull Queue.Task getUpperTask() {
        return upperTask;
    }

    @Exported
    @Override
    public boolean isBuildBlocked() {
        return upperTask.isBuildBlocked();
    }

    @Exported
    @Override
    public String getWhyBlocked() {
        return upperTask.getWhyBlocked();
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        return upperTask.getCauseOfBlockage();
    }

    @Exported
    @Override
    public String getName() {
        return upperTask.getName();
    }

    @Exported
    @Override
    public String getFullDisplayName() {
        return upperTask.getFullDisplayName();
    }

    @Override
    public void checkAbortPermission() {
        upperTask.checkAbortPermission();
    }

    @Override
    public boolean hasAbortPermission() {
        return upperTask.hasAbortPermission();
    }

    @Exported
    @Override
    public String getUrl() {
        return upperTask.getUrl();
    }

    @Exported
    @Override
    public boolean isConcurrentBuild() {
        return upperTask.isConcurrentBuild();
    }

    @Override
    public Collection<? extends SubTask> getSubTasks() {
        return upperTask.getSubTasks();
    }

    @Override
    public Authentication getDefaultAuthentication() {
        return upperTask.getDefaultAuthentication();
    }

    @Override
    public Authentication getDefaultAuthentication(Queue.Item item) {
        return upperTask.getDefaultAuthentication(item);
    }

    @Override
    public String getDisplayName() {
        return upperTask.getDisplayName();
    }

    @Exported
    @Override
    public Label getAssignedLabel() {
        return upperTask.getAssignedLabel();
    }

    @Override
    public Node getLastBuiltOn() {
        return upperTask.getLastBuiltOn();
    }

    @Exported
    @Override
    public long getEstimatedDuration() {
        return upperTask.getEstimatedDuration();
    }

    @Override
    public Queue.Executable createExecutable() throws IOException {
        return upperTask.createExecutable();
    }

    @Override
    public Queue.Task getOwnerTask() {
        return upperTask.getOwnerTask();
    }

    @Override
    public Object getSameNodeConstraint() {
        return upperTask.getSameNodeConstraint();
    }

    @Override
    public ResourceList getResourceList() {
        return upperTask.getResourceList();
    }     
}
