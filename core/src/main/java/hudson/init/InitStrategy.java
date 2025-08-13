package hudson.init;

import hudson.PluginManager;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.jvnet.hudson.reactor.Task;
import org.kohsuke.MetaInfServices;

/**
 * Strategy pattern of the various key decision making during the Jenkins initialization.
 *
 * <p>
 * Because the act of initializing plugins is a part of the Jenkins initialization,
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
     * Returns the list of *.jpi, *.hpi and *.hpl to expand and load.
     *
     * <p>
     * Normally we look at {@code $JENKINS_HOME/plugins/*.jpi} and *.hpi and *.hpl.
     *
     * @return
     *      never null but can be empty. The list can contain different versions of the same plugin,
     *      and when that happens, Jenkins will ignore all but the first one in the list.
     */
    public List<File> listPluginArchives(PluginManager pm) throws IOException {
        List<File> r = new ArrayList<>();

        // the ordering makes sure that during the debugging we get proper precedence among duplicates.
        // for example, while doing "mvn jpi:run" or "mvn hpi:run" on a plugin that's bundled with Jenkins, we want to the
        // *.jpl file to override the bundled jpi/hpi file.
        getBundledPluginsFromProperty(r);

        // similarly, we prefer *.jpi over *.hpi
        listPluginFiles(pm, ".jpl", r); // linked plugin. for debugging.
        listPluginFiles(pm, ".hpl", r); // linked plugin. for debugging. (for backward compatibility)
        listPluginFiles(pm, ".jpi", r); // plugin jar file
        listPluginFiles(pm, ".hpi", r); // plugin jar file (for backward compatibility)

        return r;
    }

    private void listPluginFiles(PluginManager pm, String extension, Collection<File> all) throws IOException {
        File[] files = pm.rootDir.listFiles(new FilterByExtension(extension));
        if (files == null)
            throw new IOException("Jenkins is unable to create " + pm.rootDir + "\nPerhaps its security privilege is insufficient");

        List<File> pluginFiles = new ArrayList<>();
        pluginFiles.addAll(List.of(files));
        pluginFiles.sort(Comparator.comparing(File::getName));

        all.addAll(pluginFiles);
    }

    /**
     * Lists up additional bundled plugins from the system property {@code hudson.bundled.plugins}.
     * Since 1.480 glob syntax is supported.
     * For use in {@code mvn jetty:run}.
     * TODO: maven-hpi-plugin should inject its own InitStrategy instead of having this in the core.
     */
    protected void getBundledPluginsFromProperty(final List<File> r) {
        String hplProperty = SystemProperties.getString("hudson.bundled.plugins");
        if (hplProperty != null) {
            List<File> pluginFiles = new ArrayList<>();
            for (String hplLocation : hplProperty.split(",")) {
                File hpl = new File(hplLocation.trim());
                if (hpl.exists()) {
                    pluginFiles.add(hpl);
                } else if (hpl.getName().contains("*")) {
                    try {
                        new DirScanner.Glob(hpl.getName(), null).scan(hpl.getParentFile(), new FileVisitor() {
                            @Override public void visit(File f, String relativePath) throws IOException {
                                pluginFiles.add(f);
                            }
                        });
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, "could not expand " + hplLocation, x);
                    }
                } else {
                    LOGGER.warning("bundled plugin " + hplLocation + " does not exist");
                }
            }
            pluginFiles.sort(Comparator.comparing(File::getName));
            r.addAll(pluginFiles);
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
        Iterator<InitStrategy> it = ServiceLoader.load(InitStrategy.class, cl).iterator();
        if (!it.hasNext()) {
            return new InitStrategy(); // default
        }
        InitStrategy s = it.next();
        LOGGER.log(Level.FINE, "Using {0} as InitStrategy", s);
        return s;
    }

    private static final Logger LOGGER = Logger.getLogger(InitStrategy.class.getName());

    private static class FilterByExtension implements FilenameFilter {
        private final List<String> extensions;

        FilterByExtension(String... extensions) {
            this.extensions = Arrays.asList(extensions);
        }

        @Override
        public boolean accept(File dir, String name) {
            for (String extension : extensions) {
                if (name.endsWith(extension))
                    return true;
            }
            return false;
        }
    }
}
