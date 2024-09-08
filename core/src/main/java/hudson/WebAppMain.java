/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Tom Huybrechts
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

package hudson;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import hudson.model.Hudson;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.AWTProblem;
import hudson.util.BootFailure;
import hudson.util.ChartUtil;
import hudson.util.HudsonFailedToLoad;
import hudson.util.HudsonIsLoading;
import hudson.util.IncompatibleAntVersionDetected;
import hudson.util.IncompatibleServletVersionDetected;
import hudson.util.IncompatibleVMDetected;
import hudson.util.InsufficientPermissionDetected;
import hudson.util.NoHomeDir;
import hudson.util.NoTempDir;
import hudson.util.RingBufferLogHandler;
import io.jenkins.servlet.ServletContextEventWrapper;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.SessionTrackingMode;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.security.Security;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.JenkinsJVM;
import jenkins.util.SystemProperties;
import org.apache.tools.ant.types.FileSet;
import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.jelly.JellyFacet;

/**
 * Entry point when Hudson is used as a webapp.
 *
 * @author Kohsuke Kawaguchi
 */
public class WebAppMain implements ServletContextListener {

    /**
     * System property name to force the session tracking by cookie.
     * This prevents Tomcat to use the URL tracking in addition to the cookie by default.
     * This could be useful for instances that requires to have
     * the {@link jenkins.security.SuspiciousRequestFilter#allowSemicolonsInPath} turned off.
     * <p>
     * If you allow semicolon in URL and the session to be tracked by URL and you have
     * a SecurityRealm that does not invalidate session after authentication,
     * your instance is vulnerable to session hijacking.
     * <p>
     * The SecurityRealm should be corrected but this is a hardening in Jenkins core.
     * <p>
     * As this property is read during startup, you will not be able to change it at runtime
     * depending on your application server (not possible with Jetty nor Tomcat)
     * <p>
     * When running hpi:run, the default tracking is COOKIE+URL.
     * When running java -jar with Winstone/Jetty, the default setting is set to COOKIE only.
     * When running inside Tomcat, the default setting is COOKIE+URL.
     */
    @Restricted(NoExternalUse.class)
    public static final String FORCE_SESSION_TRACKING_BY_COOKIE_PROP = WebAppMain.class.getName() + ".forceSessionTrackingByCookie";

    private final RingBufferLogHandler handler = new RingBufferLogHandler(WebAppMain.getDefaultRingBufferSize()) {

        @Override public synchronized void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                super.publish(record);
            }
        }
    };

    /**
     * This getter returns the int DEFAULT_RING_BUFFER_SIZE from the class RingBufferLogHandler from a static context.
     * Exposes access from RingBufferLogHandler.DEFAULT_RING_BUFFER_SIZE to WebAppMain.
     * Written for the requirements of JENKINS-50669
     * @return int This returns DEFAULT_RING_BUFFER_SIZE
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-50669">JENKINS-50669</a>
     * @since 2.259
     */
    public static int getDefaultRingBufferSize() {
        return RingBufferLogHandler.getDefaultRingBufferSize();
    }

    private static final String APP = "app";
    private boolean terminated;
    private Thread initThread;

    /**
     * Creates the sole instance of {@link jenkins.model.Jenkins} and register it to the {@link ServletContext}.
     */
    @Override
    public void contextInitialized(ServletContextEvent event) {
        // Nicer console log formatting when using mvn jetty:run.
        if (Main.isDevelopmentMode && System.getProperty("java.util.logging.config.file") == null) {
            try {
                Formatter formatter = (Formatter) Class.forName("io.jenkins.lib.support_log_formatter.SupportLogFormatter").getDeclaredConstructor().newInstance();
                for (Handler h : Logger.getLogger("").getHandlers()) {
                    if (h instanceof ConsoleHandler) {
                        h.setFormatter(formatter);
                    }
                }
            } catch (ClassNotFoundException x) {
                // ignore
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }

        JenkinsJVMAccess._setJenkinsJVM(true);
        final ServletContext context = event.getServletContext();
        File home = null;
        try {

            // use the current request to determine the language
            LocaleProvider.setProvider(new LocaleProvider() {
                @Override
                public Locale get() {
                    return Functions.getCurrentLocale();
                }
            });

            // quick check to see if we (seem to) have enough permissions to run. (see JENKINS-719)
            JVM jvm;
            try {
                jvm = new JVM();
                new URLClassLoader(new URL[0], getClass().getClassLoader());
            } catch (SecurityException e) {
                throw new InsufficientPermissionDetected(e);
            }

            try { // remove Sun PKCS11 provider if present. See http://wiki.jenkins-ci.org/display/JENKINS/Solaris+Issue+6276483
                Security.removeProvider("SunPKCS11-Solaris");
            } catch (SecurityException e) {
                // ignore this error.
            }

            installLogger();

            final FileAndDescription describedHomeDir = getHomeDir(event);
            home = describedHomeDir.file.getAbsoluteFile();
            try {
                Util.createDirectories(home.toPath());
            } catch (IOException | InvalidPathException e) {
                throw (NoHomeDir) new NoHomeDir(home).initCause(e);
            }
            LOGGER.info("Jenkins home directory: " + home + " found at: " + describedHomeDir.description);

            recordBootAttempt(home);

            // make sure that we are using XStream in the "enhanced" (JVM-specific) mode
            if (jvm.bestReflectionProvider().getClass() == PureJavaReflectionProvider.class) {
                throw new IncompatibleVMDetected(); // nope
            }

            // make sure this is servlet 2.4 container or above
            try {
                ServletResponse.class.getMethod("setCharacterEncoding", String.class);
            } catch (NoSuchMethodException e) {
                throw (IncompatibleServletVersionDetected) new IncompatibleServletVersionDetected(ServletResponse.class).initCause(e);
            }

            // make sure that we see Ant 1.7
            try {
                FileSet.class.getMethod("getDirectoryScanner");
            } catch (NoSuchMethodException e) {
                throw (IncompatibleAntVersionDetected) new IncompatibleAntVersionDetected(FileSet.class).initCause(e);
            }

            // make sure AWT is functioning, or else JFreeChart won't even load.
            if (ChartUtil.awtProblemCause != null) {
                throw new AWTProblem(ChartUtil.awtProblemCause);
            }

            // some containers (in particular Tomcat) doesn't abort a launch
            // even if the temp directory doesn't exist.
            // check that and report an error
            try {
                File f = File.createTempFile("test", "test");
                boolean result = f.delete();
                if (!result) {
                    LOGGER.log(FINE, "Temp file test.test could not be deleted.");
                }
            } catch (IOException e) {
                throw new NoTempDir(e);
            }

            installExpressionFactory(event);

            context.setAttribute(APP, new HudsonIsLoading());
            if (SystemProperties.getBoolean(FORCE_SESSION_TRACKING_BY_COOKIE_PROP, true)) {
                context.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
            }

            final File _home = home;
            initThread = new Thread("Jenkins initialization thread") {
                @Override
                public void run() {
                    boolean success = false;
                    try {
                        Jenkins instance = new Hudson(_home, context);

                        // one last check to make sure everything is in order before we go live
                        if (Thread.interrupted())
                            throw new InterruptedException();

                        context.setAttribute(APP, instance);

                        Files.deleteIfExists(BootFailure.getBootFailureFile(_home).toPath());

                        // at this point we are open for business and serving requests normally
                        Jenkins.get().getLifecycle().onReady();
                        success = true;
                    } catch (Error e) {
                        new HudsonFailedToLoad(e).publish(context, _home);
                        throw e;
                    } catch (Exception e) {
                        // Allow plugins to override error page on boot with custom BootFailure subclass thrown
                        Throwable error = unwrapException(e);
                        if (error instanceof InvocationTargetException) {
                            Throwable targetException = ((InvocationTargetException) error).getTargetException();

                            if (targetException instanceof BootFailure) {
                                ((BootFailure) targetException).publish(context, _home);
                            } else {
                                new HudsonFailedToLoad(e).publish(context, _home);
                            }
                        } else {
                            new HudsonFailedToLoad(e).publish(context, _home);
                        }
                    } finally {
                        Jenkins instance = Jenkins.getInstanceOrNull();
                        if (!success && instance != null)
                            instance.cleanUp();
                    }
                }

                private Throwable unwrapException(Exception e) {
                    Throwable error = e;
                    while (error.getCause() != null) {
                        if (error.getCause() instanceof InvocationTargetException) {
                            return error.getCause();
                        }
                        error = error.getCause();
                    }
                    return error;
                }
            };
            initThread.start();
        } catch (BootFailure e) {
            e.publish(context, home);
        } catch (Error | RuntimeException e) {
            LOGGER.log(SEVERE, "Failed to initialize Jenkins", e);
            throw e;
        }
    }

    public void joinInit() throws InterruptedException {
        initThread.join();
    }

    /**
     * To assist boot failure script, record the number of boot attempts.
     * This file gets deleted in case of successful boot.
     *
     * @see BootFailure
     */
    private void recordBootAttempt(File home) {
        try (OutputStream o = Files.newOutputStream(BootFailure.getBootFailureFile(home).toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            o.write((new Date() + System.getProperty("line.separator", "\n")).getBytes(Charset.defaultCharset()));
        } catch (IOException | InvalidPathException e) {
            LOGGER.log(WARNING, "Failed to record boot attempts", e);
        }
    }

    /**
     * @since TODO
     */
    public static void installExpressionFactory(ServletContextEvent event) {
        JellyFacet.setExpressionFactory(event, new ExpressionFactory2());
    }

    /**
     * @deprecated use {@link #installExpressionFactory(ServletContextEvent)}
     */
    @Deprecated
    public static void installExpressionFactory(javax.servlet.ServletContextEvent event) {
        installExpressionFactory(ServletContextEventWrapper.toJakartaServletContextEvent(event));
    }

    /**
     * Installs log handler to monitor all Hudson logs.
     */
    private void installLogger() {
        Jenkins.logRecords = handler.getView();
        Logger.getLogger("").addHandler(handler);
    }

    /** Add some metadata to a File, allowing to trace setup issues */
    public static class FileAndDescription {
        public final File file;
        public final String description;

        public FileAndDescription(File file, String description) {
            this.file = file;
            this.description = description;
        }
    }

    /**
     * Determines the home directory for Jenkins.
     *
     * <p>We look for a setting that affects the smallest scope first, then bigger ones later.
     *
     * <p>People make configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     *
     * @return the File alongside with some description to help the user troubleshoot issues
     */
    public FileAndDescription getHomeDir(ServletContextEvent event) {
        // check the system property for the home directory first
        for (String name : HOME_NAMES) {
            String sysProp = SystemProperties.getString(name);
            if (sysProp != null)
                return new FileAndDescription(new File(sysProp.trim()), "SystemProperties.getProperty(\"" + name + "\")");
        }

        // look at the env var next
        for (String name : HOME_NAMES) {
            String env = EnvVars.masterEnvVars.get(name);
            if (env != null)
                return new FileAndDescription(new File(env.trim()).getAbsoluteFile(), "EnvVars.masterEnvVars.get(\"" + name + "\")");
        }

        // otherwise pick a place by ourselves

        String root = event.getServletContext().getRealPath("/WEB-INF/workspace");
        if (root != null) {
            File ws = new File(root.trim());
            if (ws.exists())
                // Hudson <1.42 used to prefer this before ~/.hudson, so
                // check the existence and if it's there, use it.
                // otherwise if this is a new installation, prefer ~/.hudson
                return new FileAndDescription(ws, "getServletContext().getRealPath(\"/WEB-INF/workspace\")");
        }

        File legacyHome = new File(new File(System.getProperty("user.home")), ".hudson");
        if (legacyHome.exists()) {
            return new FileAndDescription(legacyHome, "$user.home/.hudson"); // before rename, this is where it was stored
        }

        File newHome = new File(new File(System.getProperty("user.home")), ".jenkins");
        return new FileAndDescription(newHome, "$user.home/.jenkins");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        try (ACLContext old = ACL.as2(ACL.SYSTEM2)) {
            Jenkins instance = Jenkins.getInstanceOrNull();
            try {
                if (instance != null) {
                    instance.cleanUp();
                }
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to clean up. Restart will continue.", e);
            }

            terminated = true;
            Thread t = initThread;
            if (t != null && t.isAlive()) {
                LOGGER.log(Level.INFO, "Shutting down a Jenkins instance that was still starting up", new Throwable("reason"));
                t.interrupt();
            }

            // Logger is in the system classloader, so if we don't do this
            // the whole web app will never be undeployed.
            Logger.getLogger("").removeHandler(handler);
        } finally {
            JenkinsJVMAccess._setJenkinsJVM(false);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(WebAppMain.class.getName());

    private static final class JenkinsJVMAccess extends JenkinsJVM {
        private static void _setJenkinsJVM(boolean jenkinsJVM) {
            JenkinsJVM.setJenkinsJVM(jenkinsJVM);
        }
    }

    private static final String[] HOME_NAMES = {"JENKINS_HOME", "HUDSON_HOME"};
}
