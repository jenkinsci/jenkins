package hudson.remoting;

import hudson.remoting.RemoteClassLoader.IClassLoader;

import java.util.Hashtable;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
final class ImportedClassLoaderTable {
    final Channel channel;
    final Map<IClassLoader,ClassLoader> classLoaders = new Hashtable<IClassLoader,ClassLoader>();

    ImportedClassLoaderTable(Channel channel) {
        this.channel = channel;
    }

    public synchronized ClassLoader get(int oid) {
        return get(RemoteInvocationHandler.wrap(channel,oid,IClassLoader.class,false));
    }

    public synchronized ClassLoader get(IClassLoader classLoaderProxy) {
        ClassLoader r = classLoaders.get(classLoaderProxy);
        if(r==null) {
            // we need to be able to use the same hudson.remoting classes, hence delegate
            // to this class loader.
            r = RemoteClassLoader.create(getClass().getClassLoader(),classLoaderProxy);
            classLoaders.put(classLoaderProxy,r);
        }
        return r;
    }
}
