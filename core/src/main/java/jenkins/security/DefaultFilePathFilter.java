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
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.ComputerListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.FilePathFilter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

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
            private void op(String op, File f) throws SecurityException {
                if (BYPASS_LOG != null) {
                    BYPASS_LOG.println(op + " " + f);
                    return;
                }
                if (BYPASS) {
                    LOGGER.log(Level.FINE, "slave allowed to {0} {1}", new Object[] {op, f});
                } else {
                    // TODO allow finer-grained control, for example by regexp (or Ant pattern) of relative path inside $JENKINS_HOME
                    throw new SecurityException("slave may not " + op + " " + f);
                }
            }
            @Override public void read(File f) throws SecurityException {
                op("read", f);
            }
            @Override public void write(File f) throws SecurityException {
                op("write", f);
            }
            @Override public void mkdirs(File f) throws SecurityException {
                op("mkdirs", f);
            }
            @Override public void create(File f) throws SecurityException {
                op("create", f);
            }
            @Override public void delete(File f) throws SecurityException {
                op("delete", f);
            }
            @Override public void stat(File f) throws SecurityException {
                op("stat", f);
            }
        }.installTo(builder);
    }
}
