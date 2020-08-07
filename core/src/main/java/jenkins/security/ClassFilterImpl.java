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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import hudson.ExtensionList;
import hudson.Main;
import hudson.remoting.ClassFilter;
import hudson.remoting.Which;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Customized version of {@link ClassFilter#DEFAULT}.
 * First of all, {@link CustomClassFilter}s are given the first right of decision.
 * Then delegates to {@link ClassFilter#STANDARD} for its blacklist.
 * A class not mentioned in the blacklist is permitted unless it is defined in some third-party library
 * (as opposed to {@code jenkins-core.jar}, a plugin JAR, or test code during {@link Main#isUnitTest})
 * yet is not mentioned in {@code whitelisted-classes.txt}.
 */
@Restricted(NoExternalUse.class)
public class ClassFilterImpl extends ClassFilter {

    private static final Logger LOGGER = Logger.getLogger(ClassFilterImpl.class.getName());

    private static /* not final */ boolean SUPPRESS_WHITELIST = SystemProperties.getBoolean("jenkins.security.ClassFilterImpl.SUPPRESS_WHITELIST");
    private static /* not final */ boolean SUPPRESS_ALL = SystemProperties.getBoolean("jenkins.security.ClassFilterImpl.SUPPRESS_ALL");

    private static final String JENKINS_LOC = codeSource(Jenkins.class);
    private static final String REMOTING_LOC = codeSource(ClassFilter.class);

    /**
     * Register this implementation as the default in the system.
     */
    public static void register() {
        if (Main.isUnitTest && JENKINS_LOC == null) {
            mockOff();
            return;
        }
        ClassFilter.setDefault(new ClassFilterImpl());
        if (SUPPRESS_ALL) {
            LOGGER.warning("All class filtering suppressed. Your Jenkins installation is at risk from known attacks. See https://jenkins.io/redirect/class-filter/");
        } else if (SUPPRESS_WHITELIST) {
            LOGGER.warning("JEP-200 class filtering by whitelist suppressed. Your Jenkins installation may be at risk. See https://jenkins.io/redirect/class-filter/");
        }
    }

    /**
     * Undo {@link #register}.
     */
    public static void unregister() {
        ClassFilter.setDefault(ClassFilter.STANDARD);
    }

    private static void mockOff() {
        LOGGER.warning("Disabling class filtering since we appear to be in a special test environment, perhaps Mockito/PowerMock");
        ClassFilter.setDefault(ClassFilter.NONE); // even Method on the standard blacklist is going to explode
    }

    @VisibleForTesting
    /*package*/ ClassFilterImpl() {}

    /** Whether a given class is blacklisted. */
    private final Map<Class<?>, Boolean> cache = Collections.synchronizedMap(new WeakHashMap<>());
    /** Whether a given code source location is whitelisted. */
    private final Map<String, Boolean> codeSourceCache = Collections.synchronizedMap(new HashMap<>());
    /** Names of classes outside Jenkins core or plugins which have a special serial form but are considered safe. */
    static final Set<String> WHITELISTED_CLASSES;
    static {
        try (InputStream is = ClassFilterImpl.class.getResourceAsStream("whitelisted-classes.txt")) {
            WHITELISTED_CLASSES = ImmutableSet.copyOf(IOUtils.readLines(is, StandardCharsets.UTF_8).stream().filter(line -> !line.matches("#.*|\\s*")).collect(Collectors.toSet()));
        } catch (IOException x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    @Override
    public boolean isBlacklisted(Class _c) {
        for (CustomClassFilter f : ExtensionList.lookup(CustomClassFilter.class)) {
            Boolean r = f.permits(_c);
            if (r != null) {
                if (r) {
                    LOGGER.log(Level.FINER, "{0} specifies a policy for {1}: {2}", new Object[] {f, _c.getName(), true});
                } else {
                    notifyRejected(_c, _c.getName(), String.format("%s specifies a policy for %s: %s ", f, _c.getName(), r));
                }
                return !r;
            }
        }
        return cache.computeIfAbsent(_c, c -> {
            String name = c.getName();
            if (Main.isUnitTest && (name.contains("$$EnhancerByMockitoWithCGLIB$$") || name.contains("$$FastClassByMockitoWithCGLIB$$") || name.startsWith("org.mockito."))) {
                mockOff();
                return false;
            }
            if (ClassFilter.STANDARD.isBlacklisted(c)) { // currently never true, but may issue diagnostics
                notifyRejected(_c, _c.getName(), String.format("%s is not permitted ", _c.getName()));
                return true;
            }
            if (c.isArray()) {
                LOGGER.log(Level.FINE, "permitting {0} since it is an array", name);
                return false;
            }
            if (Throwable.class.isAssignableFrom(c)) {
                LOGGER.log(Level.FINE, "permitting {0} since it is a throwable", name);
                return false;
            }
            if (Enum.class.isAssignableFrom(c)) { // Class.isEnum seems to be false for, e.g., java.util.concurrent.TimeUnit$6
                LOGGER.log(Level.FINE, "permitting {0} since it is an enum", name);
                return false;
            }
            String location = codeSource(c);
            if (location != null) {
                if (isLocationWhitelisted(location)) {
                    LOGGER.log(Level.FINE, "permitting {0} due to its location in {1}", new Object[] {name, location});
                    return false;
                }
            } else {
                ClassLoader loader = c.getClassLoader();
                if (loader != null && loader.getClass().getName().equals("hudson.remoting.RemoteClassLoader")) {
                    LOGGER.log(Level.FINE, "permitting {0} since it was loaded by a remote class loader", name);
                    return false;
                }
            }
            if (WHITELISTED_CLASSES.contains(name)) {
                LOGGER.log(Level.FINE, "tolerating {0} by whitelist", name);
                return false;
            }
            if (SUPPRESS_WHITELIST || SUPPRESS_ALL) {
                notifyRejected(_c, null,
                        String.format("%s in %s might be dangerous, so would normally be rejected; see https://jenkins.io/redirect/class-filter/", name, location != null ?location : "JRE"));

                return false;
            }
            notifyRejected(_c, null,
                    String.format("%s in %s might be dangerous, so rejecting; see https://jenkins.io/redirect/class-filter/", name, location != null ?location : "JRE"));
            return true;
        });
    }

    private static final Pattern CLASSES_JAR = Pattern.compile("(file:/.+/)WEB-INF/lib/classes[.]jar");
    private boolean isLocationWhitelisted(String _loc) {
        return codeSourceCache.computeIfAbsent(_loc, loc -> {
            if (loc.equals(JENKINS_LOC)) {
                LOGGER.log(Level.FINE, "{0} seems to be the location of Jenkins core, OK", loc);
                return true;
            }
            if (loc.equals(REMOTING_LOC)) {
                LOGGER.log(Level.FINE, "{0} seems to be the location of Remoting, OK", loc);
                return true;
            }
            if (loc.matches("file:/.+[.]jar")) {
                try (JarFile jf = new JarFile(new File(new URI(loc)), false)) {
                    Manifest mf = jf.getManifest();
                    if (mf != null) {
                        if (isPluginManifest(mf)) {
                            LOGGER.log(Level.FINE, "{0} seems to be a Jenkins plugin, OK", loc);
                            return true;
                        } else {
                            LOGGER.log(Level.FINE, "{0} does not look like a Jenkins plugin", loc);
                        }
                    } else {
                        LOGGER.log(Level.FINE, "ignoring {0} with no manifest", loc);
                    }
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "problem checking " + loc, x);
                }
            }
            Matcher m = CLASSES_JAR.matcher(loc);
            if (m.matches()) {
                // Cf. ClassicPluginStrategy.createClassJarFromWebInfClasses: handle legacy plugin format with unpacked WEB-INF/classes/
                try {
                    File manifestFile = new File(new URI(m.group(1) + "META-INF/MANIFEST.MF"));
                    if (manifestFile.isFile()) {
                        try (InputStream is = new FileInputStream(manifestFile)) {
                            if (isPluginManifest(new Manifest(is))) {
                                LOGGER.log(Level.FINE, "{0} looks like a Jenkins plugin based on {1}, OK", new Object[] {loc, manifestFile});
                                return true;
                            } else {
                                LOGGER.log(Level.FINE, "{0} does not look like a Jenkins plugin", manifestFile);
                            }
                        }
                    } else {
                        LOGGER.log(Level.FINE, "{0} has no matching {1}", new Object[] {loc, manifestFile});
                    }
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "problem checking " + loc, x);
                }
            }
            if (loc.endsWith("/target/classes/") || loc.matches(".+/build/classes/[^/]+/main/")) {
                LOGGER.log(Level.FINE, "{0} seems to be current plugin classes, OK", loc);
                return true;
            }
            if (Main.isUnitTest) {
                if (loc.endsWith("/target/test-classes/") || loc.endsWith("-tests.jar") || loc.matches(".+/build/classes/[^/]+/test/")) {
                    LOGGER.log(Level.FINE, "{0} seems to be test classes, OK", loc);
                    return true;
                }
                if (loc.matches(".+/jenkins-test-harness-.+[.]jar")) {
                    LOGGER.log(Level.FINE, "{0} seems to be jenkins-test-harness, OK", loc);
                    return true;
                }
            }
            LOGGER.log(Level.FINE, "{0} is not recognized; rejecting", loc);
            return false;
        });
    }

    /**
     * Tries to determine what JAR file a given class was loaded from.
     * The location is an opaque string suitable only for comparison to others.
     * Similar to {@link Which#jarFile(Class)} but potentially faster, and more tolerant of unknown URL formats.
     * @param c some class
     * @return something typically like {@code file:/…/plugins/structs/WEB-INF/lib/structs-1.10.jar};
     *         or null for classes in the Java Platform, some generated classes, etc.
     */
    private static @CheckForNull String codeSource(@NonNull Class<?> c) {
        CodeSource cs = c.getProtectionDomain().getCodeSource();
        if (cs == null) {
            return null;
        }
        URL loc = cs.getLocation();
        if (loc == null) {
            return null;
        }
        String r = loc.toString();
        if (r.endsWith(".class")) {
            // JENKINS-49147: Tomcat bug. Now do the more expensive check…
            String suffix = c.getName().replace('.', '/') + ".class";
            if (r.endsWith(suffix)) {
                r = r.substring(0, r.length() - suffix.length());
            }
        }
        if (r.startsWith("jar:file:/") && r.endsWith(".jar!/")) {
            // JENKINS-49543: also an old behavior of Tomcat. Legal enough, but unexpected by isLocationWhitelisted.
            r = r.substring(4, r.length() - 2);
        }
        return r;
    }

    private static boolean isPluginManifest(Manifest mf) {
        Attributes attr = mf.getMainAttributes();
        return attr.getValue("Short-Name") != null && (attr.getValue("Plugin-Version") != null || attr.getValue("Jenkins-Version") != null) ||
               "true".equals(attr.getValue("Jenkins-ClassFilter-Whitelisted"));
    }

    @Override
    public boolean isBlacklisted(String name) {
        if (Main.isUnitTest && name.contains("$$EnhancerByMockitoWithCGLIB$$")) {
            mockOff();
            return false;
        }
        for (CustomClassFilter f : ExtensionList.lookup(CustomClassFilter.class)) {
            Boolean r = f.permits(name);
            if (r != null) {
                if (r) {
                    LOGGER.log(Level.FINER, "{0} specifies a policy for {1}: {2}", new Object[] {f, name, true});
                } else {
                    notifyRejected(null, name,
                            String.format("%s specifies a policy for %s: %s", f, name, r));
                }

                return !r;
            }
        }
        // could apply a cache if the pattern search turns out to be slow
        if (ClassFilter.STANDARD.isBlacklisted(name)) {
            if (SUPPRESS_ALL) {
                notifyRejected(null, name,
                        String.format("would normally reject %s according to standard blacklist; see https://jenkins.io/redirect/class-filter/", name));
                return false;
            }
            notifyRejected(null, name,
                    String.format("rejecting %s according to standard blacklist; see https://jenkins.io/redirect/class-filter/", name));
            return true;
        } else {
            return false;
        }
    }

    private void notifyRejected(@CheckForNull Class<?> clazz, @CheckForNull String clazzName, String message) {
        Throwable cause = null;
        if (LOGGER.isLoggable(Level.FINE)) {
            cause = new SecurityException("Class rejected by the class filter: " +
                    (clazz != null ? clazz.getName() : clazzName));
        }
        LOGGER.log(Level.WARNING, message, cause);

        // TODO: add a Telemetry implementation (JEP-304)
    }
}
