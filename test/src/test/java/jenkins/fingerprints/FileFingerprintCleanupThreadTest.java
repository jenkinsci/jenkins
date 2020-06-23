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
package jenkins.fingerprints;

import hudson.model.Fingerprint;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jenkins.model.FingerprintFacet;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

public class FileFingerprintCleanupThreadTest {

    private static final Fingerprint.BuildPtr ptr = new Fingerprint.BuildPtr("fred", 23);
    private static final long DAY = 24*60*1000*60;
    private Path tempDirectory;
    private Path fpFile;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testDoesNotLogUnimportantExcessiveLogMessage() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFileFingerprintCleanupThread(new TestFingerprint(true));
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertFalse("Should not have logged unimportant, excessive message.", logOutput.contains("possibly trimming"));
    }

    @Test
    public void testFingerprintFileIsEmpty() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFileFingerprintCleanupThread(new TestFingerprint(false));
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertFalse("Should have deleted obsolete file.", fpFile.toFile().exists());
    }

    @Test
    public void testGetRecurrencePeriod() throws IOException {
        FingerprintCleanupThread cleanupThread = new TestFileFingerprintCleanupThread(new TestFingerprint());
        Assert.assertEquals("Wrong recurrence period.", DAY, cleanupThread.getRecurrencePeriod());
    }

    @Test
    public void testNoFingerprintsDir() throws IOException {
        createTestDir();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFileFingerprintCleanupThread(new TestFingerprint());
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertTrue("Should have done nothing.", logOutput.startsWith("Cleaned up 0 records"));
    }

    @Test
    public void testIOExceptionOnLoad() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFingerprintCleanupThreadThrowsExceptionOnLoad(new TestFingerprint());
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertTrue("Should have logged IOException.", logOutput.contains("ERROR: Failed to process"));
    }

    @Test
    public void testBlockingFacetBlocksDeletion() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        Fingerprint fp = new TestFingerprint(false);
        fp.getPersistedFacets().setOwner(Saveable.NOOP);
        TestFingperprintFacet facet = new TestFingperprintFacet(fp, System.currentTimeMillis(), true);
        fp.getPersistedFacets().add(facet);
        FingerprintCleanupThread cleanupThread = new TestFileFingerprintCleanupThread(fp);
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertThat(logOutput, containsString("blocked deletion of"));
    }

    @Test
    public void testUnblockedFacetsDontBlockDeletion() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        Fingerprint fp = new TestFingerprint(false);
        fp.getPersistedFacets().setOwner(Saveable.NOOP);
        TestFingperprintFacet facet = new TestFingperprintFacet(fp, System.currentTimeMillis(), false);
        fp.getPersistedFacets().add(facet);
        FingerprintCleanupThread cleanupThread = new TestFileFingerprintCleanupThread(fp);
        cleanupThread.execute(testTaskListener);
        assertThat(fpFile.toFile(), is(not(aReadableFile())));
    }

    private void createFolderStructure() throws IOException {
        createTestDir();
        Path fingerprintsPath = tempDirectory.resolve(FileFingerprintCleanupThread.FINGERPRINTS_DIR_NAME);
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

        private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private PrintStream logStream = new PrintStream(outputStream);

        @NonNull
        @Override
        public PrintStream getLogger() {
            return logStream;
        }

    }

    private class TestFileFingerprintCleanupThread extends FileFingerprintCleanupThread {

        private Fingerprint fingerprintToLoad;

        public TestFileFingerprintCleanupThread(Fingerprint fingerprintToLoad) throws IOException {
            this.fingerprintToLoad = fingerprintToLoad;
        }

        @Override
        protected Fingerprint getFingerprint(Fingerprint fp) throws IOException {
            return new Fingerprint(null, "file", new byte[0]);
        }

        @Override
        protected File getRootDir() {
            return tempDirectory.toFile();
        }

        @Override
        protected Fingerprint loadFingerprint(File fingerprintFile, TaskListener taskListener) {
            return fingerprintToLoad;
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

        public TestFingerprint() throws IOException {
            super(null, "fred", new byte[0]);
        }

        public TestFingerprint(boolean isAlive) throws IOException {
            super(null, "fred", new byte[0]);
            this.isAlive = isAlive;
        }

        @Override
        public synchronized boolean isAlive() {
            return isAlive;
        }
    }

    private class TestFingerprintCleanupThreadThrowsExceptionOnLoad extends TestFileFingerprintCleanupThread {

        public TestFingerprintCleanupThreadThrowsExceptionOnLoad(Fingerprint fingerprintToLoad) throws IOException {
            super(fingerprintToLoad);
        }

        @Override
        protected Fingerprint loadFingerprint(File fingerprintFile) {
            throw new IOException("Test exception");
        }
    }
}
