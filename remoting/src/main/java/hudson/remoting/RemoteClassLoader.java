package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;

/**
 * Loads class files from the other peer through {@link Channel}.
 *
 * @author Kohsuke Kawaguchi
 */
final class RemoteClassLoader extends ClassLoader {
    private final IClassLoader proxy;
    /**
     * Remote peer that the {@link #proxy} is connected to.
     */
    private final Channel channel;

    private final Map<String,File> resourceMap = new HashMap<String,File>();
    private final Map<String,Vector<File>> resourcesMap = new HashMap<String,Vector<File>>();

    public static ClassLoader create(ClassLoader parent, IClassLoader proxy) {
        if(proxy instanceof ClassLoaderProxy) {
            // when the remote sends 'RemoteIClassLoader' as the proxy, on this side we get it
            // as ClassLoaderProxy. This means, the so-called remote classloader here is
            // actually our classloader that we exported to the other side.
            return ((ClassLoaderProxy)proxy).cl;
        }
        return new RemoteClassLoader(parent, proxy);
    }

    private RemoteClassLoader(ClassLoader parent, IClassLoader proxy) {
        super(parent);
        this.proxy = proxy;
        this.channel = RemoteInvocationHandler.unwrap(proxy);
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        long startTime = System.nanoTime();
        byte[] bytes = proxy.fetch(name);
        channel.classLoadingTime.addAndGet(System.nanoTime()-startTime);
        channel.classLoadingCount.incrementAndGet();
        return defineClass(name, bytes, 0, bytes.length);
    }

    protected URL findResource(String name) {
        try {
            if(resourceMap.containsKey(name)) {
                File f = resourceMap.get(name);
                if(f==null) return null;    // no such resource
                if(f.exists())
                    // be defensive against external factors that might have deleted this file, since we use /tmp
                    // see http://www.nabble.com/Surefire-reports-tt17554215.html
                    return f.toURL();
            }

            long startTime = System.nanoTime();
            byte[] image = proxy.getResource(name);
            channel.resourceLoadingTime.addAndGet(System.nanoTime()-startTime);
            channel.resourceLoadingCount.incrementAndGet();
            if(image==null) {
                resourceMap.put(name,null);
                return null;
            }
    
            File res = makeResource(name, image);
            resourceMap.put(name,res);
            return res.toURL();
        } catch (IOException e) {
            throw new Error("Unable to load resource "+name,e);
        }
    }

    private static Vector<URL> toURLs(Vector<File> files) throws MalformedURLException {
        Vector<URL> r = new Vector<URL>(files.size());
        for (File f : files) {
            if(!f.exists()) return null;    // abort
            r.add(f.toURL());
        }
        return r;
    }

    protected Enumeration<URL> findResources(String name) throws IOException {
        Vector<File> files = resourcesMap.get(name);
        if(files!=null) {
            Vector<URL> urls = toURLs(files);
            if(urls!=null)
                return urls.elements();
        }

        long startTime = System.nanoTime();
        byte[][] images = proxy.getResources(name);
        channel.resourceLoadingTime.addAndGet(System.nanoTime()-startTime);
        channel.resourceLoadingCount.incrementAndGet();

        files = new Vector<File>();
        for( byte[] image: images )
            files.add(makeResource(name,image));
        resourcesMap.put(name,files);

        return toURLs(files).elements();
    }

    private File makeResource(String name, byte[] image) throws IOException {
        int idx = name.lastIndexOf('/');
        File f = File.createTempFile("hudson-remoting","."+name.substring(idx+1));
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(image);
        fos.close();
        f.deleteOnExit();

        return f;
    }

    /**
     * Remoting interface.
     */
    /*package*/ static interface IClassLoader {
        byte[] fetch(String className) throws ClassNotFoundException;
        byte[] getResource(String name) throws IOException;
        byte[][] getResources(String name) throws IOException;
    }

    public static IClassLoader export(ClassLoader cl, Channel local) {
        if (cl instanceof RemoteClassLoader) {
            // check if this is a remote classloader from the channel
            final RemoteClassLoader rcl = (RemoteClassLoader) cl;
            int oid = RemoteInvocationHandler.unwrap(rcl.proxy, local);
            if(oid!=-1) {
                return new RemoteIClassLoader(oid,rcl.proxy);
            }
        }
        return local.export(IClassLoader.class, new ClassLoaderProxy(cl), false);
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
                throw new ClassNotFoundException(className);

            try {
                return readFully(in);
            } catch (IOException e) {
                throw new ClassNotFoundException();
            }
        }


        public byte[] getResource(String name) throws IOException {
            InputStream in = cl.getResourceAsStream(name);
            if(in==null)   return null;

            return readFully(in);
        }

        public byte[][] getResources(String name) throws IOException {
            List<byte[]> images = new ArrayList<byte[]>();

            Enumeration<URL> e = cl.getResources(name);
            while(e.hasMoreElements()) {
                images.add(readFully(e.nextElement().openStream()));
            }

            return images.toArray(new byte[images.size()][]);
        }

        private byte[] readFully(InputStream in) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            byte[] buf = new byte[8192];
            int len;
            while((len=in.read(buf))>0)
                baos.write(buf,0,len);
            in.close();

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

    /**
     * {@link IClassLoader} to be shipped back to the channel where it came from.
     *
     * <p>
     * When the object stays on the side where it's created, delegate to the proxy field
     * to work (which will be the remote instance.) Once transferred to the other side,
     * resolve back to the instance on the server.
     */
    private static class RemoteIClassLoader implements IClassLoader, Serializable {
        private transient final IClassLoader proxy;
        private final int oid;

        private RemoteIClassLoader(int oid, IClassLoader proxy) {
            this.proxy = proxy;
            this.oid = oid;
        }

        public byte[] fetch(String className) throws ClassNotFoundException {
            return proxy.fetch(className);
        }

        public byte[] getResource(String name) throws IOException {
            return proxy.getResource(name);
        }

        public byte[][] getResources(String name) throws IOException {
            return proxy.getResources(name);
        }

        private Object readResolve() {
            return Channel.current().getExportedObject(oid);
        }

        private static final long serialVersionUID = 1L;
    }

}
