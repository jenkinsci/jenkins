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

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import javax.annotation.CheckForNull;
import hudson.model.Run;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Represents a unit of hand-over to {@link Executor} from {@link Queue}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.377
 */
@ExportedBean
public final class WorkUnit {
    /**
     * Task to be executed.
     */
    public final SubTask work;

    /**
     * Shared context among {@link WorkUnit}s.
     */
    public final WorkUnitContext context;

    private volatile Executor executor;
    private Executable executable;

    WorkUnit(WorkUnitContext context, SubTask work) {
        this.context = context;
        this.work = work;
    }

    /**
     * {@link Executor} running this work unit.
     * <p>
     * {@link Executor#getCurrentWorkUnit()} and {@link WorkUnit#getExecutor()}
     * form a bi-directional reachability between them.
     */
    public @CheckForNull Executor getExecutor() {
        return executor;
    }

    public void setExecutor(@CheckForNull Executor e) {
        executor = e;
        if (e != null) {
            context.future.addExecutor(e);
        }
    }

    /**
     * If the execution has already started, return the executable that was created.
     */
    public Executable getExecutable() {
        return executable;
    }

    /**
     * This method is only meant to be called internally by {@link Executor}.
     */
    @Restricted(NoExternalUse.class)
    public void setExecutable(Executable executable) {
        this.executable = executable;
        if (executable instanceof Run) {
            ((Run) executable).setQueueId(context.item.getId());
        }
    }

    /**
     * Is this work unit the "main work", which is the primary {@link SubTask}
     * represented by {@link Task} itself.
     */
    public boolean isMainWork() {
        return context.task==work;
    }

    @Override
    public String toString() {
        if (work==context.task)
            return super.toString()+"[work="+context.task.getFullDisplayName()+"]";
        else
            return super.toString()+"[work="+work+",context.task="+context.task.getFullDisplayName()+"]";
    }
}
