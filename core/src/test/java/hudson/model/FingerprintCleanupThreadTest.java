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

import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintCleanupThreadTest {

    private static final Fingerprint.BuildPtr ptr = new Fingerprint.BuildPtr("fred", 23);
    private Path tempDirectory;
    private Path fpFile;

    @Test
    public void testDoesNotLogUnimportantExcessiveLogMessage() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFingerprintCleanupThread(new TestFingerprint(true));
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertFalse("Should not have logged unimportant, excessive message.", logOutput.contains("possibly trimming"));
    }

    @Test
    public void testFingerprintFileIsEmpty() throws IOException {
        createFolderStructure();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFingerprintCleanupThread(new TestFingerprint(false));
        cleanupThread.execute(testTaskListener);
        String logOutput = testTaskListener.outputStream.toString();
        assertFalse("Should have deleted obsolete file.", fpFile.toFile().exists());
    }

    @Test
    public void testGetRecurencePeriod() throws IOException {
        FingerprintCleanupThread cleanupThread = new TestFingerprintCleanupThread(new TestFingerprint());
        assertEquals("Wrong recurrence period.", PeriodicWork.DAY, cleanupThread.getRecurrencePeriod());
    }

    @Test
    public void testNoFingerprintsDir() throws IOException {
        createTestDir();
        TestTaskListener testTaskListener = new TestTaskListener();
        FingerprintCleanupThread cleanupThread = new TestFingerprintCleanupThread(new TestFingerprint());
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

    private void createFolderStructure() throws IOException {
        createTestDir();
        Path fingerprintsPath = tempDirectory.resolve(FingerprintCleanupThread.FINGERPRINTS_DIR_NAME);
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

    private class TestTaskListener implements TaskListener {

        private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private PrintStream logStream = new PrintStream(outputStream);

        @Nonnull
        @Override
        public PrintStream getLogger() {
            return logStream;
        }

    }

    private class TestFingerprintCleanupThread extends FingerprintCleanupThread {

        private Fingerprint fingerprintToLoad;

        public TestFingerprintCleanupThread(Fingerprint fingerprintToLoad) throws IOException {
            this.fingerprintToLoad = fingerprintToLoad;
            return;
        }

        @Override
        protected Fingerprint getFingerprint(Fingerprint fp) throws IOException {
            return new Fingerprint(ptr, "file", new byte[0]);
        }

        @Override
        protected File getRootDir() {
            return tempDirectory.toFile();
        }

        @Override
        protected Fingerprint loadFingerprint(File fingerprintFile) throws IOException {
            return fingerprintToLoad;
        }

    }

    private class TestFingerprint extends Fingerprint {

        private boolean isAlive = true;

        public TestFingerprint() throws IOException {
            super(ptr, "fred", new byte[0]);
        }

        public TestFingerprint(boolean isAlive) throws IOException {
            super(ptr, "fred", new byte[0]);
            this.isAlive = isAlive;
        }

        @Override
        public synchronized boolean isAlive() {
            return isAlive;
        }
    }

    private class TestFingerprintCleanupThreadThrowsExceptionOnLoad extends TestFingerprintCleanupThread {

        public TestFingerprintCleanupThreadThrowsExceptionOnLoad(Fingerprint fingerprintToLoad) throws IOException {
            super(fingerprintToLoad);
        }

        @Override
        protected Fingerprint loadFingerprint(File fingerprintFile) throws IOException {
            throw new IOException("Test exception");
        }
    }
}
