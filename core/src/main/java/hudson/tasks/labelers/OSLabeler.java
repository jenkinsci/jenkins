/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.tasks.labelers;

import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.DynamicLabeler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 25-May-2007 15:25:03
 */
// @Extension --- not live yet
public class OSLabeler extends DynamicLabeler {
    public Set<String> findLabels(VirtualChannel channel) {
        try {
            return channel.call(new OSLabelFinder());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private static class OSLabelFinder implements Callable<Set<String>, Exception> {
        /** Performs computation and returns the result, or throws some exception. */
        public Set<String> call() throws Exception {
            Set<String> result = new HashSet<String>();
            final String os = System.getProperty("os.name").toLowerCase();
            final String version = System.getProperty("os.version");
            final String arch = System.getProperty("os.arch");
            if (os.equals("solaris") || os.equals("SunOS")) {
                result.add("solaris");
                result.add("solaris_" + arch);
                result.add("solaris_" + arch + "_" + version);
            } else if (os.startsWith("windows")) {
                result.add("windows");
                if (os.startsWith("windows 9")) {
                    // ugh! windows 9x
                    // I have not tested these values
                    result.add("windows_9x_family");
                    if (version.startsWith("4.0")) {
                        result.add("windows_95");
                    } else if (version.startsWith("4.9")) {
                        result.add("windows_ME"); // but could be Windows ME
                    } else {
                        assert version.startsWith("4.1");
                        result.add("windows_98");
                    }
                } else {
                    // older Java Runtimes can mis-report newer versions of windows NT
                    result.add("windows_nt_family");
                    if (version.startsWith("4.0")) {
                        // Windows NT 4
                        result.add("windows_nt4");
                    } else if (version.startsWith("5.0")) {
                        result.add("windows_2000");
                    } else if (version.startsWith("5.1")) {
                        result.add("windows_xp");
                    } else if (version.startsWith("5.2")) {
                        result.add("windows_2003");
                    }
                }
            } else if (os.startsWith("linux")) {
                result.add("linux");
            } else if (os.startsWith("mac")) {
                result.add("mac");
            } else {
                // I give up!
                result.add(os);
            }
            return result;
        }
    }
}
