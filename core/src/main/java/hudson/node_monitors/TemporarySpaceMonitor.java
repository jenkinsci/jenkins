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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Monitors the disk space of "/tmp".
 *
 * @author Kohsuke Kawaguchi
 */
public class TemporarySpaceMonitor extends AbstractDiskSpaceMonitor {
    @DataBoundConstructor
    public TemporarySpaceMonitor(String freeSpaceThreshold) throws ParseException {
        super(freeSpaceThreshold);
    }

    public TemporarySpaceMonitor() {}

    @Override
    public long getThresholdBytes(Computer c) {
        Node node = c.getNode();
        if (node != null) {
            DiskSpaceMonitorNodeProperty nodeProperty = node.getNodeProperty(DiskSpaceMonitorNodeProperty.class);
            if (nodeProperty != null) {
                try {
                    return DiskSpace.parse(nodeProperty.getFreeTempSpaceThreshold()).size;
                } catch (ParseException e) {
                    return getThresholdBytes();
                }
            }
        }
        return getThresholdBytes();
    }

    @Override
    protected long getWarningThresholdBytes(Computer c) {
        Node node = c.getNode();
        if (node != null) {
            DiskSpaceMonitorNodeProperty nodeProperty = node.getNodeProperty(DiskSpaceMonitorNodeProperty.class);
            if (nodeProperty != null) {
                try {
                    return DiskSpace.parse(nodeProperty.getFreeTempSpaceWarningThreshold()).size;
                } catch (ParseException e) {
                    return getWarningThresholdBytes();
                }
            }
        }
        return getWarningThresholdBytes();
    }

    public DiskSpace getFreeSpace(Computer c) {
        DiskSpaceMonitorDescriptor descriptor = (DiskSpaceMonitorDescriptor) Jenkins.get().getDescriptor(TemporarySpaceMonitor.class);
        return descriptor != null ? descriptor.get(c) : null;
    }

    @Override
    public String getColumnCaption() {
        // Hide this column from non-admins
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER) ? super.getColumnCaption() : null;
    }

    /**
     * @deprecated as of 2.0
     *      Use injection
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_PKGPROTECT", justification = "for backward compatibility")
    public static /*almost final*/ DiskSpaceMonitorDescriptor DESCRIPTOR;

    @Extension @Symbol("tmpSpace")
    public static class DescriptorImpl extends DiskSpaceMonitorDescriptor {
        @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "for backward compatibility")
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.TemporarySpaceMonitor_DisplayName();
        }

        @Override
        protected Callable<DiskSpace, IOException> createCallable(Computer c) {
            Node node = c.getNode();
            if (node == null) return null;

            FilePath p = node.getRootPath();
            if (p == null) return null;

            return p.asCallableWith(new GetTempSpace());
        }
    }

    protected static final class GetTempSpace extends MasterToSlaveFileCallable<DiskSpace> {
        @Override
        public DiskSpace invoke(File f, VirtualChannel channel) throws IOException {
                // if the disk is really filled up we can't even create a single file,
                // so calling File.createTempFile and figuring out the directory won't reliably work.
                f = new File(System.getProperty("java.io.tmpdir"));
                long s = f.getUsableSpace();
                if (s <= 0)    return null;
                DiskSpace ds = new DiskSpace(f.getCanonicalPath(), s);
                ds.setTotalSize(f.getTotalSpace());
                return ds;
        }

        private static final long serialVersionUID = 1L;
    }
}
