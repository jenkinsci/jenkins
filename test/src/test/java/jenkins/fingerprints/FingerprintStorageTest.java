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

import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Fingerprint;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

public class FingerprintStorageTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testLoadingAndSavingLocalStorageFingerprintWithExternalStorage() throws IOException {
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        Fingerprint fingerprintSaved = new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        Fingerprint fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));

        // After external storage is configured, check if local storage fingerprint is still accessible.
        configureExternalStorage();
        fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));

        // After loading the fingerprint, ensure it was moved to external storage.
        fingerprintLoaded = ExtensionList.lookup(FingerprintStorage.class).get(TestExternalFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));

        // Ensure that the loaded fingerprint was deleted from local storage after being loaded.
        fingerprintLoaded = ExtensionList.lookup(FingerprintStorage.class).get(FileFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    public void testLoadingAndSavingFingerprintWithExternalStorage() throws IOException {
        configureExternalStorage();
        String id = Util.getDigestOf("testLoadingAndSavingFingerprintWithExternalStorage");
        Fingerprint fingerprintSaved = new Fingerprint(null, "bar.jar", Util.fromHexString(id));
        Fingerprint fingerprintLoaded = ExtensionList.lookup(FingerprintStorage.class).get(TestExternalFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(not(nullValue())));
        assertThat(fingerprintSaved.toString(), is(equalTo(fingerprintLoaded.toString())));
    }

    @Test
    public void testDeletingLocalStorageFingerprintWithExternalStorageBeforeMigration() throws IOException {
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        configureExternalStorage();
        Fingerprint.delete(id);
        Fingerprint fingerprintLoaded = ExtensionList.lookup(FingerprintStorage.class).get(FileFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    public void testDeletingLocalStorageFingerprintWithExternalStorageAfterMigration() throws IOException {
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        configureExternalStorage();
        Fingerprint.load(id);
        Fingerprint.delete(id);
        Fingerprint fingerprintLoaded = ExtensionList.lookup(FingerprintStorage.class).get(TestExternalFingerprintStorage.class).load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    @Test
    public void testDeletingFingerprintWithExternalStorage() throws IOException {
        configureExternalStorage();
        String id = Util.getDigestOf("testLoadingAndSavingLocalStorageFingerprintWithExternalStorage");
        configureExternalStorage();
        new Fingerprint(null, "foo.jar", Util.fromHexString(id));
        Fingerprint.delete(id);
        Fingerprint fingerprintLoaded = Fingerprint.load(id);
        assertThat(fingerprintLoaded, is(nullValue()));
    }

    private void configureExternalStorage() {
        ExtensionList.lookup(FingerprintStorage.class).add(0, new TestExternalFingerprintStorage());
    }

    public static class TestExternalFingerprintStorage extends FingerprintStorage {

        Map<String, Fingerprint> storage = new HashMap<>();

        @Override
        public void save(Fingerprint fp) {
            storage.put(fp.getHashString(), fp);
        }

        @Override
        public Fingerprint load(String id) {
            return storage.get(id);
        }

        @Override
        public void delete(String id) {
            storage.remove(id);
        }

        @Override
        public boolean isReady() {
            return storage.size() != 0;
        }
    }
}
