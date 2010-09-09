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
import hudson.model.Queue.Task;

/**
 * Represents a unit of hand-over to {@link Executor} from {@link Queue}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class WorkUnit {
    /**
     * Task to be executed.
     */
    public final ExecutionUnit work;

    /**
     * Shared context among {@link WorkUnit}s.
     */
    public final WorkUnitContext context;

    WorkUnit(WorkUnitContext context, ExecutionUnit work) {
        this.context = context;
        this.work = work;
    }

    /**
     * Is this work unit the "main work", which is the primary {@link ExecutionUnit}
     * represented by {@link Task} itself.
     */
    public boolean isMainWork() {
        return context.task==work;
    }
}
