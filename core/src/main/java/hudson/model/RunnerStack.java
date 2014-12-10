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
package hudson.model;

import hudson.model.Run.RunExecution;

import java.util.Stack;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.CheckForNull;

/**
 * Keeps track of {@link RunExecution}s that are currently executing on the given thread
 * (that can be either an {@link Executor} or threads that are impersonating one.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 */
final class RunnerStack {
    private final Map<Executor,Stack<RunExecution>> stack = new WeakHashMap<Executor,Stack<RunExecution>>();

    synchronized void push(RunExecution r) {
        Executor e = Executor.currentExecutor();
        Stack<RunExecution> s = stack.get(e);
        if(s==null) stack.put(e,s=new Stack<RunExecution>());
        s.push(r);
    }

    synchronized void pop() {
        Executor e = Executor.currentExecutor();
        Stack<RunExecution> s = stack.get(e);
        s.pop();
        if(s.isEmpty()) stack.remove(e);
    }

    /**
     * Looks up the currently running build, if known.
     * <p>While most {@link Run} implementations do add a {@link RunExecution} to the stack for the duration of the build,
     * those which have a different implementation of {@link Run#run} (or which do additional work after {@link Run#execute} completes)
     * may not consistently or ever keep an execution on the stack.
     * In such cases this method will return null, meaning that {@link CheckPoint#block(BuildListener, String)} and {@link CheckPoint#report} will do nothing.
     * @return a running build, or null if one has not been recorded
     */
    synchronized @CheckForNull RunExecution peek() {
        Executor e = Executor.currentExecutor();
        if (e != null) {
            Stack<RunExecution> s = stack.get(e);
            if (s != null && !s.isEmpty()) {
                return s.peek();
            }
        }
        return null;
    }

    static final RunnerStack INSTANCE = new RunnerStack();
}
