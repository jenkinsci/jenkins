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

    /**
     * If this {@link RemoteClassLoader} represents a classloader from the specified channel,
     * return its exported OID. Otherwise return -1.
     */
    /*package*/ int getOid(Channel channel) {
        return RemoteInvocationHandler.unwrap(proxy,channel);
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            // first attempt to load from locally fetched jars
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            if(channel.isRestricted)
                throw e;
            // delegate to remote
            if (channel.remoteCapability.supportsMultiClassLoaderRPC()) {
                /*
                    In multi-classloader setup, RemoteClassLoaders do not retain the relationships among the original classloaders,
                    so each RemoteClassLoader ends up loading classes on its own without delegating to other RemoteClassLoaders.

                    See the classloader X/Y examples in HUDSON-5048 for the depiction of the problem.

                    So instead, we find the right RemoteClassLoader to load the class on per class basis.
                    The communication is optimized for the single classloader use, by always returning the class file image
                    along with the reference to the initiating ClassLoader (if the initiating ClassLoader has already loaded this class,
                    then the class file image is wasted.)
                 */
                long startTime = System.nanoTime();
                ClassFile cf = proxy.fetch2(name);
                channel.classLoadingTime.addAndGet(System.nanoTime()-startTime);
                channel.classLoadingCount.incrementAndGet();

                ClassLoader cl = channel.importedClassLoaders.get(cf.classLoader);
                if (cl instanceof RemoteClassLoader) {
                    RemoteClassLoader rcl = (RemoteClassLoader) cl;
                    Class<?> c = rcl.findLoadedClass(name);
                    if (c==null)
                        c = rcl.loadClassFile(name,cf.classImage);
                    return c;
                } else {
                    return cl.loadClass(name);
                }
            } else {
                long startTime = System.nanoTime();
                byte[] bytes = proxy.fetch(name);
                channel.classLoadingTime.addAndGet(System.nanoTime()-startTime);
                channel.classLoadingCount.incrementAndGet();

                return loadClassFile(name, bytes);
            }
        }
    }

    private Class<?> loadClassFile(String name, byte[] bytes) {
        // define package
        definePackage(name);

        return defineClass(name, bytes, 0, bytes.length);
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
                    return f.toURI().toURL();
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
            return res.toURI().toURL();
        } catch (IOException e) {
            throw new Error("Unable to load resource "+name,e);
        }
    }

    private static Vector<URL> toURLs(Vector<File> files) throws MalformedURLException {
        Vector<URL> r = new Vector<URL>(files.size());
        for (File f : files) {
            if(!f.exists()) return null;    // abort
            r.add(f.toURI().toURL());
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

    // FIXME move to utils
    /** Instructs Java to recursively delete the given directory (dir) and its contents when the JVM exits.
     *  @param dir File  customer  representing directory to delete. If this file argument is not a directory, it will still
     *  be deleted. <p>
     *  The method works in Java 1.3, Java 1.4, Java 5.0 and Java 6.0; but it does not work with some early Java 6.0 versions
     *  See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6437591
     */
    public static void deleteDirectoryOnExit(final File dir) {
        // Delete this on exit.  Delete on exit requests are processed in REVERSE order
        dir.deleteOnExit();

        // If it's a directory, visit its children.  This recursive walk has to be done AFTER calling deleteOnExit
        // on the directory itself because Java deletes the files to be deleted on exit in reverse order.
        if (dir.isDirectory()) {
            File[] childFiles = dir.listFiles();
            if (childFiles != null) { // listFiles may return null if there's an IO error
                for (File f: childFiles) { deleteDirectoryOnExit(f); }
            }
        }
    }


    private File makeResource(String name, byte[] image) throws IOException {
        File tmpFile = createTempDir();
        File resource = new File(tmpFile, name);
        resource.getParentFile().mkdirs();

        FileOutputStream fos = new FileOutputStream(resource);
        fos.write(image);
        fos.close();

        deleteDirectoryOnExit(tmpFile);

        return resource;
    }

    private File createTempDir() throws IOException {
    	// work around sun bug 6325169 on windows
    	// see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6325169
        int nRetry=0;
        while (true) {
            try {
                File tmpFile = File.createTempFile("hudson-remoting", "");
                tmpFile.delete();
                tmpFile.mkdir();
                return tmpFile;
            } catch (IOException e) {
                if (nRetry++ < 100)
                    continue;
                throw e;
            }
        }
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

    static class ClassFile implements Serializable {
        /**
         * oid of the classloader that should load this class.
         */
        final int classLoader;
        final byte[] classImage;

        ClassFile(int classLoader, byte[] classImage) {
            this.classLoader = classLoader;
            this.classImage = classImage;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Remoting interface.
     */
    /*package*/ static interface IClassLoader {
        byte[] fetchJar(URL url) throws IOException;
        byte[] fetch(String className) throws ClassNotFoundException;
        ClassFile fetch2(String className) throws ClassNotFoundException;
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
        return local.export(IClassLoader.class, new ClassLoaderProxy(cl,local), false);
    }

    /**
     * Exports and just returns the object ID, instead of obtaining the proxy.
     */
    static int exportId(ClassLoader cl, Channel local) {
        return local.export(new ClassLoaderProxy(cl,local), false);
    }

    /*package*/ static final class ClassLoaderProxy implements IClassLoader {
        final ClassLoader cl;
        final Channel channel;

        public ClassLoaderProxy(ClassLoader cl, Channel channel) {
        	assert cl != null;

            this.cl = cl;
            this.channel = channel;
        }

        public byte[] fetchJar(URL url) throws IOException {
            return readFully(url.openStream());
        }

        public byte[] fetch(String className) throws ClassNotFoundException {
        	if (!USE_BOOTSTRAP_CLASSLOADER && cl==PSEUDO_BOOTSTRAP) {
        		throw new ClassNotFoundException("Classloading from bootstrap classloader disabled");
        	}
        	
            InputStream in = cl.getResourceAsStream(className.replace('.', '/') + ".class");
            if(in==null)
                throw new ClassNotFoundException(className);

            try {
                return readFully(in);
            } catch (IOException e) {
                throw new ClassNotFoundException();
            }
        }

        public ClassFile fetch2(String className) throws ClassNotFoundException {
            ClassLoader ecl = cl.loadClass(className).getClassLoader();
            if (ecl == null) {
            	if (USE_BOOTSTRAP_CLASSLOADER) {
            		ecl = PSEUDO_BOOTSTRAP;
            	} else {
            		throw new ClassNotFoundException("Classloading from system classloader disabled");
            	}
            }

            try {
                return new ClassFile(
                        exportId(ecl,channel),
                        readFully(ecl.getResourceAsStream(className.replace('.', '/') + ".class")));
            } catch (IOException e) {
                throw new ClassNotFoundException();
            }
        }

        public byte[] getResource(String name) throws IOException {
        	URL resource = cl.getResource(name);
        	if (resource == null) {
        		return null;
        	}
        	
        	if (!USE_BOOTSTRAP_CLASSLOADER) {
        		URL systemResource = PSEUDO_BOOTSTRAP.getResource(name);
        		if (resource.equals(systemResource)) {
        			return null;
        		}
        	}
        	
            return readFully(resource.openStream());
        }

        public byte[][] getResources(String name) throws IOException {
            List<byte[]> images = new ArrayList<byte[]>();
            
            Set<URL> systemResources = null;
            if (!USE_BOOTSTRAP_CLASSLOADER) {
            	systemResources = new HashSet<URL>();
            	Enumeration<URL> e = PSEUDO_BOOTSTRAP.getResources(name);
            	while (e.hasMoreElements()) {
            		systemResources.add(e.nextElement());
            	}
            }

            Enumeration<URL> e = cl.getResources(name);
            while(e.hasMoreElements()) {
            	URL url = e.nextElement();
            	if (systemResources == null || !systemResources.contains(url)) {
            		images.add(readFully(url.openStream()));
            	}
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

        /**
         * Since bootstrap classloader by itself doesn't have the {@link ClassLoader} object
         * representing it (a crazy design, really), accessing it is unnecessarily hard.
         *
         * <p>
         * So we create a child classloader that delegates directly to the bootstrap, without adding
         * any new classpath. In this way, we can effectively use this classloader as a representation
         * of the bootstrap classloader.
         */
        private static final ClassLoader PSEUDO_BOOTSTRAP = new URLClassLoader(new URL[0],null);
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

        public ClassFile fetch2(String className) throws ClassNotFoundException {
            return proxy.fetch2(className);
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

    /**
     * If set to true, classes loaded by the bootstrap classloader will be also remoted to the remote JVM.
     * By default, classes that belong to the bootstrap classloader will NOT be remoted, as each JVM gets its own JRE
     * and their versions can be potentially different.
     */
    public static boolean USE_BOOTSTRAP_CLASSLOADER = Boolean.getBoolean(RemoteClassLoader.class.getName() + ".useBootstrapClassLoader");
}
