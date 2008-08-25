package org.jvnet.hudson.test.rhino;

import org.mozilla.javascript.debug.Debugger;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.debug.DebugFrame;
import org.mozilla.javascript.Context;

import java.util.List;
import java.util.ArrayList;

/**
 * Monitors the execution of the JavaScript inside HtmlUnit, and provides debug information
 * such as call stacks, variables, arguments, etc.
 *
 * <h2>Usage</h2>
 * <p>
 * When you set a break point in Java code that are directly/indirectly invoked through JavaScript,
 * use {@link #toString()} to see the JavaScript stack trace (and variables at each stack frame.)
 * This helps you see where the problem is.
 *
 * <p>
 * TODO: add programmatic break point API, selective method invocation tracing, and
 * allow arbitrary script evaluation in arbitrary stack frame.
 *
 * @author Kohsuke Kawaguchi
 */
public class JavaScriptDebugger implements Debugger {
    /**
     * Call stack as a list. The list grows at the end, so the first element in the list
     * is the oldest stack frame.
     */
    public final List<CallStackFrame> callStack = new ArrayList<CallStackFrame>();

    public void handleCompilationDone(Context cx, DebuggableScript fnOrScript, String source) {
    }

    public DebugFrame getFrame(Context cx, DebuggableScript fnOrScript) {
        return new CallStackFrame(this,fnOrScript);
    }

    /**
     * Formats the current call stack into a human readable string.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        for( int i=callStack.size()-1; i>=0; i-- )
            buf.append(callStack.get(i)).append('\n');
        return buf.toString();
    }
}
