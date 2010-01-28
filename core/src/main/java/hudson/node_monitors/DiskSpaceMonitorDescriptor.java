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

import hudson.FilePath.FileCallable;
import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import hudson.Util;
import hudson.slaves.OfflineCause;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

/**
 * {@link AbstractNodeMonitorDescriptor} for {@link NodeMonitor} that checks a free disk space of some directory.
 *
 * @author Kohsuke Kawaguchi
*/
/*package*/ abstract class DiskSpaceMonitorDescriptor extends AbstractNodeMonitorDescriptor<DiskSpace> {
    /**
     * Value object that represents the disk space.
     */
    @ExportedBean
    public static final class DiskSpace extends OfflineCause implements Serializable {
        @Exported
        public final long size;
        
        private boolean triggered;

        public DiskSpace(long size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return String.valueOf(size);
        }

        /**
         * Gets GB left.
         */
        public String getGbLeft() {
            long space = size;
            space/=1024L;   // convert to KB
            space/=1024L;   // convert to MB

            return new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString();
        }

        /**
         * Returns the HTML representation of the space.
         */
        public String toHtml() {
            long space = size;
            space/=1024L;   // convert to KB
            space/=1024L;   // convert to MB
            if(triggered) {
                // less than a GB
                return Util.wrapToErrorSpan(new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString()+"GB");
            }

            return space/1024+"GB";
        }
        
        /**
         * Sets whether this disk space amount should be treated as outside
         * the acceptable conditions or not.
         */
        protected void setTriggered(boolean triggered) {
        	this.triggered = triggered;
        }

        /**
         * Parses a human readable size description like "1GB", "0.5m", etc. into {@link DiskSpace}
         *
         * @throws ParseException
         *      If failed to parse.
         */
        public static DiskSpace parse(String size) throws ParseException {
            size = size.toUpperCase(Locale.ENGLISH).trim();
            if (size.endsWith("B"))    // cut off 'B' from KB, MB, etc.
                size = size.substring(0,size.length()-1);

            long multiplier=1;

            // look for the size suffix. The goal here isn't to detect all invalid size suffix,
            // so we ignore double suffix like "10gkb" or anything like that.
            String suffix = "KMGT";
            for (int i=0; i<suffix.length(); i++) {
                if (size.endsWith(suffix.substring(i,i+1))) {
                    multiplier = 1;
                    for (int j=0; j<=i; j++ )
                        multiplier*=1024;
                    size = size.substring(0,size.length()-1);
                }
            }

            return new DiskSpace((long)(Double.parseDouble(size.trim())*multiplier));
        }

        private static final long serialVersionUID = 2L;
    }

    protected DiskSpace monitor(Computer c) throws IOException, InterruptedException {
        return getFreeSpace(c);
    }

    /**
     * Computes the free size.
     */
    protected abstract DiskSpace getFreeSpace(Computer c) throws IOException, InterruptedException;

    protected static final class GetUsableSpace implements FileCallable<DiskSpace> {
        @IgnoreJRERequirement
        public DiskSpace invoke(File f, VirtualChannel channel) throws IOException {
            try {
                long s = f.getUsableSpace();
                if(s<=0)    return null;
                return new DiskSpace(s);
            } catch (LinkageError e) {
                // pre-mustang
                return null;
            }
        }
        private static final long serialVersionUID = 1L;
    }
}
