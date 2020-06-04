/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.remoting.ClassFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Allows extensions to adjust the behavior of {@link ClassFilter#DEFAULT}.
 * Custom filters can be called frequently, and return values are uncached, so implementations should be fast.
 * @see ClassFilterImpl
 * @since 2.102
 */
public interface CustomClassFilter extends ExtensionPoint {

    /**
     * Determine whether a class should be permitted by {@link ClassFilter#isBlacklisted(Class)} of {@link ClassFilter#DEFAULT}.
     * @param c the class
     * @return true to permit it when it would normally be rejected (for example due to having a custom serialization method and being from a third-party library);
     *         false to reject it when it would normally be permitted;
     *         null to express no opinion (the default)
     */
    default @CheckForNull Boolean permits(Class<?> c) {
        return null;
    }

    /**
     * Determine whether a class should be permitted by {@link ClassFilter#isBlacklisted(String)} of {@link ClassFilter#DEFAULT}.
     * @param name a class name
     * @return true to permit it when it would normally be rejected (currently useless);
     *         false to reject it when it would normally be permitted (currently due to {@link ClassFilter#STANDARD};
     *         null to express no opinion (the default)
     */
    default @CheckForNull Boolean permits(String name) {
        return null;
    }

    /**
     * Standard filter which pays attention to a system property.
     * To use, specify a system property {@code hudson.remoting.ClassFilter} containing a comma-separated list of {@link Class#getName} to whitelist.
     * Entries may also be preceded by {@code !} to blacklist.
     * Example: {@code -Dhudson.remoting.ClassFilter=com.google.common.collect.LinkedListMultimap,!com.acme.illadvised.YoloReflectionFactory$Handle}
     */
    @Restricted(NoExternalUse.class)
    @Extension
    class Static implements CustomClassFilter {

        /**
         * Map from {@link Class#getName} to true to permit, false to reject.
         * Unmentioned classes are not treated specially.
         * Intentionally {@code public} for possible mutation without restart by Groovy scripting.
         */
        public final Map<String, Boolean> overrides = new HashMap<>();

        public Static() {
            String entries = SystemProperties.getString("hudson.remoting.ClassFilter");
            if (entries != null) {
                for (String entry : entries.split(",")) {
                    if (entry.startsWith("!")) {
                        overrides.put(entry.substring(1), false);
                    } else {
                        overrides.put(entry, true);
                    }
                }
                Logger.getLogger(Static.class.getName()).log(Level.FINE, "user-defined entries: {0}", overrides);
            }
        }

        @Override
        public Boolean permits(Class<?> c) {
            return permits(c.getName());
        }

        @Override
        public Boolean permits(String name) {
            return overrides.get(name);
        }

    }

    /**
     * Standard filter which can load whitelists and blacklists from plugins.
     * To use, add a resource {@code META-INF/hudson.remoting.ClassFilter} to your plugin.
     * Each line should be the {@link Class#getName} of a class to whitelist.
     * Or you may blacklist a class by preceding its name with {@code !}.
     * Example:
     * <pre>
     * com.google.common.collect.LinkedListMultimap
     * !com.acme.illadvised.YoloReflectionFactory$Handle
     * </pre>
     */
    @Restricted(NoExternalUse.class)
    @Extension
    class Contributed implements CustomClassFilter {

        /**
         * Map from {@link Class#getName} to true to permit, false to reject.
         * Unmentioned classes are not treated specially.
         */
        private final Map<String, Boolean> overrides = new HashMap<>();

        @Override
        public Boolean permits(Class<?> c) {
            return permits(c.getName());
        }

        @Override
        public Boolean permits(String name) {
            return overrides.get(name);
        }

        @Initializer(after = InitMilestone.PLUGINS_PREPARED, before = InitMilestone.PLUGINS_STARTED, fatal = false)
        public static void load() throws IOException {
            Map<String, Boolean> overrides = ExtensionList.lookup(CustomClassFilter.class).get(Contributed.class).overrides;
            overrides.clear();
            Enumeration<URL> resources = Jenkins.get().getPluginManager().uberClassLoader.getResources("META-INF/hudson.remoting.ClassFilter");
            while (resources.hasMoreElements()) {
                try (InputStream is = resources.nextElement().openStream()) {
                    for (String entry : IOUtils.readLines(is, StandardCharsets.UTF_8)) {
                        //noinspection StatementWithEmptyBody
                        if (entry.matches("#.*|\\s*")) {
                            // skip
                        } else if (entry.startsWith("!")) {
                            overrides.put(entry.substring(1), false);
                        } else {
                            overrides.put(entry, true);
                        }
                    }
                }
            }
            Logger.getLogger(Contributed.class.getName()).log(Level.FINE, "plugin-defined entries: {0}", overrides);
        }

    }

}
