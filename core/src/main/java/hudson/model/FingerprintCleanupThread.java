/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.fingerprints.FileFingerprintStorage;
import jenkins.fingerprints.FingerprintStorage;
import jenkins.fingerprints.GlobalFingerprintConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.logging.Logger;

/**
 * Scans the fingerprint database and remove old records
 * that are no longer relevant.
 *
 * <p>
 * A {@link Fingerprint} is removed when none of the builds that
 * it point to is available in the records.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("fingerprintCleanup")
@Restricted(NoExternalUse.class)
public class FingerprintCleanupThread extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(FingerprintCleanupThread.class.getName());

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

    /**
     * Initiates the cleanup of fingerprints IF enabled.
     * In case of configured external storage, the file system based storage cleanup is also performed.
     */
    public void execute(TaskListener listener) {
        if (GlobalFingerprintConfiguration.get().isFingerprintCleanupDisabled()) {
            LOGGER.fine("Fingerprint cleanup is disabled. Skipping execution");
            return;
        }
        FingerprintStorage.get().iterateAndCleanupFingerprints(listener);

        if (!(FingerprintStorage.get() instanceof FileFingerprintStorage) &&
                FingerprintStorage.getFileFingerprintStorage().isReady()) {
            FileFingerprintStorage.getFileFingerprintStorage().iterateAndCleanupFingerprints(listener);
        }
    }

}
