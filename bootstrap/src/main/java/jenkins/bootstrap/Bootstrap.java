/*
 * The MIT License
 * 
 * Copyright (c) 2004-2017, CloudBees, Inc., Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Tom Huybrechts
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
package jenkins.bootstrap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Entry point when Jenkins is used as a webapp.
 *
 * <p>
 * This is a singleton component that can be injected.
 *
 * @author Kohsuke Kawaguchi
 * @see #get(ServletContext)
 */
public class Bootstrap implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(Bootstrap.class.getName());

    private static final String[] HOME_NAMES = {"JENKINS_HOME","HUDSON_HOME"};

    /**
     * Next step of the boot process.
     */
    private List<BootLogic> bootLogics = Collections.emptyList();

    private ServletContext context;

    /**
     * ClassLoader that loads Jenkins core.
     */
    private ClassLoader coreClassLoader;
    /**
     * JENKINS_HOME
     */
    private File home;
    /**
     * From a dependency that was overriden to a dependency that overrode it.
      */
    private final OverrideJournal overrides = new OverrideJournal();

    /**
     * Creates the sole instance of Jenkins and register it to the {@link ServletContext}.
     */
    public void contextInitialized(ServletContextEvent event) {
        context = event.getServletContext();
        context.setAttribute(Bootstrap.class.getName(),this);   // allow discovery of this
        try {

            final FileAndDescription describedHomeDir = getHomeDir(event);
            home = describedHomeDir.file.getAbsoluteFile();
            home.mkdirs();
            System.out.println("Jenkins home directory: "+ home +" found at: "+describedHomeDir.description);

            // check that home exists (as mkdirs could have failed silently), otherwise throw a meaningful error
            if (!home.exists())
                throw new Error("Invalid JENKINS_HOME: "+ home);

            recordBootAttempt(home);

            coreClassLoader = buildCoreClassLoader();
            Thread.currentThread().setContextClassLoader(coreClassLoader);

            bootLogics = loadBootLogics(coreClassLoader);

            for (BootLogic b : bootLogics) {
                b.contextInitialized(event);
            }
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to initialize Jenkins",e);
            throw new Error(e);
        } catch (final Throwable e) {
            LOGGER.log(SEVERE, "Failed to initialize Jenkins",e);
            throw e;
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        for (BootLogic b : bootLogics) {
            b.contextDestroyed(event);
        }
        try {
            if (coreClassLoader instanceof Closeable)
                ((Closeable)coreClassLoader).close();
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to clean up core classloader", e);
        }
    }

    /**
     * {@link BootLogic}s that were found, in the order of preference.
     */
    public List<BootLogic> getBootLogics() {
        return Collections.unmodifiableList(bootLogics);
    }

    /**
     * ClassLoader that loads Jenkins core.
     */
    public ClassLoader getCoreClassLoader() {
        return coreClassLoader;
    }

    /**
     * Location of JENKINS_HOME.
     */
    public File getHome() {
        return home;
    }

    /**
     * Obtains the record of core component overrides.
     */
    public OverrideJournal getOverrides() {
        return overrides;
    }

    private ClassLoader buildCoreClassLoader() throws IOException {
        List<URL> urls = new ArrayList<>();

        DependenciesTxt core = new DependenciesTxt(getClass().getClassLoader().getResourceAsStream("dependencies.txt"));

        File[] plugins = new File(home, "plugins").listFiles();
        if (plugins!=null) {
            for (File p : plugins) {
                String n = p.getName();
                if (p.isFile() && (n.endsWith(".jpi") || n.endsWith(".hpi"))) {
                    try {
                        new Plugin(p).process(core, urls, overrides);
                    } catch (IOException e) {
                        LOGGER.log(WARNING, "Skipping "+p, e);
                    }
                }
            }
        }

        Set<String> jars = context.getResourcePaths("/WEB-INF/jars");
        if (jars==null || jars.isEmpty()) {
            throw new IllegalStateException("No WEB-INF/jars");
        }

        for (String jar : jars) {
            if (!jar.endsWith(".jar"))
                continue;   // not a jar file

            String justName = jar.substring(jar.lastIndexOf('/')+1);

            Dependency d = core.fromFileName(justName);
            if (d==null) {
                // a jar is present in WEB-INF/lib that's not accounted for in dependencies.txt
                // perhaps somebody manually repackaged a war? let that be in the classpath so as
                // not to block a desparate attempt by an admin
                LOGGER.log(INFO, "Allowing unexpected core jar file without override check: "+jar);
            } else {
                if (overrides.isOverridden(d))
                    continue; // this jar got overriden
            }

            urls.add(context.getResource(jar));
        }

        return new URLClassLoader(urls.toArray(new URL[urls.size()]),Thread.currentThread().getContextClassLoader());
    }

    private List<BootLogic> loadBootLogics(ClassLoader cl) {
        List<BootLogic> r = new ArrayList<>();
        for (BootLogic b : ServiceLoader.load(BootLogic.class,cl)) {
            r.add(b);
        }

        r.sort((o1, o2) -> -Float.compare(o1.ordinal(), o2.ordinal()));

        if (r.isEmpty()) {
            throw new IllegalStateException("No BootLogic found. Aborting");
        }

        return r;
    }

    /**
     * To assist boot failure script, record the number of boot attempts.
     * This file gets deleted in case of successful boot.
     *
     * See {@code BootFailure} in core.
     */
    private void recordBootAttempt(File home) {
        try (FileOutputStream o=new FileOutputStream(getBootFailureFile(home), true)) {
            o.write((new Date().toString() + System.getProperty("line.separator", "\n")).getBytes());
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to record boot attempts",e);
        }
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
            String sysProp = getString(name);
            if(sysProp!=null)
                return new FileAndDescription(new File(sysProp.trim()),"SystemProperties.getProperty(\""+name+"\")");
        }

        // look at the env var next
        for (String name : HOME_NAMES) {
            String env = System.getenv(name);
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

    private String getString(String key) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if (value != null) {
            return value;
        }

        value = context.getInitParameter(key);
        if (value != null) {
            return value;
        }

        return null;
    }

    /**
     * This file captures failed boot attempts.
     * Every time we try to boot, we add the timestamp to this file,
     * then when we boot, the file gets deleted.
     */
    public static File getBootFailureFile(File home) {
        return new File(home, "failed-boot-attempts.txt");
    }

    /**
     * Access the sole instance of {@link Bootstrap} constructed during the boot up
     */
    public static Bootstrap get(ServletContext context) {
        return (Bootstrap) context.getAttribute(Bootstrap.class.getName());
    }
}
