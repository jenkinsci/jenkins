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
