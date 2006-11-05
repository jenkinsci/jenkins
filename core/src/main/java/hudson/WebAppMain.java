package hudson;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.triggers.Trigger;
import hudson.util.IncompatibleVMDetected;
import hudson.util.RingBufferLogHandler;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Entry point when Hudson is used as a webapp.
 *
 * @author Kohsuke Kawaguchi
 */
public class WebAppMain implements ServletContextListener {

    /**
     * Creates the sole instance of {@link Hudson} and register it to the {@link ServletContext}.
     */
    public void contextInitialized(ServletContextEvent event) {
        installLogger();

        File home = getHomeDir(event);
        home.mkdirs();
        System.out.println("hudson home directory: "+home);

        ServletContext context = event.getServletContext();

        // make sure that we are using XStream in the "enhenced" (JVM-specific) mode
        if(new JVM().bestReflectionProvider().getClass()==PureJavaReflectionProvider.class) {
            // nope
            context.setAttribute("app",new IncompatibleVMDetected());
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
        Object ver = props.get("version");
        if(ver==null)   ver="?";
        context.setAttribute("version",ver);

        Trigger.init(); // start running trigger

        // trigger the loading of changelogs in the background,
        // but give the system 10 seconds so that the first page
        // can be served quickly
        Trigger.timer.schedule(new TimerTask() {
            public void run() {
                User.get("nobody").getBuilds();
            }
        }, 1000*10);

    }

    /**
     * Installs log handler to monitor all Hudson logs.
     */
    private void installLogger() {
        RingBufferLogHandler handler = new RingBufferLogHandler();
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
                return new File(value.trim());
        } catch (NamingException e) {
            // ignore
        }

        // look at the env var next
        String env = EnvVars.masterEnvVars.get("HUDSON_HOME");
        if(env!=null)
            return new File(env.trim());

        // finally check the system property
        String sysProp = System.getProperty("HUDSON_HOME");
        if(sysProp!=null)
            return new File(sysProp.trim());

        // otherwise pick a place by ourselves

        String root = event.getServletContext().getRealPath("/WEB-INF/workspace");
        if(root!=null) {
            File ws = new File(root.trim());
            if(ws.exists())
                // Hudson <1.42 used to prefer this betfore ~/.hudson, so
                // check the existence and if it's there, use it.
                // otherwise if this is a new installation, prefer ~/.hudson
                return ws;
        }

        // if for some reason we can't put it within the webapp, use home directory.
        return new File(new File(System.getProperty("user.home")),".hudson");
    }

    public void contextDestroyed(ServletContextEvent event) {
        Hudson instance = Hudson.getInstance();
        if(instance!=null)
            instance.cleanUp();
    }
}
