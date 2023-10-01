/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import jenkins.fingerprints.FileFingerprintStorage;
import jenkins.fingerprints.FingerprintStorage;
import jenkins.fingerprints.FingerprintStorageDescriptor;
import jenkins.fingerprints.GlobalFingerprintConfiguration;
import jenkins.model.FingerprintFacet;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class FingerprintCleanupThreadTest {

    private static final Fingerprint.BuildPtr ptr = new Fingerprint.BuildPtr("fred", 23);
    private static Path tempDirectory;
    private static Path fpFile;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testDoesNotLogUnimportantExcessiveLogMessage() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        configureLocalTestStorage(new TestFingerprint(true));
        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString(Charset.defaultCharset());
        assertFalse("Should not have logged unimportant, excessive message.", logOutput.contains("possibly trimming"));
    }

    @Test
    public void testFingerprintFileIsEmpty() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        configureLocalTestStorage(new TestFingerprint(false));
        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString(Charset.defaultCharset());
        assertFalse("Should have deleted obsolete file.", fpFile.toFile().exists());
    }

    @Test
    public void testGetRecurrencePeriod() {
        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        assertEquals("Wrong recurrence period.", PeriodicWork.DAY, cleanupThread.getRecurrencePeriod());
    }

    @Test
    public void testNoFingerprintsDir() throws IOException {
        createTestDir();
        TestTaskListener testTaskListener = new TestTaskListener();
        configureLocalTestStorage(new TestFingerprint());
        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString(Charset.defaultCharset());
        assertTrue("Should have done nothing.", logOutput.startsWith("Cleaned up 0 records"));
    }

    @Test
    public void testBlockingFacetBlocksDeletion() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        Fingerprint fp = new TestFingerprint(false);
        fp.facets.setOwner(Saveable.NOOP);
        TestFingperprintFacet facet = new TestFingperprintFacet(fp, System.currentTimeMillis(), true);
        fp.facets.add(facet);
        configureLocalTestStorage(fp);
        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString(Charset.defaultCharset());
        assertThat(logOutput, containsString("blocked deletion of"));
    }

    @Test
    public void testUnblockedFacetsDontBlockDeletion() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        Fingerprint fp = new TestFingerprint(false);
        fp.facets.setOwner(Saveable.NOOP);
        TestFingperprintFacet facet = new TestFingperprintFacet(fp, System.currentTimeMillis(), false);
        fp.facets.add(facet);
        configureLocalTestStorage(fp);
        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);
        assertThat(fpFile.toFile(), is(not(aReadableFile())));
    }

    @Test
    public void testExternalStorageCleanupWithoutLocalFingerprints() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        Fingerprint fingerprint = new TestFingerprint(false);
        configureExternalTestStorage();
        String fingerprintId = fingerprint.getHashString();

        fingerprint.save();
        assertThat(Fingerprint.load(fingerprintId), is(not(nullValue())));

        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);
        assertThat(Fingerprint.load(fingerprintId), is(nullValue()));
    }

    @Test
    public void testExternalStorageCleanupWithLocalFingerprints() throws IOException {
        TestTaskListener testTaskListener = new TestTaskListener();

        String localFingerprintId = Util.getDigestOf("local");
        new Fingerprint((Run) null, "foo.jar", Util.fromHexString(localFingerprintId));

        configureExternalTestStorage();
        String externalFingerprintId = Util.getDigestOf("local");
        new Fingerprint((Run) null, "bar.jar", Util.fromHexString(externalFingerprintId));

        assertThat(Fingerprint.load(localFingerprintId), is(not(nullValue())));
        assertThat(Fingerprint.load(externalFingerprintId), is(not(nullValue())));

        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);

        assertThat(Fingerprint.load(localFingerprintId), is(nullValue()));
        assertThat(Fingerprint.load(externalFingerprintId), is(nullValue()));
    }

    @Test
    public void shouldNotCleanFingerprintsWhenDisabled() throws IOException {
        GlobalFingerprintConfiguration.get().setFingerprintCleanupDisabled(true);

        TestTaskListener testTaskListener = new TestTaskListener();

        String localFingerprintId = Util.getDigestOf("local");
        new Fingerprint((Run) null, "foo.jar", Util.fromHexString(localFingerprintId));

        configureExternalTestStorage();
        String externalFingerprintId = Util.getDigestOf("local");
        new Fingerprint((Run) null, "bar.jar", Util.fromHexString(externalFingerprintId));

        assertThat(Fingerprint.load(localFingerprintId), is(not(nullValue())));
        assertThat(Fingerprint.load(externalFingerprintId), is(not(nullValue())));

        FingerprintCleanupThread cleanupThread = new FingerprintCleanupThread();
        cleanupThread.execute(testTaskListener);

        assertThat(Fingerprint.load(localFingerprintId), is(not(nullValue())));
        assertThat(Fingerprint.load(externalFingerprintId), is(not(nullValue())));
    }

    private void configureLocalTestStorage(Fingerprint fingerprint) {
        GlobalFingerprintConfiguration.get().setStorage(new TestFileFingerprintStorage(fingerprint));
    }

    private void configureExternalTestStorage() {
        GlobalFingerprintConfiguration.get().setStorage(new TestExternalFingerprintStorage());
    }

    private void createFolderStructure() throws IOException {
        createTestDir();
        Path fingerprintsPath = tempDirectory.resolve(FileFingerprintStorage.FINGERPRINTS_DIR_NAME);
        Files.createDirectory(fingerprintsPath);
        Path aaDir = fingerprintsPath.resolve("aa");
        Files.createDirectory(aaDir);
        Path bbDir = aaDir.resolve("bb");
        Files.createDirectory(bbDir);
        fpFile = bbDir.resolve("0123456789012345678901234567.xml");
        Files.createFile(fpFile);
    }

    private void createTestDir() throws IOException {
        tempDirectory = Files.createTempDirectory(Paths.get("target"), "fpCleanupThreadTest");
        tempDirectory.toFile().deleteOnExit();
    }

    private static class TestTaskListener implements TaskListener {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final PrintStream logStream = new PrintStream(outputStream, false, Charset.defaultCharset());

        @NonNull
        @Override
        public PrintStream getLogger() {
            return logStream;
        }

    }

    private static class TestFileFingerprintStorage extends FileFingerprintStorage {

        private Fingerprint fingerprintToLoad;

        TestFileFingerprintStorage(Fingerprint fingerprintToLoad) {
            this.fingerprintToLoad = fingerprintToLoad;
        }

        @Override
        protected Fingerprint getFingerprint(Fingerprint fp) {
            return new Fingerprint(ptr, "foo", Util.fromHexString(Util.getDigestOf("foo")));
        }

        @Override
        protected File getRootDir() {
            return tempDirectory.toFile();
        }

        @Override
        protected Fingerprint loadFingerprint(File fingerprintFile) {
            return fingerprintToLoad;
        }

        @Override
        public boolean isReady() {
            return fpFile.toFile().exists();
        }

        @Extension
        public static class DescriptorImpl extends FingerprintStorageDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return "TestFileFingerprintStorage";
            }

        }

    }

    public static final class TestFingperprintFacet extends FingerprintFacet {

        private boolean deletionBlocked;

        public TestFingperprintFacet(Fingerprint fingerprint, long timestamp, boolean deletionBlocked) {
            super(fingerprint, timestamp);
            this.deletionBlocked = deletionBlocked;
        }

        @Override public boolean isFingerprintDeletionBlocked() {
            return deletionBlocked;
        }

    }

    private static class TestFingerprint extends Fingerprint {

        private boolean isAlive = true;

        TestFingerprint() {
            super(ptr, "foo", Util.fromHexString(Util.getDigestOf("foo")));
        }

        TestFingerprint(boolean isAlive) {
            super(ptr, "foo", Util.fromHexString(Util.getDigestOf("foo")));
            this.isAlive = isAlive;
        }

        @Override
        public synchronized boolean isAlive() {
            return isAlive;
        }
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
            return !storage.isEmpty();
        }

        @Override
        public void iterateAndCleanupFingerprints(TaskListener taskListener) {
            for (Fingerprint fingerprint : storage.values()) {
                cleanFingerprint(fingerprint, taskListener);
            }
        }

        @Override
        protected Fingerprint getFingerprint(Fingerprint fp) {
            return new Fingerprint(ptr, "foo", Util.fromHexString(Util.getDigestOf("foo")));
        }

        @Extension
        public static class DescriptorImpl extends FingerprintStorageDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return "TestExternalFingerprintStorage";
            }

        }
    }
}
