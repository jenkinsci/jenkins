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

package jenkins.security.s2m;

import hudson.Extension;
import hudson.remoting.ChannelBuilder;
import jenkins.ReflectiveFilePathFilter;
import jenkins.security.ChannelConfigurator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Blocks slaves from writing to files on the master by default (and also provide the kill switch.)
 */
@Restricted(DoNotUse.class) // impl
@Extension public class DefaultFilePathFilter extends ChannelConfigurator {

    /**
     * Escape hatch to disable this check completely.
     */
    public static boolean BYPASS = Boolean.getBoolean(DefaultFilePathFilter.class.getName()+".allow");

    private static final Logger LOGGER = Logger.getLogger(DefaultFilePathFilter.class.getName());

    @Override
    public void onChannelBuilding(ChannelBuilder builder, Object context) {
        new ReflectiveFilePathFilter() {
            protected boolean op(String op, File f) throws SecurityException {
                if (BYPASS) {
                    LOGGER.log(Level.FINE, "slave allowed to {0} {1}", new Object[] {op, f});
                    return true;
                } else {
                    return false;
                }
            }
        }.installTo(builder, AdminFilePathFilter.ORDINAL+100);
        // for the bypass switch to be effective, it should have a high priority
    }
}
