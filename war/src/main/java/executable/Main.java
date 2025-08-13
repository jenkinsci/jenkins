/*
 * The MIT License
 *
 * Copyright (c) 2008-2011, Sun Microsystems, Inc., Alan Harder, Jerome Lacoste, Kohsuke Kawaguchi,
 * bap2000, CloudBees, Inc.
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

package executable;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.MissingResourceException;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Launcher class for stand-alone execution of Jenkins as
 * {@code java -jar jenkins.war}.
 *
 * <p>On a high-level architectural note, this class is intended to be a very thin wrapper whose
 * primary purpose is to extract Winstone and delegate to Winstone's own initialization mechanism.
 * The logic in this class should only perform Jenkins-specific argument and environment validation
 * and Jenkins-specific Winstone customization prior to delegating to Winstone.
 *
 * <p>In particular, managing the logging subsystem is completely delegated to Winstone, and this
 * class should neither assume that logging has been initialized nor take advantage of the logging
 * subsystem. In the event that this class needs to print information to the user, it should do so
 * via the standard output (stdout) and standard error (stderr) streams rather than via the logging
 * subsystem. Such messages should generally be avoided except for fatal scenarios, such as an
 * inappropriate Java Virtual Machine (JVM) or some other serious failure that would preclude
 * starting Winstone.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {

    /**
     * This list must remain synchronized with the one in {@code
     * JavaVersionRecommendationAdminMonitor}.
     */
    private static final NavigableSet<Integer> SUPPORTED_JAVA_VERSIONS = new TreeSet<>(List.of(17, 21));

    /**
     * Sets custom session cookie name.
     * It may be used to prevent randomization of JSESSIONID cookies and issues like
     * <a href="https://issues.jenkins-ci.org/browse/JENKINS-25046">JENKINS-25046</a>.
     * @since 2.66
     */
    private static final String JSESSIONID_COOKIE_NAME =
            System.getProperty("executableWar.jetty.sessionIdCookieName");

    /**
     * Disables usage of the custom cookie names when starting the WAR file.
     * If the flag is specified, the session ID will be defined by the internal Jetty logic.
     * In such case it becomes configurable via
     * <a href="http://www.eclipse.org/jetty/documentation/9.4.x/jetty-xml-config.html">Jetty XML Config file</a>>
     * or via system properties.
     * @since 2.66
     */
    private static final boolean DISABLE_CUSTOM_JSESSIONID_COOKIE_NAME =
            Boolean.getBoolean("executableWar.jetty.disableCustomSessionIdCookieName");

    /**
     * Flag to bypass the Java version check when starting.
     */
    private static final String ENABLE_FUTURE_JAVA_CLI_SWITCH = "--enable-future-java";

    /*package*/ static void verifyJavaVersion(int releaseVersion, boolean enableFutureJava) {
        if (SUPPORTED_JAVA_VERSIONS.contains(releaseVersion)) {
            // Great!
        } else if (releaseVersion >= SUPPORTED_JAVA_VERSIONS.first()) {
            if (enableFutureJava) {
                System.err.printf(
                        "Running with Java %d from %s, which is not fully supported. "
                                + "Continuing because %s is set. "
                                + "Supported Java versions are: %s. "
                                + "See https://jenkins.io/redirect/java-support/ for more information.%n",
                        releaseVersion,
                        System.getProperty("java.home"),
                        ENABLE_FUTURE_JAVA_CLI_SWITCH,
                        SUPPORTED_JAVA_VERSIONS);
            } else if (releaseVersion > SUPPORTED_JAVA_VERSIONS.last()) {
                throw new UnsupportedClassVersionError(
                        String.format(
                                "Running with Java %d from %s, which is not yet fully supported.%n"
                                        + "Run the command again with the %s flag to enable preview support for future Java versions.%n"
                                        + "Supported Java versions are: %s",
                                releaseVersion,
                                System.getProperty("java.home"),
                                ENABLE_FUTURE_JAVA_CLI_SWITCH,
                                SUPPORTED_JAVA_VERSIONS));
            } else {
                throw new UnsupportedClassVersionError(
                        String.format(
                                "Running with Java %d from %s, which is not fully supported.%n"
                                        + "Run the command again with the %s flag to bypass this error.%n"
                                        + "Supported Java versions are: %s",
                                releaseVersion,
                                System.getProperty("java.home"),
                                ENABLE_FUTURE_JAVA_CLI_SWITCH,
                                SUPPORTED_JAVA_VERSIONS));
            }
        } else {
            throw new UnsupportedClassVersionError(
                    String.format(
                            "Running with Java %d from %s, which is older than the minimum required version (Java %d).%n"
                                    + "Supported Java versions are: %s",
                            releaseVersion,
                            System.getProperty("java.home"),
                            SUPPORTED_JAVA_VERSIONS.first(),
                            SUPPORTED_JAVA_VERSIONS));
        }
    }

    /**
     * Returns true if the Java runtime version check should not be done, and any version allowed.
     *
     * @see #ENABLE_FUTURE_JAVA_CLI_SWITCH
     */
    private static boolean isFutureJavaEnabled(String[] args) {
        return hasArgument(ENABLE_FUTURE_JAVA_CLI_SWITCH, args) || Boolean.parseBoolean(System.getenv("JENKINS_ENABLE_FUTURE_JAVA"));
    }

    // TODO: Rework everything to use List
    private static boolean hasArgument(@NonNull String argument, @NonNull String[] args) {
        for (String arg : args) {
            if (argument.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "User provided values for running the program")
    public static void main(String[] args) throws IllegalAccessException {
        try {
            verifyJavaVersion(Runtime.version().feature(), isFutureJavaEnabled(args));
        } catch (UnsupportedClassVersionError e) {
            System.err.println(e.getMessage());
            System.err.println("See https://jenkins.io/redirect/java-support/ for more information.");
            System.exit(1);
        }

        //Allows to pass arguments through stdin to "hide" sensitive parameters like httpsKeyStorePassword
        //to achieve this use --paramsFromStdIn
        if (hasArgument("--paramsFromStdIn", args)) {
            System.out.println("--paramsFromStdIn detected. Parameters are going to be read from stdin. Other parameters passed directly will be ignored.");
            String argsInStdIn;
            try {
                argsInStdIn = new String(System.in.readNBytes(131072), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            args = argsInStdIn.split(" +");
        }
        // If someone just wants to know the version, print it out as soon as possible, with no extraneous file or webroot info.
        // This makes it easier to grab the version from a script
        final List<String> arguments = new ArrayList<>(List.of(args));
        if (arguments.contains("--version")) {
            System.out.println(getVersion("?"));
            return;
        }

        File extractedFilesFolder = null;
        for (String arg : args) {
            if (arg.startsWith("--extractedFilesFolder=")) {
                extractedFilesFolder = new File(arg.substring("--extractedFilesFolder=".length()));
                if (!extractedFilesFolder.isDirectory()) {
                    System.err.println("The extractedFilesFolder value is not a directory. Ignoring.");
                    extractedFilesFolder = null;
                }
            }
        }

        for (String arg : args) {
            if (arg.startsWith("--pluginroot=")) {
                System.setProperty("hudson.PluginManager.workDir",
                        new File(arg.substring("--pluginroot=".length())).getAbsolutePath());
                // if specified multiple times, the first one wins
                break;
            }
        }

        // this is so that JFreeChart can work nicely even if we are launched as a daemon
        System.setProperty("java.awt.headless", "true");

        File me = whoAmI(extractedFilesFolder);
        System.out.println("Running from: " + me);
        System.setProperty("executable-war", me.getAbsolutePath());  // remember the location so that we can access it from within webapp

        // figure out the arguments
        trimOffOurOptions(arguments);
        arguments.add(0, "--warfile=" + me.getAbsolutePath());
        if (!hasOption(arguments, "--webroot=")) {
            // defaults to ~/.jenkins/war since many users reported that cron job attempts to clean up
            // the contents in the temporary directory.
            final File jenkinsHome = getJenkinsHome();
            final File webRoot = new File(jenkinsHome, "war");
            System.out.println("webroot: " + webRoot);
            arguments.add("--webroot=" + webRoot);
        }

        // only do a cleanup if you set the extractedFilesFolder property.
        if (extractedFilesFolder != null) {
            deleteContentsFromFolder(extractedFilesFolder, "winstone.*\\.jar");
        }

        // put winstone jar in a file system so that we can load jars from there
        File tmpJar = extractFromJar("winstone.jar", "winstone", ".jar", extractedFilesFolder);
        tmpJar.deleteOnExit();

        // clean up any previously extracted copy, since
        // winstone doesn't do so and that causes problems when newer version of Jenkins
        // is deployed.
        File tempFile;
        try {
            tempFile = Files.createTempFile("dummy", "dummy").toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        deleteWinstoneTempContents(new File(tempFile.getParent(), "winstone/" + me.getName()));
        if (!tempFile.delete()) {
            System.err.println("Failed to delete temporary file: " + tempFile);
        }

        // locate the Winstone launcher
        ClassLoader cl;
        try {
            cl = new URLClassLoader("Jenkins Main ClassLoader", new URL[] {tmpJar.toURI().toURL()}, ClassLoader.getSystemClassLoader());
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
        Class<?> launcher;
        Method mainMethod;
        try {
            launcher = cl.loadClass("winstone.Launcher");
            mainMethod = launcher.getMethod("main", String[].class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new AssertionError(e);
        }

        // override the usage screen
        Field usage;
        try {
            usage = launcher.getField("USAGE");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        usage.set(null, "Jenkins Automation Server Engine " + getVersion("") + "\n" +
                "Usage: java -jar jenkins.war [--option=value] [--option=value]\n" +
                "\n" +
                "Options:\n" +
                "   --webroot                = folder where the WAR file is expanded into. Default is ${JENKINS_HOME}/war\n" +
                "   --pluginroot             = folder where the plugin archives are expanded into. Default is ${JENKINS_HOME}/plugins\n" +
                "                              (NOTE: this option does not change the directory where the plugin archives are stored)\n" +
                "   --extractedFilesFolder   = folder where extracted files are to be located. Default is the temp folder\n" +
                "   " + ENABLE_FUTURE_JAVA_CLI_SWITCH + "     = allows running with Java versions which are not fully supported\n" +
                "   --paramsFromStdIn        = Read parameters from standard input (stdin)\n" +
                "   --version                = Print version to standard output (stdout) and exit\n" +
                "{OPTIONS}");

        if (!DISABLE_CUSTOM_JSESSIONID_COOKIE_NAME) {
            /*
             Set an unique cookie name.

             As can be seen in discussions like http://stackoverflow.com/questions/1146112/jsessionid-collision-between-two-servers-on-same-ip-but-different-ports
             and http://stackoverflow.com/questions/1612177/are-http-cookies-port-specific, RFC 2965 says
             cookies from one port of one host may be sent to a different port of the same host.
             This means if someone runs multiple Jenkins on different ports of the same host,
             their sessions get mixed up.

             To fix the problem, use unique session cookie name.

             This change breaks the cluster mode of Winstone, as all nodes in the cluster must share the same session cookie name.
             Jenkins doesn't support clustered operation anyway, so we need to do this here, and not in Winstone.
            */
            try {
                Field f = cl.loadClass("winstone.WinstoneSession").getField("SESSION_COOKIE_NAME");
                f.setAccessible(true);
                if (JSESSIONID_COOKIE_NAME != null) {
                    // Use the user-defined cookie name
                    f.set(null, JSESSIONID_COOKIE_NAME);
                } else {
                    // Randomize session names by default to prevent collisions when running multiple Jenkins instances on the same host.
                    f.set(null, "JSESSIONID." + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                }
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                throw new AssertionError(e);
            }
        }

        // run
        Thread.currentThread().setContextClassLoader(cl);
        try {
            mainMethod.invoke(null, new Object[] {arguments.toArray(new String[0])});
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof IOException) {
                throw new UncheckedIOException((IOException) t);
            } else if (t instanceof Exception) {
                throw new RuntimeException(t);
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private static void trimOffOurOptions(List<String> arguments) {
        arguments.removeIf(arg -> arg.startsWith("--extractedFilesFolder")
                || arg.startsWith("--pluginroot") || arg.startsWith(ENABLE_FUTURE_JAVA_CLI_SWITCH));
    }

    /**
     * Figures out the version from the manifest.
     */
    private static String getVersion(String fallback) {
      try {
        Enumeration<URL> manifests = Main.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements()) {
            URL res = manifests.nextElement();
            Manifest manifest = new Manifest(res.openStream());
            String v = manifest.getMainAttributes().getValue("Jenkins-Version");
            if (v != null) {
                return v;
            }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return fallback;
    }

    private static boolean hasOption(List<String> args, String prefix) {
        for (String s : args) {
            if (s.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Figures out the URL of {@code jenkins.war}.
     */
    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "URLCONNECTION_SSRF_FD"}, justification = "User provided values for running the program.")
    public static File whoAmI(File directory) {
        // JNLP returns the URL where the jar was originally placed (like http://jenkins-ci.org/...)
        // not the local cached file. So we need a rather round about approach to get to
        // the local file name.
        // There is no portable way to find where the locally cached copy
        // of jenkins.war/jar is; JDK 6 is too smart. (See JENKINS-2326.)
        try {
            URL classFile = Main.class.getClassLoader().getResource("executable/Main.class");
            JarFile jf = ((JarURLConnection) classFile.openConnection()).getJarFile();
            return new File(jf.getName());
        } catch (Exception x) {
            System.err.println("ZipFile.name trick did not work, using fallback: " + x);
        }
        File myself;
        try {
            myself = Files.createTempFile(directory.toPath(), "jenkins", ".jar").toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        myself.deleteOnExit();
        try (InputStream is = Main.class.getProtectionDomain().getCodeSource().getLocation().openStream();
             OutputStream os = new FileOutputStream(myself)) {
            is.transferTo(os);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return myself;
    }

    /**
     * Extract a resource from jar, mark it for deletion upon exit, and return its location.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "User provided values for running the program.")
    private static File extractFromJar(String resource, String fileName, String suffix, File directory) {
        URL res = Main.class.getResource(resource);
        if (res == null) {
            throw new MissingResourceException("Unable to find the resource: " + resource, Main.class.getName(), resource);
        }

        // put this jar in a file system so that we can load jars from there
        File tmp;
        try {
            tmp = Files.createTempFile(directory.toPath(), fileName, suffix).toFile();
        } catch (IOException e) {
            String tmpdir = directory == null ? System.getProperty("java.io.tmpdir") : directory.getAbsolutePath();
            throw new UncheckedIOException("Jenkins failed to create a temporary file in " + tmpdir + ": " + e, e);
        }
        try (InputStream is = res.openStream(); OutputStream os = new FileOutputStream(tmp)) {
            is.transferTo(os);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tmp.deleteOnExit();
        return tmp;
    }

    /**
     * Search contents to delete in a folder that match with some patterns.
     *
     * @param folder folder where the contents are.
     * @param patterns patterns that identifies the contents to search.
     */
    private static void deleteContentsFromFolder(File folder, final String... patterns) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                for (String pattern : patterns) {
                    if (file.getName().matches(pattern)) {
                        deleteWinstoneTempContents(file);
                    }
                }
            }
        }
    }

    private static void deleteWinstoneTempContents(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) { // be defensive
                for (File value : files) {
                    deleteWinstoneTempContents(value);
                }
            }
        }
        if (!file.delete()) {
            System.err.println("Failed to delete temporary Winstone file: " + file);
        }
    }

    /**
     * Determines the home directory for Jenkins.
     * <p>
     * People makes configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "User provided values for running the program.")
    private static File getJenkinsHome() {
        // check the system property for the home directory first
        for (String name : HOME_NAMES) {
            String sysProp = System.getProperty(name);
            if (sysProp != null) {
                return new File(sysProp.trim());
            }
        }

        // look at the env var next
        for (String name : HOME_NAMES) {
            String env = System.getenv(name);
            if (env != null) {
                return new File(env.trim());
            }
        }

        // otherwise pick a place by ourselves
        File legacyHome = new File(new File(System.getProperty("user.home")), ".hudson");
        if (legacyHome.exists()) {
            return legacyHome; // before rename, this is where it was stored
        }

        return new File(new File(System.getProperty("user.home")), ".jenkins");
    }

    private static final String[] HOME_NAMES = {"JENKINS_HOME", "HUDSON_HOME"};
}
