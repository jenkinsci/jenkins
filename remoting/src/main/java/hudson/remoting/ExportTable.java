package hudson.remoting;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages unique ID for exported objects, and allows look-up from IDs.
 *
 * @author Kohsuke Kawaguchi
 */
final class ExportTable<T> {
    private final Map<Integer,Entry<T>> table = new HashMap<Integer,Entry<T>>();
    private final Map<T,Entry<T>> reverse = new HashMap<T,Entry<T>>();

    /**
     * Information about one exporetd object.
     */
    private static final class Entry<T> {
        final int id;
        final T object;
        /**
         * Where was this object first exported?
         */
        final Exception allocationTrace;
        /**
         * Current reference count.
         * Access to {@link ExportTable} is guarded by synchronized block,
         * so accessing this field requires no further synchronization.
         */
        private int referenceCount;

        Entry(ExportTable<T> parent, T object) {
            this.id = parent.iota++;
            this.object = object;
            this.allocationTrace = new Exception();
            allocationTrace.fillInStackTrace();
            addRef();

            parent.table.put(id,this);
            parent.reverse.put(object,this);
        }

        void addRef() {
            referenceCount++;
        }

        boolean release() {
            return --referenceCount==0;
        }
    }

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
    public synchronized int export(T t) {
        if(t==null)    return 0;   // bootstrap classloader

        Entry<T> e = reverse.get(t);
        if(e==null)
            e = new Entry<T>(this,t);
        else
            e.addRef();

        return e.id;
    }

    public synchronized T get(int id) {
        Entry<T> e = table.get(id);
        if(e!=null) return e.object;
        else        return null;
    }

    /**
     * Removes the exported object from the table.
     */
    public synchronized void unexport(T t) {
        if(t==null)     return;
        Entry<T> e = reverse.get(t);
        if(e==null)    return; // presumably already unexported
        if(e.release()) {
            table.remove(e.id);
            reverse.remove(e.object);
        }
    }

    /**
     * Dumps the contents of the table to a file.
     */
    public synchronized void dump(PrintWriter w) throws IOException {
        for (Entry<T> e : table.values()) {
            w.printf("#%d (ref.%d) : %s\n", e.id, e.referenceCount, e.object);
            e.allocationTrace.printStackTrace(w);
        }
    }
}
