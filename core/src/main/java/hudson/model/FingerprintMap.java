/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import hudson.Util;
import hudson.util.KeyedDataStorage;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Cache of {@link Fingerprint}s.
 *
 * <p>
 * This implementation makes sure that no two {@link Fingerprint} objects
 * lie around for the same hash code, and that unused {@link Fingerprint}
 * will be adequately GC-ed to prevent memory leak.
 *
 * @author Kohsuke Kawaguchi
 * @see Jenkins#getFingerprintMap()
 */
public final class FingerprintMap extends KeyedDataStorage<Fingerprint,FingerprintMap.FingerprintParams> {

    /**
     * Returns true if there's some data in the fingerprint database.
     */
    public boolean isReady() {
        return new File(Jenkins.getInstance().getRootDir(),"fingerprints").exists();
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
        return super.getOrCreate(md5sum, new FingerprintParams(build,fileName));
    }

    public Fingerprint getOrCreate(Run build, String fileName, String md5sum) throws IOException {
        return super.getOrCreate(md5sum, new FingerprintParams(build,fileName));
    }

    @Override
    protected Fingerprint get(String md5sum, boolean createIfNotExist, FingerprintParams createParams) throws IOException {
        // sanity check
        if(md5sum.length()!=32)
            return null;    // illegal input
        md5sum = md5sum.toLowerCase(Locale.ENGLISH);

        return super.get(md5sum,createIfNotExist,createParams);
    }

    private byte[] toByteArray(String md5sum) {
        byte[] data = new byte[16];
        for( int i=0; i<md5sum.length(); i+=2 )
            data[i/2] = (byte)Integer.parseInt(md5sum.substring(i,i+2),16);
        return data;
    }

    protected Fingerprint create(String md5sum, FingerprintParams createParams) throws IOException {
        return new Fingerprint(createParams.build, createParams.fileName, toByteArray(md5sum));
    }

    protected Fingerprint load(String key) throws IOException {
        return Fingerprint.load(toByteArray(key));
    }

static class FingerprintParams {
    /**
     * Null if the build isn't claiming to be the owner.
     */
    final Run build;
    final String fileName;

    public FingerprintParams(Run build, String fileName) {
        this.build = build;
        this.fileName = fileName;

        assert fileName!=null;
    }
}
}
