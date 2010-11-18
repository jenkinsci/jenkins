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

import hudson.model.Run.Runner;

import java.util.Stack;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps track of {@link Runner}s that are currently executing on the given thread
 * (that can be either an {@link Executor} or threads that are impersonating one.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 */
final class RunnerStack {
    private final Map<Executor,Stack<Runner>> stack = new WeakHashMap<Executor,Stack<Runner>>();

    synchronized void push(Runner r) {
        Executor e = Executor.currentExecutor();
        Stack<Runner> s = stack.get(e);
        if(s==null) stack.put(e,s=new Stack<Runner>());
        s.push(r);
    }

    synchronized void pop() {
        Executor e = Executor.currentExecutor();
        Stack<Runner> s = stack.get(e);
        s.pop();
        if(s.isEmpty()) stack.remove(e);
    }

    synchronized Runner peek() {
        return stack.get(Executor.currentExecutor()).peek();
    }

    static final RunnerStack INSTANCE = new RunnerStack();
}
