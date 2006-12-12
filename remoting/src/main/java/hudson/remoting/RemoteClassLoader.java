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
    private final Channel channel;
    private final int id;

    public RemoteClassLoader(ClassLoader parent, Channel channel, int id) {
        super(parent);
        this.id = id;
        this.channel = channel;
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            byte[] bytes = new ClassImageFetchRequest(id,name).call(channel);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (InterruptedException e) {
            ClassNotFoundException x = new ClassNotFoundException(e.getMessage());
            x.initCause(e);
            throw x;
        } catch (IOException e) {
            ClassNotFoundException x = new ClassNotFoundException(e.getMessage());
            x.initCause(e);
            throw x;
        }
    }

    private static final class ClassImageFetchRequest extends Request<byte[],ClassNotFoundException> {
        private final int id;
        private final String className;

        public ClassImageFetchRequest(int id, String className) {
            this.id = id;
            this.className = className;
        }

        protected byte[] perform(Channel channel) throws ClassNotFoundException {
            ClassLoader cl = channel.exportedClassLoaders.get(id);
            if(cl==null)
                throw new ClassNotFoundException();

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


        public String toString() {
            return "fetchClassImage : "+className;
        }
    }
}
