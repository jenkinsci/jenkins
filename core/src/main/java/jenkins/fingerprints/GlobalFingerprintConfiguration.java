/*
 * The MIT License
 *
 * Copyright (c) 2020, Jenkins project contributors
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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Allows configuring the settings of fingerprints.
 */
@Extension
@Symbol("fingerprints")
public class GlobalFingerprintConfiguration extends GlobalConfiguration {

    private FingerprintStorage storage = ExtensionList.lookupSingleton(FileFingerprintStorage.class);
    private static final Logger LOGGER = Logger.getLogger(GlobalFingerprintConfiguration.class.getName());
    private boolean fingerprintCleanupDisabled;

    public GlobalFingerprintConfiguration() {
        load();
    }

    public static GlobalFingerprintConfiguration get() {
        return ExtensionList.lookupSingleton(GlobalFingerprintConfiguration.class);
    }

    public FingerprintStorage getStorage() {
        return storage;
    }

    @DataBoundSetter
    public void setStorage(FingerprintStorage fingerprintStorage) {
        this.storage = fingerprintStorage;
        LOGGER.fine("Fingerprint Storage for the system changed to " +
                fingerprintStorage.getDescriptor().getDisplayName());
    }

    public boolean isFingerprintCleanupDisabled() {
        return fingerprintCleanupDisabled;
    }

    @DataBoundSetter
    public void setFingerprintCleanupDisabled(boolean fingerprintCleanupDisabled) {
        this.fingerprintCleanupDisabled = fingerprintCleanupDisabled;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public DescriptorExtensionList<FingerprintStorage, FingerprintStorageDescriptor> getFingerprintStorageDescriptors() {
        return FingerprintStorageDescriptor.all();
    }

}
