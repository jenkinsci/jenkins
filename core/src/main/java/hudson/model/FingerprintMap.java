package hudson.model;

import hudson.Util;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

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
    private final Map<String,WeakReference<Fingerprint>> core = new HashMap<String, WeakReference<Fingerprint>>();

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
    public synchronized Fingerprint getOrCreate(AbstractBuild build, String fileName, byte[] md5sum) throws IOException {
        return getOrCreate(build,fileName, Util.toHexString(md5sum));
    }

    public synchronized Fingerprint getOrCreate(AbstractBuild build, String fileName, String md5sum) throws IOException {
        assert build!=null;
        assert fileName!=null;
        Fingerprint fp = get(md5sum);
        if(fp!=null)
            return fp;  // found it.

        // not found. need to create one.
        // creates a new one
        fp = new Fingerprint(build,fileName,toByteArray(md5sum));

        core.put(md5sum,new WeakReference<Fingerprint>(fp));

        return fp;
    }

    public synchronized Fingerprint get(String md5sum) throws IOException {
        if(md5sum.length()!=32)
            return null;    // illegal input
        md5sum = md5sum.toLowerCase();

        WeakReference<Fingerprint> wfp = core.get(md5sum);
        if(wfp!=null) {
            Fingerprint fp = wfp.get();
            if(fp!=null)
                return fp;  // found it
        }

        return Fingerprint.load(toByteArray(md5sum));
    }

    private byte[] toByteArray(String md5sum) {
        byte[] data = new byte[16];
        for( int i=0; i<md5sum.length(); i+=2 )
            data[i/2] = (byte)Integer.parseInt(md5sum.substring(i,i+2),16);
        return data;
    }

}
