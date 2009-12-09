package hudson.init;

import org.kohsuke.MetaInfServices;
import org.jvnet.hudson.reactor.Task;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import hudson.PluginManager;
import hudson.util.Service;

/**
 * Strategy pattern of the various key decision making during the Hudson initialization.
 *
 * <p>
 * Because the act of initializing plugins is a part of the Hudson initialization,
 * this extension point cannot be implemented in a plugin. You need to place your jar
 * inside {@code WEB-INF/lib} instead.
 *
 * <p>
 * To register, put {@link MetaInfServices} on your implementation.
 * 
 * @author Kohsuke Kawaguchi
 */
public class InitStrategy {
    /**
     * Returns the list of *.hpi and *.hpl to expand and load.
     *
     * <p>
     * Normally we look at {@code $HUDSON_HOME/plugins/*.hpi} and *.hpl.
     *
     * @return
     *      never null but can be empty. The list can contain different versions of the same plugin,
     *      and when that happens, Hudson will ignore all but the first one in the list.
     */
    public List<File> listPluginArchives(PluginManager pm) throws IOException {
        File[] archives = pm.rootDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".hpi")        // plugin jar file
                    || name.endsWith(".hpl");       // linked plugin. for debugging.
            }
        });
        if (archives == null)
            throw new IOException("Hudson is unable to create " + pm.rootDir + "\nPerhaps its security privilege is insufficient");

        List<File> r = new ArrayList<File>();
        getBundledPluginsFromProperty(r);
        r.addAll(Arrays.asList(archives));

        return r;
    }

    /**
     * Lists up additional bundled plugins from the system property.
     *
     * For use in the "mvn hudson-dev:run".
     * TODO: maven-hpi-plugin should inject its own InitStrategy instead of having this in the core.
     */
    protected void getBundledPluginsFromProperty(List<File> r) {
        String hplProperty = System.getProperty("hudson.bundled.plugins");
        if (hplProperty != null) {
            for (String hplLocation : hplProperty.split(",")) {
                File hpl = new File(hplLocation.trim());
                if (hpl.exists())
                    r.add(hpl);
                else
                    LOGGER.warning("bundled plugin " + hplLocation + " does not exist");
            }
        }
    }

    /**
     * Selectively skip some of the initialization tasks.
     * 
     * @return
     *      true to skip the execution.
     */
    public boolean skipInitTask(Task task) {
        return false;
    }


    /**
     * Obtains the instance to be used.
     */
    public static InitStrategy get(ClassLoader cl) throws IOException {
        List<InitStrategy> r = Service.loadInstances(cl, InitStrategy.class);
        if (r.isEmpty())    return new InitStrategy();      // default

        InitStrategy s = r.get(0);
        LOGGER.fine("Using "+s+" as InitStrategy");
        return s;
    }

    private static final Logger LOGGER = Logger.getLogger(InitStrategy.class.getName());
}
