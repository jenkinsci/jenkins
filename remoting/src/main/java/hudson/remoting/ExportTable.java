package hudson.remoting;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Manages unique ID for classloaders.
 *
 * @author Kohsuke Kawaguchi
 */
final class ExportTable<T> {
    private final Map<Integer, WeakReference<T>> table = new HashMap<Integer, WeakReference<T>>();
    private final WeakHashMap<T,Integer> reverse = new WeakHashMap<T,Integer>();

    // id==0 is reserved for bootstrap classloader
    private int iota = 1;


    public synchronized int intern(T cl) {
        if(cl==null)    return 0;   // bootstrap classloader

        Integer id = reverse.get(cl);
        if(id==null) {
            id = iota++;
            table.put(id,new WeakReference<T>(cl));
            reverse.put(cl,id);
        }

        return id;
    }

    public synchronized T get(int id) {
        WeakReference<T> ref = table.get(id);
        if(ref==null)   return null;
        return ref.get();
    }
}
