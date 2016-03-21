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
import jenkins.util.SystemProperties;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import com.trilead.ssh2.util.IOUtils;
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
import org.kohsuke.stapler.jelly.JellyFacet;
import org.apache.tools.ant.types.FileSet;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletResponse;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.Security;
import java.util.logging.LogRecord;

import static java.util.logging.Level.*;

/**
 * Entry point when Hudson is used as a webapp.
 *
 * @author Kohsuke Kawaguchi
 */
public class WebAppMain implements ServletContextListener {
    private final RingBufferLogHandler handler = new RingBufferLogHandler() {
        @Override public synchronized void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                super.publish(record);
            }
        }
    };
    private static final String APP = "app";
    private boolean terminated;
    private Thread initThread;

    /**
     * Creates the sole instance of {@link jenkins.model.Jenkins} and register it to the {@link ServletContext}.
     */
    public void contextInitialized(ServletContextEvent event) {
        JenkinsJVMAccess._setJenkinsJVM(true);
        final ServletContext context = event.getServletContext();
        File home=null;
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
                new URLClassLoader(new URL[0],getClass().getClassLoader());
            } catch(SecurityException e) {
                throw new InsufficientPermissionDetected(e);
            }

            try {// remove Sun PKCS11 provider if present. See http://wiki.jenkins-ci.org/display/JENKINS/Solaris+Issue+6276483
                Security.removeProvider("SunPKCS11-Solaris");
            } catch (SecurityException e) {
                // ignore this error.
            }

            installLogger();

            final FileAndDescription describedHomeDir = getHomeDir(event);
            home = describedHomeDir.file.getAbsoluteFile();
            home.mkdirs();
            System.out.println("Jenkins home directory: "+home+" found at: "+describedHomeDir.description);

            // check that home exists (as mkdirs could have failed silently), otherwise throw a meaningful error
            if (!home.exists())
                throw new NoHomeDir(home);

            recordBootAttempt(home);

            // make sure that we are using XStream in the "enhanced" (JVM-specific) mode
            if(jvm.bestReflectionProvider().getClass()==PureJavaReflectionProvider.class) {
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
                ServletResponse.class.getMethod("setCharacterEncoding",String.class);
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
            if(ChartUtil.awtProblemCause!=null) {
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
                LOGGER.log(WARNING, "XSLT not configured correctly. Hudson will try to fix this. See http://issues.apache.org/bugzilla/show_bug.cgi?id=40895 for more details",x);
                System.setProperty(TransformerFactory.class.getName(),"com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
                try {
                    TransformerFactory.newInstance();
                    LOGGER.info("XSLT is set to the JAXP RI in JRE");
                } catch(TransformerFactoryConfigurationError y) {
                    LOGGER.log(SEVERE, "Failed to correct the problem.");
                }
            }

            installExpressionFactory(event);

            context.setAttribute(APP,new HudsonIsLoading());

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
                        new HudsonFailedToLoad(e).publish(context,_home);
                        throw e;
                    } catch (Exception e) {
                        new HudsonFailedToLoad(e).publish(context,_home);
                    } finally {
                        Jenkins instance = Jenkins.getInstanceOrNull();
                        if(!success && instance!=null)
                            instance.cleanUp();
                    }
                }
            };
            initThread.start();
        } catch (BootFailure e) {
            e.publish(context, home);
        } catch (Error | RuntimeException e) {
            LOGGER.log(SEVERE, "Failed to initialize Jenkins",e);
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
        FileOutputStream o=null;
        try {
            o = new FileOutputStream(BootFailure.getBootFailureFile(home), true);
            o.write((new Date().toString() + System.getProperty("line.separator", "\n")).toString().getBytes());
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to record boot attempts",e);
        } finally {
            IOUtils.closeQuietly(o);
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
        Jenkins.logRecords = handler.getView();
        Logger.getLogger("").addHandler(handler);
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

    /**
     * Determines the home directory for Jenkins.
     *
     * <p>
     * We look for a setting that affects the smallest scope first, then bigger ones later.
     *
     * <p>
     * People makes configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     * 
     * <p>
     * @return the File alongside with some description to help the user troubleshoot issues
     */
    public FileAndDescription getHomeDir(ServletContextEvent event) {
        // check JNDI for the home directory first
        for (String name : HOME_NAMES) {
            try {
                InitialContext iniCtxt = new InitialContext();
                Context env = (Context) iniCtxt.lookup("java:comp/env");
                String value = (String) env.lookup(name);
                if(value!=null && value.trim().length()>0)
                    return new FileAndDescription(new File(value.trim()),"JNDI/java:comp/env/"+name);
                // look at one more place. See issue #1314
                value = (String) iniCtxt.lookup(name);
                if(value!=null && value.trim().length()>0)
                    return new FileAndDescription(new File(value.trim()),"JNDI/"+name);
            } catch (NamingException e) {
                // ignore
            }
        }

        // next the system property
        for (String name : HOME_NAMES) {
            String sysProp = SystemProperties.getString(name);
            if(sysProp!=null)
                return new FileAndDescription(new File(sysProp.trim()),"SystemProperties.getProperty(\""+name+"\")");
        }

        // look at the env var next
        for (String name : HOME_NAMES) {
            String env = EnvVars.masterEnvVars.get(name);
            if(env!=null)
                return new FileAndDescription(new File(env.trim()).getAbsoluteFile(),"EnvVars.masterEnvVars.get(\""+name+"\")");
        }

        // otherwise pick a place by ourselves

        String root = event.getServletContext().getRealPath("/WEB-INF/workspace");
        if(root!=null) {
            File ws = new File(root.trim());
            if(ws.exists())
                // Hudson <1.42 used to prefer this before ~/.hudson, so
                // check the existence and if it's there, use it.
                // otherwise if this is a new installation, prefer ~/.hudson
                return new FileAndDescription(ws,"getServletContext().getRealPath(\"/WEB-INF/workspace\")");
        }

        File legacyHome = new File(new File(System.getProperty("user.home")),".hudson");
        if (legacyHome.exists()) {
            return new FileAndDescription(legacyHome,"$user.home/.hudson"); // before rename, this is where it was stored
        }

        File newHome = new File(new File(System.getProperty("user.home")),".jenkins");
        return new FileAndDescription(newHome,"$user.home/.jenkins");
    }

    public void contextDestroyed(ServletContextEvent event) {
        try (ACLContext old = ACL.as(ACL.SYSTEM)) {
            terminated = true;
            Jenkins instance = Jenkins.getInstanceOrNull();
            if (instance != null)
                instance.cleanUp();
            Thread t = initThread;
            if (t != null && t.isAlive()) {
                LOGGER.log(Level.INFO, "Shutting down a Jenkins instance that was still starting up", new Throwable("reason"));
                t.interrupt();
            }

            // Logger is in the system classloader, so if we don't do this
            // the whole web app will never be undepoyed.
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

    private static final String[] HOME_NAMES = {"JENKINS_HOME","HUDSON_HOME"};
}
