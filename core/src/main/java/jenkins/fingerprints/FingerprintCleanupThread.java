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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Fingerprint;
import hudson.model.TaskListener;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Symbol("fingerprintCleanup")
@Restricted(NoExternalUse.class)
public class FingerprintCleanupThread extends AsyncPeriodicWork implements ExtensionPoint {

    public FingerprintCleanupThread() {
        super("Fingerprint cleanup");
    }

    public long getRecurrencePeriod() {
        return DAY;
    }

    public static void invoke() {
        getInstance().run();
    }

    private static FingerprintCleanupThread getInstance() {
        return ExtensionList.lookup(AsyncPeriodicWork.class).get(FingerprintCleanupThread.class);
    }

    public void execute(TaskListener taskListener) {
        List<String> fingerprintIds = FingerprintStorage.get().getAllFingerprintIds();
        for (String id : fingerprintIds) {
            try {
                Fingerprint fingerprint = Fingerprint.load(id);
                if (fingerprint != null) {
                    cleanFingerprint(fingerprint,taskListener);
                    fingerprint.save();
                }
            } catch (IOException e) {
                Functions.printStackTrace(e, taskListener.error("Failed to process " + id));
            }
        }

    }

    public static boolean cleanFingerprint(@NonNull Fingerprint fingerprint, TaskListener taskListener) {
        try {
            if (!fingerprint.isAlive() && fingerprint.getFacetBlockingDeletion() == null) {
                taskListener.getLogger().println("deleting obsolete " + fingerprint.toString());
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

    protected static Fingerprint getFingerprint(Fingerprint fp) throws IOException {
        return Jenkins.get()._getFingerprint(fp.getHashString());
    }

}
