package hudson;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.triggers.Trigger;
import hudson.triggers.SafeTimerTask;
import hudson.util.IncompatibleServletVersionDetected;
import hudson.util.IncompatibleVMDetected;
import hudson.util.RingBufferLogHandler;
import hudson.util.NoHomeDir;

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
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point when Hudson is used as a webapp.
 *
 * @author Kohsuke Kawaguchi
 */
public class WebAppMain implements ServletContextListener {
    private final RingBufferLogHandler handler = new RingBufferLogHandler();

    /**
     * Creates the sole instance of {@link Hudson} and register it to the {@link ServletContext}.
     */
    public void contextInitialized(ServletContextEvent event) {
        installLogger();

        ServletContext context = event.getServletContext();

        File home = getHomeDir(event);
        home.mkdirs();
        System.out.println("hudson home directory: "+home);

        // check that home exists (as mkdirs could have failed silently), otherwise throw a meaningful error
        if (! home.exists()) {
            context.setAttribute("app",new NoHomeDir(home));
            return;
        }

        // make sure that we are using XStream in the "enhanced" (JVM-specific) mode
        if(new JVM().bestReflectionProvider().getClass()==PureJavaReflectionProvider.class) {
            // nope
            context.setAttribute("app",new IncompatibleVMDetected());
            return;
        }

        // make sure this is servlet 2.4 container or above
        try {
            ServletResponse.class.getMethod("setCharacterEncoding",String.class);
        } catch (NoSuchMethodException e) {
            context.setAttribute("app,",new IncompatibleServletVersionDetected());
            return;
        }

        // Tomcat breaks XSLT with JDK 5.0 and onward. Check if that's the case, and if so,
        // try to correct it
        try {
            TransformerFactory.newInstance();
            // if this works we are all happy
        } catch (TransformerFactoryConfigurationError x) {
            // no it didn't.
            Logger logger = Logger.getLogger(WebAppMain.class.getName());

            logger.log(Level.WARNING, "XSLT not configured correctly. Hudson will try to fix this. See http://issues.apache.org/bugzilla/show_bug.cgi?id=40895 for more details",x);
            System.setProperty(TransformerFactory.class.getName(),"com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
            try {
                TransformerFactory.newInstance();
                logger.info("XSLT is set to the JAXP RI in JRE");
            } catch(TransformerFactoryConfigurationError y) {
                logger.log(Level.SEVERE, "Failed to correct the problem.");
            }
        }


        try {
            context.setAttribute("app",new Hudson(home,context));
        } catch( IOException e ) {
            throw new Error(e);
        }

        // set the version
        Properties props = new Properties();
        try {
            InputStream is = getClass().getResourceAsStream("hudson-version.properties");
            if(is!=null)
                props.load(is);
        } catch (IOException e) {
            e.printStackTrace(); // if the version properties is missing, that's OK.
        }
        String ver = props.getProperty("version");
        if(ver==null)   ver="?";
        Hudson.VERSION = ver;
        context.setAttribute("version",ver);

        if(ver.equals("?"))
            Hudson.RESOURCE_PATH = "";
        else
            Hudson.RESOURCE_PATH = "/static/"+Util.getDigestOf(ver).substring(0,8);

        Trigger.init(); // start running trigger

        // trigger the loading of changelogs in the background,
        // but give the system 10 seconds so that the first page
        // can be served quickly
        Trigger.timer.schedule(new SafeTimerTask() {
            public void doRun() {
                User.get("nobody").getBuilds();
            }
        }, 1000*10);

    }

    /**
     * Installs log handler to monitor all Hudson logs.
     */
    private void installLogger() {
        Hudson.logRecords = handler.getView();
        Logger.getLogger("hudson").addHandler(handler);
    }

    /**
     * Determines the home directory for Hudson.
     *
     * People makes configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     */
    private File getHomeDir(ServletContextEvent event) {
        // check JNDI for the home directory first
        try {
            Context env = (Context) new InitialContext().lookup("java:comp/env");
            String value = (String) env.lookup("HUDSON_HOME");
            if(value!=null && value.trim().length()>0)
                return new File(value.trim()).getAbsoluteFile();
        } catch (NamingException e) {
            // ignore
        }

        // look at the env var next
        String env = EnvVars.masterEnvVars.get("HUDSON_HOME");
        if(env!=null)
            return new File(env.trim()).getAbsoluteFile();

        // finally check the system property
        String sysProp = System.getProperty("HUDSON_HOME");
        if(sysProp!=null)
            return new File(sysProp.trim()).getAbsoluteFile();

        // otherwise pick a place by ourselves

        String root = event.getServletContext().getRealPath("/WEB-INF/workspace");
        if(root!=null) {
            File ws = new File(root.trim());
            if(ws.exists())
                // Hudson <1.42 used to prefer this before ~/.hudson, so
                // check the existence and if it's there, use it.
                // otherwise if this is a new installation, prefer ~/.hudson
                return ws.getAbsoluteFile();
        }

        // if for some reason we can't put it within the webapp, use home directory.
        return new File(new File(System.getProperty("user.home")),".hudson").getAbsoluteFile();
    }

    public void contextDestroyed(ServletContextEvent event) {
        Hudson instance = Hudson.getInstance();
        if(instance!=null)
            instance.cleanUp();

        // Logger is in the system classloader, so if we don't do this
        // the whole web app will never be undepoyed.
        Logger.getLogger("hudson").removeHandler(handler);
    }
}
