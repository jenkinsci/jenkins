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
package hudson.node_monitors;

import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Checks available disk space of the remote FS root.
 * Requires Mustang.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public class DiskSpaceMonitor extends NodeMonitor {
    public DiskSpace getFreeSpace(Computer c) {
        return DESCRIPTOR.get(c);
    }

    @Override
    public String getColumnCaption() {
        // Hide this column from non-admins
        return Hudson.getInstance().hasPermission(Hudson.ADMINISTER) ? super.getColumnCaption() : null;
    }

    public static final DiskSpaceMonitorDescriptor DESCRIPTOR = new DiskSpaceMonitorDescriptor() {
        public String getDisplayName() {
            return Messages.DiskSpaceMonitor_DisplayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DiskSpaceMonitor();
        }

        protected DiskSpace getFreeSpace(Computer c) throws IOException, InterruptedException {
            FilePath p = c.getNode().getRootPath();
            if(p==null) return null;

            return p.act(new GetUsableSpace());
        }
    };

    @Extension
    public static DiskSpaceMonitorDescriptor install() {
        if(Functions.isMustangOrAbove())    return DESCRIPTOR;
        return null;
    }
}
