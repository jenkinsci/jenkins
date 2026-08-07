/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

package hudson.node_monitors;

import hudson.Functions;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import jenkins.MasterToSlaveFileCallable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link AbstractNodeMonitorDescriptor} for {@link NodeMonitor} that checks a free disk space of some directory.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
*/
public abstract class DiskSpaceMonitorDescriptor extends AbstractAsyncNodeMonitorDescriptor<DiskSpace> {

    private static final Logger LOGGER = Logger.getLogger(DiskSpaceMonitorDescriptor.class.getName());

    @Override
    protected Map<Computer, DiskSpace> monitor() throws InterruptedException {
        Result<DiskSpace> base = monitorDetailed();
        Map<Computer, DiskSpace> data = base.getMonitoringData();
        AbstractDiskSpaceMonitor monitor = (AbstractDiskSpaceMonitor) ComputerSet.getMonitors().get(this);
        for (Map.Entry<Computer, DiskSpace> e : data.entrySet()) {
            Computer c = e.getKey();
            DiskSpace d = e.getValue();
            if (base.getSkipped().contains(c)) {
                assert d == null;
                continue;
            }
            if (d == null) {
                e.setValue(d = get(c));
            }
            markNodeOfflineOrOnline(c, d, monitor);
        }
        return data;
    }

    @Restricted(NoExternalUse.class)
    public void markNodeOfflineOrOnline(Computer c, DiskSpace size, AbstractDiskSpaceMonitor monitor) {
        if (size != null) {
            long threshold = monitor.getThresholdBytes(c);
            size.setThreshold(threshold);
            long warningThreshold = monitor.getWarningThresholdBytes(c);
            size.setWarningThreshold(warningThreshold);
            if (size.size <= threshold) {
                size.setTrigger(monitor.getClass());
                if (markOffline(c, size)) {
                    LOGGER.warning(Messages.DiskSpaceMonitor_MarkedOffline(c.getDisplayName()));
                }
            }
            if (size.size > threshold) {
                if (c.isOffline() && c.getOfflineCause() instanceof DiskSpace) {
                    if (monitor.getClass().equals(((DiskSpace) c.getOfflineCause()).getTrigger())) {
                        if (markOnline(c)) {
                            LOGGER.info(Messages.DiskSpaceMonitor_MarkedOnline(c.getDisplayName()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Value object that represents the disk space.
     */
    @ExportedBean
    public static final class DiskSpace extends MonitorOfflineCause implements Serializable {
        private final String path;
        @Exported
        public final long size;

        private long totalSize;

        private Class<? extends AbstractDiskSpaceMonitor> trigger;
        private long threshold;
        private long warningThreshold;

        /**
         * @param path
         *      Specify the file path that was monitored.
         */
        public DiskSpace(String path, long size) {
            this.path = path;
            this.size = size;
        }

        @Restricted(NoExternalUse.class)
        public void setTotalSize(long totalSize) {
            this.totalSize = totalSize;
        }

        @Restricted(NoExternalUse.class)
        @Exported
        public long getTotalSize() {
            return totalSize;
        }

        @Override
        public String toString() {
            if (isTriggered()) {
                if (threshold >= 0) {
                    return Messages.DiskSpaceMonitorDescriptor_DiskSpace_FreeSpaceTooLow(
                            Functions.humanReadableByteSize(size),
                            path,
                            Functions.humanReadableByteSize(threshold),
                            Functions.humanReadableByteSize(totalSize));
                } else {
                    return Messages.DiskSpaceMonitorDescriptor_DiskSpace_FreeSpaceTooLow(
                            Functions.humanReadableByteSize(size),
                            path,
                            "unset",
                            Functions.humanReadableByteSize(totalSize));
                }
            }
            if (isWarning()) {
                return Messages.DiskSpaceMonitorDescriptor_DiskSpace_FreeSpaceTooLow(
                        Functions.humanReadableByteSize(size),
                        path,
                        Functions.humanReadableByteSize(warningThreshold),
                        Functions.humanReadableByteSize(totalSize));
            }
            return Messages.DiskSpaceMonitorDescriptor_DiskSpace_FreeSpace(
                    Functions.humanReadableByteSize(size),
                    path,
                    Functions.humanReadableByteSize(totalSize));
        }

        /**
         * The path that was checked
         */
        @Exported
        public String getPath() {
            return path;
        }

        // Needed for jelly that does not seem to be able to access properties
        // named 'size' as it confuses it with built-in size method and fails
        // to parse the expression expecting '()'.
        @Restricted(DoNotUse.class)
        public long getFreeSize() {
            return size;
        }

        /**
         * Gets GB left.
         *
         * @deprecated
         *   Directly use the size field or to get a human-readable value with units use
         *   {@link Functions#humanReadableByteSize(long)}
         */
        @Deprecated(since = "2.434")
        public String getGbLeft() {
            long space = size;
            space /= 1024L;   // convert to KB
            space /= 1024L;   // convert to MB

            return new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString();
        }

        /**
         * Returns the HTML representation of the space.
         */
        public String toHtml() {
            return Functions.humanReadableByteSize(size);
        }

        @Restricted(NoExternalUse.class)
        public boolean isTriggered() {
            return size <= threshold;
        }

        @Restricted(NoExternalUse.class)
        public boolean isWarning() {
            return size > threshold && size < warningThreshold;
        }

        /**
         * Sets the trigger class which made the decision
         */
        protected void setTrigger(Class<? extends AbstractDiskSpaceMonitor> trigger) {
            this.trigger = trigger;
        }

        public void setThreshold(long threshold) {
            this.threshold = threshold;
        }

        @Exported
        public long getThreshold() {
            return threshold;
        }

        public void setWarningThreshold(long warningThreshold) {
            this.warningThreshold = warningThreshold;
        }

        @Exported
        public long getWarningThreshold() {
            return warningThreshold;
        }

        @Override
        public Class<? extends AbstractDiskSpaceMonitor> getTrigger() {
            return trigger;
        }

        /**
         * Parses a human readable size description like "1GB", "0.5m", "500KiB", etc. into {@link DiskSpace}
         *
         * @throws ParseException
         *      If failed to parse.
         */
        public static DiskSpace parse(String size) throws ParseException {
            size = size.toUpperCase(Locale.ENGLISH).trim();
            if (size.endsWith("B"))    // cut off 'B' from KB, MB, KiB, etc.
                size = size.substring(0, size.length() - 1);
            if (size.endsWith("I"))    // cut off 'i' from KiB, MiB, etc.
                size = size.substring(0, size.length() - 1);

            long multiplier = 1;

            // look for the size suffix. The goal here isn't to detect all invalid size suffix,
            // so we ignore double suffix like "10gkb" or anything like that.
            String suffix = "KMGT";
            for (int i = 0; i < suffix.length(); i++) {
                if (size.endsWith(suffix.substring(i, i + 1))) {
                    multiplier = 1;
                    for (int j = 0; j <= i; j++)
                        multiplier *= 1024;
                    size = size.substring(0, size.length() - 1);
                }
            }

            try {
                return new DiskSpace("", (long) (Double.parseDouble(size.trim()) * multiplier));
            } catch (NumberFormatException nfe) {
                throw new ParseException(nfe.getLocalizedMessage(), 0);
            }
        }

        private static final long serialVersionUID = 2L;
    }

    protected static final class GetUsableSpace extends MasterToSlaveFileCallable<DiskSpace> {
        public GetUsableSpace() {}

        @Override
        public DiskSpace invoke(File f, VirtualChannel channel) throws IOException {
            long s = f.getUsableSpace();
            if (s <= 0)    return null;
            DiskSpace ds = new DiskSpace(f.getCanonicalPath(), s);
            ds.setTotalSize(f.getTotalSpace());
            return ds;
        }

        private static final long serialVersionUID = 1L;
    }
}
