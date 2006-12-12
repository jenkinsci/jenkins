package hudson.remoting;

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
    private final int classLoaderId;
    private final String toString;

    public UserRequest(Channel local, Callable<?,EXC> c) throws IOException {
        request = serialize(c);
        this.toString = c.toString();
        classLoaderId = local.exportedClassLoaders.intern(c.getClass().getClassLoader());
    }

    protected UserResponse<RSP> perform(Channel channel) throws EXC {
        try {
            ClassLoader cl = channel.importedClassLoaders.get(classLoaderId);

            Object o = new ObjectInputStreamEx(new ByteArrayInputStream(request), cl).readObject();
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

            return new UserResponse<RSP>(serialize(r),classLoaderId);
        } catch (IOException e) {
            // propagate this to the calling process
            throw (EXC)e;
        } catch (ClassNotFoundException e) {
            // propagate this to the calling process
            throw (EXC)e;
        }
    }

    private byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(o);
        return baos.toByteArray();
    }

    public String toString() {
        return "UserRequest:"+toString;
    }
}

final class UserResponse<RSP extends Serializable> implements Serializable {
    private final byte[] response;
    private final int classLoaderId;

    public UserResponse(byte[] response, int classLoaderId) {
        this.response = response;
        this.classLoaderId = classLoaderId;
    }

    public RSP retrieve(Channel channel) throws IOException, ClassNotFoundException {
        return (RSP) new ObjectInputStreamEx(
            new ByteArrayInputStream(response),
            channel.exportedClassLoaders.get(classLoaderId)
            ).readObject();
    }

    private static final long serialVersionUID = 1L;
}
