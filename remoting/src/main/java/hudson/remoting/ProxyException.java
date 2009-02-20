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

import java.io.IOException;

/**
 * Used when the exception thrown by the remoted code cannot be serialized.
 *
 * <p>
 * This exception captures the part of the information of the original exception
 * so that the caller can get some information about the problem that happened.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProxyException extends IOException {
    public ProxyException(Throwable cause) {
        super(cause.toString()); // use toString() to capture the class name and error message
        setStackTrace(cause.getStackTrace());

        // wrap all the chained exceptions
        if(cause.getCause()!=null)
            initCause(new ProxyException(cause.getCause()));
    }

    /**
     * {@link ProxyException} all the way down.
     */
    @Override
    public ProxyException getCause() {
        return (ProxyException)super.getCause();
    }
}
