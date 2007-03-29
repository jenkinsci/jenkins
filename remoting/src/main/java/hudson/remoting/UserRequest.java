package hudson.remoting;

import hudson.remoting.RemoteClassLoader.IClassLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.NotSerializableException;

/**
 * {@link Request} that can take {@link Callable} whose actual implementation
 * may not be known to the remote system in advance.
 *
 * <p>
 * This code assumes that the {@link Callable} object and all reachable code
 * are loaded by a single classloader.
 *
 * @author Kohsuke Kawaguchi
 */
final class UserRequest<RSP,EXC extends Throwable> extends Request<UserResponse<RSP,EXC>,EXC> {

    private final byte[] request;
    private final IClassLoader classLoaderProxy;
    private final String toString;

    public UserRequest(Channel local, Callable<?,EXC> c) throws IOException {
        request = serialize(c,local);
        this.toString = c.toString();
        ClassLoader cl = getClassLoader(c);
        classLoaderProxy = RemoteClassLoader.export(cl,local);
    }

    /*package*/ static ClassLoader getClassLoader(Callable<?,?> c) {
        ClassLoader cl = c.getClass().getClassLoader();
        if(c instanceof DelegatingCallable)
            cl = ((DelegatingCallable)c).getClassLoader();
        return cl;
    }

    protected UserResponse<RSP,EXC> perform(Channel channel) throws EXC {
        try {
            ClassLoader cl = channel.importedClassLoaders.get(classLoaderProxy);

            RSP r = null;
            Channel oldc = Channel.setCurrent(channel);
            try {
                Object o = new ObjectInputStreamEx(new ByteArrayInputStream(request), cl).readObject();

                Callable<RSP,EXC> callable = (Callable<RSP,EXC>)o;

                ClassLoader old = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(cl);
                // execute the service
                try {
                    r = callable.call();
                } finally {
                    Thread.currentThread().setContextClassLoader(old);
                }
            } finally {
                Channel.setCurrent(oldc);
            }

            return new UserResponse<RSP,EXC>(serialize(r,channel),false);
        } catch (Throwable e) {
            // propagate this to the calling process
            try {
                byte[] response;
                try {
                    response = serialize(e, channel);
                } catch (NotSerializableException x) {
                    // perhaps the thrown runtime exception is of time we can't handle
                    response = serialize(new ProxyException(e), channel);
                }
                return new UserResponse<RSP,EXC>(response,true);
            } catch (IOException x) {
                // throw it as a lower-level exception
                throw (EXC)x;
            }
        }
    }

    private byte[] serialize(Object o, Channel localChannel) throws IOException {
        Channel old = Channel.setCurrent(localChannel);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(o);
            return baos.toByteArray();
        } catch( NotSerializableException e ) {
            IOException x = new IOException("Unable to serialize " + o);
            x.initCause(e);
            throw x;
        } finally {
            Channel.setCurrent(old);
        }
    }

    public String toString() {
        return "UserRequest:"+toString;
    }
}

final class UserResponse<RSP,EXC extends Throwable> implements Serializable {
    private final byte[] response;
    private final boolean isException;

    public UserResponse(byte[] response, boolean isException) {
        this.response = response;
        this.isException = isException;
    }

    public RSP retrieve(Channel channel, ClassLoader cl) throws IOException, ClassNotFoundException, EXC {
        Channel old = Channel.setCurrent(channel);
        try {
            Object o = new ObjectInputStreamEx(new ByteArrayInputStream(response), cl).readObject();

            if(isException)
                throw (EXC)o;
            else
                return (RSP) o;
        } finally {
            Channel.setCurrent(old);
        }
    }

    private static final long serialVersionUID = 1L;
}
