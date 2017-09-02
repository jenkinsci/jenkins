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

import hudson.security.ACLContext;
import hudson.security.HudsonFilter;
import hudson.security.csrf.CrumbFilter;
import hudson.util.CharacterEncodingFilter;
import hudson.util.PluginServletFilter;
import jenkins.ContextClassLoaderFilter;
import jenkins.JenkinsHttpSessionListener;
import jenkins.bootstrap.BootLogic;
import jenkins.bootstrap.Bootstrap;
import jenkins.util.SystemProperties;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import hudson.model.Hudson;
import hudson.security.ACL;
import hudson.util.BootFailure;
import jenkins.model.Jenkins;
import hudson.util.HudsonIsLoading;
import hudson.util.IncompatibleServletVersionDetected;
import hudson.util.IncompatibleVMDetected;
import hudson.util.InsufficientPermissionDetected;
import hudson.util.NoHomeDir;
import hudson.util.RingBufferLogHandler;
import hudson.util.NoTempDir;
import hudson.util.IncompatibleAntVersionDetected;
import hudson.util.HudsonFailedToLoad;
import hudson.util.ChartUtil;
import hudson.util.AWTProblem;
import jenkins.util.JenkinsJVM;
import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.MetaInfServices;
import org.kohsuke.stapler.DiagnosticThreadNameFilter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.compression.CompressionFilter;
import org.kohsuke.stapler.jelly.JellyFacet;
import org.apache.tools.ant.types.FileSet;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.ServletResponse;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.Security;
import java.util.logging.LogRecord;

import static java.util.logging.Level.*;

/**
 * Entry point when Jenkins is used as a webapp.
 *
 * @author Kohsuke Kawaguchi
 */
@MetaInfServices
public class WebAppMain implements BootLogic {
    private RingBufferLogHandler handler;

    private static final String APP = "app";
    private boolean terminated;
    private Thread initThread;
    private ServletContext context;

    @Override
    public float ordinal() {
        return 0;
    }

    /**
     * Entry point for jenkins-test-harness to set up filters &amp; servlets.
     */
    public void initForTest(ServletContext context) {
        this.context = context;
        setupServlet();
        setupFilters();
    }

    /**
     * Creates the sole instance of {@link jenkins.model.Jenkins} and register it to the {@link ServletContext}.
     */
    public void contextInitialized(ServletContextEvent event) {
        JenkinsJVMAccess._setJenkinsJVM(true);
        context = event.getServletContext();

        registerToServletContainer();

        final File home = getHome();
        try {

            // use the current request to determine the language
            LocaleProvider.setProvider(new LocaleProvider() {
                public Locale get() {
                    return Functions.getCurrentLocale();
                }
            });

            // quick check to see if we (seem to) have enough permissions to run. (see #719)
            JVM jvm;
            try {
                jvm = new JVM();
                new URLClassLoader(new URL[0], getClass().getClassLoader());
            } catch (SecurityException e) {
                throw new InsufficientPermissionDetected(e);
            }

            try {// remove Sun PKCS11 provider if present. See http://wiki.jenkins-ci.org/display/JENKINS/Solaris+Issue+6276483
                Security.removeProvider("SunPKCS11-Solaris");
            } catch (SecurityException e) {
                // ignore this error.
            }

            installLogger();

            // check that home exists (as mkdirs could have failed silently), otherwise throw a meaningful error
            if (!home.exists())
                throw new NoHomeDir(home);

            recordBootAttempt(home);

            // make sure that we are using XStream in the "enhanced" (JVM-specific) mode
            if (jvm.bestReflectionProvider().getClass() == PureJavaReflectionProvider.class) {
                throw new IncompatibleVMDetected(); // nope
            }

//  JNA is no longer a hard requirement. It's just nice to have. See HUDSON-4820 for more context.
//            // make sure JNA works. this can fail if
//            //    - platform is unsupported
//            //    - JNA is already loaded in another classloader
//            // see http://wiki.jenkins-ci.org/display/JENKINS/JNA+is+already+loaded
//            // TODO: or shall we instead modify Hudson to work gracefully without JNA?
//            try {
//                /*
//                    java.lang.UnsatisfiedLinkError: Native Library /builds/apps/glassfish/domains/hudson-domain/generated/jsp/j2ee-modules/hudson-1.309/loader/com/sun/jna/sunos-sparc/libjnidispatch.so already loaded in another classloader
//                        at java.lang.ClassLoader.loadLibrary0(ClassLoader.java:1743)
//                        at java.lang.ClassLoader.loadLibrary(ClassLoader.java:1674)
//                        at java.lang.Runtime.load0(Runtime.java:770)
//                        at java.lang.System.load(System.java:1005)
//                        at com.sun.jna.Native.loadNativeLibraryFromJar(Native.java:746)
//                        at com.sun.jna.Native.loadNativeLibrary(Native.java:680)
//                        at com.sun.jna.Native.<clinit>(Native.java:108)
//                        at hudson.util.jna.GNUCLibrary.<clinit>(GNUCLibrary.java:86)
//                        at hudson.Util.createSymlink(Util.java:970)
//                        at hudson.model.Run.run(Run.java:1174)
//                        at hudson.matrix.MatrixBuild.run(MatrixBuild.java:149)
//                        at hudson.model.ResourceController.execute(ResourceController.java:88)
//                        at hudson.model.Executor.run(Executor.java:123)
//                 */
//                String.valueOf(Native.POINTER_SIZE); // this meaningless operation forces the classloading and initialization
//            } catch (LinkageError e) {
//                if (e.getMessage().contains("another classloader"))
//                    context.setAttribute(APP,new JNADoublyLoaded(e));
//                else
//                    context.setAttribute(APP,new HudsonFailedToLoad(e));
//            }

            // make sure this is servlet 2.4 container or above
            try {
                ServletResponse.class.getMethod("setCharacterEncoding", String.class);
            } catch (NoSuchMethodException e) {
                throw new IncompatibleServletVersionDetected(ServletResponse.class);
            }

            // make sure that we see Ant 1.7
            try {
                FileSet.class.getMethod("getDirectoryScanner");
            } catch (NoSuchMethodException e) {
                throw new IncompatibleAntVersionDetected(FileSet.class);
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
                f.delete();
            } catch (IOException e) {
                throw new NoTempDir(e);
            }

            // Tomcat breaks XSLT with JDK 5.0 and onward. Check if that's the case, and if so,
            // try to correct it
            try {
                TransformerFactory.newInstance();
                // if this works we are all happy
            } catch (TransformerFactoryConfigurationError x) {
                // no it didn't.
                LOGGER.log(WARNING, "XSLT not configured correctly. Hudson will try to fix this. See http://issues.apache.org/bugzilla/show_bug.cgi?id=40895 for more details", x);
                System.setProperty(TransformerFactory.class.getName(), "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
                try {
                    TransformerFactory.newInstance();
                    LOGGER.info("XSLT is set to the JAXP RI in JRE");
                } catch (TransformerFactoryConfigurationError y) {
                    LOGGER.log(SEVERE, "Failed to correct the problem.");
                }
            }

            installExpressionFactory(event);

            context.setAttribute(APP, new HudsonIsLoading());

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

                        BootFailure.getBootFailureFile(_home).delete();

                        // at this point we are open for business and serving requests normally
                        LOGGER.info("Jenkins is fully up and running");
                        success = true;
                    } catch (Error e) {
                        new HudsonFailedToLoad(e).publish(context, _home);
                        throw e;
                    } catch (Exception e) {
                        new HudsonFailedToLoad(e).publish(context, _home);
                    } finally {
                        Jenkins instance = Jenkins.getInstanceOrNull();
                        if (!success && instance != null)
                            instance.cleanUp();
                    }
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

    /**
     * Register callbacks and hooks to the servlet container
     */
    protected void registerToServletContainer() {
        setupServlet();
        setupFilters();
        context.addListener(new JenkinsHttpSessionListener());
    }

    protected File getHome() {
        return Bootstrap.get(context).getHome();
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
        try (OutputStream o=Files.newOutputStream(BootFailure.getBootFailureFile(home).toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            o.write((new Date().toString() + System.getProperty("line.separator", "\n")).toString().getBytes());
        } catch (IOException | InvalidPathException e) {
            LOGGER.log(WARNING, "Failed to record boot attempts",e);
        }
    }

    public static void installExpressionFactory(ServletContextEvent event) {
        JellyFacet.setExpressionFactory(event, new ExpressionFactory2());
    }

	/**
     * Installs log handler to monitor all Hudson logs.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE")
    private void installLogger() {
        // use RingBufferLogHandler class name to configure for backward compatibility
        int DEFAULT_RING_BUFFER_SIZE = SystemProperties.getInteger(RingBufferLogHandler.class.getName() + ".defaultSize", 256);

        handler = new RingBufferLogHandler(DEFAULT_RING_BUFFER_SIZE) {
            @Override public synchronized void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                    super.publish(record);
                }
            }
        };

        Jenkins.logRecords = handler.getView();
        Logger.getLogger("").addHandler(handler);
    }

    /**
     * Registers servlets.
     */
    private void setupServlet() {
        Dynamic r = context.addServlet("Stapler", Stapler.class);
        r.setInitParameter("default-encodings","text/html=UTF-8");
        r.setInitParameter("diagnosticThreadName","false");
        r.setAsyncSupported(true);
        r.addMapping("/*");
    }

    private void setupFilters() {
        // TODO: should be turned into a proper ExtensionPoint, and clear up PluginServletFilter
        addFilter(ContextClassLoaderFilter.class);
        addFilter(DiagnosticThreadNameFilter.class);
        addFilter(CharacterEncodingFilter.class);
        addFilter(CompressionFilter.class);
        addFilter(HudsonFilter.class);
        addFilter(CrumbFilter.class);
        addFilter(PluginServletFilter.class);
    }

    private void addFilter(Class<? extends Filter> f) {
        FilterRegistration.Dynamic r = context.addFilter(f.getName(), f);
        r.setAsyncSupported(true);
        r.addMappingForUrlPatterns(null, true, "/*");
    }

    /** Add some metadata to a File, allowing to trace setup issues */
    public static class FileAndDescription {
        public final File file;
        public final String description;
        public FileAndDescription(File file,String description) {
            this.file = file;
            this.description = description;
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        try (ACLContext old = ACL.as(ACL.SYSTEM)) {
            Jenkins instance = Jenkins.getInstanceOrNull();
            try {
                if (instance != null) {
                    instance.cleanUp();
                }
            } catch (Exception e) {
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
            if (handler!=null)
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
}
