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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.remoting.AsyncFutureImpl;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import jenkins.model.Jenkins;

/**
 * Created when {@link hudson.model.Queue.Item} is created so that the caller can track the progress of the task.
 *
 * @author Kohsuke Kawaguchi
 */
public final class FutureImpl extends AsyncFutureImpl<Executable> implements QueueTaskFuture<Executable> {
    private final Task task;

    /**
     * If the computation has started, set to {@link Executor}s that are running the build.
     */
    private final Set<Executor> executors = new HashSet<>();

    /**
     * {@link Future} that completes when the task started running.
     *
     * In contrast, {@link FutureImpl} will complete when the task finishes.
     */
    /*package*/ final AsyncFutureImpl<Executable> start = new AsyncFutureImpl<>();

    public FutureImpl(Task task) {
        this.task = task;
    }

    @Override
    public Future<Executable> getStartCondition() {
        return start;
    }

    @Override
    public Executable waitForStart() throws InterruptedException, ExecutionException {
        return getStartCondition().get();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Queue q = Jenkins.get().getQueue();
        synchronized (this) {
            if (!executors.isEmpty()) {
                if (mayInterruptIfRunning)
                    for (Executor e : executors)
                        e.interrupt();
                return mayInterruptIfRunning;
            }
            return q.cancel(task);
        }
    }

    @Override
    public synchronized void setAsCancelled() {
        super.setAsCancelled();
        if (!start.isDone()) {
            start.setAsCancelled();
        }
    }

    synchronized void addExecutor(@NonNull Executor executor) {
        this.executors.add(executor);
    }

    synchronized void finished() {
        executors.clear();
    }
}
