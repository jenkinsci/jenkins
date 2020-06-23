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

import hudson.Functions;
import hudson.model.Fingerprint;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class FileFingerprintCleanupThread extends FingerprintCleanupThread{

    static final String FINGERPRINTS_DIR_NAME = "fingerprints";
    private static final Pattern FINGERPRINT_FILE_PATTERN = Pattern.compile("[0-9a-f]{28}\\.xml");
    private static final Logger LOGGER = Logger.getLogger(hudson.model.FingerprintCleanupThread.class.getName());

    @Override
    public void execute(TaskListener listener) {
        Object fingerprintStorage = FingerprintStorage.get();
        if (!(fingerprintStorage instanceof FileFingerprintStorage)) {
            LOGGER.fine("External fingerprint storage is configured. Skipping execution");
            return;
        }

        int numFiles = 0;

        File root = new File(getRootDir(), FINGERPRINTS_DIR_NAME);
        File[] files1 = root.listFiles(f -> f.isDirectory() && f.getName().length()==2);
        if(files1!=null) {
            for (File file1 : files1) {
                File[] files2 = file1.listFiles(f -> f.isDirectory() && f.getName().length()==2);
                for(File file2 : files2) {
                    File[] files3 = file2.listFiles(f -> f.isFile() && FINGERPRINT_FILE_PATTERN.matcher(f.getName()).matches());
                    for(File file3 : files3) {
                        Fingerprint fingerprint = loadFingerprint(file3, listener);
                        if(fingerprint != null && FingerprintCleanupThread.cleanFingerprint(fingerprint, listener))
                            numFiles++;
                    }
                    deleteIfEmpty(file2);
                }
                deleteIfEmpty(file1);
            }
        }

        listener.getLogger().println("Cleaned up "+numFiles+" records");
    }

    /**
     * Deletes a directory if it's empty.
     */
    private void deleteIfEmpty(File dir) {
        String[] r = dir.list();
        if(r==null)     return; // can happen in a rare occasion
        if(r.length==0)
            dir.delete();
    }

    protected Fingerprint loadFingerprint(File fingerprintFile, TaskListener taskListener) {
        try {
            return FileFingerprintStorage.load(fingerprintFile);
        } catch (IOException e) {
            Functions.printStackTrace(e, taskListener.error("Failed to process " + fingerprintFile));
            return null;
        }
    }

    protected File getRootDir() {
        return Jenkins.get().getRootDir();
    }

}
