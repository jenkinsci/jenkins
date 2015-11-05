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
package org.jvnet.hudson.test.rhino;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.debug.DebugFrame;
import net.sourceforge.htmlunit.corejs.javascript.debug.DebuggableScript;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Stack frame.
 *
 * @author Kohsuke Kawaguchi
 */
public class CallStackFrame implements DebugFrame {
    /**
     * {@link JavaScriptDebugger} that this stack frame lives in.
     */
    public final JavaScriptDebugger owner;
    /**
     * The function being executed.
     */
    public final DebuggableScript fnOrScript;

    private Scriptable activation;
    private Scriptable thisObj;
    private Object[] args;
    private int line;

    public CallStackFrame(JavaScriptDebugger owner, DebuggableScript fnOrScript) {
        this.owner = owner;
        this.fnOrScript = fnOrScript;
    }

    public void onEnter(Context cx, Scriptable activation, Scriptable thisObj, Object[] args) {
        this.activation = activation;
        this.thisObj = thisObj;
        this.args = args;
        owner.addCallStackFrame(this);
    }

    public void onExit(Context cx, boolean byThrow, Object resultOrException) {
        owner.removeCallStackFrame(this);

        activation = null;
        thisObj = null;
        args = null;
        line = -1;
    }

    public void onLineChange(Context cx, int lineNumber) {
        this.line = lineNumber;
    }

    public void onExceptionThrown(Context cx, Throwable ex) {
    }

    public void onDebuggerStatement(Context cx) {
    }

    /**
     * In-scope variables.
     */
    public SortedMap<String,Object> getVariables() {
        SortedMap<String,Object> r = new TreeMap<String,Object>();
        for( int i=fnOrScript.getParamAndVarCount()-1; i>=0; i-- ) {
            String name =fnOrScript.getParamOrVarName(i);
            r.put(name,activation.get(name,activation));
        }
        return r;
    }

    /**
     * Formats this call stack, arguments, and its local variables as a human readable string.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(fnOrScript.getFunctionName());
        buf.append('(');
        for( int i=0; i<args.length; i++ ) {
            if(i!=0)    buf.append(',');
            buf.append(args[i]);
        }
        buf.append(')');
        buf.append("\n  at ").append(fnOrScript.getSourceName()).append('#').append(line);

        buf.append("\n  variables=").append(getVariables());
        
        return buf.toString();
    }
}
