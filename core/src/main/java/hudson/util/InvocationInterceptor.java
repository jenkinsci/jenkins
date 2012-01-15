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
package hudson.util;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;

/**
 * Interceptor around {@link InvocationHandler}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.232
 */
public interface InvocationInterceptor {
    /**
     * This method can intercept the invocation of {@link InvocationHandler} either before or after
     * the invocation happens.
     *
     * <p>
     * The general coding pattern is:
     *
     * <pre>
     * Object invoke(Object proxy, Method method, Object[] args, InvocationHandler delegate) {
     *   ... do pre-invocation work ...
     *   ret = delegate.invoke(proxy,method,args);
     *   ... do post-invocation work ...
     *   return ret;
     * }
     * </pre>
     *
     * <p>
     * But the implementation may choose to skip calling the 'delegate' object, alter arguments,
     * and alter the return value. 
     */
    Object invoke(Object proxy, Method method, Object[] args, InvocationHandler delegate) throws Throwable;
}
