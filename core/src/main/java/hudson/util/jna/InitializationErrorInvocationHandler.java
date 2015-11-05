package hudson.util.jna;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * {@link InvocationHandler} that reports the same exception over and over again when methods are invoked
 * on the interface.
 *
 * This is convenient to remember why the initialization of the real JNA proxy failed.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.487
 * @see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7199848">Related bug report against JDK</a>
 */
public class InitializationErrorInvocationHandler implements InvocationHandler {
    private final Throwable cause;

    private InitializationErrorInvocationHandler(Throwable cause) {
        this.cause = cause;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass()==Object.class)
            return method.invoke(this,args);

        throw new UnsupportedOperationException("Failed to link the library: "+method.getDeclaringClass(), cause);
    }

    public static <T> T create(Class<T> type, Throwable cause) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new InitializationErrorInvocationHandler(cause)));
    }
}
