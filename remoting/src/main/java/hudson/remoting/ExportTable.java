package hudson.remoting;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages unique ID for exported objects, and allows look-up from IDs.
 *
 * @author Kohsuke Kawaguchi
 */
final class ExportTable<T> {
    private final Map<Integer,T> table = new HashMap<Integer,T>();
    private final Map<T,Integer> reverse = new HashMap<T,Integer>();

    /**
     * Unique ID generator.
     */
    private int iota = 1;

    /**
     * Exports the given object.
     *
     * <p>
     * Until the object is {@link #unexport(Object) unexported}, it will
     * not be subject to GC.
     *
     * @return
     *      The assigned 'object ID'. If the object is already exported,
     *      it will return the ID already assigned to it.
     */
    // TODO: the 'intern' semantics requires reference counting for proper unexport op.
    public synchronized int export(T t) {
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

    /**
     * Removes the exported object from the table.
     */
    public synchronized void unexport(T t) {
        if(t==null)     return;
        Integer id = reverse.remove(t);
        if(id==null)    return; // presumably already unexported
        table.remove(id);
    }
}
