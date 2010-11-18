package hudson.remoting;

import hudson.remoting.RemoteClassLoader.IClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ObjectInputStream}/{@link ObjectOutputStream} pair that can handle object graph that spans across
 * multiple classloaders.
 *
 * <p>
 * To pass around ClassLoaders, this class uses OID instead of {@link IClassLoader}, since doing so
 * can results in recursive class resolution that may end up with NPE in ObjectInputStream.defaultReadFields like
 * described in the comment from huybrechts in HUDSON-4293.
 *
 * @author Kohsuke Kawaguchi
 * @see Capability#supportsMultiClassLoaderRPC() 
 */
class MultiClassLoaderSerializer {
    static final class Output extends ObjectOutputStream {
        private final Channel channel;
        /**
         * Encountered Classloaders, to their indices.
         */
        private final Map<ClassLoader,Integer> classLoaders = new HashMap<ClassLoader, Integer>();

        Output(Channel channel, OutputStream out) throws IOException {
            super(out);
            this.channel = channel;
        }

        @Override
        protected void annotateClass(Class<?> c) throws IOException {
            ClassLoader cl = c.getClassLoader();
            if (cl==null) {// bootstrap classloader. no need to export.
                writeInt(TAG_SYSTEMCLASSLOADER);
                return;
            }

            Integer idx = classLoaders.get(cl);
            if (idx==null) {
                classLoaders.put(cl,classLoaders.size());
                if (cl instanceof RemoteClassLoader) {
                    int oid = ((RemoteClassLoader) cl).getOid(channel);
                    if (oid>=0) {
                        // this classloader came from where we are sending this classloader to.
                        writeInt(TAG_LOCAL_CLASSLOADER);
                        writeInt(oid);
                        return;
                    }
                }

                // tell the receiving side that they need to import a new classloader
                writeInt(TAG_EXPORTED_CLASSLOADER);
                writeInt(RemoteClassLoader.exportId(cl,channel));
            } else {// reference to a classloader that's already written
                writeInt(idx);
            }
        }

        @Override
        protected void annotateProxyClass(Class<?> cl) throws IOException {
            annotateClass(cl);
        }
    }

    static final class Input extends ObjectInputStream {
        private final Channel channel;
        private final List<ClassLoader> classLoaders = new ArrayList<ClassLoader>();

        Input(Channel channel, InputStream in) throws IOException {
            super(in);
            this.channel = channel;
        }

        private ClassLoader readClassLoader() throws IOException, ClassNotFoundException {
            ClassLoader cl;
            int code = readInt();
            switch (code) {
            case TAG_SYSTEMCLASSLOADER:
                return null;

            case TAG_LOCAL_CLASSLOADER:
                cl = ((RemoteClassLoader.ClassLoaderProxy)channel.getExportedObject(readInt())).cl;
                classLoaders.add(cl);
                return cl;

            case TAG_EXPORTED_CLASSLOADER:
                cl = channel.importedClassLoaders.get(readInt());
                classLoaders.add(cl);
                return cl;
            default:
                return classLoaders.get(code);
            }
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            try {
                ClassLoader cl = readClassLoader();
                Class<?> c = Class.forName(name, false, cl);
                return c;
            } catch (ClassNotFoundException ex) {
                return super.resolveClass(desc);
            }
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            ClassLoader cl = readClassLoader();

            Class[] classes = new Class[interfaces.length];
            for (int i = 0; i < interfaces.length; i++)
                classes[i] = Class.forName(interfaces[i], false, cl);
            return Proxy.getProxyClass(cl, classes);
        }
    }

    /**
     * Indicates that the class being sent should be loaded from the system classloader.
     */
    private static final int TAG_SYSTEMCLASSLOADER = -3;
    /**
     * Indicates that the class being sent originates from the sender side. The sender exports this classloader
     * and sends its OID in the following int. The receiver will import this classloader to resolve the class.
     */
    private static final int TAG_EXPORTED_CLASSLOADER = -2;
    /**
     * Indicates that the class being sent originally came from the receiver. The following int indicates
     * the OID of the classloader exported from the receiver, which the sender used.
     */
    private static final int TAG_LOCAL_CLASSLOADER = -1;
}
