/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.security;

import hudson.Extension;
import hudson.remoting.ChannelBuilder;
import jenkins.FilePathFilter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Blocks slaves from writing to files on the master by default.
 */
@Restricted(DoNotUse.class) // impl
@Extension public class DefaultFilePathFilter extends ChannelConfigurator {

    /**
     * Escape hatch to disable this check completely.
     */
    public static boolean BYPASS = Boolean.getBoolean(DefaultFilePathFilter.class.getName()+".allow");
    private static final PrintWriter BYPASS_LOG; // TODO delete before release
    static {
        String log = System.getProperty("jenkins.security.DefaultFilePathFilter.log");
        if (log == null) {
            BYPASS_LOG = null;
        } else {
            try {
                BYPASS_LOG = new PrintWriter(new OutputStreamWriter(new FileOutputStream(log, true)), true);
            } catch (FileNotFoundException x) {
                throw new ExceptionInInitializerError(x);
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(DefaultFilePathFilter.class.getName());

    @Override
    public void onChannelBuilding(ChannelBuilder builder, Object context) {
        new FilePathFilter() {
            private boolean op(String op, File f) throws SecurityException {
                if (BYPASS_LOG != null) {
                    BYPASS_LOG.println(op + " " + f);
                    return true;
                }
                if (BYPASS) {
                    LOGGER.log(Level.FINE, "slave allowed to {0} {1}", new Object[] {op, f});
                    return true;
                } else {
                    // TODO allow finer-grained control, for example by regexp (or Ant pattern) of relative path inside $JENKINS_HOME
                    // will do this by writing other FilePathFilters
                    return false;
                }
            }
            @Override public boolean read(File f) throws SecurityException {
                return op("read", f);
            }
            @Override public boolean write(File f) throws SecurityException {
                return op("write", f);
            }
            @Override public boolean mkdirs(File f) throws SecurityException {
                return op("mkdirs", f);
            }
            @Override public boolean create(File f) throws SecurityException {
                return op("create", f);
            }
            @Override public boolean delete(File f) throws SecurityException {
                return op("delete", f);
            }
            @Override public boolean stat(File f) throws SecurityException {
                return op("stat", f);
            }
        }.installTo(builder);
    }
}
