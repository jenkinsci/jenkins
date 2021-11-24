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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.Command;
import hudson.remoting.Request;
import java.io.File;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.ReflectiveFilePathFilter;
import jenkins.security.ChannelConfigurator;
import jenkins.telemetry.impl.SlaveToMasterFileCallableUsage;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Blocks agents from writing to files on the master by default (and also provide the kill switch.)
 */
@Restricted(NoExternalUse.class) // impl
@Extension public class DefaultFilePathFilter extends ChannelConfigurator {

    /**
     * Escape hatch to disable this check completely.
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static boolean BYPASS = SystemProperties.getBoolean(DefaultFilePathFilter.class.getName()+".allow");

    private static final Logger LOGGER = Logger.getLogger(DefaultFilePathFilter.class.getName());

    @Override
    public void onChannelBuilding(ChannelBuilder builder, Object context) {
        new ReflectiveFilePathFilter() {
            @Override
            protected boolean op(String op, File f) throws SecurityException {
                if (BYPASS) {
                    LOGGER.log(Level.FINE, "agent allowed to {0} {1}", new Object[] {op, f});
                    return true;
                } else {
                    try {
                        Field current = Request.class.getDeclaredField("CURRENT");
                        current.setAccessible(true);
                        Field createdAt = Command.class.getDeclaredField("createdAt");
                        createdAt.setAccessible(true);
                        Throwable trace = (Throwable) createdAt.get(((ThreadLocal) current.get(null)).get());
                        ExtensionList.lookupSingleton(SlaveToMasterFileCallableUsage.class).recordTrace(trace);
                        LOGGER.log(Level.WARNING, "Permitting agent-to-controller '" + op + "' on '" + f + "'. This is deprecated and will soon be rejected. Learn more: https://www.jenkins.io/redirect/permitted-agent-to-controller-file-access", trace);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                    return false;
                }
            }
        }.installTo(builder, AdminFilePathFilter.ORDINAL+100);
        // for the bypass switch to be effective, it should have a high priority
    }
}
