package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads class files from the other peer through {@link Channel}.
 *
 * @author Kohsuke Kawaguchi
 */
final class RemoteClassLoader extends ClassLoader {
    private final IClassLoader proxy;

    public RemoteClassLoader(ClassLoader parent, IClassLoader proxy) {
        super(parent);
        this.proxy = proxy;
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = proxy.fetch(name);
        return defineClass(name, bytes, 0, bytes.length);
    }

    /**
     * Remoting interface.
     */
    /*package*/ static interface IClassLoader {
        byte[] fetch(String className) throws ClassNotFoundException;
    }

    public static IClassLoader export(ClassLoader cl, Channel local) {
        return local.export(IClassLoader.class, new ClassLoaderProxy(cl));
    }

    /**
     * Exports and just returns the object ID, instead of obtaining the proxy.
     */
    static int exportId(ClassLoader cl, Channel local) {
        return local.export(new ClassLoaderProxy(cl));
    }

    /*package*/ static final class ClassLoaderProxy implements IClassLoader {
        private final ClassLoader cl;

        public ClassLoaderProxy(ClassLoader cl) {
            this.cl = cl;
        }

        public byte[] fetch(String className) throws ClassNotFoundException {
            InputStream in = cl.getResourceAsStream(className.replace('.', '/') + ".class");
            if(in==null)
                throw new ClassNotFoundException();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try {
                byte[] buf = new byte[8192];
                int len;
                while((len=in.read(buf))>0)
                baos.write(buf,0,len);
            } catch (IOException e) {
                throw new ClassNotFoundException();
            }

            return baos.toByteArray();
        }

        public boolean equals(Object that) {
            if (this == that) return true;
            if (that == null || getClass() != that.getClass()) return false;

            return cl.equals(((ClassLoaderProxy) that).cl);
        }

        public int hashCode() {
            return cl.hashCode();
        }
    }
}
