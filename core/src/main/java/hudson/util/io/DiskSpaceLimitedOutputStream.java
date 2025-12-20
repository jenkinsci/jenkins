/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Eric Lefevre-Ardant, Erik Ramfelt, Michael B. Donohue, Alan Harder,
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
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

package hudson.util.io;

import hudson.node_monitors.DiskSpaceMonitorDescriptor;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

public class DiskSpaceLimitedOutputStream extends OutputStream {
    private static final Logger LOGGER = Logger.getLogger(DiskSpaceLimitedOutputStream.class.getName());

    private final OutputStream out;
    private final File targetDir;
    private final long thresholdBytes;

    private static final long DEFAULT_THRESHOLD_BYTES = 1L * 1024L * 1024L * 1024L; // 1GB
    private static final String PROP = "jenkins.controller.diskspace.threshold";

    // tuning: check every 1MB or every 500ms
    private static final int CHECK_INTERVAL_BYTES = 1 << 20; // 1MB
    private static final long CHECK_INTERVAL_MS = 500L;

    private int bytesSinceLastCheck = 0;
    private long lastCheckTime = 0L;

    public DiskSpaceLimitedOutputStream(OutputStream out, File targetDir, long thresholdBytes) {
        this.out = Objects.requireNonNull(out, "out");
        this.targetDir = Objects.requireNonNull(targetDir, "targetDir");
        if (thresholdBytes < 0) throw new IllegalArgumentException("thresholdBytes must be >= 0");
        this.thresholdBytes = thresholdBytes;
    }

    /**
     * Convenience factory that creates a DiskSpaceLimitedOutputStream targeting the Jenkins controller root,
     * resolving the threshold from (in order): system property, disk-space monitor config for controller, default.
     */
    public static DiskSpaceLimitedOutputStream forController(OutputStream out) {
        long threshold = DEFAULT_THRESHOLD_BYTES;
        try {
            Jenkins j = Jenkins.get();
            if (j != null) {
                // 1) system property override (use Jenkins SystemProperties helper)
                String prop = SystemProperties.getString(PROP);
                if (prop != null && !prop.isBlank()) {
                    try {
                        threshold = DiskSpace.parse(prop).size;
                    } catch (ParseException e) {
                        LOGGER.log(Level.WARNING, "Invalid value for " + PROP + ": " + prop + ", using fallback", e);
                    }
                } else {
                    // 2) configured disk-space monitor threshold for controller (if available)
                    DiskSpaceMonitorDescriptor desc = j.getDescriptorByType(DiskSpaceMonitorDescriptor.class);
                    if (desc != null) {
                        try {
                            DiskSpace ds = desc.get(j.toComputer());
                            if (ds != null && ds.getThreshold() > 0) {
                                threshold = ds.getThreshold();
                            }
                        } catch (Exception e) {
                            // ignore and use fallback
                        }
                    }
                }
                File root = j.getRootDir();
                return new DiskSpaceLimitedOutputStream(out, root, threshold);
            }
        } catch (Throwable e) {
            LOGGER.log(Level.FINER, "Unable to resolve controller threshold, using default", e);
        }
        // Jenkins not available or error -> keep default but make fallback explicit and logged
        File fallback = new File(System.getProperty("user.dir", "."));
        LOGGER.log(Level.WARNING, "Jenkins not available; using fallback targetDir=" + fallback +
                " and threshold=" + threshold + " bytes. Consider setting " + PROP + " or ensuring DiskSpace monitor is configured.");
        return new DiskSpaceLimitedOutputStream(out, fallback, threshold);
    }

    /**
     * Central check that enforces threshold: after writing upcomingBytes, usable space should still be >= thresholdBytes.
     */
    private void checkNow(int upcomingBytes) throws IOException {
        try {
            long usable = targetDir.getUsableSpace();
            long usableAfter = usable - (long) upcomingBytes;
            if (usableAfter < thresholdBytes) {
                String msg = "Aborting write: insufficient disk space on " + targetDir +
                        " (usable=" + usable + " bytes, upcoming=" + upcomingBytes +
                        " bytes, threshold=" + thresholdBytes + " bytes)";
                LOGGER.log(Level.WARNING, msg);
                throw new IOException(msg);
            }
        } catch (SecurityException e) {
            String msg = "Unable to determine usable disk space for " + targetDir;
            LOGGER.log(Level.WARNING, msg, e);
            throw new IOException(msg, e);
        }
    }

    /**
     * Decide whether to perform a check now based on bytes/time heuristics.
     */
    private void maybeCheck(int upcomingBytes) throws IOException {
        // immediate check if a single write is large enough that it could cross the threshold
        if (upcomingBytes >= CHECK_INTERVAL_BYTES) {
            checkNow(upcomingBytes);
            // reset counters after an immediate check
            bytesSinceLastCheck = 0;
            lastCheckTime = System.currentTimeMillis();
            return;
        }

        bytesSinceLastCheck += upcomingBytes;
        long now = System.currentTimeMillis();
        if (bytesSinceLastCheck >= CHECK_INTERVAL_BYTES || (lastCheckTime == 0L) || (now - lastCheckTime) >= CHECK_INTERVAL_MS) {
            checkNow(bytesSinceLastCheck);
            bytesSinceLastCheck = 0;
            lastCheckTime = now;
        }
    }

    @Override
    public void write(int b) throws IOException {
        maybeCheck(1);
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return;
        }
        maybeCheck(len);
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
