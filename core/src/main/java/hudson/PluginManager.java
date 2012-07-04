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
import hudson.model.AbstractModelObject;
import hudson.model.AdministrativeMonitor;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.IOException2;
import hudson.util.PersistedList;
import hudson.util.Service;
import jenkins.ClassLoaderReflectionToolkit;
import jenkins.InitReactorRunner;
import jenkins.RestartRequiredException;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import jenkins.util.io.OnMaster;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.LogFactory;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.*;

/**
 * Manages {@link PluginWrapper}s.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class PluginManager extends AbstractModelObject implements OnMaster {
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
     * @deprecated as of 1.355
     *      {@link PluginManager} can now live longer than {@link jenkins.model.Jenkins} instance, so
     *      use {@code Hudson.getInstance().servletContext} instead.
     */
    public final ServletContext context;

    /**
     * {@link ClassLoader} that can load all the publicly visible classes from plugins
     * (and including the classloader that loads Hudson itself.)
     *
     */
    // implementation is minimal --- just enough to run XStream
    // and load plugin-contributed classes.
    public final ClassLoader uberClassLoader = new UberClassLoader();

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

    public PluginManager(ServletContext context, File rootDir) {
        this.context = context;

        this.rootDir = rootDir;
        if(!rootDir.exists())
            rootDir.mkdirs();
        
        strategy = createPluginStrategy();
    }

    public Api getApi() {
        return new Api(this);
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
                                        ListIterator<PluginWrapper> litr = plugins.listIterator();
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
     * TODO: revisit where/how to expose this. This is an experiment.
     */
    public void dynamicLoad(File arc) throws IOException, InterruptedException, RestartRequiredException {
        LOGGER.info("Attempting to dynamic load "+arc);
        final PluginWrapper p = strategy.createPluginWrapper(arc);
        String sn = p.getShortName();
        if (getPlugin(sn)!=null)
            throw new RestartRequiredException(Messages._PluginManager_PluginIsAlreadyInstalled_RestartRequired(sn));

        if (p.supportsDynamicLoad()== YesNoMaybe.NO)
            throw new RestartRequiredException(Messages._PluginManager_PluginDoesntSupportDynamicLoad_RestartRequired(sn));

        // there's no need to do cyclic dependency check, because we are deploying one at a time,
        // so existing plugins can't be depending on this newly deployed one.

        plugins.add(p);
        activePlugins.add(p);

        try {
            p.resolvePluginDependencies();
            strategy.load(p);

            Jenkins.getInstance().refreshExtensions();

            p.getPlugin().postInitialize();
        } catch (Exception e) {
            failedPlugins.add(new FailedPlugin(sn, e));
            activePlugins.remove(p);
            plugins.remove(p);
            throw new IOException2("Failed to install "+ sn +" plugin",e);
        }

        // run initializers in the added plugin
        Reactor r = new Reactor(InitMilestone.ordering());
        r.addAll(new InitializerFinder(p.classLoader) {
            @Override
            protected boolean filter(Method e) {
                return e.getDeclaringClass().getClassLoader()!=p.classLoader || super.filter(e);
            }
        }.discoverTasks(r));
        try {
            new InitReactorRunner().run(r);
        } catch (ReactorException e) {
            throw new IOException2("Failed to initialize "+ sn +" plugin",e);
        }
        LOGGER.info("Plugin " + sn + " dynamically installed");
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
				LOGGER.log(Level.WARNING, "Could not instantiate plugin strategy: "
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
     * Returns true if any new plugin was added, which means a restart is required
     * for the change to take effect.
     */
    public boolean isPluginUploaded() {
        return pluginUploaded;
    }

    /**
     * All discovered plugins.
     */
    @Exported
    public List<PluginWrapper> getPlugins() {
        return plugins;
    }

    public List<FailedPlugin> getFailedPlugins() {
        return failedPlugins;
    }

    public PluginWrapper getPlugin(String shortName) {
        for (PluginWrapper p : plugins) {
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
        for (PluginWrapper p : plugins) {
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
        for (PluginWrapper p : plugins) {
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

        Enumeration<String> en = req.getParameterNames();
        while (en.hasMoreElements()) {
            String n =  en.nextElement();
            if(n.startsWith("plugin.")) {
                n = n.substring(7);
                if (n.indexOf(".") > 0) {
                    String[] pluginInfo = n.split("\\.");
                    UpdateSite.Plugin p = Jenkins.getInstance().getUpdateCenter().getById(pluginInfo[1]).getPlugin(pluginInfo[0]);
                    if(p==null)
                        throw new Failure("No such plugin: "+n);
                    p.deploy(dynamicLoad);
                }
            }
        }
        rsp.sendRedirect("../updateCenter/");
    }
    

    /**
     * Bare-minimum configuration mechanism to change the update center.
     */
    public HttpResponse doSiteConfigure(@QueryParameter String site) throws IOException {
        Jenkins hudson = Jenkins.getInstance();
        hudson.checkPermission(CONFIGURE_UPDATECENTER);
        UpdateCenter uc = hudson.getUpdateCenter();
        PersistedList<UpdateSite> sites = uc.getSites();
        for (UpdateSite s : sites) {
            if (s.getId().equals("default"))
                sites.remove(s);
        }
        sites.add(new UpdateSite("default",site));
        
        return HttpResponses.redirectToContextRoot();
    }


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
            final String baseName = FilenameUtils.getBaseName(fileName);
            new File(rootDir, baseName + ".hpi").delete(); // don't keep confusing legacy *.hpi
            fileItem.write(new File(rootDir, baseName + ".jpi")); // rename all new plugins to *.jpi
            fileItem.delete();

            PluginWrapper existing = getPlugin(baseName);
            if (existing!=null && existing.isBundled){
                existing.doPin();
            }

            pluginUploaded = true;

            return new HttpRedirect(".");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {// grrr. fileItem.write throws this
            throw new ServletException(e);
        }
    }

    public Descriptor<ProxyConfiguration> getProxyDescriptor() {
        return Jenkins.getInstance().getDescriptor(ProxyConfiguration.class);
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

        private ClassLoaderReflectionToolkit clt = new ClassLoaderReflectionToolkit();

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

            if (FAST_LOOKUP) {
                for (PluginWrapper p : activePlugins) {
                    try {
                        Class c = clt.findLoadedClass(p.classLoader,name);
                        if (c!=null)    return c;
                        // calling findClass twice appears to cause LinkageError: duplicate class def
                        return clt.findClass(p.classLoader,name);
                    } catch (InvocationTargetException e) {
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
            // not found in any of the classloader. delegate.
            throw new ClassNotFoundException(name);
        }

        @Override
        protected URL findResource(String name) {
            if (FAST_LOOKUP) {
                try {
                    for (PluginWrapper p : activePlugins) {
                        URL url = clt.findResource(p.classLoader,name);
                        if(url!=null)
                            return url;
                    }
                } catch (InvocationTargetException e) {
                    throw new Error(e);
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
                try {
                    for (PluginWrapper p : activePlugins) {
                        resources.addAll(Collections.list(clt.findResources(p.classLoader, name)));
                    }
                } catch (InvocationTargetException e) {
                    throw new Error(e);
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
}
