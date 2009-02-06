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
package hudson.remoting;

/**
 * Request/response pattern over {@link Command}.
 *
 * This is layer 1.
 *
 * @author Kohsuke Kawaguchi
 * @see Request
 */
final class Response<RSP,EXC extends Throwable> extends Command {
    /**
     * ID of the {@link Request} for which
     */
    private final int id;

    final RSP returnValue;
    final EXC exception;

    Response(int id, RSP returnValue) {
        this.id = id;
        this.returnValue = returnValue;
        this.exception = null;
    }

    Response(int id, EXC exception) {
        this.id = id;
        this.returnValue = null;
        this.exception = exception;
    }

    /**
     * Notifies the waiting {@link Request}.
     */
    @Override
    protected void execute(Channel channel) {
        Request req = channel.pendingCalls.get(id);
        if(req==null)
            return; // maybe aborted
        req.onCompleted(this);
        channel.pendingCalls.remove(id);
    }

    public String toString() {
        return "Response[retVal="+toString(returnValue)+",exception="+toString(exception)+"]";
    }

    private static String toString(Object o) {
        if(o==null) return "null";
        else        return o.toString();
    }

    private static final long serialVersionUID = 1L;
}
