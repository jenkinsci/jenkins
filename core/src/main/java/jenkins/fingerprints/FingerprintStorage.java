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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Fingerprint;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Date;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Pluggable fingerprint storage API for fingerprints.
 *
 * @author Sumit Sarin
 */
@Restricted(Beta.class)
public abstract class FingerprintStorage implements Describable<FingerprintStorage>, ExtensionPoint {

    /**
     * Returns the configured {@link FingerprintStorage} engine chosen by the user for the system.
     */
    public static FingerprintStorage get() {
        return ExtensionList.lookupSingleton(GlobalFingerprintConfiguration.class).getStorage();
    }

    /**
     * Returns the file system based {@link FileFingerprintStorage} configured on the system.
     * @deprecated since 2.324, use {@code ExtensionList.lookupSingleton(FileFingerprintStorage.class)} instead.
     */
    @Deprecated
    public static FingerprintStorage getFileFingerprintStorage() {
        return ExtensionList.lookupSingleton(FileFingerprintStorage.class);
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

    /**
     * Iterates a set of fingerprints, and cleans them up. Cleaning up a fingerprint implies deleting the builds
     * associated with the fingerprints, once they are no longer available on the system. If all the builds have been
     * deleted, the fingerprint itself is deleted.
     *
     * This method is called periodically by {@link hudson.model.FingerprintCleanupThread}.
     * For reference, see {@link FileFingerprintStorage#iterateAndCleanupFingerprints(TaskListener)}
     * For cleaning up the fingerprint {@link #cleanFingerprint(Fingerprint, TaskListener)} may be used.
     *
     * @since 2.246
     */
    public abstract void iterateAndCleanupFingerprints(TaskListener taskListener);

    /**
     * This method performs the cleanup of the given fingerprint.
     *
     * @since 2.246
     */
    public boolean cleanFingerprint(@NonNull Fingerprint fingerprint, TaskListener taskListener) {
        try {
            if (!fingerprint.isAlive() && fingerprint.getFacetBlockingDeletion() == null) {
                taskListener.getLogger().println("deleting obsolete " + fingerprint);
                Fingerprint.delete(fingerprint.getHashString());
                return true;
            } else {
                if (!fingerprint.isAlive()) {
                    FingerprintFacet deletionBlockerFacet = fingerprint.getFacetBlockingDeletion();
                    taskListener.getLogger().println(deletionBlockerFacet.getClass().getName() + " created on " +
                            new Date(deletionBlockerFacet.getTimestamp()) + " blocked deletion of " +
                            fingerprint.getHashString());
                }
                // get the fingerprint in the official map so have the changes visible to Jenkins
                // otherwise the mutation made in FingerprintMap can override our trimming.
                fingerprint = getFingerprint(fingerprint);
                return fingerprint.trim();
            }
        } catch (IOException e) {
            Functions.printStackTrace(e, taskListener.error("Failed to process " + fingerprint.getHashString()));
            return false;
        }
    }

    protected Fingerprint getFingerprint(Fingerprint fp) throws IOException {
        return Jenkins.get()._getFingerprint(fp.getHashString());
    }

    /**
     * @since 2.246
     */
    @Override public FingerprintStorageDescriptor getDescriptor() {
        return (FingerprintStorageDescriptor) Describable.super.getDescriptor();

    }

}
