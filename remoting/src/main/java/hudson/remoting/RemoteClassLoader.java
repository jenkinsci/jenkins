/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Loads class files from the other peer through {@link Channel}.
 *
 * <p>
 * If the {@linkplain Channel#isRestricted channel is restricted}, this classloader will be
 * created by will not attempt to load anything from the remote classloader. The reason we
 * create such a useless instance is so that when such classloader is sent back to the remote side again,
 * the remoting system can re-discover what {@link ClassLoader} this was tied to.
 *
 * @author Kohsuke Kawaguchi
 */
final class RemoteClassLoader extends URLClassLoader {
    /**
     * Proxy to the code running on remote end.
     */
    private final IClassLoader proxy;
    /**
     * Remote peer that the {@link #proxy} is connected to.
     */
    private final Channel channel;

    private final Map<String,File> resourceMap = new HashMap<String,File>();
    private final Map<String,Vector<File>> resourcesMap = new HashMap<String,Vector<File>>();

    /**
     * List of jars that are already pre-fetched through {@link #addURL(URL)}.
     *
     * <p>
     * Note that URLs in this set are URLs on the other peer.
     */
    private final Set<URL> prefetchedJars = new HashSet<URL>();

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
        super(new URL[0],parent);
        this.proxy = proxy;
        this.channel = RemoteInvocationHandler.unwrap(proxy);
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            // first attempt to load from locally fetched jars
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            if(channel.isRestricted)
                throw e;
            // delegate to remote
            long startTime = System.nanoTime();
            byte[] bytes = proxy.fetch(name);
            channel.classLoadingTime.addAndGet(System.nanoTime()-startTime);
            channel.classLoadingCount.incrementAndGet();

            // define package
            definePackage(name);

            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    /**
     * Defining a package is necessary to make {@link Class#getPackage()} work,
     * which is often used to retrieve package-level annotations.
     * (for example, JAXB RI and Hadoop use them.) 
     */
    private void definePackage(String name) {
        int idx = name.lastIndexOf('.');
        if (idx<0)  return; // not in a package
        
        String packageName = name.substring(0,idx);
        if (getPackage(packageName) != null)    // already defined
            return;

        definePackage(packageName, null, null, null, null, null, null, null);
    }

    public URL findResource(String name) {
        // first attempt to load from locally fetched jars
        URL url = super.findResource(name);
        if(url!=null || channel.isRestricted)   return url;

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

    public Enumeration<URL> findResources(String name) throws IOException {
        if(channel.isRestricted)
            return new Vector<URL>().elements();

        // TODO: use the locally fetched jars to speed up the look up
        // the challenge is how to combine the list from local jars
        // and the remote list

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
     * Prefetches the jar into this class loader.
     *
     * @param jar
     *      Jar to be prefetched. Note that this file is an file on the other end,
     *      and doesn't point to anything meaningful locally.
     * @return
     *      true if the prefetch happened. false if the jar is already prefetched.
     * @see Channel#preloadJar(Callable, Class[]) 
     */
    /*package*/ boolean prefetch(URL jar) throws IOException {
        synchronized (prefetchedJars) {
            if(prefetchedJars.contains(jar))
                return false;

            String p = jar.getPath().replace('\\','/');
            p = p.substring(p.lastIndexOf('/')+1);
            File localJar = makeResource(p,proxy.fetchJar(jar));
            addURL(localJar.toURI().toURL());
            prefetchedJars.add(jar);
            return true;
        }
    }

    /**
     * Remoting interface.
     */
    /*package*/ static interface IClassLoader {
        byte[] fetchJar(URL url) throws IOException;
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

        public byte[] fetchJar(URL url) throws IOException {
            return readFully(url.openStream());
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

        public byte[] fetchJar(URL url) throws IOException {
            return proxy.fetchJar(url);
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
