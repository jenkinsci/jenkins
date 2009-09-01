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


        protected void annotateClass(Class<?> c) throws IOException {
            ClassLoader cl = c.getClassLoader();
            if (cl==null) {// bootstrap classloader. no need to export.
                writeInt(-2);
                return;
            }

            Integer idx = classLoaders.get(cl);
            if (idx==null) {
                classLoaders.put(cl,classLoaders.size());
                writeInt(-1);
                writeObject(RemoteClassLoader.export(cl,channel));
            } else {
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
            int code = readInt();
            switch (code) {
            case -2:
                return null;
            case -1:
                // fill the entry with some value in preparation of recursive readObject below.
                // this is actually only necessary for classLoader[0].
                classLoaders.add(Channel.class.getClassLoader());

                ClassLoader cl = channel.importedClassLoaders.get((IClassLoader) readObject());
                classLoaders.set(classLoaders.size()-1,cl);
                return cl;
            default:
                return classLoaders.get(code);
            }
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            try {
                return Class.forName(name, false, readClassLoader());
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
}
