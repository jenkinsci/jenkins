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
import hudson.Functions;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

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

    static final String FINGERPRINTS_DIR_NAME = "fingerprints";
    private static final Pattern FINGERPRINT_FILE_PATTERN = Pattern.compile("[0-9a-f]{28}\\.xml");

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

    public void execute(TaskListener listener) {
        int numFiles = 0;

        File root = new File(getRootDir(), FINGERPRINTS_DIR_NAME);
        File[] files1 = root.listFiles(f -> f.isDirectory() && f.getName().length()==2);
        if(files1!=null) {
            for (File file1 : files1) {
                File[] files2 = file1.listFiles(f -> f.isDirectory() && f.getName().length()==2);
                for(File file2 : files2) {
                    File[] files3 = file2.listFiles(f -> f.isFile() && FINGERPRINT_FILE_PATTERN.matcher(f.getName()).matches());
                    for(File file3 : files3) {
                        if(check(file3, listener))
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

    /**
     * Examines the file and returns true if a file was deleted.
     */
    private boolean check(File fingerprintFile, TaskListener listener) {
        try {
            Fingerprint fp = loadFingerprint(fingerprintFile);
            if (fp == null || !fp.isAlive()) {
                listener.getLogger().println("deleting obsolete " + fingerprintFile);
                fingerprintFile.delete();
                return true;
            } else {
                // get the fingerprint in the official map so have the changes visible to Jenkins
                // otherwise the mutation made in FingerprintMap can override our trimming.
                fp = getFingerprint(fp);
                return fp.trim();
            }
        } catch (IOException e) {
            Functions.printStackTrace(e, listener.error("Failed to process " + fingerprintFile));
            return false;
        }
    }

    protected Fingerprint loadFingerprint(File fingerprintFile) throws IOException {
        return Fingerprint.load(fingerprintFile);
    }

    protected Fingerprint getFingerprint(Fingerprint fp) throws IOException {
        return Jenkins.get()._getFingerprint(fp.getHashString());
    }

    protected File getRootDir() {
        return Jenkins.get().getRootDir();
    }

}
