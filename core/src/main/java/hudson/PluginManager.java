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

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.PLUGINS_LISTED;
import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import static hudson.init.InitMilestone.PLUGINS_STARTED;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.PluginWrapper.Dependency;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.init.InitializerFinder;
import hudson.lifecycle.Lifecycle;
import hudson.model.AbstractItem;
import hudson.model.AbstractModelObject;
import hudson.model.AdministrativeMonitor;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.DownloadService;
import hudson.model.Failure;
import hudson.model.ItemGroupMixIn;
import hudson.model.UpdateCenter;
import hudson.model.UpdateCenter.DownloadJob;
import hudson.model.UpdateCenter.InstallationJob;
import hudson.model.UpdateSite;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.util.CheckingExistenceClassLoader;
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.DelegatingClassLoader;
import hudson.util.FormValidation;
import hudson.util.PersistedList;
import hudson.util.Retrier;
import hudson.util.Service;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import io.jenkins.servlet.ServletContextWrapper;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.CodeSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import jenkins.ClassLoaderReflectionToolkit;
import jenkins.ExtensionRefreshException;
import jenkins.InitReactorRunner;
import jenkins.MissingDependencyException;
import jenkins.RestartRequiredException;
import jenkins.YesNoMaybe;
import jenkins.install.InstallState;
import jenkins.install.InstallUtil;
import jenkins.model.Jenkins;
import jenkins.plugins.DetachedPluginsUtil;
import jenkins.security.CustomClassFilter;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import jenkins.util.io.OnMaster;
import jenkins.util.xml.RestrictiveEntityResolver;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletDiskFileUpload;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.LogFactory;
import org.jenkinsci.Symbol;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.core.Authentication;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Manages {@link PluginWrapper}s.
 *
 * <p>
 * <b>Setting default Plugin Managers</b>. The default plugin manager in {@code Jenkins} can be replaced by defining a
 * System Property ({@code hudson.PluginManager.className}). See {@link #createDefault(Jenkins)}.
 * This className should be available on early startup, so it cannot come only from a library
 * (e.g. Jenkins module or Extra library dependency in the WAR file project).
 * Plugins cannot be used for such purpose.
 * In order to be correctly instantiated, the class definition must have at least one constructor with the same
 * signature as the following ones:
 * <ol>
 *     <li>{@link LocalPluginManager#LocalPluginManager(Jenkins)} </li>
 *     <li>{@link LocalPluginManager#LocalPluginManager(ServletContext, File)} </li>
 *     <li>{@link LocalPluginManager#LocalPluginManager(File)} </li>
 * </ol>
 * Constructors are searched in the order provided above and only the first found suitable constructor is
 * tried to build an instance. In the last two cases the {@link File} argument refers to the <i>Jenkins home directory</i>.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class PluginManager extends AbstractModelObject implements OnMaster, StaplerOverridable, StaplerProxy {
    /** Custom plugin manager system property or context param. */
    public static final String CUSTOM_PLUGIN_MANAGER = PluginManager.class.getName() + ".className";

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    /**
     * Time elapsed between retries to check the updates sites. It's kind of constant, but let it so for tests
     */
    /* private final */ static int CHECK_UPDATE_SLEEP_TIME_MILLIS;

    /**
     * Number of attempts to check the updates sites. It's kind of constant, but let it so for tests
     */
    /* private final */ static int CHECK_UPDATE_ATTEMPTS;

    /**
     * Class name prefixes to skip in the class loading
     */
    private static final String[] CLASS_PREFIXES_TO_SKIP = {
            "SimpleTemplateScript",  // cf. groovy.text.SimpleTemplateEngine
            "groovy.tmp.templates.GStringTemplateScript", // Leaks on classLoader in some cases, see JENKINS-75879
    };

    static {
        try {
            // Secure initialization
            CHECK_UPDATE_SLEEP_TIME_MILLIS = SystemProperties.getInteger(PluginManager.class.getName() + ".checkUpdateSleepTimeMillis", 1000);
            CHECK_UPDATE_ATTEMPTS = SystemProperties.getInteger(PluginManager.class.getName() + ".checkUpdateAttempts", 1);
        } catch (RuntimeException e) {
            LOGGER.warning(String.format("There was an error initializing the PluginManager. Exception: %s", e));
        } finally {
            CHECK_UPDATE_ATTEMPTS = CHECK_UPDATE_ATTEMPTS > 0 ? CHECK_UPDATE_ATTEMPTS : 1;
            CHECK_UPDATE_SLEEP_TIME_MILLIS = CHECK_UPDATE_SLEEP_TIME_MILLIS > 0 ? CHECK_UPDATE_SLEEP_TIME_MILLIS : 1000;
        }
    }

    /** Accepted constructors for custom plugin manager, in the order they are tried. */
    private enum PMConstructor {
        JENKINS {
            @Override
            @NonNull
            PluginManager doCreate(@NonNull Class<? extends PluginManager> klass,
                                   @NonNull Jenkins jenkins) throws ReflectiveOperationException {
                return klass.getConstructor(Jenkins.class).newInstance(jenkins);
            }
        },
        SC_FILE2 {
            @Override
            @NonNull PluginManager doCreate(@NonNull Class<? extends PluginManager> klass,
                                            @NonNull Jenkins jenkins) throws ReflectiveOperationException {
                return klass.getConstructor(ServletContext.class, File.class).newInstance(jenkins.getServletContext(), jenkins.getRootDir());
            }
        },
        /**
         * @deprecated use {@link #SC_FILE2}
         */
        @Deprecated
        SC_FILE {
            @Override
            @NonNull PluginManager doCreate(@NonNull Class<? extends PluginManager> klass,
                                            @NonNull Jenkins jenkins) throws ReflectiveOperationException {
                return klass.getConstructor(javax.servlet.ServletContext.class, File.class).newInstance(jenkins.servletContext, jenkins.getRootDir());
            }
        },
        FILE {
            @Override
            @NonNull PluginManager doCreate(@NonNull Class<? extends PluginManager> klass,
                                            @NonNull Jenkins jenkins) throws ReflectiveOperationException {
                return klass.getConstructor(File.class).newInstance(jenkins.getRootDir());
            }
        };

        final @CheckForNull PluginManager create(@NonNull Class<? extends PluginManager> klass,
                                                 @NonNull Jenkins jenkins) throws ReflectiveOperationException {
            try {
                return doCreate(klass, jenkins);
            } catch (NoSuchMethodException e) {
                // Constructor not found. Will try the remaining ones.
                return null;
            }
        }

        abstract @NonNull PluginManager doCreate(@NonNull Class<? extends PluginManager> klass,
                                                 @NonNull Jenkins jenkins) throws ReflectiveOperationException;
    }

    /**
     * Creates the {@link PluginManager} to use if no one is provided to a {@link Jenkins} object.
     * This method will be called after creation of {@link Jenkins} object, but before it is fully initialized.
     * @param jenkins Jenkins Instance.
     * @return Plugin manager to use. If no custom class is configured or in case of any error, the default
     * {@link LocalPluginManager} is returned.
     */
    public static @NonNull PluginManager createDefault(@NonNull Jenkins jenkins) {
        String pmClassName = SystemProperties.getString(CUSTOM_PLUGIN_MANAGER);
        if (pmClassName != null && !pmClassName.isBlank()) {
            LOGGER.log(FINE, String.format("Use of custom plugin manager [%s] requested.", pmClassName));
            try {
                final Class<? extends PluginManager> klass = Class.forName(pmClassName).asSubclass(PluginManager.class);
                // Iteration is in declaration order
                for (PMConstructor c : PMConstructor.values()) {
                    PluginManager pm = c.create(klass, jenkins);
                    if (pm != null) {
                        return pm;
                    }
                }
                LOGGER.log(WARNING, String.format("Provided custom plugin manager [%s] does not provide any of the suitable constructors. Using default.", pmClassName));
            } catch (ClassCastException e) {
                LOGGER.log(WARNING, String.format("Provided class [%s] does not extend PluginManager. Using default.", pmClassName));
            } catch (Exception e) {
                LOGGER.log(WARNING, String.format("Unable to instantiate custom plugin manager [%s]. Using default.", pmClassName), e);
            }
        }
        return new LocalPluginManager(jenkins);
    }

    /**
     * All discovered plugins.
     */
    protected final List<PluginWrapper> plugins = new CopyOnWriteArrayList<>();

    /**
     * All active plugins, topologically sorted so that when X depends on Y, Y appears in the list before X does.
     */
    protected final List<PluginWrapper> activePlugins = new CopyOnWriteArrayList<>();

    protected final List<FailedPlugin> failedPlugins = new ArrayList<>();

    /**
     * Plug-in root directory.
     */
    public final File rootDir;

    /**
     * Hold the status of the last try to check update centers. Consumed from the check.jelly to show an
     * error message if the last attempt failed.
     */
    private String lastErrorCheckUpdateCenters = null;

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
    public final ClassLoader uberClassLoader = new UberClassLoader(activePlugins);

    /**
     * Once plugin is uploaded, this flag becomes true.
     * This is used to report a message that Jenkins needs to be restarted
     * for new plugins to take effect.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
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
     * @since 2.475
     */
    protected PluginManager(ServletContext context, File rootDir) {
        this.context = context;

        this.rootDir = rootDir;
        try {
            Util.createDirectories(rootDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String workDir = SystemProperties.getString(PluginManager.class.getName() + ".workDir");
        this.workDir = workDir == null || workDir.isBlank() ? null : new File(workDir);

        strategy = createPluginStrategy();
    }

    /**
     * @deprecated use {@link #PluginManager(ServletContext, File)}
     */
    @Deprecated
    protected PluginManager(javax.servlet.ServletContext context, File rootDir) {
        this(context != null ? ServletContextWrapper.toJakartaServletContext(context) : null, rootDir);
    }

    public Api getApi() {
        Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
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
                        @Override
                        public void run(Reactor session) throws Exception {
                            bundledPlugins = loadBundledPlugins();
                        }
                    });

                    Handle listUpPlugins = requires(loadBundledPlugins).add("Listing up plugins", new Executable() {
                        @Override
                        public void run(Reactor session) throws Exception {
                            archives = initStrategy.listPluginArchives(PluginManager.this);
                        }
                    });

                    requires(listUpPlugins).attains(PLUGINS_LISTED).add("Preparing plugins", new Executable() {
                        @Override
                        public void run(Reactor session) throws Exception {
                            // once we've listed plugins, we can fill in the reactor with plugin-specific initialization tasks
                            TaskGraphBuilder g = new TaskGraphBuilder();

                            final Map<String, File> inspectedShortNames = new HashMap<>();

                            for (final File arc : archives) {
                                g.followedBy().notFatal().attains(PLUGINS_LISTED).add("Inspecting plugin " + arc, new Executable() {
                                    @Override
                                    public void run(Reactor session1) throws Exception {
                                        try {
                                            PluginWrapper p = strategy.createPluginWrapper(arc);
                                            if (isDuplicate(p)) return;

                                            p.isBundled = containsHpiJpi(bundledPlugins, arc.getName());
                                            plugins.add(p);
                                        } catch (IOException e) {
                                            failedPlugins.add(new FailedPlugin(arc.getName(), e));
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
                                            LOGGER.info("Ignoring " + arc + " because " + inspectedShortNames.get(shortName) + " is already loaded");
                                            return true;
                                        }

                                        inspectedShortNames.put(shortName, arc);
                                        return false;
                                    }
                                });
                            }

                            g.followedBy().attains(PLUGINS_LISTED).add("Checking cyclic dependencies", new Executable() {
                                /**
                                 * Makes sure there's no cycle in dependencies.
                                 */
                                @Override
                                public void run(Reactor reactor) throws Exception {
                                    try {
                                        CyclicGraphDetector<PluginWrapper> cgd = new CyclicGraphDetector<>() {
                                            @Override
                                            protected List<PluginWrapper> getEdges(PluginWrapper p) {
                                                List<PluginWrapper> next = new ArrayList<>();
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
                                            protected void reactOnCycle(PluginWrapper q, List<PluginWrapper> cycle) {

                                                LOGGER.log(Level.SEVERE, "found cycle in plugin dependencies: (root=" + q + ", deactivating all involved) " + cycle.stream().map(Object::toString).collect(Collectors.joining(" -> ")));
                                                for (PluginWrapper pluginWrapper : cycle) {
                                                    pluginWrapper.setHasCycleDependency(true);
                                                    failedPlugins.add(new FailedPlugin(pluginWrapper, new CycleDetectedException(cycle)));
                                                }
                                            }

                                        };
                                        cgd.run(getPlugins());

                                        // obtain topologically sorted list and overwrite the list
                                        for (PluginWrapper p : cgd.getSorted()) {
                                            if (p.isActive()) {
                                                activePlugins.add(p);
                                                ((UberClassLoader) uberClassLoader).clearCacheMisses();
                                            }
                                        }
                                    } catch (CycleDetectedException e) { // TODO this should be impossible, since we override reactOnCycle to not throw the exception
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
                builder, new TaskGraphBuilder() {{
            requires(PLUGINS_LISTED).attains(PLUGINS_PREPARED).add("Loading plugins", new Executable() {
                /**
                 * Once the plugins are listed, schedule their initialization.
                 */
                @Override
                public void run(Reactor session) throws Exception {
                    Jenkins.get().lookup.set(PluginInstanceStore.class, new PluginInstanceStore());
                    TaskGraphBuilder g = new TaskGraphBuilder();

                    // schedule execution of loading plugins
                    for (final PluginWrapper p : activePlugins.toArray(new PluginWrapper[0])) {
                        g.followedBy().notFatal().attains(PLUGINS_PREPARED).add(String.format("Loading plugin %s v%s (%s)", p.getLongName(), p.getVersion(), p.getShortName()), new Executable() {
                            @Override
                            public void run(Reactor session) throws Exception {
                                try {
                                    p.resolvePluginDependencies();
                                    strategy.load(p);
                                } catch (MissingDependencyException e) {
                                    failedPlugins.add(new FailedPlugin(p, e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    p.releaseClassLoader();
                                    LOGGER.log(Level.SEVERE, "Failed to install {0}: {1}", new Object[] { p.getShortName(), e.getMessage() });
                                } catch (IOException e) {
                                    failedPlugins.add(new FailedPlugin(p, e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    p.releaseClassLoader();
                                    throw e;
                                }
                            }
                        });
                    }

                    // schedule execution of initializing plugins
                    for (final PluginWrapper p : activePlugins.toArray(new PluginWrapper[0])) {
                        g.followedBy().notFatal().attains(PLUGINS_STARTED).add("Initializing plugin " + p.getShortName(), new Executable() {
                            @Override
                            public void run(Reactor session) throws Exception {
                                if (!activePlugins.contains(p)) {
                                    return;
                                }
                                try {
                                    p.getPluginOrFail().postInitialize();
                                } catch (Exception e) {
                                    failedPlugins.add(new FailedPlugin(p, e));
                                    activePlugins.remove(p);
                                    plugins.remove(p);
                                    p.releaseClassLoader();
                                    throw e;
                                }
                            }
                        });
                    }

                    g.followedBy().attains(PLUGINS_STARTED).add("Discovering plugin initialization tasks", new Executable() {
                        @Override
                        public void run(Reactor reactor) throws Exception {
                            // rescan to find plugin-contributed @Initializer
                            reactor.addAll(initializerFinder.discoverTasks(reactor));
                        }
                    });

                    // register them all
                    session.addAll(g.discoverTasks(session));
                }
            });

            // All plugins are loaded. Now we can figure out who depends on who.
            requires(PLUGINS_PREPARED).attains(COMPLETED).add("Resolving Dependent Plugins Graph", new Executable() {
                @Override
                public void run(Reactor reactor) throws Exception {
                    resolveDependentPlugins();
                }
            });
        }});
    }

    void considerDetachedPlugin(String shortName, String source) {
        if (new File(rootDir, shortName + ".jpi").isFile() ||
            new File(rootDir, shortName + ".hpi").isFile() ||
            new File(rootDir, shortName + ".jpl").isFile() ||
            new File(rootDir, shortName + ".hpl").isFile()) {
            LOGGER.fine(() -> "not considering loading a detached dependency " + shortName + " as it is already on disk");
            return;
        }
        LOGGER.fine(() -> "considering loading a detached dependency " + shortName);
        for (String loadedFile : loadPluginsFromWar(getDetachedLocation(), (dir, name) -> normalisePluginName(name).equals(shortName))) {
            String loaded = normalisePluginName(loadedFile);
            File arc = new File(rootDir, loaded + ".jpi");
            LOGGER.info(() -> "Loading a detached plugin " + arc + " as a dependency of " + source);
            try {
                plugins.add(strategy.createPluginWrapper(arc));
            } catch (IOException e) {
                failedPlugins.add(new FailedPlugin(arc.getName(), e));
            }

        }
    }

    /**
     * Defines the location of the detached plugins in the WAR.
     * @return by default, {@code /WEB-INF/detached-plugins}
     * @since 2.377
     */
    protected @NonNull String getDetachedLocation() {
        return "/WEB-INF/detached-plugins";
    }

    protected @NonNull Set<String> loadPluginsFromWar(@NonNull String fromPath) {
        return loadPluginsFromWar(fromPath, null);
    }

    //TODO: Consider refactoring in order to avoid DMI_COLLECTION_OF_URLS
    @SuppressFBWarnings(value = "DMI_COLLECTION_OF_URLS", justification = "Plugin loading happens only once on Jenkins startup")
    protected @NonNull Set<String> loadPluginsFromWar(@NonNull String fromPath, @CheckForNull FilenameFilter filter) {
        Set<String> names = new HashSet<>();

        ServletContext context = Jenkins.get().getServletContext();
        Set<String> plugins = Util.fixNull(context.getResourcePaths(fromPath));
        Set<URL> copiedPlugins = new HashSet<>();
        Set<URL> dependencies = new HashSet<>();

        for (String pluginPath : plugins) {
            String fileName = pluginPath.substring(pluginPath.lastIndexOf('/') + 1);
            if (fileName.isEmpty()) {
                // see http://www.nabble.com/404-Not-Found-error-when-clicking-on-help-td24508544.html
                // I suspect some containers are returning directory names.
                continue;
            }
            try {
                URL url = context.getResource(pluginPath);
                if (filter != null && url != null) {
                    if (!filter.accept(new File(url.getFile()).getParentFile(), fileName)) {
                        continue;
                    }
                }

                names.add(fileName);
                copyBundledPlugin(Objects.requireNonNull(url), fileName);
                copiedPlugins.add(url);
                try {
                    addDependencies(url, fromPath, dependencies);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to resolve dependencies for the bundled plugin " + fileName, e);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to extract the bundled plugin " + fileName, e);
            }
        }

        // Copy dependencies. These are not detached plugins, but are required by them.
        for (URL dependency : dependencies) {
            if (copiedPlugins.contains(dependency)) {
                // Ignore. Already copied.
                continue;
            }

            String fileName = new File(dependency.getFile()).getName();
            try {
                names.add(fileName);
                copyBundledPlugin(dependency, fileName);
                copiedPlugins.add(dependency);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to extract the bundled dependency plugin " + fileName, e);
            }
        }

        return names;
    }

    //TODO: Consider refactoring in order to avoid DMI_COLLECTION_OF_URLS
    @SuppressFBWarnings(value = "DMI_COLLECTION_OF_URLS", justification = "Plugin loading happens only once on Jenkins startup")
    protected static void addDependencies(URL hpiResUrl, String fromPath, Set<URL> dependencySet) throws URISyntaxException, MalformedURLException {
        if (dependencySet.contains(hpiResUrl)) {
            return;
        }

        Manifest manifest = parsePluginManifest(hpiResUrl);
        if (manifest == null) {
            return;
        }

        String dependencySpec = manifest.getMainAttributes().getValue("Plugin-Dependencies");
        if (dependencySpec != null) {
            String[] dependencyTokens = dependencySpec.split(",");
            ServletContext context = Jenkins.get().getServletContext();

            for (String dependencyToken : dependencyTokens) {
                if (dependencyToken.endsWith(";resolution:=optional")) {
                    // ignore optional dependencies
                    continue;
                }

                String[] artifactIdVersionPair = dependencyToken.split(":");
                String artifactId = artifactIdVersionPair[0];
                VersionNumber dependencyVersion = new VersionNumber(artifactIdVersionPair[1]);

                PluginManager manager = Jenkins.get().getPluginManager();
                VersionNumber installedVersion = manager.getPluginVersion(manager.rootDir, artifactId);
                if (installedVersion != null && !installedVersion.isOlderThan(dependencyVersion)) {
                    // Do not downgrade dependencies that are already installed.
                    continue;
                }

                URL dependencyURL = context.getResource(fromPath + "/" + artifactId + ".hpi");

                if (dependencyURL == null) {
                    // Maybe bundling has changed .jpi files
                    dependencyURL = context.getResource(fromPath + "/" + artifactId + ".jpi");
                }

                if (dependencyURL != null) {
                    // And transitive deps...
                    addDependencies(dependencyURL, fromPath, dependencySet);
                    // And then add the current plugin
                    dependencySet.add(dependencyURL);
                }
            }
        }
    }

    /**
     * Load detached plugins and their dependencies.
     * <p>
     * Only loads plugins that:
     * <ul>
     *     <li>Have been detached since the last running version.</li>
     *     <li>Are already installed and need to be upgraded. This can be the case if this Jenkins install has been running since before plugins were "unbundled".</li>
     *     <li>Are dependencies of one of the above e.g. script-security is not one of the detached plugins but it must be loaded if matrix-project is loaded.</li>
     * </ul>
     */
    protected void loadDetachedPlugins() {
        VersionNumber lastExecVersion = new VersionNumber(InstallUtil.getLastExecVersion());
        if (lastExecVersion.isNewerThan(InstallUtil.NEW_INSTALL_VERSION) && lastExecVersion.isOlderThan(Jenkins.getVersion())) {

            LOGGER.log(INFO, "Upgrading Jenkins. The last running version was {0}. This Jenkins is version {1}.",
                    new Object[] {lastExecVersion, Jenkins.VERSION});

            final List<DetachedPluginsUtil.DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins(lastExecVersion);

            Set<String> loadedDetached = loadPluginsFromWar(getDetachedLocation(), new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    name = normalisePluginName(name);

                    // If this was a plugin that was detached some time in the past i.e. not just one of the
                    // plugins that was bundled "for fun".
                    if (DetachedPluginsUtil.isDetachedPlugin(name)) {
                        VersionNumber installedVersion = getPluginVersion(rootDir, name);
                        VersionNumber bundledVersion = getPluginVersion(dir, name);
                        // If the plugin is already installed, we need to decide whether to replace it with the bundled version.
                        if (installedVersion != null && bundledVersion != null) {
                            // If the installed version is older than the bundled version, then it MUST be upgraded.
                            // If the installed version is newer than the bundled version, then it MUST NOT be upgraded.
                            // If the versions are equal we just keep the installed version.
                            return installedVersion.isOlderThan(bundledVersion);
                        }
                    }

                    // If it's a plugin that was detached since the last running version.
                    for (DetachedPluginsUtil.DetachedPlugin detachedPlugin : detachedPlugins) {
                        if (detachedPlugin.getShortName().equals(name)) {
                            return true;
                        }
                    }

                    // Otherwise skip this and do not install.
                    return false;
                }
            });

            LOGGER.log(INFO, "Upgraded Jenkins from version {0} to version {1}. Loaded detached plugins (and dependencies): {2}",
                    new Object[] {lastExecVersion, Jenkins.VERSION, loadedDetached});
        } else {
            final Set<DetachedPluginsUtil.DetachedPlugin> forceUpgrade = new HashSet<>();
            // TODO using getDetachedPlugins here seems wrong; should be forcing an upgrade when the installed version is older than that in getDetachedLocation()
            for (DetachedPluginsUtil.DetachedPlugin p : DetachedPluginsUtil.getDetachedPlugins()) {
                VersionNumber installedVersion = getPluginVersion(rootDir, p.getShortName());
                VersionNumber requiredVersion = p.getRequiredVersion();
                if (installedVersion != null && installedVersion.isOlderThan(requiredVersion)) {
                    LOGGER.log(Level.WARNING,
                            "Detached plugin {0} found at version {1}, required minimum version is {2}",
                            new Object[]{p.getShortName(), installedVersion, requiredVersion});
                    forceUpgrade.add(p);
                }
            }
            if (!forceUpgrade.isEmpty()) {
                Set<String> loadedDetached = loadPluginsFromWar(getDetachedLocation(), new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        name = normalisePluginName(name);
                        for (DetachedPluginsUtil.DetachedPlugin detachedPlugin : forceUpgrade) {
                            if (detachedPlugin.getShortName().equals(name)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
                LOGGER.log(INFO, "Upgraded detached plugins (and dependencies): {0}",
                        new Object[]{loadedDetached});
            }
        }
    }

    private String normalisePluginName(@NonNull String name) {
        // Normalise the name by stripping off the file extension (if present)...
        return name.replace(".jpi", "").replace(".hpi", "");
    }

    private @CheckForNull VersionNumber getPluginVersion(@NonNull File dir, @NonNull String pluginId) {
        VersionNumber version = getPluginVersion(new File(dir, pluginId + ".jpi"));
        if (version == null) {
            version = getPluginVersion(new File(dir, pluginId + ".hpi"));
        }
        return version;
    }

    private @CheckForNull VersionNumber getPluginVersion(@NonNull File pluginFile) {
        if (!pluginFile.exists()) {
            return null;
        }
        try {
            return getPluginVersion(pluginFile.toURI().toURL());
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private @CheckForNull VersionNumber getPluginVersion(@NonNull URL pluginURL) {
        Manifest manifest = parsePluginManifest(pluginURL);
        if (manifest == null) {
            return null;
        }
        String versionSpec = manifest.getMainAttributes().getValue("Plugin-Version");
        return new VersionNumber(versionSpec);
    }

    /*
     * contains operation that considers xxx.hpi and xxx.jpi as equal
     * this is necessary since the bundled plugins are still called *.hpi
     */
    private boolean containsHpiJpi(Collection<String> bundledPlugins, String name) {
        return bundledPlugins.contains(name.replaceAll("\\.hpi", ".jpi"))
                || bundledPlugins.contains(name.replaceAll("\\.jpi", ".hpi"));
    }

    /**
     * Returns the manifest of a bundled but not-extracted plugin.
     * @deprecated removed without replacement
     */
    @Deprecated // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
    public @CheckForNull Manifest getBundledPluginManifest(String shortName) {
        return null;
    }

    /**
     * TODO: revisit where/how to expose this. This is an experiment.
     */
    public void dynamicLoad(File arc) throws IOException, InterruptedException, RestartRequiredException {
        dynamicLoad(arc, false, null);
    }

    /**
     * Try the dynamicLoad, removeExisting to attempt to dynamic load disabled plugins
     */
    @Restricted(NoExternalUse.class)
    public void dynamicLoad(File arc, boolean removeExisting, @CheckForNull List<PluginWrapper> batch) throws IOException, InterruptedException, RestartRequiredException {
        try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
            LOGGER.log(FINE, "Attempting to dynamic load {0}", arc);
            PluginWrapper p = null;
            String sn;
            try {
                sn = strategy.getShortName(arc);
            } catch (AbstractMethodError x) {
                LOGGER.log(WARNING, "JENKINS-12753 fix not active: {0}", x.getMessage());
                p = strategy.createPluginWrapper(arc);
                sn = p.getShortName();
            }
            PluginWrapper pw = getPlugin(sn);
            if (pw != null) {
                if (removeExisting) { // try to load disabled plugins
                    for (Iterator<PluginWrapper> i = plugins.iterator(); i.hasNext();) {
                        pw = i.next();
                        if (sn.equals(pw.getShortName())) {
                            i.remove();
                            break;
                        }
                    }
                } else {
                    throw new RestartRequiredException(Messages._PluginManager_PluginIsAlreadyInstalled_RestartRequired(sn));
                }
            }
            if (!Lifecycle.get().supportsDynamicLoad()) {
                throw new RestartRequiredException(Messages._PluginManager_LifecycleDoesNotSupportDynamicLoad_RestartRequired());
            }
            if (p == null) {
                p = strategy.createPluginWrapper(arc);
            }
            if (p.supportsDynamicLoad() == YesNoMaybe.NO)
                throw new RestartRequiredException(Messages._PluginManager_PluginDoesntSupportDynamicLoad_RestartRequired(sn));

            // there's no need to do cyclic dependency check, because we are deploying one at a time,
            // so existing plugins can't be depending on this newly deployed one.

            plugins.add(p);
            if (p.isActive()) {
                activePlugins.add(p);
                ((UberClassLoader) uberClassLoader).clearCacheMisses();
            }

            // TODO antimodular; perhaps should have a PluginListener to complement ExtensionListListener?
            CustomClassFilter.Contributed.load();

            try {
                p.resolvePluginDependencies();
                strategy.load(p);

                if (batch != null) {
                    batch.add(p);
                } else {
                    start(List.of(p));
                }

            } catch (Exception e) {
                failedPlugins.add(new FailedPlugin(p, e));
                activePlugins.remove(p);
                plugins.remove(p);
                p.releaseClassLoader();
                throw new IOException("Failed to install " + sn + " plugin", e);
            }

            LOGGER.log(FINE, "Plugin {0}:{1} dynamically {2}", new Object[] {p.getShortName(), p.getVersion(), batch != null ? "loaded but not yet started" : "installed"});
        }
    }

    @Restricted(NoExternalUse.class)
    public void start(List<PluginWrapper> plugins) throws Exception {
      try (ACLContext context = ACL.as2(ACL.SYSTEM2)) {
        Map<String, PluginWrapper> pluginsByName = plugins.stream().collect(Collectors.toMap(PluginWrapper::getShortName, p -> p));

        // recalculate dependencies of plugins optionally depending the newly deployed ones.
        for (PluginWrapper depender : this.plugins) {
            if (plugins.contains(depender)) {
                // skip itself.
                continue;
            }
            for (Dependency d : depender.getOptionalDependencies()) {
                PluginWrapper dependee = pluginsByName.get(d.shortName);
                if (dependee != null) {
                    // this plugin depends on the newly loaded one!
                    // recalculate dependencies!
                    getPluginStrategy().updateDependency(depender, dependee);
                    break;
                }
            }
        }

        // Redo who depends on who.
        resolveDependentPlugins();

        try {
            Jenkins.get().refreshExtensions();
        } catch (ExtensionRefreshException e) {
            throw new IOException("Failed to refresh extensions after installing some plugins", e);
        }
        for (PluginWrapper p : plugins) {
            //TODO:According to the postInitialize() documentation, one may expect that
            //p.getPluginOrFail() NPE will continue the initialization. Keeping the original behavior ATM
          p.getPluginOrFail().postInitialize();
        }

        // run initializers in the added plugins
        Reactor r = new Reactor(InitMilestone.ordering());
        Set<ClassLoader> loaders = plugins.stream().map(p -> p.classLoader).collect(Collectors.toSet());
        r.addAll(new InitializerFinder(uberClassLoader) {
          @Override
          protected boolean filter(Method e) {
            return !loaders.contains(e.getDeclaringClass().getClassLoader()) || super.filter(e);
          }
        }.discoverTasks(r));
        new InitReactorRunner().run(r);
      }
    }

    @Restricted(NoExternalUse.class)
    public synchronized void resolveDependentPlugins() {
        for (PluginWrapper plugin : plugins) {
            // Set of optional dependents plugins of plugin
            Set<String> optionalDependents = new HashSet<>();
            Set<String> dependents = new HashSet<>();
            for (PluginWrapper possibleDependent : plugins) {
                // No need to check if plugin is dependent of itself
                if (possibleDependent.getShortName().equals(plugin.getShortName())) {
                    continue;
                }

                // The plugin could have just been deleted. If so, it doesn't
                // count as a dependent.
                if (possibleDependent.isDeleted()) {
                    continue;
                }
                List<Dependency> dependencies = possibleDependent.getDependencies();
                for (Dependency dependency : dependencies) {
                    if (dependency.shortName.equals(plugin.getShortName())) {
                        dependents.add(possibleDependent.getShortName());

                        // If, in addition, the dependency is optional, add to the optionalDependents list
                        if (dependency.optional) {
                            optionalDependents.add(possibleDependent.getShortName());
                        }

                        // already know possibleDependent depends on plugin, no need to continue with the rest of
                        // dependencies. We continue with the next possibleDependent
                        break;
                    }
                }
            }
            plugin.setDependents(dependents);
            plugin.setOptionalDependents(optionalDependents);
        }
    }

    /**
     * If the war file has any "/WEB-INF/plugins/[*.jpi | *.hpi]", extract them into the plugin directory.
     *
     * @return
     *      File names of the bundled plugins. Normally empty (not to be confused with {@link #loadDetachedPlugins}) but OEM WARs may have some.
     * @throws Exception
     *      Any exception will be reported and halt the startup.
     */
    protected abstract Collection<String> loadBundledPlugins() throws Exception;

    /**
     * Copies the plugin from the given URL to the given destination.
     * Despite the name, this is used also from {@link #loadDetachedPlugins}.
     * Includes a reasonable up-to-date check.
     * A convenience method to be used by {@link #loadBundledPlugins()}.
     * @param fileName like {@code abc.jpi}
     */
    protected void copyBundledPlugin(URL src, String fileName) throws IOException {
        LOGGER.log(FINE, "Copying {0}", src);
        fileName = fileName.replace(".hpi", ".jpi"); // normalize fileNames to have the correct suffix
        String legacyName = fileName.replace(".jpi", ".hpi");
        long lastModified = getModificationDate(src);
        File file = new File(rootDir, fileName);

        // normalization first, if the old file exists.
        rename(new File(rootDir, legacyName), file);

        // update file if:
        //  - no file exists today
        //  - bundled version and current version differs (by timestamp).
        if (!file.exists() || file.lastModified() != lastModified) {
            FileUtils.copyURLToFile(src, file);
            Files.setLastModifiedTime(Util.fileToPath(file), FileTime.fromMillis(getModificationDate(src)));
            // lastModified is set for two reasons:
            // - to avoid unpacking as much as possible, but still do it on both upgrade and downgrade
            // - to make sure the value is not changed after each restart, so we can avoid
            // unpacking the plugin itself in ClassicPluginStrategy.explode
        }

        // Plugin pinning has been deprecated.
        // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
    }

    /*package*/ static @CheckForNull Manifest parsePluginManifest(URL bundledJpi) {
        try (URLClassLoader cl = new URLClassLoader("Temporary classloader for parsing " + bundledJpi.toString(), new URL[]{bundledJpi}, ClassLoader.getSystemClassLoader())) {
            InputStream in = null;
            try {
                URL res = cl.findResource(PluginWrapper.MANIFEST_FILENAME);
                if (res != null) {
                    in = getBundledJpiManifestStream(res);
                    return new Manifest(in);
                }
            } finally {
                Util.closeAndLogFailures(in, LOGGER, PluginWrapper.MANIFEST_FILENAME, bundledJpi.toString());
            }
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to parse manifest of " + bundledJpi, e);
        }
        return null;
    }

    /**
     * Retrieves input stream for the Manifest url.
     * The method intelligently handles the case of {@link JarURLConnection} pointing to files within JAR.
     * @param url Url of the manifest file
     * @return Input stream, which allows to retrieve manifest. This stream must be closed outside
     * @throws IOException Operation error
     */
    @NonNull
    /*package*/ static InputStream getBundledJpiManifestStream(@NonNull URL url) throws IOException {
        URLConnection uc = url.openConnection();
        InputStream in = null;
        // Magic, which allows to avoid using stream generated for JarURLConnection.
        // It prevents getting into JENKINS-37332 due to the file descriptor leak
        if (uc instanceof JarURLConnection) {
            final JarURLConnection jarURLConnection = (JarURLConnection) uc;
            final String entryName = jarURLConnection.getEntryName();

            try (JarFile jarFile = jarURLConnection.getJarFile()) {
                final JarEntry entry = entryName != null && jarFile != null ? jarFile.getJarEntry(entryName) : null;
                if (entry != null) {
                    try (InputStream i = jarFile.getInputStream(entry)) {
                        byte[] manifestBytes = i.readAllBytes();
                        in = new ByteArrayInputStream(manifestBytes);
                    }
                } else {
                    LOGGER.log(Level.WARNING, "Failed to locate the JAR file for {0}"
                            + "The default URLConnection stream access will be used, file descriptor may be leaked.",
                               url);
                }
            }
        }

        // If input stream is undefined, use the default implementation
        if (in == null) {
            in = url.openStream();
        }

        return in;
    }

    /**
     * Retrieves modification date of the specified file.
     * The method intelligently handles the case of {@link JarURLConnection} pointing to files within JAR.
     * @param url Url of the file
     * @return Modification date
     * @throws IOException Operation error
     */
    @NonNull
    /*package*/ static long getModificationDate(@NonNull URL url) throws IOException {
        URLConnection uc = url.openConnection();

        // It prevents file descriptor leak if the URL references a file within JAR
        // See JENKINS-37332  for more info
        // The code idea is taken from https://github.com/jknack/handlebars.java/pull/394
        if (uc instanceof JarURLConnection) {
            final JarURLConnection connection = (JarURLConnection) uc;
            final URL jarURL = connection.getJarFileURL();
            if (jarURL.getProtocol().equals("file")) {
                String file = jarURL.getFile();
                return new File(file).lastModified();
            } else {
                // We access the data without file protocol
                if (connection.getEntryName() != null) {
                    LOGGER.log(WARNING, "Accessing modification date of {0} file, which is an entry in JAR file. "
                        + "The access protocol is not file:, falling back to the default logic (risk of file descriptor leak).",
                            url);
                }
            }
        }

        // Fallbak to the default implementation
        return uc.getLastModified();
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
        String strategyName = SystemProperties.getString(PluginStrategy.class.getName());
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
        return Collections.unmodifiableList(plugins);
    }

    @Restricted(NoExternalUse.class) // used by jelly
    public List<PluginWrapper> getPluginsSortedByTitle() {
        return plugins.stream()
                .sorted(Comparator.comparing(PluginWrapper::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toUnmodifiableList());
    }

    public List<FailedPlugin> getFailedPlugins() {
        return failedPlugins;
    }

    /**
     * Get the plugin instance with the given short name.
     * @param shortName the short name of the plugin
     * @return The plugin singleton or {@code null} if a plugin with the given short name does not exist.
     *         The fact the plugin is loaded does not mean it is enabled and fully initialized for the current Jenkins session.
     *         Use {@link PluginWrapper#isActive()} to check it.
     */
    @CheckForNull
    public PluginWrapper getPlugin(String shortName) {
        for (PluginWrapper p : getPlugins()) {
            if (p.getShortName().equals(shortName))
                return p;
        }
        return null;
    }

    /**
     * Get the plugin instance that implements a specific class, use to find your plugin singleton.
     * Note: beware the classloader fun.
     * @param pluginClazz The class that your plugin implements.
     * @return The plugin singleton or {@code null} if for some reason the plugin is not loaded.
     *         The fact the plugin is loaded does not mean it is enabled and fully initialized for the current Jenkins session.
     *         Use {@link Plugin#getWrapper()} and then {@link PluginWrapper#isActive()} to check it.
     */
    @CheckForNull
    public PluginWrapper getPlugin(Class<? extends Plugin> pluginClazz) {
        for (PluginWrapper p : getPlugins()) {
            if (pluginClazz.isInstance(p.getPlugin()))
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
        List<PluginWrapper> result = new ArrayList<>();
        for (PluginWrapper p : getPlugins()) {
            if (pluginSuperclass.isInstance(p.getPlugin()))
                result.add(p);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String getDisplayName() {
        return Messages.PluginManager_DisplayName();
    }

    @Override
    public String getSearchUrl() {
        return "pluginManager";
    }

    /**
     * Discover all the service provider implementations of the given class,
     * via {@code META-INF/services}.
     * @deprecated Use {@link ServiceLoader} instead, or (more commonly) {@link ExtensionList}.
     */
    @Deprecated
    public <T> Collection<Class<? extends T>> discover(Class<T> spi) {
        Set<Class<? extends T>> result = new HashSet<>();

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
            if (p.classLoader == cl) {
                if (oneAndOnly != null)
                    return null;    // ambiguous
                oneAndOnly = p;
            }
        }
        if (oneAndOnly == null && Main.isUnitTest) {
            // compare jenkins.security.ClassFilterImpl
            CodeSource cs = c.getProtectionDomain().getCodeSource();
            if (cs != null) {
                URL loc = cs.getLocation();
                if (loc != null) {
                    if ("file".equals(loc.getProtocol())) {
                        File file;
                        try {
                            file = Paths.get(loc.toURI()).toFile();
                        } catch (InvalidPathException | URISyntaxException e) {
                            LOGGER.log(Level.WARNING, "could not inspect " + loc, e);
                            return null;
                        }
                        if (file.isFile()) { // ignore directories
                            try (JarFile jf = new JarFile(file)) {
                                Manifest mf = jf.getManifest();
                                if (mf != null) {
                                    java.util.jar.Attributes attr = mf.getMainAttributes();
                                    if (attr.getValue("Plugin-Version") != null) {
                                        String shortName = attr.getValue("Short-Name");
                                        LOGGER.fine(() -> "found " + shortName + " for " + c);
                                        return getPlugin(shortName);
                                    }
                                }
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "could not inspect " + loc, e);
                            }
                        }
                    }
                }
            }
        }
        return oneAndOnly;
    }

    /**
     * Orderly terminates all the plugins.
     */
    public synchronized void stop() {
        for (PluginWrapper p : activePlugins) {
            p.stop();
        }
        List<PluginWrapper> pluginsCopy = new ArrayList<>(plugins);
        for (PluginWrapper p : pluginsCopy) {
            activePlugins.remove(p);
            plugins.remove(p);
            p.releaseClassLoader();
        }
        // Work around a bug in commons-logging.
        // See http://www.szegedi.org/articles/memleak.html
        LogFactory.release(uberClassLoader);
    }

    @Restricted(NoExternalUse.class)
    public static boolean isNonMetaLabel(String label) {
        return !("adopt-this-plugin".equals(label) || "deprecated".equals(label));
    }

    /**
     * This allows "Update Center" to live at the URL
     * {@code /pluginManager/updates/} in addition to its {@code /updateCenter/}
     * URL which is provided by {@link jenkins.model.Jenkins#getUpdateCenter()}.
     * For purposes of Stapler, this object is the current item serving the
     * view, and since this is not a {@link hudson.model.ModelObject}, it does
     * not appear as an additional breadcrumb and only the "Plugin Manager"
     * breadcrumb is shown.
     */
    @Restricted(NoExternalUse.class)
    public static class UpdateCenterProxy implements StaplerFallback {
        @Override
        public Object getStaplerFallback() {
            return Jenkins.get().getUpdateCenter();
        }
    }

    public UpdateCenterProxy getUpdates() {
        return new UpdateCenterProxy();
    }

    @Restricted(NoExternalUse.class)
    public HttpResponse doPluginsSearch(@QueryParameter String query, @QueryParameter Integer limit) {
        List<JSONObject> plugins = new ArrayList<>();
        for (UpdateSite site : Jenkins.get().getUpdateCenter().getSiteList()) {
            List<JSONObject> sitePlugins = site.getAvailables().stream()
                .filter(plugin -> {
                    if (query == null || query.isBlank()) {
                        return true;
                    }
                    return (plugin.name != null && plugin.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) ||
                        (plugin.title != null && plugin.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) ||
                        (plugin.excerpt != null && plugin.excerpt.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) ||
                        plugin.hasCategory(query) ||
                        plugin.getCategoriesStream()
                            .map(UpdateCenter::getCategoryDisplayName)
                            .anyMatch(category -> category != null && category.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) ||
                        plugin.hasWarnings() && query.equalsIgnoreCase("warning:");
                })
                .limit(Math.max(limit - plugins.size(), 1))
                .sorted((o1, o2) -> {
                    String o1DisplayName = o1.getDisplayName();
                    if (o1.name.equalsIgnoreCase(query) ||
                        o1DisplayName.equalsIgnoreCase(query)) {
                        return -1;
                    }
                    String o2DisplayName = o2.getDisplayName();
                    if (o2.name.equalsIgnoreCase(query) || o2DisplayName.equalsIgnoreCase(query)) {
                        return 1;
                    }
                    if (o1.name.equals(o2.name)) {
                        return 0;
                    }
                    final int pop = Double.compare(o2.popularity, o1.popularity);
                    if (pop != 0) {
                        return pop; // highest popularity first
                    }
                    return o1DisplayName.compareTo(o2DisplayName);
                })
                .map(plugin -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("name", plugin.name);
                    jsonObject.put("sourceId", plugin.sourceId);
                    jsonObject.put("title", plugin.title);
                    jsonObject.put("displayName", plugin.getDisplayName());
                    if (plugin.wiki == null || !(plugin.wiki.startsWith("https://") || plugin.wiki.startsWith("http://"))) {
                        jsonObject.put("wiki", "");
                    } else {
                        jsonObject.put("wiki", plugin.wiki);
                    }
                    jsonObject.put("categories", plugin.getCategoriesStream()
                        .filter(PluginManager::isNonMetaLabel)
                        .map(UpdateCenter::getCategoryDisplayName)
                        .collect(toList())
                    );

                    if (hasAdoptThisPluginLabel(plugin)) {
                        jsonObject.put("adoptMe", Messages.PluginManager_adoptThisPlugin());
                    }
                    if (plugin.isDeprecated()) {
                        jsonObject.put("deprecated", Messages.PluginManager_deprecationWarning(plugin.getDeprecation().url));
                    }
                    jsonObject.put("excerpt", plugin.excerpt);
                    jsonObject.put("version", plugin.version);
                    jsonObject.put("popularity", plugin.popularity);
                    if (plugin.isForNewerHudson()) {
                        jsonObject.put("newerCoreRequired", Messages.PluginManager_coreWarning(Util.xmlEscape(plugin.requiredCore)));
                    }
                    if (plugin.hasWarnings()) {
                        JSONObject unresolvedSecurityWarnings = new JSONObject();
                        unresolvedSecurityWarnings.put("text", Messages.PluginManager_securityWarning());
                        Set<UpdateSite.Warning> pluginWarnings = plugin.getWarnings();
                        if (pluginWarnings == null) {
                            throw new IllegalStateException("warnings cannot be null here");
                        }
                        List<JSONObject> warnings = pluginWarnings.stream()
                            .map(warning -> {
                                JSONObject jsonWarning = new JSONObject();
                                jsonWarning.put("url", warning.url);
                                jsonWarning.put("message", warning.message);
                                return jsonWarning;
                            }).collect(toList());
                        unresolvedSecurityWarnings.put("warnings", warnings);
                        jsonObject.put("unresolvedSecurityWarnings", unresolvedSecurityWarnings);
                    }
                    if (plugin.releaseTimestamp != null) {
                        JSONObject releaseTimestamp = new JSONObject();
                        releaseTimestamp.put("iso8601", Functions.iso8601DateTime(plugin.releaseTimestamp));
                        releaseTimestamp.put("displayValue", Messages.PluginManager_ago(Functions.getTimeSpanString(plugin.releaseTimestamp)));
                        jsonObject.put("releaseTimestamp", releaseTimestamp);
                    }
                    if (plugin.healthScore != null) {
                        jsonObject.put("healthScore", plugin.healthScore);
                        jsonObject.put("healthScoreClass", plugin.healthScoreClass);
                    }
                    return jsonObject;
                })
                .collect(toList());
            plugins.addAll(sitePlugins);
            if (plugins.size() >= limit) {
                break;
            }
        }

        JSONArray mappedPlugins = new JSONArray();
        mappedPlugins.addAll(plugins);

        return hudson.util.HttpResponses.okJSON(mappedPlugins);
    }

    /**
     * Get the list of all plugins - available and installed.
     * @return The list of all plugins - available and installed.
     */
    @Restricted(DoNotUse.class) // WebOnly
    public HttpResponse doPlugins() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONArray response = new JSONArray();
        Map<String, JSONObject> allPlugins = new HashMap<>();
        for (PluginWrapper plugin : plugins) {
            JSONObject pluginInfo = new JSONObject();
            pluginInfo.put("installed", true);
            pluginInfo.put("name", plugin.getShortName());
            pluginInfo.put("title", plugin.getDisplayName());
            pluginInfo.put("active", plugin.isActive());
            pluginInfo.put("enabled", plugin.isEnabled());
            pluginInfo.put("bundled", plugin.isBundled);
            pluginInfo.put("deleted", plugin.isDeleted());
            pluginInfo.put("downgradable", plugin.isDowngradable());
            pluginInfo.put("website", plugin.getUrl());
            List<Dependency> dependencies = plugin.getDependencies();
            if (dependencies != null && !dependencies.isEmpty()) {
                Map<String, String> dependencyMap = new HashMap<>();
                for (Dependency dependency : dependencies) {
                    dependencyMap.put(dependency.shortName, dependency.version);
                }
                pluginInfo.put("dependencies", dependencyMap);
            } else {
                pluginInfo.put("dependencies", Collections.emptyMap());
            }
            response.add(pluginInfo);
        }
        for (UpdateSite site : Jenkins.get().getUpdateCenter().getSiteList()) {
            for (UpdateSite.Plugin plugin : site.getAvailables()) {
                JSONObject pluginInfo = allPlugins.get(plugin.name);
                if (pluginInfo == null) {
                    pluginInfo = new JSONObject();
                    pluginInfo.put("installed", false);
                }
                pluginInfo.put("name", plugin.name);
                pluginInfo.put("title", plugin.getDisplayName());
                pluginInfo.put("excerpt", plugin.excerpt);
                pluginInfo.put("site", site.getId());
                pluginInfo.put("dependencies", plugin.dependencies);
                pluginInfo.put("website", plugin.wiki);
                response.add(pluginInfo);
            }
        }
        return hudson.util.HttpResponses.okJSON(response);
    }

    @RequirePOST
    public HttpResponse doUpdateSources(StaplerRequest2 req) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (req.hasParameter("remove")) {
            UpdateCenter uc = Jenkins.get().getUpdateCenter();
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
     * Called to progress status beyond installing plugins, e.g. if
     * there were failures that prevented installation from naturally proceeding
     */
    @RequirePOST
    @Restricted(DoNotUse.class) // WebOnly
    public void doInstallPluginsDone() {
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.ADMINISTER);
        InstallUtil.proceedToNextStateFrom(InstallState.INITIAL_PLUGINS_INSTALLING);
    }

    /**
     * Performs the installation of the plugins.
     */
    @RequirePOST
    public void doInstall(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        Set<String> plugins = new LinkedHashSet<>();

        Enumeration<String> en = req.getParameterNames();
        while (en.hasMoreElements()) {
            String n =  en.nextElement();
            if (n.startsWith("plugin.")) {
                n = n.substring(7);
                plugins.add(n);
            }
        }

        boolean dynamicLoad = req.getParameter("dynamicLoad") != null;
        install(plugins, dynamicLoad);

        rsp.sendRedirect("updates/");
    }

    /**
     * Installs a list of plugins from a JSON POST.
     * @param req The request object.
     * @return A JSON response that includes a "correlationId" in the "data" element.
     * That "correlationId" can then be used in calls to
     * {@link UpdateCenter#doInstallStatus(org.kohsuke.stapler.StaplerRequest2)}.
     * @throws IOException Error reading JSON payload fro request.
     */
    @RequirePOST
    @Restricted(DoNotUse.class) // WebOnly
    public HttpResponse doInstallPlugins(StaplerRequest2 req) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String payload = IOUtils.toString(req.getInputStream(), req.getCharacterEncoding());
        JSONObject request = JSONObject.fromObject(payload);
        JSONArray pluginListJSON = request.getJSONArray("plugins");
        List<String> plugins = new ArrayList<>();

        for (int i = 0; i < pluginListJSON.size(); i++) {
            plugins.add(pluginListJSON.getString(i));
        }

        UUID correlationId = UUID.randomUUID();
        try {
            boolean dynamicLoad = request.getBoolean("dynamicLoad");
            install(plugins, dynamicLoad, correlationId);

            JSONObject responseData = new JSONObject();
            responseData.put("correlationId", correlationId.toString());

            return hudson.util.HttpResponses.okJSON(responseData);
        } catch (RuntimeException e) {
            return hudson.util.HttpResponses.errorJSON(e.getMessage());
        }
    }

    /**
     * Performs the installation of the plugins.
     * @param plugins The collection of plugins to install.
     * @param dynamicLoad If true, the plugin will be dynamically loaded into this Jenkins. If false,
     *                    the plugin will only take effect after the reboot.
     *                    See {@link UpdateCenter#isRestartRequiredForCompletion()}
     * @return The install job list.
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public List<Future<UpdateCenter.UpdateCenterJob>> install(@NonNull Collection<String> plugins, boolean dynamicLoad) {
        return install(plugins, dynamicLoad, null);
    }

    private List<Future<UpdateCenter.UpdateCenterJob>> install(@NonNull Collection<String> plugins, boolean dynamicLoad, @CheckForNull UUID correlationId) {
        List<Future<UpdateCenter.UpdateCenterJob>> installJobs = new ArrayList<>();

        LOGGER.log(INFO, "Starting installation of a batch of {0} plugins plus their dependencies", plugins.size());
        long start = System.nanoTime();
        List<PluginWrapper> batch = new ArrayList<>();

        for (String n : plugins) {
            // JENKINS-22080 plugin names can contain '.' as could (according to rumour) update sites
            int index = n.indexOf('.');
            UpdateSite.Plugin p = null;

            if (index == -1) {
                p = getPlugin(n, UpdateCenter.ID_DEFAULT);
            } else {
                while (index != -1) {
                    if (index + 1 >= n.length()) {
                        break;
                    }
                    String pluginName = n.substring(0, index);
                    String siteName = n.substring(index + 1);
                    UpdateSite.Plugin plugin = getPlugin(pluginName, siteName);
                    // There could be cases like:
                    // 'plugin.ambiguous.updatesite' where both
                    // 'plugin' @ 'ambiguous.updatesite' and 'plugin.ambiguous' @ 'updatesite' resolve to valid plugins
                    if (plugin != null) {
                        if (p != null) {
                            throw new Failure("Ambiguous plugin: " + n);
                        }
                        p = plugin;
                    }
                    index = n.indexOf('.', index + 1);
                }
            }

            if (p == null) {
                throw new Failure("No such plugin: " + n);
            }
            Future<UpdateCenter.UpdateCenterJob> jobFuture = p.deploy(dynamicLoad, correlationId, batch, false);
            installJobs.add(jobFuture);
        }

        final Jenkins jenkins = Jenkins.get();
        final UpdateCenter updateCenter = jenkins.getUpdateCenter();

        if (dynamicLoad) {
            installJobs.add(updateCenter.addJob(updateCenter.new CompleteBatchJob(batch, start, correlationId)));
        }

        final Authentication currentAuth = Jenkins.getAuthentication2();

        if (!jenkins.getInstallState().isSetupComplete()) {
            jenkins.setInstallState(InstallState.INITIAL_PLUGINS_INSTALLING);
            updateCenter.persistInstallStatus();
            new Thread() {
                @Override
                public void run() {
                    boolean failures = false;
                    INSTALLING: while (true) {
                        try {
                            updateCenter.persistInstallStatus();
                            Thread.sleep(500);
                            failures = false;
                            for (Future<UpdateCenter.UpdateCenterJob> jobFuture : installJobs) {
                                if (!jobFuture.isDone() && !jobFuture.isCancelled()) {
                                    continue INSTALLING;
                                }
                                UpdateCenter.UpdateCenterJob job = jobFuture.get();
                                if (job instanceof InstallationJob && ((InstallationJob) job).status instanceof DownloadJob.Failure) {
                                    failures = true;
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.log(WARNING, "Unexpected error while waiting for initial plugin set to install.", e);
                        }
                        break;
                    }
                    updateCenter.persistInstallStatus();
                    if (!failures) {
                        try (ACLContext acl = ACL.as2(currentAuth)) {
                            InstallUtil.proceedToNextStateFrom(InstallState.INITIAL_PLUGINS_INSTALLING);
                        }
                    }
                }
            }.start();
        }

        return installJobs;
    }

    @CheckForNull
    private UpdateSite.Plugin getPlugin(String pluginName, String siteName) {
        UpdateSite updateSite = Jenkins.get().getUpdateCenter().getById(siteName);
        if (updateSite == null) {
            throw new Failure("No such update center: " + siteName);
        }
        return updateSite.getPlugin(pluginName);
    }

    /**
     * Bare-minimum configuration mechanism to change the update center.
     */
    @RequirePOST
    public HttpResponse doSiteConfigure(@QueryParameter String site) throws IOException {
        Jenkins hudson = Jenkins.get();
        hudson.checkPermission(Jenkins.ADMINISTER);
        UpdateCenter uc = hudson.getUpdateCenter();
        PersistedList<UpdateSite> sites = uc.getSites();
        sites.removeIf(s -> s.getId().equals(UpdateCenter.ID_DEFAULT));
        sites.add(new UpdateSite(UpdateCenter.ID_DEFAULT, site));

        return new HttpRedirect("advanced");
    }

    @POST
    public HttpResponse doProxyConfigure(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);

        ProxyConfiguration pc = req.bindJSON(ProxyConfiguration.class, req.getSubmittedForm());
        ProxyConfigurationManager.saveProxyConfiguration(pc);
        return new HttpRedirect("advanced");
    }

    interface PluginCopier {
        void copy(File target) throws Exception;

        void cleanup();
    }

    static class FileUploadPluginCopier implements PluginCopier {
        private FileItem fileItem;

        FileUploadPluginCopier(FileItem fileItem) {
            this.fileItem = fileItem;
        }

        @Override
        public void copy(File target) throws IOException {
            fileItem.write(Util.fileToPath(target));
        }

        @Override
        public void cleanup() {
            try {
                fileItem.delete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static class UrlPluginCopier implements PluginCopier {
        private String url;

        UrlPluginCopier(String url) {
            this.url = url;
        }

        @Override
        public void copy(File target) throws Exception {
            try (InputStream input =  ProxyConfiguration.getInputStream(new URL(url))) {
                Files.copy(input, target.toPath());
            }
        }

        @Override
        public void cleanup() {

        }
    }

    /**
     * Uploads a plugin.
     */
    @RequirePOST
    public HttpResponse doUploadPlugin(StaplerRequest2 req) throws IOException, ServletException {
        if (Util.isOverridden(PluginManager.class, getClass(), "doUploadPlugin", StaplerRequest.class)) {
            try {
                return doUploadPlugin(StaplerRequest.fromStaplerRequest2(req));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            return doUploadPluginImpl(req);
        }
    }

    /**
     * @deprecated use {@link #doUploadPlugin(StaplerRequest2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public HttpResponse doUploadPlugin(StaplerRequest req) throws IOException, javax.servlet.ServletException {
        try {
            return doUploadPluginImpl(StaplerRequest.toStaplerRequest2(req));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private HttpResponse doUploadPluginImpl(StaplerRequest2 req) throws IOException, ServletException {
        try {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            String fileName = "";
            PluginCopier copier;
            File tmpDir = Files.createTempDirectory("uploadDir").toFile();
            JakartaServletFileUpload<DiskFileItem, DiskFileItemFactory> upload = new JakartaServletDiskFileUpload(DiskFileItemFactory.builder().setFile(tmpDir).get());
            List<DiskFileItem> items = upload.parseRequest(req);
            String string = items.get(1).getString();
            if (string != null && !string.isBlank()) {
                // this is a URL deployment
                fileName = string;
                copier = new UrlPluginCopier(fileName);
            } else {
                // this is a file upload
                FileItem fileItem = items.get(0);
                fileName = Util.getFileName(fileItem.getName());
                copier = new FileUploadPluginCopier(fileItem);
            }

            if ("".equals(fileName)) {
                return new HttpRedirect("advanced");
            }
            // we allow the upload of the new jpi's and the legacy hpi's
            if (!fileName.endsWith(".jpi") && !fileName.endsWith(".hpi")) {
                throw new Failure(hudson.model.Messages.Hudson_NotAPlugin(fileName));
            }

            // first copy into a temporary file name
            File t = File.createTempFile("uploaded", ".jpi", tmpDir);
            tmpDir.deleteOnExit();
            t.deleteOnExit();
            // TODO Remove this workaround after FILEUPLOAD-293 is resolved.
            Files.delete(Util.fileToPath(t));
            try {
                copier.copy(t);
            } catch (Exception e) {
                // Exception thrown is too generic so at least limit the scope where it can occur
                throw new ServletException(e);
            }
            copier.cleanup();

            final String baseName = identifyPluginShortName(t);

            pluginUploaded = true;

            JSONArray dependencies = new JSONArray();
            try {
                Manifest m;
                try (JarFile jarFile = new JarFile(t)) {
                    m = jarFile.getManifest();
                }
                String deps = m.getMainAttributes().getValue("Plugin-Dependencies");

                if (deps != null && !deps.isBlank()) {
                    // now we get to parse it!
                    String[] plugins = deps.split(",");
                    for (String p : plugins) {
                        // should have name:version[;resolution:=optional]
                        String[] attrs = p.split("[:;]");
                        dependencies.add(new JSONObject()
                                .element("name", attrs[0])
                                .element("version", attrs[1])
                                .element("optional", p.contains("resolution:=optional")));
                    }
                }
            } catch (IOException e) {
                LOGGER.log(WARNING, "Unable to setup dependency list for plugin upload", e);
            }

            // Now create a dummy plugin that we can dynamically load (the InstallationJob will force a restart if one is needed):
            JSONObject cfg = new JSONObject().
                    element("name", baseName).
                    element("version", "0"). // unused but mandatory
                    element("url", t.toURI().toString()).
                    element("dependencies", dependencies);
            new UpdateSite(UpdateCenter.ID_UPLOAD, null).new Plugin(UpdateCenter.ID_UPLOAD, cfg).deploy(true);
            return new HttpRedirect("updates/");
        } catch (FileUploadException e) {
            throw new ServletException(e);
        }
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST public FormValidation doCheckPluginUrl(StaplerRequest2 request, @QueryParameter String value) throws IOException {
        if (value != null && !value.isBlank()) {
            try {
                URL url = new URL(value);
                if (!url.getProtocol().startsWith("http")) {
                    return FormValidation.error(Messages.PluginManager_invalidUrl());
                }

                if (!url.getProtocol().equals("https")) {
                    return FormValidation.warning(Messages.PluginManager_insecureUrl());
                }
            } catch (MalformedURLException e) {
                return FormValidation.error(e.getMessage());
            }
        }
        return FormValidation.ok();
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST public FormValidation doCheckUpdateSiteUrl(StaplerRequest2 request, @QueryParameter String value) throws InterruptedException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return checkUpdateSiteURL(value);
    }

    @Restricted(DoNotUse.class) // visible for testing only
    FormValidation checkUpdateSiteURL(@CheckForNull String value) throws InterruptedException {
        value = Util.fixEmptyAndTrim(value);

        if (value == null) {
            return FormValidation.error(Messages.PluginManager_emptyUpdateSiteUrl());
        }

        final URI baseUri;
        try {
            baseUri = new URI(value);
        } catch (URISyntaxException ex) {
            return FormValidation.error(ex, Messages.PluginManager_invalidUrl());
        }

        if ("file".equalsIgnoreCase(baseUri.getScheme())) {
            File f = new File(baseUri);
            if (f.isFile()) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.PluginManager_connectionFailed());
        }

        if ("https".equalsIgnoreCase(baseUri.getScheme()) || "http".equalsIgnoreCase(baseUri.getScheme())) {
            final URI uriWithQuery;
            try {
                if (baseUri.getRawQuery() == null) {
                    uriWithQuery = new URI(value + "?version=" + Jenkins.VERSION + "&uctest");
                } else {
                    uriWithQuery = new URI(value + "&version=" + Jenkins.VERSION + "&uctest");
                }
            } catch (URISyntaxException e) {
                return FormValidation.error(e, Messages.PluginManager_invalidUrl());
            }
            HttpClient httpClient = ProxyConfiguration.newHttpClientBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest httpRequest;
            try {
                httpRequest = ProxyConfiguration.newHttpRequestBuilder(uriWithQuery)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
            } catch (IllegalArgumentException e) {
                return FormValidation.error(e, Messages.PluginManager_invalidUrl());
            }
            try {
                java.net.http.HttpResponse<Void> httpResponse = httpClient.send(
                        httpRequest, java.net.http.HttpResponse.BodyHandlers.discarding());
                if (100 <= httpResponse.statusCode() && httpResponse.statusCode() <= 399) {
                    return FormValidation.ok();
                }
                LOGGER.log(Level.FINE, "Obtained a non OK ({0}) response from the update center",
                        new Object[] {httpResponse.statusCode(), baseUri});
                return FormValidation.error(Messages.PluginManager_connectionFailed());
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to check update site", e);
                return FormValidation.error(e, Messages.PluginManager_connectionFailed());
            }

        }
        // not a file or http(s) scheme
        return FormValidation.error(Messages.PluginManager_invalidUrl());
    }

    @Restricted(NoExternalUse.class)
    @RequirePOST public HttpResponse doCheckUpdatesServer() throws IOException {
        Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);

        // We'll check the update servers with a try-retry mechanism. The retrier is built with a builder
        Retrier<FormValidation> updateServerRetrier = new Retrier.Builder<>(
                // the action to perform
                this::checkUpdatesServer,

                // the way we know whether this attempt was right or wrong
                (currentAttempt, result) -> result.kind == FormValidation.Kind.OK,

                // the action name we are trying to perform
                "check updates server")

                // the number of attempts to try
                .withAttempts(CHECK_UPDATE_ATTEMPTS)

                // the delay between attempts
                .withDelay(CHECK_UPDATE_SLEEP_TIME_MILLIS)

                // whatever exception raised is considered as a fail attempt (all exceptions), not a failure
                .withDuringActionExceptions(new Class[] {Exception.class})

                // what we do with a failed attempt due to an allowed exception, return an FormValidation.error with the message
                .withDuringActionExceptionListener((attempt, e) -> FormValidation.errorWithMarkup(e.getClass().getSimpleName() + ": " + e.getLocalizedMessage()))

                // lets get our retrier object
                .build();

        try {
            // Begin the process
            FormValidation result = updateServerRetrier.start();

            // Check how it went
            if (!FormValidation.Kind.OK.equals(result.kind)) {
                LOGGER.log(Level.SEVERE, Messages.PluginManager_UpdateSiteError(CHECK_UPDATE_ATTEMPTS, result.getMessage()));
                if (CHECK_UPDATE_ATTEMPTS > 1 && !Logger.getLogger(Retrier.class.getName()).isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.SEVERE, Messages.PluginManager_UpdateSiteChangeLogLevel(Retrier.class.getName()));
                }

                lastErrorCheckUpdateCenters = Messages.PluginManager_CheckUpdateServerError(result.getMessage());
            } else {
                lastErrorCheckUpdateCenters = null;
            }

        } catch (Exception e) {
            // It's never going to be reached because we declared all Exceptions in the withDuringActionExceptions, so
            // whatever exception is considered a expected failed attempt and the retries continue
            LOGGER.log(Level.WARNING, Messages.PluginManager_UnexpectedException(), e);

            // In order to leave this method as it was, rethrow as IOException
            throw new IOException(e);
        }

        // Stay in the same page in any case
        return HttpResponses.forwardToPreviousPage();
    }

    private FormValidation checkUpdatesServer() throws Exception {
        for (UpdateSite site : Jenkins.get().getUpdateCenter().getSites()) {
            FormValidation v = site.updateDirectlyNow();
            if (v.kind != FormValidation.Kind.OK) {
                // Stop with an error
                return v;
            }
        }
        for (DownloadService.Downloadable d : DownloadService.Downloadable.all()) {
            FormValidation v = d.updateNow();
            if (v.kind != FormValidation.Kind.OK) {
                // Stop with an error
                return v;
            }
        }
        return FormValidation.ok();
    }

    /**
     * Returns the last error raised during the update sites checking.
     * @return the last error message
     */
    public String getLastErrorCheckUpdateCenters() {
        return lastErrorCheckUpdateCenters;
    }

    protected String identifyPluginShortName(File t) {
        try {
            try (JarFile j = new JarFile(t)) {
                String name = j.getManifest().getMainAttributes().getValue("Short-Name");
                if (name != null) return name;
            }
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to identify the short name from " + t, e);
        }
        return FilenameUtils.getBaseName(t.getName());    // fall back to the base name of what's uploaded
    }

    public Descriptor<ProxyConfiguration> getProxyDescriptor() {
        return Jenkins.get().getDescriptor(ProxyConfiguration.class);
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
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        List<Future<UpdateCenter.UpdateCenterJob>> jobs = new ArrayList<>();
        UpdateCenter uc = Jenkins.get().getUpdateCenter();
        // TODO call uc.updateAllSites() when available? perhaps not, since we should not block on network here
        for (Map.Entry<String, VersionNumber> requestedPlugin : parseRequestedPlugins(configXml).entrySet()) {
            PluginWrapper pw = getPlugin(requestedPlugin.getKey());
            if (pw == null) { // install new
                UpdateSite.Plugin toInstall = uc.getPlugin(requestedPlugin.getKey(), requestedPlugin.getValue());
                if (toInstall == null) {
                    LOGGER.log(WARNING, "No such plugin {0} to install", requestedPlugin.getKey());
                    continue;
                }
                logPluginWarnings(requestedPlugin, toInstall);
                jobs.add(toInstall.deploy(true));
            } else if (pw.isOlderThan(requestedPlugin.getValue())) { // upgrade
                UpdateSite.Plugin toInstall = uc.getPlugin(requestedPlugin.getKey(), requestedPlugin.getValue());
                if (toInstall == null) {
                    LOGGER.log(WARNING, "No such plugin {0} to upgrade", requestedPlugin.getKey());
                    continue;
                }
                if (!pw.isOlderThan(new VersionNumber(toInstall.version))) {
                    LOGGER.log(WARNING, "{0}@{1} is no newer than what we already have", new Object[] {toInstall.name, toInstall.version});
                    continue;
                }
                logPluginWarnings(requestedPlugin, toInstall);
                if (!toInstall.isCompatibleWithInstalledVersion()) {
                    LOGGER.log(WARNING, "{0}@{1} is incompatible with the installed @{2}", new Object[] {toInstall.name, toInstall.version, pw.getVersion()});
                }
                jobs.add(toInstall.deploy(true)); // dynamicLoad=true => sure to throw RestartRequiredException, but at least message is nicer
            } // else already good
        }
        return jobs;
    }

    private void logPluginWarnings(Map.Entry<String, VersionNumber> requestedPlugin, UpdateSite.Plugin toInstall) {
        if (new VersionNumber(toInstall.version).compareTo(requestedPlugin.getValue()) < 0) {
            LOGGER.log(WARNING, "{0} can only be satisfied in @{1}", new Object[] {requestedPlugin, toInstall.version});
        }
        if (toInstall.isForNewerHudson()) {
            LOGGER.log(WARNING, "{0}@{1} was built for a newer Jenkins", new Object[] {toInstall.name, toInstall.version});
        }
    }

    /**
     * Like {@link #doInstallNecessaryPlugins(StaplerRequest2)} but only checks if everything is installed
     * or if some plugins need updates or installation.
     *
     * This method runs without side-effect. I'm still requiring the ADMINISTER permission since
     * XML file can contain various external references and we don't configure parsers properly against
     * that.
     *
     * @since 1.483
     */
    @RequirePOST
    public JSONArray doPrevalidateConfig(StaplerRequest2 req) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray response = new JSONArray();

        for (Map.Entry<String, VersionNumber> p : parseRequestedPlugins(req.getInputStream()).entrySet()) {
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
    public HttpResponse doInstallNecessaryPlugins(StaplerRequest2 req) throws IOException {
        prevalidateConfig(req.getInputStream());
        return HttpResponses.redirectViaContextPath("pluginManager/updates/");
    }

    /**
     * Parses configuration XML files and picks up references to XML files.
     */
    public Map<String, VersionNumber> parseRequestedPlugins(InputStream configXml) throws IOException {
        final Map<String, VersionNumber> requestedPlugins = new TreeMap<>();
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            spf.newSAXParser().parse(configXml, new DefaultHandler() {
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
            throw new IOException("Failed to parse XML", x);
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e); // impossible since we don't tweak XMLParser
        }
        return requestedPlugins;
    }

    @Restricted(DoNotUse.class) // table.jelly
    public MetadataCache createCache() {
        return new MetadataCache();
    }

    /**
     * Disable a list of plugins using a strategy for their dependents plugins.
     * @param strategy the strategy regarding how the dependent plugins are processed
     * @param plugins the list of plugins
     * @return the list of results for every plugin and their dependent plugins.
     * @throws IOException see {@link PluginWrapper#disable()}
     */
    public @NonNull List<PluginWrapper.PluginDisableResult> disablePlugins(@NonNull PluginWrapper.PluginDisableStrategy strategy, @NonNull List<String> plugins) throws IOException {
        // Where we store the results of each plugin disablement
        List<PluginWrapper.PluginDisableResult> results = new ArrayList<>(plugins.size());

        // Disable all plugins passed
        for (String pluginName : plugins) {
            PluginWrapper plugin = this.getPlugin(pluginName);

            if (plugin == null) {
                results.add(new PluginWrapper.PluginDisableResult(pluginName, PluginWrapper.PluginDisableStatus.NO_SUCH_PLUGIN, Messages.PluginWrapper_NoSuchPlugin(pluginName)));
            } else {
                results.add(plugin.disable(strategy));
            }
        }

        return results;
    }

    @Restricted(NoExternalUse.class) // table.jelly
    public static final class MetadataCache {
        private final Map<String, Object> data = new HashMap<>();

        public <T> T of(String key, Class<T> type, Supplier<T> func) {
            return type.cast(data.computeIfAbsent(key, _ignored -> func.get()));
        }
    }

    /**
     * {@link ClassLoader} that can see all plugins.
     */
    public static final class UberClassLoader extends DelegatingClassLoader {
        private final List<PluginWrapper> activePlugins;

        /** Cache of loaded, or known to be unloadable, classes. */
        private final ConcurrentMap<String, Optional<Class<?>>> loaded = new ConcurrentHashMap<>();

        public UberClassLoader(List<PluginWrapper> activePlugins) {
            super("UberClassLoader", new CheckingExistenceClassLoader(PluginManager.class.getClassLoader()));
            this.activePlugins = activePlugins;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (String namePrefixToSkip : CLASS_PREFIXES_TO_SKIP) {
                if (name.startsWith(namePrefixToSkip)) {
                    throw new ClassNotFoundException("ignoring " + name);
                }
            }
            return loaded.computeIfAbsent(name, this::computeValue).orElseThrow(() -> new ClassNotFoundException(name));
        }

        private Optional<Class<?>> computeValue(String name) {
            for (PluginWrapper p : activePlugins) {
                try {
                    if (FAST_LOOKUP) {
                        return Optional.of(ClassLoaderReflectionToolkit.loadClass(p.classLoader, name));
                    } else {
                        return Optional.of(p.classLoader.loadClass(name));
                    }
                } catch (ClassNotFoundException e) {
                    // Not found. Try the next class loader.
                }
            }
            // Not found in any of the class loaders. Delegate.
            return Optional.empty();
        }

        @Override
        protected URL findResource(String name) {
            for (PluginWrapper p : activePlugins) {
                URL url;
                if (FAST_LOOKUP) {
                    url = ClassLoaderReflectionToolkit._findResource(p.classLoader, name);
                } else {
                    url = p.classLoader.getResource(name);
                }
                if (url != null) {
                    return url;
                }
            }
            return null;
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            List<URL> resources = new ArrayList<>();
            for (PluginWrapper p : activePlugins) {
                if (FAST_LOOKUP) {
                    resources.addAll(Collections.list(ClassLoaderReflectionToolkit._findResources(p.classLoader, name)));
                } else {
                    resources.addAll(Collections.list(p.classLoader.getResources(name)));
                }
            }
            return Collections.enumeration(resources);
        }

        void clearCacheMisses() {
            loaded.values().removeIf(Optional::isEmpty);
        }

        @Override
        public String toString() {
            // only for debugging purpose
            return "classLoader " +  getClass().getName();
        }

        // TODO Remove this once we require post 2024-07 remoting minimum version and deleted ClassLoaderProxy#fetchJar(URL)
        @SuppressFBWarnings(
                value = "DMI_COLLECTION_OF_URLS",
                justification = "All URLs point to local files, so no DNS lookup.")
        @Restricted(NoExternalUse.class)
        public boolean isPluginJar(URL jarUrl) {
            for (PluginWrapper plugin : activePlugins) {
                if (plugin.classLoader instanceof URLClassLoader) {
                    if (Set.of(((URLClassLoader) plugin.classLoader).getURLs()).contains(jarUrl)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean FAST_LOOKUP = !SystemProperties.getBoolean(PluginManager.class.getName() + ".noFastLookup");

    /** @deprecated in Jenkins 2.222 use {@link Jenkins#ADMINISTER} instead */
    @Deprecated
    public static final Permission UPLOAD_PLUGINS = new Permission(Jenkins.PERMISSIONS, "UploadPlugins", Messages._PluginManager_UploadPluginsPermission_Description(), Jenkins.ADMINISTER, PermissionScope.JENKINS);

    /** @deprecated in Jenkins 2.222 use {@link Jenkins#ADMINISTER} instead */
    @Deprecated
    public static final Permission CONFIGURE_UPDATECENTER = new Permission(Jenkins.PERMISSIONS, "ConfigureUpdateCenter", Messages._PluginManager_ConfigureUpdateCenterPermission_Description(), Jenkins.ADMINISTER, PermissionScope.JENKINS);

    /**
     * Remembers why a plugin failed to deploy.
     */
    public static final class FailedPlugin {
        public final String name;
        public final Exception cause;
        @Nullable
        public final PluginWrapper pluginWrapper;

        /**
         * Constructor for FailedPlugin when we do not have an associated PluginWrapper
         */
        public FailedPlugin(String name, Exception cause) {
            this.name = name;
            this.cause = cause;
            this.pluginWrapper = null;
        }

        /**
         * Constructor for FailedPlugin when we know which PluginWrapper failed
         */
        public FailedPlugin(PluginWrapper pluginWrapper, Exception cause) {
            this.name = pluginWrapper.getShortName();
            this.cause = cause;
            this.pluginWrapper = pluginWrapper;
        }

        public String getExceptionString() {
            return Functions.printThrowable(cause);
        }
    }

    /**
     * Stores {@link Plugin} instances.
     */
    /*package*/ static final class PluginInstanceStore {
        final Map<PluginWrapper, Plugin> store = new ConcurrentHashMap<>();
    }

    /**
     * {@link AdministrativeMonitor} that checks if there are any plugins with cycle dependencies.
     */
    @Extension @Symbol("pluginCycleDependencies")
    public static final class PluginCycleDependenciesMonitor extends AdministrativeMonitor {

        @Override
        public String getDisplayName() {
            return Messages.PluginManager_PluginCycleDependenciesMonitor_DisplayName();
        }

        private transient volatile boolean isActive = false;

        private transient volatile List<PluginWrapper> pluginsWithCycle;

        @Override
        public boolean isActivated() {
            if (pluginsWithCycle == null) {
                pluginsWithCycle = new ArrayList<>();
                for (PluginWrapper p : Jenkins.get().getPluginManager().getPlugins()) {
                    if (p.hasCycleDependency()) {
                        pluginsWithCycle.add(p);
                        isActive = true;
                    }
                }
            }
            return isActive;
        }

        public List<PluginWrapper> getPluginsWithCycle() {
            return pluginsWithCycle;
        }
    }

    /**
     * {@link AdministrativeMonitor} that informs the administrator about a required plugin update.
     * @since 1.491
     */
    @Extension @Symbol("pluginUpdate")
    public static final class PluginUpdateMonitor extends AdministrativeMonitor {

        private Map<String, PluginUpdateInfo> pluginsToBeUpdated = new HashMap<>();

        /**
         * Convenience method to ease access to this monitor, this allows other plugins to register required updates.
         * @return this monitor.
         */
        public static PluginUpdateMonitor getInstance() {
            return ExtensionList.lookupSingleton(PluginUpdateMonitor.class);
        }

        /**
         * Report to the administrator if the plugin with the given name is older then the required version.
         *
         * @param pluginName shortName of the plugin (artifactId)
         * @param requiredVersion the lowest version which is OK (e.g. 2.2.2)
         * @param message the message to show (plain text)
         */
        public void ifPluginOlderThenReport(String pluginName, String requiredVersion, String message) {
            Plugin plugin = Jenkins.get().getPlugin(pluginName);
            if (plugin != null) {
                if (plugin.getWrapper().getVersionNumber().isOlderThan(new VersionNumber(requiredVersion))) {
                    pluginsToBeUpdated.put(pluginName, new PluginUpdateInfo(pluginName, message));
                }
            }
        }

        @Override
        public boolean isActivated() {
            return !pluginsToBeUpdated.isEmpty();
        }

        @Override
        public String getDisplayName() {
            return Messages.PluginManager_PluginUpdateMonitor_DisplayName();
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

    /**
     * {@link AdministrativeMonitor} that checks if there are any plugins that are deprecated.
     *
     * @since 2.246
     */
    @Restricted(NoExternalUse.class)
    @Symbol("pluginDeprecation")
    @Extension
    public static final class PluginDeprecationMonitor extends AdministrativeMonitor {

        @Override
        public String getDisplayName() {
            return Messages.PluginManager_PluginDeprecationMonitor_DisplayName();
        }

        @Override
        public boolean isActivated() {
            return !getDeprecatedPlugins().isEmpty();
        }

        public Map<PluginWrapper, String> getDeprecatedPlugins() {
            return Jenkins.get().getPluginManager().getPlugins().stream()
                    .filter(PluginWrapper::isDeprecated)
                    .sorted(Comparator.comparing(PluginWrapper::getDisplayName)) // Sort by plugin name
                    .collect(LinkedHashMap::new,
                            (map, plugin) -> map.put(plugin, plugin.getDeprecations().get(0).url),
                            Map::putAll);
        }
    }

    @Restricted(DoNotUse.class)
    public String unscientific(double d) {
        return String.format(Locale.US, "%15.4f", d);
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
        }
        return this;
    }

    @Restricted(DoNotUse.class) // Used from table.jelly
    public boolean isMetaLabel(String label) {
        return "adopt-this-plugin".equals(label) || "deprecated".equals(label);
    }

    @Restricted(DoNotUse.class) // Used from table.jelly
    public boolean hasAdoptThisPluginLabel(UpdateSite.Plugin plugin) {
        return plugin.hasCategory("adopt-this-plugin");
    }

    @Restricted(DoNotUse.class) // Used from table.jelly
    public boolean hasAdoptThisPluginLabel(PluginWrapper plugin) {
        final UpdateSite.Plugin pluginMeta = Jenkins.get().getUpdateCenter().getPlugin(plugin.getShortName());
        if (pluginMeta == null) {
            return false;
        }
        return pluginMeta.hasCategory("adopt-this-plugin");
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(PluginManager.class.getName() + ".skipPermissionCheck");
}
