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

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Util;
import hudson.Functions;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.remoting.VirtualChannel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * Checks available disk space of the node.
 * Requres Mustang.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public class DiskSpaceMonitor extends NodeMonitor {
    public Long getFreeSpace(Computer c) {
        return DESCRIPTOR.get(c);
    }

    /**
     * Returns the HTML representation of the space.
     */
    public String toHtml(long space) {
        space/=1024L;   // convert to KB
        space/=1024L;   // convert to MB
        if(space<1024) {
            // less than a GB
            return Util.wrapToErrorSpan(new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString()+"GB");
        }

        return space/1024+"GB";
    }

    @Override
    public String getColumnCaption() {
        // Hide this column from non-admins
        return Hudson.getInstance().hasPermission(Hudson.ADMINISTER) ? super.getColumnCaption() : null;
    }

    public static final AbstractNodeMonitorDescriptor<Long> DESCRIPTOR = new AbstractNodeMonitorDescriptor<Long>() {
        protected Long monitor(Computer c) throws IOException, InterruptedException {
            FilePath p = c.getNode().getRootPath();
            if(p==null) return null;

            Long size = p.act(new GetUsableSpace());
            if(size!=null && size!=0 && size/(1024*1024*1024)==0) {
                // TODO: this scheme should be generalized, so that Hudson can remember why it's marking the node
                // as offline, as well as allowing the user to force Hudson to use it.
                if(!c.isTemporarilyOffline()) {
                    LOGGER.warning(Messages.DiskSpaceMonitor_MarkedOffline(c.getName()));
                    c.setTemporarilyOffline(true);
                }
            }
            return size;
        }

        public String getDisplayName() {
            return Messages.DiskSpaceMonitor_displayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DiskSpaceMonitor();
        }
    };

    @Extension
    public static AbstractNodeMonitorDescriptor<Long> install() {
        if(Functions.isMustangOrAbove())    return DESCRIPTOR;
        return null;
    }

    private static final class GetUsableSpace implements FileCallable<Long> {
        @IgnoreJRERequirement
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            try {
                return f.getUsableSpace();
            } catch (LinkageError e) {
                // pre-mustang
                return null;
            }
        }
        private static final long serialVersionUID = 1L;
    }

    private static final Logger LOGGER = Logger.getLogger(DiskSpaceMonitor.class.getName());
}
