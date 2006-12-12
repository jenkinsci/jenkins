package hudson.remoting;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages unique ID for exported objects.
 *
 * @author Kohsuke Kawaguchi
 */
final class ExportTable<T> {
    private final Map<Integer,T> table = new HashMap<Integer,T>();
    private final Map<T,Integer> reverse = new HashMap<T,Integer>();

    // id==0 is reserved for bootstrap classloader
    private int iota = 1;

    public synchronized int intern(T t) {
        if(t==null)    return 0;   // bootstrap classloader

        Integer id = reverse.get(t);
        if(id==null) {
            id = iota++;
            table.put(id,t);
            reverse.put(t,id);
        }

        return id;
    }

    public synchronized T get(int id) {
        return table.get(id);
    }

    public synchronized void unexport(T t) {
        if(t==null)     return;
        Integer id = reverse.remove(t);
        if(id==null)    return; // presumably already unexported
        table.remove(id);
    }
}
