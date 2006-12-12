package hudson.remoting;

import hudson.remoting.RemoteClassLoader.IClassLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

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
final class UserRequest<RSP extends Serializable,EXC extends Throwable> extends Request<UserResponse<RSP>,EXC> {

    private final byte[] request;
    private final IClassLoader classLoaderProxy;
    private final String toString;

    public UserRequest(Channel local, Callable<?,EXC> c) throws IOException {
        request = serialize(c,local);
        this.toString = c.toString();
        classLoaderProxy = RemoteClassLoader.export( c.getClass().getClassLoader(), local );
    }

    protected UserResponse<RSP> perform(Channel channel) throws EXC {
        try {
            ClassLoader cl = channel.importedClassLoaders.get(classLoaderProxy);

            Object o;
            Channel oldc = Channel.setCurrent(channel);
            try {
                o = new ObjectInputStreamEx(new ByteArrayInputStream(request), cl).readObject();
            } finally {
                Channel.setCurrent(oldc);
            }
            Callable<RSP,EXC> callable = (Callable<RSP,EXC>)o;

            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(cl);
            // execute the service
            RSP r = null;
            try {
                r = callable.call();
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }

            return new UserResponse<RSP>(serialize(r,channel));
        } catch (IOException e) {
            // propagate this to the calling process
            throw (EXC)e;
        } catch (ClassNotFoundException e) {
            // propagate this to the calling process
            throw (EXC)e;
        }
    }

    private byte[] serialize(Object o, Channel localChannel) throws IOException {
        Channel old = Channel.setCurrent(localChannel);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(o);
            return baos.toByteArray();
        } finally {
            Channel.setCurrent(old);
        }
    }

    public String toString() {
        return "UserRequest:"+toString;
    }
}

final class UserResponse<RSP extends Serializable> implements Serializable {
    private final byte[] response;

    public UserResponse(byte[] response) {
        this.response = response;
    }

    public RSP retrieve(Channel channel, ClassLoader cl) throws IOException, ClassNotFoundException {
        return (RSP) new ObjectInputStreamEx(new ByteArrayInputStream(response),cl).readObject();
    }

    private static final long serialVersionUID = 1L;
}
