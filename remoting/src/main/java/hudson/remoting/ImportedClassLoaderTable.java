package hudson.remoting;

import java.util.Hashtable;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
final class ImportedClassLoaderTable {
    final Channel channel;
    final Map<Integer,ClassLoader> classLoaders = new Hashtable<Integer,ClassLoader>();

    ImportedClassLoaderTable(Channel channel) {
        this.channel = channel;
    }

    public synchronized ClassLoader get(int id) {
        ClassLoader r = classLoaders.get(id);
        if(r==null) {
            // we need to be able to use the same hudson.remoting classes, hence delegate
            // to this class loader.
            r = new RemoteClassLoader(getClass().getClassLoader(),channel,id);
            classLoaders.put(id,r);
        }
        return r;
    }
}
