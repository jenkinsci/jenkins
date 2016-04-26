/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, Tom Huybrechts
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

import hudson.PluginWrapper.Dependency;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.init.InitializerFinder;
import hudson.model.AbstractItem;
import hudson.model.AbstractModelObject;
import hudson.model.AdministrativeMonitor;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.ItemGroupMixIn;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.IOUtils;
import hudson.util.PersistedList;
import hudson.util.Service;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import jenkins.ClassLoaderReflectionToolkit;
import jenkins.InitReactorRunner;
import jenkins.RestartRequiredException;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import jenkins.util.io.OnMaster;
import jenkins.util.xml.RestrictiveEntityResolver;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.LogFactory;
import org.jenkinsci.bytecode.Transformer;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import static hudson.init.InitMilestone.*;
import hudson.model.DownloadService;
import hudson.util.FormValidation;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Manages {@link PluginWrapper}s.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class PluginManager extends AbstractModelObject implements OnMaster, StaplerOverridable {
    /**
     * All discovered plugins.
     */
    protected final List<PluginWrapper> plugins = new ArrayList<PluginWrapper>();

    /**
     * All active plugins, topologically sorted so that when X depends on Y, Y appears in the list before X does.
     */
    protected final List<PluginWrapper> activePlugins = new CopyOnWriteArrayList<PluginWrapper>();

    protected final List<FailedPlugin> failedPlugins = new ArrayList<FailedPlugin>();

    /**
     * Plug-in root directory.
     */
    public final File rootDir;

    /**
     * If non-null, the base directory for all exploded .hpi/.jpi plugins. Controlled by the system property / servlet
     * context parameter {@literal hudson.PluginManager.workDir}.
     */
    @CheckForNull
    private final File workDir;

    /**
     * @deprecated as of 1.355
     *      {@link PluginManager} can now live longer than {@link jenkins.model.Jenkins} instance, so
     *      use {@code Hudson.getInstance().servletContext} instead.
     */
    @Deprecated
    public final ServletContext context;

    /**
     * {@link ClassLoader} that can load all the publicly visible classes from plugins
     * (and including the classloader that loads Hudson itself.)
     *
     */
    // implementation is minimal --- just enough to run XStream
    // and load plugin-contributed classes.
    public final ClassLoader uberClassLoader = new UberClassLoader();

    private final Transformer compatibilityTransformer = new Transformer();

    /**
     * Once plugin is uploaded, this flag becomes true.
     * This is used to report a message that Jenkins needs to be restarted
     * for new plugins to take effect.
     */
    public volatile boolean pluginUploaded = false;

    /**
     * The initialization of {@link PluginManager} splits into two parts;
     * one is the part about listing them, extracting them, and preparing classloader for them.
     * The 2nd part is about creating instances. Once the former completes this flags become true,
     * as the 2nd part can be repeated for each Hudson instance.
     */
    private boolean pluginListed = false;

    /**
     * Strategy for creating and initializing plugins
     */
    private final PluginStrategy strategy;

    /**
     * Manifest of the plugin binaries that are bundled with core.
     */
    private final Map<String,Manifest> bundledPluginManifests = new HashMap<String, Manifest>();

    public PluginManager(ServletContext context, File rootDir) {
        this.context = context;

        this.rootDir = rootDir;
        if(!rootDir.exists())
            rootDir.mkdirs();
        String workDir = System.getProperty(PluginManager.class.getName()+".workDir");
        if (workDir == null && context != null) {
            workDir = context.getInitParameter(PluginManager.class.getName() + ".workDir");
        }
        this.workDir = workDir == null ? null : new File(workDir);

        strategy = createPluginStrategy();

        // load up rules for the core first
        try {
            compatibilityTransformer.loadRules(getClass().getClassLoader());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load compatibility rewrite rules",e);
        }
    }

    public Transformer getCompatibilityTransformer() {
        return compatibilityTransformer;
    }

    public Api getApi() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return new Api(this);
    }

    /**
     * If non-null, the base directory for all exploded .hpi/.jpi plugins.
     * @return the base directory for all exploded .hpi/.jpi plugins or {@code null} to leave this up to the strategy.
     */
    @CheckForNull
    public File getWorkDir() {
        return workDir;
    }

    /**
     * Find all registered overrides (intended to allow overriding/adding views)
     * @return List of extensions
     * @since 1.627
     */
    @Override
    public Collection<PluginManagerStaplerOverride> getOverrides() {
        return PluginManagerStaplerOverride.all();
    }

    /**
     * Called immediately after the construction.
     * This is a separate method so that code executed from here will see a valid value in
     * {@link jenkins.model.Jenkins#pluginManager}.
     */
    public TaskBuilder initTasks(final InitStrategy initStrategy) {
        TaskBuilder builder;
        if (!pluginListed) {
            builder = new TaskGraphBuilder() {
                List<File> archives;
                Collection<String> bundledPlugins;

                {
                    Handle loadBundledPlugins = add("Loading bundled plugins", new Executable() {
                        public void run(Reactor session) throws Exception {
                            bundledPlugins = loadBundledPlugins();
                        }
                    });

                    Handle listUpPlugins = requires(loadBundledPlugins).add("Listing up plugins", new Executable() {
                        public void run(Reactor session) throws Exception {
                            archives = initStrategy.listPluginArchives(PluginManager.this);
                        }
                    });

                    requires(listUpPlugins).attains(PLUGINS_LISTED).add("Preparing plugins",new Executable() {
                        public void run(Reactor session) throws Exception {
                            // once we've listed plugins, we can fill in the reactor with plugin-specific initialization tasks
                            TaskGraphBuilder g = new TaskGraphBuilder();

                            final Map<String,File> inspectedShortNames = new HashMap<String,File>();

                            for( final File arc : archives ) {
                                g.followedBy().notFatal().attains(PLUGINS_LISTED).add("Inspecting plugin " + arc, new Executable() {
                                    public void run(Reactor session1) throws Exception {
                                        try {
                                            PluginWrapper p = strategy.createPluginWrapper(arc);
                                            if (isDuplicate(p)) return;

                                            p.isBundled = containsHpiJpi(bundledPlugins, arc.getName());
                                            plugins.add(p);
                                        } catch (IOException e) {
                                            failedPlugins.add(new FailedPlugin(arc.getName(),e));
                                            throw e;
                                        }
                                    }

                                    /**
                                     * Inspects duplication. this happens when you run hpi:run on a bundled plugin,
                                     * as well as putting numbered jpi files, like "cobertura-1.0.jpi" and "cobertura-1.1.jpi"
                                     */
                                    private boolean isDuplicate(PluginWrapper p) {
                                        String shortName = p.getShortName();
                                        if (inspectedShortNames.containsKey(shortName)) {
                                            LOGGER.info("Ignoring "+arc+" because "+inspectedShortNames.get(shortName)+" is already loaded");
                                            return true;
                                        }

                                        inspectedShortNames.put(shortName,arc);
                                        return false;
                                    }
                                });
                            }

                            g.followedBy().attains(PLUGINS_LISTED).add("Checking cyclic dependencies", new Executable() {
                                /**
                                 * Makes sure there's no cycle in dependencies.
                                 */
                                public void run(Reactor reactor) throws Exception {
                                    try {
                                        CyclicGraphDetector<PluginWrapper> cgd = new CyclicGraphDetector<PluginWrapper>() {
                                            @Override
                                            protected List<PluginWrapper> getEdges(PluginWrapper p) {
                                                List<PluginWrapper> next = new ArrayList<PluginWrapper>();
                                                addTo(p.getDependencies(), next);
                                                addTo(p.getOptionalDependencies(), next);
                                                return next;
                                            }

                                            private void addTo(List<Dependency> dependencies, List<PluginWrapper> r) {
                                                for (Dependency d : dependencies) {
                                                    PluginWrapper p = getPlugin(d.shortName);
                                                    if (p != null)
                                                        r.add(p);
                                                }
                                            }

                                            @Override
                                            protected void reactOnCycle(PluginWrapper q, List<PluginWrapper> cycle)
                                                    throws hudson.util.CyclicGraphDetector.CycleDetectedException {

                                                LOGGER.log(Level.SEVERE, "found cycle in plugin dependencies: (root="+q+", deactivating all involved) "+Util.join(cycle," -> "));
                                                for (PluginWrapper pluginWrapper : cycle) {
                                                    pluginWrapper.setHasCycleDependency(true);
                                                    failedPlugins.add(new FailedPlugin(pluginWrapper.getShortName(), new CycleDetectedException(cycle)));
                                                }
                                            }

                                        };
                                        cgd.run(getPlugins());

                                        // obtain topologically sorted list and overwrite the list
                                        ListIterator<PluginWrapper> litr = getPlugins().listIterator();
                                        for (PluginWrapper p : cgd.getSorted()) {
                                            litr.next();
                                            litr.set(p);
                                            if(p.isActive())
                                                activePlugins.add(p);
                                        }
                                    } catch (CycleDetectedException e) {
                                        stop(); // disable all plugins since classloading from them can lead to StackOverflow
                                        throw e;    // let Hudson fail
                                    }
                                }
                            });

                            // Let's see for a while until we open this functionality up to plugins
//                            g.followedBy().attains(PLUGINS_LISTED).add("Load compatibility rules", new Executable() {
//                                public void run(Reactor reactor) throws Exception {
//                                    compatibilityTransformer.loadRules(uberClassLoader);
//                                }
//                            });

                            session.addAll(g.discoverTasks(session));

                            pluginListed = true; // technically speaking this is still too early, as at this point tasks are merely scheduled, not necessarily executed.
                        }
                    });
                }
            };
        } else {
            builder = TaskBuilder.EMPTY_BUILDER;
        }

        final InitializerFinder initializerFinder = new InitializerFinder(uberClassLoader);        // misc. stuff

        // lists up initialization tasks about loading plugins.
        return TaskBuilder.union(initializerFinder, // this scans @Initializer in the core once
            builder,new TaskGraphBuilder() {{
            requires(PLUGINS_LISTED).attains(PLUGINS_PREPARED).add("Loading plugins",new Executable() {
                /**
                 * Once the plugins are listed, schedule their initialization.
                 */
                public void run(Reactor session) throws Exception {
                    Jenkins.getInstance().lookup.set(PluginInstanceStore.class,new PluginInstanceStore());
                    TaskGraphBuilder g = new TaskGraphBuilder();

                    // schedule execution of loading plugins
                    for (final PluginWrapper p : activePlugins.toArray(new PluginWrapper[activePlugins.size()])) {
                        g.followedBy().notFatal().attains(PLUGINS_PREPARED).add("Loading plugin " + p.getShortName(), new Executable() {
                            public void run(Reactor session) throws Exception {
                                try {
                                    p.resolvePluginDependencies();
                                    strategy.load(p);
                                } catch (IOException e) {
                                    failedPlugins.add(new FailedPlugin(p.getShortName(), e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    throw e;
                                }
                            }
                        });
                    }

                    // schedule execution of initializing plugins
                    for (final PluginWrapper p : activePlugins.toArray(new PluginWrapper[activePlugins.size()])) {
                        g.followedBy().notFatal().attains(PLUGINS_STARTED).add("Initializing plugin " + p.getShortName(), new Executable() {
                            public void run(Reactor session) throws Exception {
                                if (!activePlugins.contains(p)) {
                                    return;
                                }
                                try {
                                    p.getPlugin().postInitialize();
                                } catch (Exception e) {
                                    failedPlugins.add(new FailedPlugin(p.getShortName(), e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    throw e;
                                }
                            }
                        });
                    }

                    g.followedBy().attains(PLUGINS_STARTED).add("Discovering plugin initialization tasks", new Executable() {
                        public void run(Reactor reactor) throws Exception {
                            // rescan to find plugin-contributed @Initializer
                            reactor.addAll(initializerFinder.discoverTasks(reactor));
                        }
                    });

                    // register them all
                    session.addAll(g.discoverTasks(session));
                }
            });
        }});
    }

    /*
     * contains operation that considers xxx.hpi and xxx.jpi as equal
     * this is necessary since the bundled plugins are still called *.hpi
     */
    private boolean containsHpiJpi(Collection<String> bundledPlugins, String name) {
        return bundledPlugins.contains(name.replaceAll("\\.hpi",".jpi"))
                || bundledPlugins.contains(name.replaceAll("\\.jpi",".hpi"));
    }

    /**
     * Returns the manifest of a bundled but not-extracted plugin.
     */
    public @CheckForNull Manifest getBundledPluginManifest(String shortName) {
        return bundledPluginManifests.get(shortName);
    }

    /**
     * TODO: revisit where/how to expose this. This is an experiment.
     */
    public void dynamicLoad(File arc) throws IOException, InterruptedException, RestartRequiredException {
        LOGGER.info("Attempting to dynamic load "+arc);
        PluginWrapper p = null;
        String sn;
        try {
            sn = strategy.getShortName(arc);
        } catch (AbstractMethodError x) {
            LOGGER.log(WARNING, "JENKINS-12753 fix not active: {0}", x.getMessage());
            p = strategy.createPluginWrapper(arc);
            sn = p.getShortName();
        }
        if (getPlugin(sn)!=null)
            throw new RestartRequiredException(Messages._PluginManager_PluginIsAlreadyInstalled_RestartRequired(sn));

        if (p == null) {
            p = strategy.createPluginWrapper(arc);
        }
        if (p.supportsDynamicLoad()== YesNoMaybe.NO)
            throw new RestartRequiredException(Messages._PluginManager_PluginDoesntSupportDynamicLoad_RestartRequired(sn));

        // there's no need to do cyclic dependency check, because we are deploying one at a time,
        // so existing plugins can't be depending on this newly deployed one.

        plugins.add(p);
        activePlugins.add(p);
        synchronized (((UberClassLoader) uberClassLoader).loaded) {
            ((UberClassLoader) uberClassLoader).loaded.clear();
        }

        try {
            p.resolvePluginDependencies();
            strategy.load(p);

            Jenkins.getInstance().refreshExtensions();

            p.getPlugin().postInitialize();
        } catch (Exception e) {
            failedPlugins.add(new FailedPlugin(sn, e));
            activePlugins.remove(p);
            plugins.remove(p);
            throw new IOException("Failed to install "+ sn +" plugin",e);
        }

        // run initializers in the added plugin
        Reactor r = new Reactor(InitMilestone.ordering());
        final ClassLoader loader = p.classLoader;
        r.addAll(new InitializerFinder(loader) {
            @Override
            protected boolean filter(Method e) {
                return e.getDeclaringClass().getClassLoader() != loader || super.filter(e);
            }
        }.discoverTasks(r));
        try {
            new InitReactorRunner().run(r);
        } catch (ReactorException e) {
            throw new IOException("Failed to initialize "+ sn +" plugin",e);
        }

        // recalculate dependencies of plugins optionally depending the newly deployed one.
        for (PluginWrapper depender: plugins) {
            if (depender.equals(p)) {
                // skip itself.
                continue;
            }
            for (Dependency d: depender.getOptionalDependencies()) {
                if (d.shortName.equals(p.getShortName())) {
                    // this plugin depends on the newly loaded one!
                    // recalculate dependencies!
                    try {
                        getPluginStrategy().updateDependency(depender, p);
                    } catch (AbstractMethodError x) {
                        LOGGER.log(WARNING, "{0} does not yet implement updateDependency", getPluginStrategy().getClass());
                    }
                    break;
                }
            }
        }

        // Redo who depends on who.
        resolveDependantPlugins();

        LOGGER.info("Plugin " + p.getShortName()+":"+p.getVersion() + " dynamically installed");
    }

    @Restricted(NoExternalUse.class)
    public synchronized void resolveDependantPlugins() {
        for (PluginWrapper plugin : plugins) {
            Set<String> dependants = new HashSet<>();
            for (PluginWrapper possibleDependant : plugins) {
                // The plugin could have just been deleted. If so, it doesn't
                // count as a dependant.
                if (possibleDependant.isDeleted()) {
                    continue;
                }
                List<Dependency> dependencies = possibleDependant.getDependencies();
                for (Dependency dependency : dependencies) {
                    if (dependency.shortName.equals(plugin.getShortName())) {
                        dependants.add(possibleDependant.getShortName());
                    }
                }
            }
            plugin.setDependants(dependants);
        }
    }

    /**
     * If the war file has any "/WEB-INF/plugins/[*.jpi | *.hpi]", extract them into the plugin directory.
     *
     * @return
     *      File names of the bundled plugins. Like {"ssh-slaves.hpi","subvesrion.jpi"}
     * @throws Exception
     *      Any exception will be reported and halt the startup.
     */
    protected abstract Collection<String> loadBundledPlugins() throws Exception;

    /**
     * Copies the bundled plugin from the given URL to the destination of the given file name (like 'abc.jpi'),
     * with a reasonable up-to-date check. A convenience method to be used by the {@link #loadBundledPlugins()}.
     */
    protected void copyBundledPlugin(URL src, String fileName) throws IOException {
        fileName = fileName.replace(".hpi",".jpi"); // normalize fileNames to have the correct suffix
        String legacyName = fileName.replace(".jpi",".hpi");
        long lastModified = src.openConnection().getLastModified();
        File file = new File(rootDir, fileName);
        File pinFile = new File(rootDir, fileName+".pinned");

        // normalization first, if the old file exists.
        rename(new File(rootDir,legacyName),file);
        rename(new File(rootDir,legacyName+".pinned"),pinFile);

        // update file if:
        //  - no file exists today
        //  - bundled version and current version differs (by timestamp), and the file isn't pinned.
        if (!file.exists() || (file.lastModified() != lastModified && !pinFile.exists())) {
            FileUtils.copyURLToFile(src, file);
            file.setLastModified(src.openConnection().getLastModified());
            // lastModified is set for two reasons:
            // - to avoid unpacking as much as possible, but still do it on both upgrade and downgrade
            // - to make sure the value is not changed after each restart, so we can avoid
            // unpacking the plugin itself in ClassicPluginStrategy.explode
        }
        if (pinFile.exists())
            parsePinnedBundledPluginManifest(src);
    }

    /**
     * When a pin file prevented a bundled plugin from getting extracted, check if the one we currently have
     * is older than we bundled.
     */
    private void parsePinnedBundledPluginManifest(URL bundledJpi) {
        try {
            URLClassLoader cl = new URLClassLoader(new URL[]{bundledJpi});
            InputStream in=null;
            try {
                URL res = cl.findResource(PluginWrapper.MANIFEST_FILENAME);
                if (res!=null) {
                    in = res.openStream();
                    Manifest manifest = new Manifest(in);
                    String shortName = PluginWrapper.computeShortName(manifest, FilenameUtils.getName(bundledJpi.getPath()));
                    bundledPluginManifests.put(shortName, manifest);
                }
            } finally {
                IOUtils.closeQuietly(in);
                if (cl instanceof Closeable)
                    ((Closeable)cl).close();
            }
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to parse manifest of "+bundledJpi, e);
        }
    }

    /**
     * Rename a legacy file to a new name, with care to Windows where {@link File#renameTo(File)}
     * doesn't work if the destination already exists.
     */
    private void rename(File legacyFile, File newFile) throws IOException {
        if (!legacyFile.exists())   return;
        if (newFile.exists()) {
            Util.deleteFile(newFile);
        }
        if (!legacyFile.renameTo(newFile)) {
            LOGGER.warning("Failed to rename " + legacyFile + " to " + newFile);
        }
    }

    /**
     * Creates a hudson.PluginStrategy, looking at the corresponding system property.
     */
    protected PluginStrategy createPluginStrategy() {
		String strategyName = System.getProperty(PluginStrategy.class.getName());
		if (strategyName != null) {
			try {
				Class<?> klazz = getClass().getClassLoader().loadClass(strategyName);
				Object strategy = klazz.getConstructor(PluginManager.class)
						.newInstance(this);
				if (strategy instanceof PluginStrategy) {
					LOGGER.info("Plugin strategy: " + strategyName);
					return (PluginStrategy) strategy;
				} else {
					LOGGER.warning("Plugin strategy (" + strategyName +
							") is not an instance of hudson.PluginStrategy");
				}
			} catch (ClassNotFoundException e) {
				LOGGER.warning("Plugin strategy class not found: "
						+ strategyName);
			} catch (Exception e) {
				LOGGER.log(WARNING, "Could not instantiate plugin strategy: "
						+ strategyName + ". Falling back to ClassicPluginStrategy", e);
			}
			LOGGER.info("Falling back to ClassicPluginStrategy");
		}

		// default and fallback
		return new ClassicPluginStrategy(this);
    }

    public PluginStrategy getPluginStrategy() {
        return strategy;
    }

    /**
     * Returns true if any new plugin was added.
     */
    public boolean isPluginUploaded() {
        return pluginUploaded;
    }

    /**
     * All discovered plugins.
     */
    @Exported
    public List<PluginWrapper> getPlugins() {
        List<PluginWrapper> out = new ArrayList<PluginWrapper>(plugins.size());
        out.addAll(plugins);
        return out;
    }

    public List<FailedPlugin> getFailedPlugins() {
        return failedPlugins;
    }

    /**
     * Get the plugin instance with the given short name.
     * @param shortName the short name of the plugin
     * @return The plugin singleton or <code>null</code> if a plugin with the given short name does not exist.
     */
    public PluginWrapper getPlugin(String shortName) {
        for (PluginWrapper p : getPlugins()) {
            if(p.getShortName().equals(shortName))
                return p;
        }
        return null;
    }

    /**
     * Get the plugin instance that implements a specific class, use to find your plugin singleton.
     * Note: beware the classloader fun.
     * @param pluginClazz The class that your plugin implements.
     * @return The plugin singleton or <code>null</code> if for some reason the plugin is not loaded.
     */
    public PluginWrapper getPlugin(Class<? extends Plugin> pluginClazz) {
        for (PluginWrapper p : getPlugins()) {
            if(pluginClazz.isInstance(p.getPlugin()))
                return p;
        }
        return null;
    }

    /**
     * Get the plugin instances that extend a specific class, use to find similar plugins.
     * Note: beware the classloader fun.
     * @param pluginSuperclass The class that your plugin is derived from.
     * @return The list of plugins implementing the specified class.
     */
    public List<PluginWrapper> getPlugins(Class<? extends Plugin> pluginSuperclass) {
        List<PluginWrapper> result = new ArrayList<PluginWrapper>();
        for (PluginWrapper p : getPlugins()) {
            if(pluginSuperclass.isInstance(p.getPlugin()))
                result.add(p);
        }
        return Collections.unmodifiableList(result);
    }

    public String getDisplayName() {
        return Messages.PluginManager_DisplayName();
    }

    public String getSearchUrl() {
        return "pluginManager";
    }

    /**
     * Discover all the service provider implementations of the given class,
     * via <tt>META-INF/services</tt>.
     */
    public <T> Collection<Class<? extends T>> discover( Class<T> spi ) {
        Set<Class<? extends T>> result = new HashSet<Class<? extends T>>();

        for (PluginWrapper p : activePlugins) {
            Service.load(spi, p.classLoader, result);
        }

        return result;
    }

    /**
     * Return the {@link PluginWrapper} that loaded the given class 'c'.
     *
     * @since 1.402.
     */
    public PluginWrapper whichPlugin(Class c) {
        PluginWrapper oneAndOnly = null;
        ClassLoader cl = c.getClassLoader();
        for (PluginWrapper p : activePlugins) {
            if (p.classLoader==cl) {
                if (oneAndOnly!=null)
                    return null;    // ambigious
                oneAndOnly = p;
            }
        }
        return oneAndOnly;
    }

    /**
     * Orderly terminates all the plugins.
     */
    public void stop() {
        for (PluginWrapper p : activePlugins) {
            p.stop();
            p.releaseClassLoader();
        }
        activePlugins.clear();
        // Work around a bug in commons-logging.
        // See http://www.szegedi.org/articles/memleak.html
        LogFactory.release(uberClassLoader);
    }

    public HttpResponse doUpdateSources(StaplerRequest req) throws IOException {
        Jenkins.getInstance().checkPermission(CONFIGURE_UPDATECENTER);

        if (req.hasParameter("remove")) {
            UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
            BulkChange bc = new BulkChange(uc);
            try {
                for (String id : req.getParameterValues("sources"))
                    uc.getSites().remove(uc.getById(id));
            } finally {
                bc.commit();
            }
        } else
        if (req.hasParameter("add"))
            return new HttpRedirect("addSite");

        return new HttpRedirect("./sites");
    }

    /**
     * Performs the installation of the plugins.
     */
    public void doInstall(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        boolean dynamicLoad = req.getParameter("dynamicLoad")!=null;
        final List<Future<UpdateCenter.UpdateCenterJob>> deployJobs = new ArrayList<>();

        Enumeration<String> en = req.getParameterNames();
        while (en.hasMoreElements()) {
            String n =  en.nextElement();
            if(n.startsWith("plugin.")) {
                n = n.substring(7);
                // JENKINS-22080 plugin names can contain '.' as could (according to rumour) update sites
                int index = n.indexOf('.');
                UpdateSite.Plugin p = null;
                while (index != -1) {
                    if (index + 1 >= n.length()) {
                        break;
                    }
                    String pluginName = n.substring(0, index);
                    String siteName = n.substring(index + 1);
                    UpdateSite updateSite = Jenkins.getInstance().getUpdateCenter().getById(siteName);
                    if (updateSite == null) {
                        throw new Failure("No such update center: " + siteName);
                    } else {
                        UpdateSite.Plugin plugin = updateSite.getPlugin(pluginName);
                        if (plugin != null) {
                            if (p != null) {
                                throw new Failure("Ambiguous plugin: " + n);
                            }
                            p = plugin;
                        }
                    }
                    index = n.indexOf('.', index + 1);
                }
                if (p == null) {
                    throw new Failure("No such plugin: " + n);
                }
                
                deployJobs.add(p.deploy(dynamicLoad));
            }
        }
        
        // Fire a one-off thread to wait for the plugins to be deployed and then
        // refresh the dependant plugins list.
        new Thread() {
            @Override
            public void run() {
                INSTALLING: while (true) {
                    for (Future<UpdateCenter.UpdateCenterJob> deployJob : deployJobs) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            LOGGER.log(SEVERE, "Unexpected error while waiting for some plugins to install. Plugin Manager state may be invalid. Please restart Jenkins ASAP.", e);
                        }
                        if (!deployJob.isCancelled() && !deployJob.isDone()) {
                            // One of the plugins is not installing/canceled, so
                            // go back to sleep and try again in a while.
                            continue INSTALLING;
                        }
                    }
                    // All the plugins are installed. It's now safe to refresh.
                    resolveDependantPlugins();
                    break;
                }
            }
        }.start();
        
        rsp.sendRedirect("../updateCenter/");
    }


    /**
     * Bare-minimum configuration mechanism to change the update center.
     */
    @RequirePOST
    public HttpResponse doSiteConfigure(@QueryParameter String site) throws IOException {
        Jenkins hudson = Jenkins.getInstance();
        hudson.checkPermission(CONFIGURE_UPDATECENTER);
        UpdateCenter uc = hudson.getUpdateCenter();
        PersistedList<UpdateSite> sites = uc.getSites();
        for (UpdateSite s : sites) {
            if (s.getId().equals(UpdateCenter.ID_DEFAULT))
                sites.remove(s);
        }
        sites.add(new UpdateSite(UpdateCenter.ID_DEFAULT, site));

        return HttpResponses.redirectToContextRoot();
    }


    @RequirePOST
    public HttpResponse doProxyConfigure(StaplerRequest req) throws IOException, ServletException {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(CONFIGURE_UPDATECENTER);

        ProxyConfiguration pc = req.bindJSON(ProxyConfiguration.class, req.getSubmittedForm());
        if (pc.name==null) {
            jenkins.proxy = null;
            ProxyConfiguration.getXmlFile().delete();
        } else {
            jenkins.proxy = pc;
            jenkins.proxy.save();
        }
        return new HttpRedirect("advanced");
    }

    /**
     * Uploads a plugin.
     */
    @RequirePOST
    public HttpResponse doUploadPlugin(StaplerRequest req) throws IOException, ServletException {
        try {
            Jenkins.getInstance().checkPermission(UPLOAD_PLUGINS);

            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());

            // Parse the request
            FileItem fileItem = (FileItem) upload.parseRequest(req).get(0);
            String fileName = Util.getFileName(fileItem.getName());
            if("".equals(fileName)){
                return new HttpRedirect("advanced");
            }
            // we allow the upload of the new jpi's and the legacy hpi's
            if(!fileName.endsWith(".jpi") && !fileName.endsWith(".hpi")){
                throw new Failure(hudson.model.Messages.Hudson_NotAPlugin(fileName));
            }

            // first copy into a temporary file name
            File t = File.createTempFile("uploaded", ".jpi");
            t.deleteOnExit();
            fileItem.write(t);
            fileItem.delete();

            final String baseName = identifyPluginShortName(t);

            pluginUploaded = true;

            // Now create a dummy plugin that we can dynamically load (the InstallationJob will force a restart if one is needed):
            JSONObject cfg = new JSONObject().
                    element("name", baseName).
                    element("version", "0"). // unused but mandatory
                    element("url", t.toURI().toString()).
                    element("dependencies", new JSONArray());
            new UpdateSite(UpdateCenter.ID_UPLOAD, null).new Plugin(UpdateCenter.ID_UPLOAD, cfg).deploy(true);
            return new HttpRedirect("../updateCenter");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {// grrr. fileItem.write throws this
            throw new ServletException(e);
        }
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST public HttpResponse doCheckUpdatesServer() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        for (UpdateSite site : Jenkins.getInstance().getUpdateCenter().getSites()) {
            FormValidation v = site.updateDirectlyNow(DownloadService.signatureCheck);
            if (v.kind != FormValidation.Kind.OK) {
                // TODO crude but enough for now
                return v;
            }
        }
        for (DownloadService.Downloadable d : DownloadService.Downloadable.all()) {
            FormValidation v = d.updateNow();
            if (v.kind != FormValidation.Kind.OK) {
                return v;
            }
        }
        return HttpResponses.forwardToPreviousPage();
    }

    protected String identifyPluginShortName(File t) {
        try {
            JarFile j = new JarFile(t);
            try {
                String name = j.getManifest().getMainAttributes().getValue("Short-Name");
                if (name!=null) return name;
            } finally {
                j.close();
            }
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to identify the short name from "+t,e);
        }
        return FilenameUtils.getBaseName(t.getName());    // fall back to the base name of what's uploaded
    }

    public Descriptor<ProxyConfiguration> getProxyDescriptor() {
        return Jenkins.getInstance().getDescriptor(ProxyConfiguration.class);
    }

    /**
     * Prepares plugins for some expected XML configuration.
     * If the configuration (typically a job’s {@code config.xml})
     * needs some plugins to be installed (or updated), those jobs
     * will be triggered.
     * Plugins are dynamically loaded whenever possible.
     * Requires {@link Jenkins#ADMINISTER}.
     * @param configXml configuration that might be uploaded
     * @return an empty list if all is well, else a list of submitted jobs which must be completed before this configuration can be fully read
     * @throws IOException if loading or parsing the configuration failed
     * @see ItemGroupMixIn#createProjectFromXML
     * @see AbstractItem#updateByXml(javax.xml.transform.Source)
     * @see XStream2
     * @see hudson.model.UpdateSite.Plugin#deploy(boolean)
     * @see PluginWrapper#supportsDynamicLoad
     * @see hudson.model.UpdateCenter.DownloadJob.SuccessButRequiresRestart
     * @since 1.483
     */
    public List<Future<UpdateCenter.UpdateCenterJob>> prevalidateConfig(InputStream configXml) throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        List<Future<UpdateCenter.UpdateCenterJob>> jobs = new ArrayList<Future<UpdateCenter.UpdateCenterJob>>();
        UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
        // TODO call uc.updateAllSites() when available? perhaps not, since we should not block on network here
        for (Map.Entry<String,VersionNumber> requestedPlugin : parseRequestedPlugins(configXml).entrySet()) {
            PluginWrapper pw = getPlugin(requestedPlugin.getKey());
            if (pw == null) { // install new
                UpdateSite.Plugin toInstall = uc.getPlugin(requestedPlugin.getKey());
                if (toInstall == null) {
                    LOGGER.log(WARNING, "No such plugin {0} to install", requestedPlugin.getKey());
                    continue;
                }
                if (new VersionNumber(toInstall.version).compareTo(requestedPlugin.getValue()) < 0) {
                    LOGGER.log(WARNING, "{0} can only be satisfied in @{1}", new Object[] {requestedPlugin, toInstall.version});
                }
                if (toInstall.isForNewerHudson()) {
                    LOGGER.log(WARNING, "{0}@{1} was built for a newer Jenkins", new Object[] {toInstall.name, toInstall.version});
                }
                jobs.add(toInstall.deploy(true));
            } else if (pw.isOlderThan(requestedPlugin.getValue())) { // upgrade
                UpdateSite.Plugin toInstall = uc.getPlugin(requestedPlugin.getKey());
                if (toInstall == null) {
                    LOGGER.log(WARNING, "No such plugin {0} to upgrade", requestedPlugin.getKey());
                    continue;
                }
                if (!pw.isOlderThan(new VersionNumber(toInstall.version))) {
                    LOGGER.log(WARNING, "{0}@{1} is no newer than what we already have", new Object[] {toInstall.name, toInstall.version});
                    continue;
                }
                if (new VersionNumber(toInstall.version).compareTo(requestedPlugin.getValue()) < 0) {
                    LOGGER.log(WARNING, "{0} can only be satisfied in @{1}", new Object[] {requestedPlugin, toInstall.version});
                }
                if (toInstall.isForNewerHudson()) {
                    LOGGER.log(WARNING, "{0}@{1} was built for a newer Jenkins", new Object[] {toInstall.name, toInstall.version});
                }
                if (!toInstall.isCompatibleWithInstalledVersion()) {
                    LOGGER.log(WARNING, "{0}@{1} is incompatible with the installed @{2}", new Object[] {toInstall.name, toInstall.version, pw.getVersion()});
                }
                jobs.add(toInstall.deploy(true)); // dynamicLoad=true => sure to throw RestartRequiredException, but at least message is nicer
            } // else already good
        }
        return jobs;
    }

    /**
     * Like {@link #doInstallNecessaryPlugins(StaplerRequest)} but only checks if everything is installed
     * or if some plugins need updates or installation.
     *
     * This method runs without side-effect. I'm still requiring the ADMINISTER permission since
     * XML file can contain various external references and we don't configure parsers properly against
     * that.
     *
     * @since 1.483
     */
    @RequirePOST
    public JSONArray doPrevalidateConfig(StaplerRequest req) throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        JSONArray response = new JSONArray();

        for (Map.Entry<String,VersionNumber> p : parseRequestedPlugins(req.getInputStream()).entrySet()) {
            PluginWrapper pw = getPlugin(p.getKey());
            JSONObject j = new JSONObject()
                    .accumulate("name", p.getKey())
                    .accumulate("version", p.getValue().toString());
            if (pw == null) { // install new
                response.add(j.accumulate("mode", "missing"));
            } else if (pw.isOlderThan(p.getValue())) { // upgrade
                response.add(j.accumulate("mode", "old"));
            } // else already good
        }

        return response;
    }

    /**
     * Runs {@link #prevalidateConfig} on posted XML and redirects to the {@link UpdateCenter}.
     * @since 1.483
     */
    @RequirePOST
    public HttpResponse doInstallNecessaryPlugins(StaplerRequest req) throws IOException {
        prevalidateConfig(req.getInputStream());
        return HttpResponses.redirectViaContextPath("updateCenter");
    }

    /**
     * Parses configuration XML files and picks up references to XML files.
     */
    public Map<String,VersionNumber> parseRequestedPlugins(InputStream configXml) throws IOException {
        final Map<String,VersionNumber> requestedPlugins = new TreeMap<String,VersionNumber>();
        try {
            SAXParserFactory.newInstance().newSAXParser().parse(configXml, new DefaultHandler() {
                @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    String plugin = attributes.getValue("plugin");
                    if (plugin == null) {
                        return;
                    }
                    if (!plugin.matches("[^@]+@[^@]+")) {
                        throw new SAXException("Malformed plugin attribute: " + plugin);
                    }
                    int at = plugin.indexOf('@');
                    String shortName = plugin.substring(0, at);
                    VersionNumber existing = requestedPlugins.get(shortName);
                    VersionNumber requested = new VersionNumber(plugin.substring(at + 1));
                    if (existing == null || existing.compareTo(requested) < 0) {
                        requestedPlugins.put(shortName, requested);
                    }
                }

                @Override public InputSource resolveEntity(String publicId, String systemId) throws IOException,
                        SAXException {
                    return RestrictiveEntityResolver.INSTANCE.resolveEntity(publicId, systemId);
                }

            });
        } catch (SAXException x) {
            throw new IOException("Failed to parse XML",x);
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e); // impossible since we don't tweak XMLParser
        }
        return requestedPlugins;
    }

    /**
     * {@link ClassLoader} that can see all plugins.
     */
    public final class UberClassLoader extends ClassLoader {
        /**
         * Make generated types visible.
         * Keyed by the generated class name.
         */
        private ConcurrentMap<String, WeakReference<Class>> generatedClasses = new ConcurrentHashMap<String, WeakReference<Class>>();
        /** Cache of loaded, or known to be unloadable, classes. */
        private final Map<String,Class<?>> loaded = new HashMap<String,Class<?>>();

        public UberClassLoader() {
            super(PluginManager.class.getClassLoader());
        }

        public void addNamedClass(String className, Class c) {
            generatedClasses.put(className,new WeakReference<Class>(c));
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            WeakReference<Class> wc = generatedClasses.get(name);
            if (wc!=null) {
                Class c = wc.get();
                if (c!=null)    return c;
                else            generatedClasses.remove(name,wc);
            }

            if (name.startsWith("SimpleTemplateScript")) { // cf. groovy.text.SimpleTemplateEngine
                throw new ClassNotFoundException("ignoring " + name);
            }
            synchronized (loaded) {
                if (loaded.containsKey(name)) {
                    Class<?> c = loaded.get(name);
                    if (c != null) {
                        return c;
                    } else {
                        throw new ClassNotFoundException("cached miss for " + name);
                    }
                }
            }
            if (FAST_LOOKUP) {
                for (PluginWrapper p : activePlugins) {
                    try {
                        Class<?> c = ClassLoaderReflectionToolkit._findLoadedClass(p.classLoader, name);
                        if (c != null) {
                            synchronized (loaded) {
                                loaded.put(name, c);
                            }
                            return c;
                        }
                        // calling findClass twice appears to cause LinkageError: duplicate class def
                        c = ClassLoaderReflectionToolkit._findClass(p.classLoader, name);
                        synchronized (loaded) {
                            loaded.put(name, c);
                        }
                        return c;
                    } catch (ClassNotFoundException e) {
                        //not found. try next
                    }
                }
            } else {
                for (PluginWrapper p : activePlugins) {
                    try {
                        return p.classLoader.loadClass(name);
                    } catch (ClassNotFoundException e) {
                        //not found. try next
                    }
                }
            }
            synchronized (loaded) {
                loaded.put(name, null);
            }
            // not found in any of the classloader. delegate.
            throw new ClassNotFoundException(name);
        }

        @Override
        protected URL findResource(String name) {
            if (FAST_LOOKUP) {
                    for (PluginWrapper p : activePlugins) {
                        URL url = ClassLoaderReflectionToolkit._findResource(p.classLoader, name);
                        if(url!=null)
                            return url;
                    }
            } else {
                for (PluginWrapper p : activePlugins) {
                    URL url = p.classLoader.getResource(name);
                    if(url!=null)
                        return url;
                }
            }
            return null;
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            List<URL> resources = new ArrayList<URL>();
            if (FAST_LOOKUP) {
                    for (PluginWrapper p : activePlugins) {
                        resources.addAll(Collections.list(ClassLoaderReflectionToolkit._findResources(p.classLoader, name)));
                    }
            } else {
                for (PluginWrapper p : activePlugins) {
                    resources.addAll(Collections.list(p.classLoader.getResources(name)));
                }
            }
            return Collections.enumeration(resources);
        }

        @Override
        public String toString() {
            // only for debugging purpose
            return "classLoader " +  getClass().getName();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    public static boolean FAST_LOOKUP = !Boolean.getBoolean(PluginManager.class.getName()+".noFastLookup");

    public static final Permission UPLOAD_PLUGINS = new Permission(Jenkins.PERMISSIONS, "UploadPlugins", Messages._PluginManager_UploadPluginsPermission_Description(),Jenkins.ADMINISTER,PermissionScope.JENKINS);
    public static final Permission CONFIGURE_UPDATECENTER = new Permission(Jenkins.PERMISSIONS, "ConfigureUpdateCenter", Messages._PluginManager_ConfigureUpdateCenterPermission_Description(),Jenkins.ADMINISTER,PermissionScope.JENKINS);

    /**
     * Remembers why a plugin failed to deploy.
     */
    public static final class FailedPlugin {
        public final String name;
        public final Exception cause;

        public FailedPlugin(String name, Exception cause) {
            this.name = name;
            this.cause = cause;
        }

        public String getExceptionString() {
            return Functions.printThrowable(cause);
        }
    }

    /**
     * Stores {@link Plugin} instances.
     */
    /*package*/ static final class PluginInstanceStore {
        final Map<PluginWrapper,Plugin> store = new Hashtable<PluginWrapper,Plugin>();
    }

    /**
     * {@link AdministrativeMonitor} that checks if there are any plugins with cycle dependencies.
     */
    @Extension
    public static final class PluginCycleDependenciesMonitor extends AdministrativeMonitor {

        private transient volatile boolean isActive = false;

        private transient volatile List<String> pluginsWithCycle;

        public boolean isActivated() {
            if(pluginsWithCycle == null){
                pluginsWithCycle = new ArrayList<String>();
                for (PluginWrapper p : Jenkins.getInstance().getPluginManager().getPlugins()) {
                    if(p.hasCycleDependency()){
                        pluginsWithCycle.add(p.getShortName());
                        isActive = true;
                    }
                }
            }
            return isActive;
        }

        public List<String> getPluginsWithCycle() {
            return pluginsWithCycle;
        }
    }

    /**
     * {@link AdministrativeMonitor} that informs the administrator about a required plugin update.
     * @since 1.491
     */
    @Extension
    public static final class PluginUpdateMonitor extends AdministrativeMonitor {

        private Map<String, PluginUpdateInfo> pluginsToBeUpdated = new HashMap<String, PluginManager.PluginUpdateMonitor.PluginUpdateInfo>();

        /**
         * Convenience method to ease access to this monitor, this allows other plugins to register required updates.
         * @return this monitor.
         */
        public static final PluginUpdateMonitor getInstance() {
            return ExtensionList.lookup(PluginUpdateMonitor.class).get(0);
        }

        /**
         * Report to the administrator if the plugin with the given name is older then the required version.
         *
         * @param pluginName shortName of the plugin (artifactId)
         * @param requiredVersion the lowest version which is OK (e.g. 2.2.2)
         * @param message the message to show (plain text)
         */
        public void ifPluginOlderThenReport(String pluginName, String requiredVersion, String message){
            Plugin plugin = Jenkins.getInstance().getPlugin(pluginName);
            if(plugin != null){
                if(plugin.getWrapper().getVersionNumber().isOlderThan(new VersionNumber(requiredVersion))) {
                    pluginsToBeUpdated.put(pluginName, new PluginUpdateInfo(pluginName, message));
                }
            }
        }

        public boolean isActivated() {
            return !pluginsToBeUpdated.isEmpty();
        }

        /**
         * adds a message about a plugin to the manage screen
         * @param pluginName the plugins name
         * @param message the message to be displayed
         */
        public void addPluginToUpdate(String pluginName, String message) {
            this.pluginsToBeUpdated.put(pluginName, new PluginUpdateInfo(pluginName, message));
        }

        public Collection<PluginUpdateInfo> getPluginsToBeUpdated() {
            return pluginsToBeUpdated.values();
        }

        public static class PluginUpdateInfo {
            public final String pluginName;
            public final String message;
            private PluginUpdateInfo(String pluginName, String message) {
                this.pluginName = pluginName;
                this.message = message;
            }
        }

    }
}
