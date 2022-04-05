package hudson.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Creates a proxy that traps every method call.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class InterceptingProxy {
    /**
     * Intercepts every method call.
     */
    protected abstract Object call(Object o, Method m, Object[] args) throws Throwable;

    public final <T> T wrap(Class<T> type, final T object) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                try {
                    return call(object, method, args);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }
        }));
    }
}
