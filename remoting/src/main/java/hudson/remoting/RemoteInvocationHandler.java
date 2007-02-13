package hudson.remoting;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Sits behind a proxy object and implements the proxy logic.
 *
 * @author Kohsuke Kawaguchi
 */
final class RemoteInvocationHandler implements InvocationHandler, Serializable {
    /**
     * This proxy acts as a proxy to the object of
     * Object ID on the remote {@link Channel}.
     */
    private final int oid;

    /**
     * Represents the connection to the remote {@link Channel}.
     *
     * <p>
     * This field is null when a {@link RemoteInvocationHandler} is just
     * created and not working as a remote proxy. Once tranferred to the
     * remote system, this field is set to non-null. 
     */
    private transient Channel channel;

    /**
     * True if we are proxying the user object.
     */
    private final boolean userProxy;

    RemoteInvocationHandler(int id, boolean userProxy) {
        this.oid = id;
        this.userProxy = userProxy;
    }

    /**
     * Creates a proxy that wraps an existing OID on the remote.
     */
    RemoteInvocationHandler(Channel channel, int id, boolean userProxy) {
        this.channel = channel;
        this.oid = id;
        this.userProxy = userProxy;
    }

    /**
     * Wraps an OID to the typed wrapper.
     */
    public static <T> T wrap(Channel channel, int id, Class<T> type, boolean userProxy) {
        return type.cast(Proxy.newProxyInstance( type.getClassLoader(), new Class[]{type},
            new RemoteInvocationHandler(channel,id,userProxy)));
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if(channel==null)
            throw new IllegalStateException("proxy is not connected to a channel");

        if(args==null)  args = EMPTY_ARRAY;

        Class<?> dc = method.getDeclaringClass();
        if(dc ==Object.class) {
            // handle equals and hashCode by ourselves
            try {
                return method.invoke(this,args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
        
        // delegate the rest of the methods to the remote object
        if(userProxy)
            return channel.call(new RPCRequest(oid,method,args,dc.getClassLoader()));
        else
            return new RPCRequest(oid,method,args).call(channel);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        channel = Channel.current();
        ois.defaultReadObject();
    }

    /**
     * Two proxies are the same iff they represent the same remote object. 
     */
    public boolean equals(Object o) {
        if(Proxy.isProxyClass(o.getClass()))
            o = Proxy.getInvocationHandler(o);

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoteInvocationHandler that = (RemoteInvocationHandler) o;

        return this.oid==that.oid && this.channel==that.channel;

    }

    public int hashCode() {
        return oid;
    }


    protected void finalize() throws Throwable {
        // unexport the remote object
        if(channel!=null)
            channel.send(new UnexportCommand(oid));
        super.finalize();
    }

    private static final long serialVersionUID = 1L;

    /**
     * Executes the method call remotely.
     *
     * If used as {@link Request}, this can be used to provide a lower-layer
     * for the use inside remoting, to implement the classloader delegation, and etc.
     * The downside of this is that the classes used as a parameter/return value
     * must be available to both JVMs.
     *
     * If used as {@link Callable} in conjunction with {@link UserRequest},
     * this can be used to send a method call to user-level objects, and
     * classes for the parameters and the return value are sent remotely if needed.
     */
    private static final class RPCRequest extends Request<Serializable,Throwable> implements DelegatingCallable<Serializable,Throwable> {
        /**
         * Target object id to invoke.
         */
        private final int oid;

        private final String methodName;
        /**
         * Type name of the arguments to invoke. They are names because
         * neither {@link Method} nor {@link Class} is serializable.
         */
        private final String[] types;
        /**
         * Arguments to invoke the method with.
         */
        private final Object[] arguments;

        /**
         * If this is used as {@link Callable}, we need to remember what classloader
         * to be used to serialize the request and the response.
         */
        private transient ClassLoader classLoader;

        public RPCRequest(int oid, Method m, Object[] arguments) {
            this(oid,m,arguments,null);
        }

        public RPCRequest(int oid, Method m, Object[] arguments, ClassLoader cl) {
            this.oid = oid;
            this.arguments = arguments;
            this.methodName = m.getName();
            this.classLoader = cl;

            this.types = new String[arguments.length];
            Class<?>[] params = m.getParameterTypes();
            for( int i=0; i<arguments.length; i++ )
                types[i] = params[i].getName();
        }

        public Serializable call() throws Throwable {
            return perform(Channel.current());
        }

        public ClassLoader getClassLoader() {
            if(classLoader!=null)
                return classLoader;
            else
                return getClass().getClassLoader();
        }

        protected Serializable perform(Channel channel) throws Throwable {
            Object o = channel.getExportedObject(oid);
            if(o==null)
                throw new IllegalStateException("Unable to call "+methodName+". Invalid object ID "+oid);
            try {
                Method m = choose(o);
                m.setAccessible(true);  // in case the class is not public
                return (Serializable) m.invoke(o,arguments);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        /**
         * Chooses the method to invoke.
         */
        private Method choose(Object o) {
            OUTER:
            for(Method m : o.getClass().getMethods()) {
                if(!m.getName().equals(methodName))
                    continue;
                Class<?>[] paramTypes = m.getParameterTypes();
                if(types.length!=arguments.length)
                    continue;
                for( int i=0; i<types.length; i++ ) {
                    if(!types[i].equals(paramTypes[i].getName()))
                        continue OUTER;
                }
                return m;
            }
            return null;
        }

        public String toString() {
            return "RPCRequest("+oid+","+methodName+")";
        }

        private static final long serialVersionUID = 1L; 
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];
}
