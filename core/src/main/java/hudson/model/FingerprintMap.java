package hudson.model;

import hudson.Util;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of {@link Fingerprint}s.
 *
 * <p>
 * This implementation makes sure that no two {@link Fingerprint} objects
 * lie around for the same hash code, and that unused {@link Fingerprint}
 * will be adequately GC-ed to prevent memory leak.
 *
 * @author Kohsuke Kawaguchi
 */
public final class FingerprintMap {
    /**
     * The value is either {@code WeakReference<Fingerprint>} or {@link Loading}.
     *
     * If it's {@link WeakReference}, that represents the currently available value.
     * If it's {@link Loading}, then that indicates the fingerprint is being loaded.
     * The thread can wait on this object to be notified when the loading completes.
     */
    private final ConcurrentHashMap<String,Object> core = new ConcurrentHashMap<String,Object>();

    private static class Loading {
        private Fingerprint value;
        private boolean set;

        public synchronized void set(Fingerprint value) {
            this.set = true;
            this.value = value;
            notifyAll();
        }

        /**
         * Blocks until the value is {@link #set(Fingerprint)} by another thread
         * and returns the value.
         */
        public synchronized Fingerprint get() {
            try {
                while(!set)
                    wait();
                return value;
            } catch (InterruptedException e) {
                // assume the loading failed, but make sure we process interruption properly later
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * Returns true if there's some data in the fingerprint database.
     */
    public boolean isReady() {
        return new File( Hudson.getInstance().getRootDir(),"fingerprints").exists();
    }

    /**
     * @param build
     *      set to non-null if {@link Fingerprint} to be created (if so)
     *      will have this build as the owner. Otherwise null, to indicate
     *      an owner-less build.
     */
    public Fingerprint getOrCreate(AbstractBuild build, String fileName, byte[] md5sum) throws IOException {
        return getOrCreate(build,fileName, Util.toHexString(md5sum));
    }

    public Fingerprint getOrCreate(AbstractBuild build, String fileName, String md5sum) throws IOException {
        assert build!=null;
        assert fileName!=null;
        Fingerprint fp = get(md5sum);
        if(fp!=null)
            return fp;  // found it.

        // not found. need to create one.
        // creates a new one.
        // since it's nearly impossible for two different files to have the same md5 sum,
        // this part is not synchronized.
        fp = new Fingerprint(build,fileName,toByteArray(md5sum));

        core.put(md5sum,new WeakReference<Fingerprint>(fp));

        return fp;
    }

    public synchronized Fingerprint get(String md5sum) throws IOException {
        if(md5sum.length()!=32)
            return null;    // illegal input
        md5sum = md5sum.toLowerCase();

        while(true) {
            Object value = core.get(md5sum);

            if(value instanceof WeakReference) {
                WeakReference<Fingerprint> wfp = (WeakReference<Fingerprint>) value;
                Fingerprint fp = wfp.get();
                if(fp!=null)
                    return fp;  // found it
            }
            if(value instanceof Loading) {
                // another thread is loading it. get the value from there.
                return ((Loading)value).get();
            }

            // the fingerprint doesn't seem to be loaded thus far, so let's load it now.
            // the care needs to be taken that other threads might be trying to do the same.
            Loading l = new Loading();
            if(!core.replace(md5sum,value,l)) {
                // the value has changed since then. another thread is attempting to do the same.
                // go back to square 1 and try it again.
                continue;
            }

            Fingerprint fp = Fingerprint.load(toByteArray(md5sum));
            // let other threads know that the value is available now
            l.set(fp);

            // the map needs to be updated to reflect the result of loading
            if(fp!=null)
                core.put(md5sum,new WeakReference<Fingerprint>(fp));
            else
                core.put(md5sum,null);

            return fp;
        }

    }

    private byte[] toByteArray(String md5sum) {
        byte[] data = new byte[16];
        for( int i=0; i<md5sum.length(); i+=2 )
            data[i/2] = (byte)Integer.parseInt(md5sum.substring(i,i+2),16);
        return data;
    }

}
