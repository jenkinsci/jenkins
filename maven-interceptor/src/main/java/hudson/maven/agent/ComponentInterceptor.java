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
package hudson.maven.agent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Base class for intercepting components through its interfaces.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ComponentInterceptor<T> implements InvocationHandler {
    protected T delegate;

    /**
     * By default, all the methods are simply delegated to the intercepted component.
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate,args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    public T wrap(T o) {
        if(delegate !=null)
            // can't intercept two objects simultaneously
            throw new IllegalStateException();

        delegate = o;
        return (T)Proxy.newProxyInstance(o.getClass().getClassLoader(),
            getImplementedInterfaces(o), this);
    }

    private static Class[] getImplementedInterfaces(Object o) {
        Stack<Class> s = new Stack<Class>();
        s.push(o.getClass());
        Set<Class> interfaces = new HashSet<Class>();

        while(!s.isEmpty()) {
            Class c = s.pop();
            for (Class intf : c.getInterfaces()) {
                interfaces.add(intf);
                s.push(intf);
            }
            Class sc = c.getSuperclass();
            if(sc!=null)
                s.push(sc);
        }

        return interfaces.toArray(new Class[interfaces.size()]);
    }
}
