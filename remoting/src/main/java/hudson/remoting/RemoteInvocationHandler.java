package hudson.remoting;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
     */
    private transient Channel channel;

    RemoteInvocationHandler(int id) {
        this.oid = id;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if(channel==null)
            throw new IllegalStateException("proxy is not connected to a channel");

        if(args==null)  args = EMPTY_ARRAY;

        if(method.getDeclaringClass()==Object.class) {
            // handle equals and hashCode by ourselves
            try {
                return method.invoke(this,args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } else {
            // delegate the rest of the methods to the remote object
            return new RPCRequest(oid,method,args).call(channel);
        }
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        channel = Channel.current();
        ois.defaultReadObject();
    }

    /**
     * Two proxies are the same iff they represent the same remote object. 
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoteInvocationHandler that = (RemoteInvocationHandler) o;

        if (oid != that.oid) return false;
        if (channel!=that.channel) return false;

        return true;
    }

    public int hashCode() {
        return oid;
    }

    private static final long serialVersionUID = 1L;

    private static final class RPCRequest extends Request<Serializable,Throwable> {
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


        public RPCRequest(int oid, Method m, Object[] arguments) {
            this.oid = oid;
            this.arguments = arguments;
            this.methodName = m.getName();

            this.types = new String[arguments.length];
            Class<?>[] params = m.getParameterTypes();
            for( int i=0; i<arguments.length; i++ )
                types[i] = params[i].getName();
        }

        protected Serializable perform(Channel channel) throws Throwable {
            Object o = channel.exportedObjects.get(oid);
            if(o==null)
                throw new IllegalStateException("Unable to call "+methodName+". Invalid object ID "+oid);
            try {
                return (Serializable)choose(o).invoke(o,arguments);
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
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];
}
