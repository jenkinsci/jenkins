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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.FingerprintCleanupThreadTest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FingerprintStorageTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    void testLoadingAndSavingLocalStorageFingerprintWithExternalStorage() throws IOException {
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        Fingerprint fingerprintSaved = new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        Fingerprint fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));

        // After external storage is configured, check if local storage fingerprint is still accessible.
        FingerprintStorage externalFingerprintStorage = configureExternalStorage();
        fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));

        // After loading the fingerprint, ensure it was moved to external storage.
        fingerprintLoaded = externalFingerprintStorage.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));

        // Ensure that the loaded fingerprint was deleted from local storage after being loaded.
        fingerprintLoaded = ExtensionList.lookupSingleton(FileFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    void testLoadingAndSavingFingerprintWithExternalStorage() throws IOException {
        FingerprintStorage externalFingerprintStorage = configureExternalStorage();
        String id = Util.getDigestOf("testLoadingAndSavingFingerprintWithExternalStorage");
        Fingerprint fingerprintSaved = new Fingerprint(null, "bar.jar", Util.fromHexString(id));
        Fingerprint fingerprintLoaded = externalFingerprintStorage.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));
    }

    @Test
    void testDeletingLocalStorageFingerprintWithExternalStorageBeforeMigration() throws IOException {
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        configureExternalStorage();
        Fingerprint.delete(id);
        Fingerprint fingerprintLoaded = ExtensionList.lookupSingleton(FileFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    void testDeletingLocalStorageFingerprintWithExternalStorageAfterMigration() throws IOException {
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        FingerprintStorage externalFingerprintStorage = configureExternalStorage();
        Fingerprint.load(id);
        Fingerprint.delete(id);
        Fingerprint fingerprintLoaded = externalFingerprintStorage.load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    void testDeletingFingerprintWithExternalStorage() throws IOException {
        configureExternalStorage();
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        Fingerprint.delete(id);
        Fingerprint fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    void testMigrationDeletesFingerprintsInMemoryFromFileStorage() throws IOException {
        String id = Util.getDigestOf("testMigrationDeletesFingerprintsInMemoryFromFileStorage");
        Fingerprint fingerprintSaved = new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        configureExternalStorage();
        fingerprintSaved.add("test", 3);
        // This fingerprint is now implicitly saved without making a load call.
        // We want the file storage to not have this fingerprint now.
        Fingerprint fingerprintLoaded = ExtensionList.lookupSingleton(FileFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(nullValue()));

    }

    private FingerprintStorage configureExternalStorage() {
        FingerprintStorage fingerprintStorage = new FingerprintCleanupThreadTest.TestExternalFingerprintStorage();
        GlobalFingerprintConfiguration.get().setStorage(fingerprintStorage);
        return fingerprintStorage;
    }

}
