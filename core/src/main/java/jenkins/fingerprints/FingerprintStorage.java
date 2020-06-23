/*
 * The MIT License
 *
 * Copyright (c) 2020, Sumit Sarin and Jenkins project contributors
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
package jenkins.fingerprints;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.io.IOException;
import java.util.List;

import hudson.model.Fingerprint;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.Restricted;

/**
 * Pluggable fingerprint storage API for fingerprints.
 *
 * @author Sumit Sarin
 */
@Restricted(Beta.class)
public abstract class FingerprintStorage implements ExtensionPoint {

    /**
     * Returns the first implementation of FingerprintStorage for the instance.
     * External storage plugins which implement FingerprintStorage are given a higher priority.
     */
    public static FingerprintStorage get() {
        return ExtensionList.lookup(FingerprintStorage.class).get(0);
    }

    /**
     * Saves the given Fingerprint in the storage.
     * This acts as a blocking operation. For file system based default storage, throws IOException when it fails.
     *
     * @throws IOException Save error
     */
    public abstract void save(Fingerprint fp) throws IOException;

    /**
     * Returns the Fingerprint with the given unique ID.
     * The unique ID for a fingerprint is defined by {@link Fingerprint#getHashString()}.
     *
     * @throws IOException Load error
     */
    public abstract @CheckForNull Fingerprint load(String id) throws IOException;

    /**
     * Deletes the Fingerprint with the given unique ID.
     * This acts as a blocking operation. For file system based default storage, throws IOException when it fails.
     * The unique ID for a fingerprint is defined by {@link Fingerprint#getHashString()}.
     * TODO: Needed for external storage fingerprint cleanup.
     *
     * @throws IOException Deletion error
     */
    public abstract void delete(String id) throws IOException;

    /**
     * Returns true if there's some data in the fingerprint database.
     */
    public abstract boolean isReady();

}
