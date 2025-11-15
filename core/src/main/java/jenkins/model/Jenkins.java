/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe,
 * Stephen Connolly, Tom Huybrechts, Yahoo! Inc., Alan Harder, CloudBees, Inc.,
 * Yahoo!, Inc.
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

package jenkins.model;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixNull;
import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import static hudson.init.InitMilestone.JOB_CONFIG_ADAPTED;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import static hudson.init.InitMilestone.SYSTEM_CONFIG_ADAPTED;
import static hudson.init.InitMilestone.SYSTEM_CONFIG_LOADED;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static jenkins.model.Messages.Hudson_Computer_IncorrectNumberOfExecutors;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Lookup;
import hudson.Main;
import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.RestrictedSince;
import hudson.TcpSlaveAgentListener;
import hudson.Util;
import hudson.WebAppMain;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.init.Initializer;
import hudson.init.TermMilestone;
import hudson.init.TerminatorFinder;
import hudson.lifecycle.Lifecycle;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.logging.LogRecorderManager;
import hudson.markup.EscapedMarkupFormatter;
import hudson.markup.MarkupFormatter;
import hudson.model.AbstractCIBase;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AdministrativeMonitor;
import hudson.model.AllView;
import hudson.model.Api;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.DescriptorByNameOwner;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Failure;
import hudson.model.Fingerprint;
import hudson.model.FingerprintCleanupThread;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Label;
import hudson.model.ListView;
import hudson.model.LoadBalancer;
import hudson.model.LoadStatistics;
import hudson.model.ManageJenkinsAction;
import hudson.model.ManagementLink;
import hudson.model.Messages;
import hudson.model.ModifiableViewGroup;
import hudson.model.NoFingerprintMatch;
import hudson.model.Node;
import hudson.model.OverallLoadStatistics;
import hudson.model.PaneStatusProperties;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.Queue.FlyweightTask;
import hudson.model.RestartListener;
import hudson.model.RootAction;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.UnprotectedRootAction;
import hudson.model.UpdateCenter;
import hudson.model.User;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;
import hudson.model.WorkspaceCleanupThread;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SCMListener;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.Callable;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.BasicAuthenticationFilter;
import hudson.security.FederatedLoginService;
import hudson.security.HudsonFilter;
import hudson.security.LegacyAuthorizationStrategy;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.security.SecurityMode;
import hudson.security.SecurityRealm;
import hudson.security.csrf.CrumbIssuer;
import hudson.security.csrf.GlobalCrumbIssuerConfiguration;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeList;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.SafeTimerTask;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AdministrativeError;
import hudson.util.ClockDifference;
import hudson.util.ComboBoxModel;
import hudson.util.CopyOnWriteList;
import hudson.util.CopyOnWriteMap;
import hudson.util.DaemonThreadFactory;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.Futures;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import hudson.util.JenkinsReloadFailed;
import hudson.util.LogTaskListener;
import hudson.util.MultipartFormDataParser;
import hudson.util.NamingThreadFactory;
import hudson.util.PluginServletFilter;
import hudson.util.QuotedStringTokenizer;
import hudson.util.RemotingDiagnostics;
import hudson.util.RemotingDiagnostics.HeapDump;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import hudson.views.DefaultMyViewsTabBar;
import hudson.views.DefaultViewsTabBar;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import hudson.widgets.Widget;
import io.jenkins.servlet.RequestDispatcherWrapper;
import io.jenkins.servlet.ServletContextWrapper;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import jenkins.AgentProtocol;
import jenkins.ErrorAttributeFilter;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionRefreshException;
import jenkins.InitReactorRunner;
import jenkins.agents.CloudSet;
import jenkins.diagnostics.URICheckEncodingMonitor;
import jenkins.install.InstallState;
import jenkins.install.SetupWizard;
import jenkins.model.ProjectNamingStrategy.DefaultProjectNamingStrategy;
import jenkins.security.ClassFilterImpl;
import jenkins.security.ConfidentialKey;
import jenkins.security.ConfidentialStore;
import jenkins.security.MasterToSlaveCallable;
import jenkins.security.RedactSecretJsonInErrorMessageSanitizer;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.security.SecurityListener;
import jenkins.security.stapler.DoActionFilter;
import jenkins.security.stapler.StaplerDispatchValidator;
import jenkins.security.stapler.StaplerDispatchable;
import jenkins.security.stapler.StaplerFilteredActionListener;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.security.stapler.TypedFilter;
import jenkins.slaves.WorkspaceLocator;
import jenkins.util.JenkinsJVM;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import jenkins.util.io.FileBoolean;
import jenkins.util.io.OnMaster;
import jenkins.util.xml.XMLUtils;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.apache.commons.logging.LogFactory;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.ReactorListener;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder.Handle;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.framework.adjunct.AdjunctManager;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;
import org.kohsuke.stapler.jelly.JellyRequestDispatcher;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.xml.sax.InputSource;

/**
 * Root object of the system.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class Jenkins extends AbstractCIBase implements DirectlyModifiableTopLevelItemGroup, StaplerProxy, StaplerFallback,
        ModifiableViewGroup, AccessControlled, DescriptorByNameOwner,
        ModelObjectWithContextMenu, ModelObjectWithChildren, OnMaster, Loadable {
    private final transient Queue queue;

    // flag indicating if we have loaded the jenkins configuration or not yet.
    private transient volatile boolean configLoaded = false;

    /**
     * Stores various objects scoped to {@link Jenkins}.
     */
    public final transient Lookup lookup = new Lookup();

    /**
     * We update this field to the current version of Jenkins whenever we save {@code config.xml}.
     * This can be used to detect when an upgrade happens from one version to next.
     *
     * <p>
     * Since this field is introduced starting 1.301, "1.0" is used to represent every version
     * up to 1.300. This value may also include non-standard versions like "1.301-SNAPSHOT" or
     * "?", etc., so parsing needs to be done with a care.
     *
     * @since 1.301
     */
    // this field needs to be at the very top so that other components can look at this value even during unmarshalling
    private String version = "1.0";

    /**
     * The Jenkins instance startup type i.e. NEW, UPGRADE etc
     */
    private transient String installStateName;

    @Deprecated
    private InstallState installState;

    /**
     * If we're in the process of an initial setup,
     * this will be set
     */
    private transient SetupWizard setupWizard;

    /**
     * Number of executors of the built-in node.
     */
    private int numExecutors = 2;

    /**
     * Job allocation strategy.
     */
    private Mode mode = Mode.NORMAL;

    /**
     * False to enable anyone to do anything.
     * Left as a field so that we can still read old data that uses this flag.
     *
     * @see #authorizationStrategy
     * @see #securityRealm
     */
    private Boolean useSecurity;

    /**
     * Controls how the
     * <a href="http://en.wikipedia.org/wiki/Authorization">authorization</a>
     * is handled in Jenkins.
     * <p>
     * This ultimately controls who has access to what.
     *
     * Never null.
     */
    private volatile AuthorizationStrategy authorizationStrategy = AuthorizationStrategy.UNSECURED;

    /**
     * Controls a part of the
     * <a href="http://en.wikipedia.org/wiki/Authentication">authentication</a>
     * handling in Jenkins.
     * <p>
     * Intuitively, this corresponds to the user database.
     *
     * See {@link HudsonFilter} for the concrete authentication protocol.
     *
     * Never null. Always use {@link #setSecurityRealm(SecurityRealm)} to
     * update this field.
     *
     * @see #getSecurity()
     * @see #setSecurityRealm(SecurityRealm)
     */
    private volatile SecurityRealm securityRealm = SecurityRealm.NO_AUTHENTICATION;

    /**
     * Disables the "Keep me signed in" option in the standard login screen.
     *
     * @since 1.534
     */
    private volatile boolean disableRememberMe;

    /**
     * The project naming strategy defines/restricts the names which can be given to a project/job. e.g. does the name have to follow a naming convention?
     */
    private ProjectNamingStrategy projectNamingStrategy = DefaultProjectNamingStrategy.DEFAULT_NAMING_STRATEGY;

    /**
     * Root directory for the workspaces.
     * This value will be variable-expanded as per {@link #expandVariablesForDirectory}.
     * @see #getWorkspaceFor(TopLevelItem)
     */
    private String workspaceDir = OLD_DEFAULT_WORKSPACES_DIR;

    /**
     * Root directory for the builds.
     * This value will be variable-expanded as per {@link #expandVariablesForDirectory}.
     * @see #getBuildDirFor(Job)
     */
    private String buildsDir = DEFAULT_BUILDS_DIR;

    /**
     * Message displayed in the top page.
     */
    private String systemMessage;

    private MarkupFormatter markupFormatter;

    /**
     * Root directory of the system.
     */
    public final transient File root;

    /**
     * Where are we in the initialization?
     */
    private transient volatile InitMilestone initLevel = InitMilestone.STARTED;

    /**
     * All {@link Item}s keyed by their {@link Item#getName() name}s.
     */
    /*package*/ final transient Map<String, TopLevelItem> items = new CopyOnWriteMap.Tree<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * The sole instance.
     */
    private static Jenkins theInstance;

    @CheckForNull
    private transient volatile QuietDownInfo quietDownInfo;

    private transient volatile boolean terminating;
    @GuardedBy("Jenkins.class")
    private transient boolean cleanUpStarted;

    /**
     * Use this to know during startup if this is a fresh one, aka first-time, startup, or a later one.
     * A file will be created at the very end of the Jenkins initialization process.
     * I.e. if the file is present, that means this is *NOT* a fresh startup.
     *
     * {@code
     *     STARTUP_MARKER_FILE.get(); // returns false if we are on a fresh startup. True for next startups.
     * }
     */
    private static FileBoolean STARTUP_MARKER_FILE;

    private volatile List<JDK> jdks = new ArrayList<>();

    private transient volatile DependencyGraph dependencyGraph;
    private transient Future<DependencyGraph> scheduledFutureDependencyGraph;
    private transient Future<DependencyGraph> calculatingFutureDependencyGraph;
    private transient Object dependencyGraphLock = new Object();

    /**
     * Currently active Views tab bar.
     */
    private volatile ViewsTabBar viewsTabBar = new DefaultViewsTabBar();

    /**
     * Currently active My Views tab bar.
     */
    private volatile MyViewsTabBar myViewsTabBar = new DefaultMyViewsTabBar();

    /**
     * All {@link ExtensionList} keyed by their {@link ExtensionList#extensionType}.
     */
    @SuppressWarnings("rawtypes")
    private final transient Map<Class, ExtensionList> extensionLists = new ConcurrentHashMap<>();

    /**
     * All {@link DescriptorExtensionList} keyed by their {@link DescriptorExtensionList#describableType}.
     */
    @SuppressWarnings("rawtypes")
    private final transient Map<Class, DescriptorExtensionList> descriptorLists = new ConcurrentHashMap<>();

    /**
     * {@link Computer}s in this Jenkins system. Read-only.
     */
    protected final transient ConcurrentMap<Node, Computer> computers = new ConcurrentHashMap<>();

    /**
     * Active {@link Cloud}s.
     */
    public final Hudson.CloudList clouds = new Hudson.CloudList(this);

    @Restricted(Beta.class)
    public void loadNode(File dir) throws IOException {
        getNodesObject().load(dir);
    }

    public static class CloudList extends DescribableList<Cloud, Descriptor<Cloud>> {
        public CloudList(Jenkins h) {
            super(h);
        }

        public CloudList() {// needed for XStream deserialization
        }

        public Cloud getByName(String name) {
            for (Cloud c : this)
                if (c.name.equals(name))
                    return c;
            return null;
        }

        @Override
        protected void onModified() throws IOException {
            super.onModified();
            Jenkins.get().trimLabels();
        }
    }

    /**
     * Legacy store of the set of installed cluster nodes.
     * @deprecated in favour of {@link Nodes}
     */
    @Deprecated
    protected transient volatile NodeList slaves;

    /**
     * The holder of the set of installed cluster nodes.
     *
     * @since 1.607
     */
    private final transient Nodes nodes = new Nodes(this);

    /**
     * Quiet period.
     *
     * This is {@link Integer} so that we can initialize it to '5' for upgrading users.
     */
    /*package*/ Integer quietPeriod;

    /**
     * Global default for {@link AbstractProject#getScmCheckoutRetryCount()}
     */
    /*package*/ int scmCheckoutRetryCount;

    /**
     * {@link View}s.
     */
    private final CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<>();

    /**
     * Name of the primary view.
     * <p>
     * Start with null, so that we can upgrade pre-1.269 data well.
     * @since 1.269
     */
    private volatile String primaryView;

    private final transient ViewGroupMixIn viewGroupMixIn = new ViewGroupMixIn(this) {
        @Override
        protected List<View> views() { return views; }

        @Override
        protected String primaryView() { return primaryView; }

        @Override
        protected void primaryView(String name) { primaryView = name; }
    };


    private final transient FingerprintMap fingerprintMap = new FingerprintMap();

    /**
     * Loaded plugins.
     */
    public final transient PluginManager pluginManager;

    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public transient volatile TcpSlaveAgentListener tcpSlaveAgentListener;

    private final transient Object tcpSlaveAgentListenerLock = new Object();

    /**
     * List of registered {@link SCMListener}s.
     */
    private final transient CopyOnWriteList<SCMListener> scmListeners = new CopyOnWriteList<>();

    /**
     * TCP agent port.
     * 0 for random, -1 to disable.
     */
    private int slaveAgentPort = getSlaveAgentPortInitialValue(0);

    private static int getSlaveAgentPortInitialValue(int def) {
        return SystemProperties.getInteger(Jenkins.class.getName() + ".slaveAgentPort", def);
    }

    /**
     * If -Djenkins.model.Jenkins.slaveAgentPort is defined, enforce it on every start instead of only the first one.
     */
    private static final boolean SLAVE_AGENT_PORT_ENFORCE = SystemProperties.getBoolean(Jenkins.class.getName() + ".slaveAgentPortEnforce", false);

    /**
     * Whitespace-separated labels assigned to the built-in node as a {@link Node}.
     */
    private String label = "";

    private static /* non-final for Groovy */ String nodeNameAndSelfLabelOverride = SystemProperties.getString(Jenkins.class.getName() + ".nodeNameAndSelfLabelOverride");

    /**
     * {@link hudson.security.csrf.CrumbIssuer}
     */
    private volatile CrumbIssuer crumbIssuer = GlobalCrumbIssuerConfiguration.createDefaultCrumbIssuer();

    /**
     * All labels known to Jenkins. This allows us to reuse the same label instances
     * as much as possible, even though that's not a strict requirement.
     */
    private final transient ConcurrentHashMap<String, Label> labels = new ConcurrentHashMap<>();

    /**
     * Load statistics of the entire system.
     *
     * This includes every executor and every job in the system.
     */
    @Exported
    public final transient OverallLoadStatistics overallLoad = new OverallLoadStatistics();

    /**
     * Load statistics of the free roaming jobs and agents.
     *
     * This includes all executors on {@link hudson.model.Node.Mode#NORMAL} nodes and jobs that do not have any assigned nodes.
     *
     * @since 1.467
     */
    @Exported
    public final transient LoadStatistics unlabeledLoad = new UnlabeledLoadStatistics();

    /**
     * {@link NodeProvisioner} that reacts to {@link #unlabeledLoad}.
     * @since 1.467
     */
    public final transient NodeProvisioner unlabeledNodeProvisioner = new NodeProvisioner(null, unlabeledLoad);

    /**
     * @deprecated as of 1.467
     *      Use {@link #unlabeledNodeProvisioner}.
     *      This was broken because it was tracking all the executors in the system, but it was only tracking
     *      free-roaming jobs in the queue. So {@link Cloud} fails to launch nodes when you have some exclusive
     *      agents and free-roaming jobs in the queue.
     */
    @Restricted(NoExternalUse.class)
    @Deprecated
    public final transient NodeProvisioner overallNodeProvisioner = unlabeledNodeProvisioner;

    /**
     * @deprecated use {@link #getServletContext}
     */
    @Deprecated
    public final transient javax.servlet.ServletContext servletContext;

    private final transient ServletContext jakartaServletContext;

    /**
     * @since 2.475
     */
    public ServletContext getServletContext() {
        return this.jakartaServletContext;
    }

    /**
     * Transient action list. Useful for adding navigation items to the navigation bar
     * on the left.
     */
    private final transient List<Action> actions = new CopyOnWriteArrayList<>();

    /**
     * List of built-in node-specific node properties
     */
    private DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties = new DescribableList<>(this);

    /**
     * List of global properties
     */
    private DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = new DescribableList<>(this);

    /**
     * {@link AdministrativeMonitor}s installed on this system.
     *
     * @see AdministrativeMonitor
     */
    public final transient List<AdministrativeMonitor> administrativeMonitors = getExtensionList(AdministrativeMonitor.class);

    /**
     * Widgets on Jenkins.
     */
    private final transient List<Widget> widgets = getExtensionList(Widget.class);

    /**
     * {@link AdjunctManager}
     */
    private final transient AdjunctManager adjuncts;

    /**
     * Code that handles {@link ItemGroup} work.
     */
    private final transient ItemGroupMixIn itemGroupMixIn = new ItemGroupMixIn(this, this) {
        @Override
        protected void add(TopLevelItem item) {
            items.put(item.getName(), item);
        }

        @Override
        protected File getRootDirFor(String name) {
            return Jenkins.this.getRootDirFor(name);
        }
    };


    /**
     * Hook for a test harness to intercept Jenkins.get()
     *
     * Do not use in the production code as the signature may change.
     */
    public interface JenkinsHolder {
        @CheckForNull Jenkins getInstance();
    }

    static JenkinsHolder HOLDER = new JenkinsHolder() {
        @Override
        public @CheckForNull Jenkins getInstance() {
            return theInstance;
        }
    };

    /**
     * Gets the {@link Jenkins} singleton.
     * @return {@link Jenkins} instance
     * @throws IllegalStateException for the reasons that {@link #getInstanceOrNull} might return null
     * @since 2.98
     */
    @NonNull
    public static Jenkins get() throws IllegalStateException {
        Jenkins instance = getInstanceOrNull();
        if (instance == null) {
            throw new IllegalStateException("Jenkins.instance is missing. Read the documentation of Jenkins.getInstanceOrNull to see what you are doing wrong.");
        }
        return instance;
    }

    /**
     * @deprecated This is a verbose historical alias for {@link #get}.
     * @since 1.590
     */
    @Deprecated
    @NonNull
    public static Jenkins getActiveInstance() throws IllegalStateException {
        return get();
    }

    /**
     * Gets the {@link Jenkins} singleton.
     * {@link #get} is what you normally want.
     * <p>In certain rare cases you may have code that is intended to run before Jenkins starts or while Jenkins is being shut down.
     * For those rare cases use this method.
     * <p>In other cases you may have code that might end up running on a remote JVM and not on the Jenkins controller or built-in node.
     * For those cases you really should rewrite your code so that when the {@link Callable} is sent over the remoting channel
     * it can do whatever it needs without ever referring to {@link Jenkins};
     * for example, gather any information you need on the controller side before constructing the callable.
     * If you must do a runtime check whether you are in the controller or agent, use {@link JenkinsJVM} rather than this method,
     * as merely loading the {@link Jenkins} class file into an agent JVM can cause linkage errors under some conditions.
     * @return The instance. Null if the {@link Jenkins} service has not been started, or was already shut down,
     *         or we are running on an unrelated JVM, typically an agent.
     * @since 1.653
     */
    @CLIResolver
    @CheckForNull
    public static Jenkins getInstanceOrNull() {
        return HOLDER.getInstance();
    }

    /**
     * @deprecated This is a historical alias for {@link #getInstanceOrNull} but with ambiguous nullability. Use {@link #get} in typical cases.
     */
    @Nullable
    @Deprecated
    public static Jenkins getInstance() {
        return getInstanceOrNull();
    }

    /**
     * Secret key generated once and used for a long time, beyond
     * container start/stop. Persisted outside {@code config.xml} to avoid
     * accidental exposure.
     */
    private final transient String secretKey;

    private final transient UpdateCenter updateCenter = UpdateCenter.createUpdateCenter(null);

    /**
     * True if the user opted out from the statistics tracking. We'll never send anything if this is true.
     */
    private Boolean noUsageStatistics;

    /**
     * If this is false, no migration is needed to reconfigure the built-in node (formerly 'master', now 'built-in').
     * Otherwise, {@link BuiltInNodeMigration} will show up.
     */
    // See #readResolve for null -> true transition and #save for null -> false transition
    @Restricted(NoExternalUse.class)
    /* package-private */ Boolean nodeRenameMigrationNeeded;

    /**
     * HTTP proxy configuration.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public transient volatile ProxyConfiguration proxy;

    /**
     * Bound to "/log".
     */
    private transient LogRecorderManager log = new LogRecorderManager();


    private final transient boolean oldJenkinsJVM;

    protected Jenkins(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root, context, null);
    }

    /**
     * @param pluginManager
     *      If non-null, use existing plugin manager.  create a new one.
     */
    @SuppressFBWarnings({
        "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", // Trigger.timer
        "DM_EXIT" // Exit is wanted here
    })
    protected Jenkins(File root, ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
        oldJenkinsJVM = JenkinsJVM.isJenkinsJVM(); // capture to restore in cleanUp()
        JenkinsJVMAccess._setJenkinsJVM(true); // set it for unit tests as they will not have gone through WebAppMain
        long start = System.currentTimeMillis();
        STARTUP_MARKER_FILE = new FileBoolean(new File(root, ".lastStarted"));
        // As Jenkins is starting, grant this process full control
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            this.root = root;
            this.jakartaServletContext = context;
            this.servletContext = ServletContextWrapper.fromJakartServletContext(context);
            computeVersion(context);
            if (theInstance != null)
                throw new IllegalStateException("second instance");
            theInstance = this;

            if (!new File(root, "jobs").exists()) {
                // if this is a fresh install, use more modern default layout that's consistent with agents
                workspaceDir = DEFAULT_WORKSPACES_DIR;
            }

            // doing this early allows InitStrategy to set environment upfront
            final InitStrategy is = InitStrategy.get(Thread.currentThread().getContextClassLoader());

            Trigger.timer = new java.util.Timer("Jenkins cron thread");
            queue = new Queue(LoadBalancer.CONSISTENT_HASH);
            labelAtomSet = Collections.unmodifiableSet(Label.parse(label));
            try {
                dependencyGraph = DependencyGraph.EMPTY;
            } catch (InternalError e) {
                if (e.getMessage().contains("window server")) {
                    throw new Error("Looks like the server runs without X. Please specify -Djava.awt.headless=true as JVM option", e);
                }
                throw e;
            }

            // get or create the secret
            TextFile secretFile = new TextFile(new File(getRootDir(), "secret.key"));
            if (secretFile.exists()) {
                secretKey = secretFile.readTrim();
            } else {
                byte[] random = new byte[32];
                RANDOM.nextBytes(random);
                secretKey = Util.toHexString(random);
                secretFile.write(secretKey);

                // this marker indicates that the secret.key is generated by the version of Jenkins post SECURITY-49.
                // this indicates that there's no need to rewrite secrets on disk
                new FileBoolean(new File(root, "secret.key.not-so-secret")).on();
            }

            try {
                proxy = ProxyConfiguration.load();
            } catch (IOException e) {
                LOGGER.log(SEVERE, "Failed to load proxy configuration", e);
            }

            if (pluginManager == null)
                pluginManager = PluginManager.createDefault(this);
            this.pluginManager = pluginManager;
            WebApp webApp = WebApp.get(getServletContext());
            // JSON binding needs to be able to see all the classes from all the plugins
            webApp.setClassLoader(pluginManager.uberClassLoader);
            webApp.setJsonInErrorMessageSanitizer(RedactSecretJsonInErrorMessageSanitizer.INSTANCE);

            TypedFilter typedFilter = new TypedFilter();
            webApp.setFilterForGetMethods(typedFilter);
            webApp.setFilterForFields(typedFilter);
            webApp.setFilterForDoActions(new DoActionFilter());

            StaplerFilteredActionListener actionListener = new StaplerFilteredActionListener();
            webApp.setFilteredGetterTriggerListener(actionListener);
            webApp.setFilteredDoActionTriggerListener(actionListener);
            webApp.setFilteredFieldTriggerListener(actionListener);

            webApp.setDispatchValidator(new StaplerDispatchValidator());
            webApp.setFilteredDispatchTriggerListener(actionListener);

            adjuncts = new AdjunctManager(getServletContext(), pluginManager.uberClassLoader, "adjuncts/" + SESSION_HASH, TimeUnit.DAYS.toMillis(365));

            ClassFilterImpl.register();
            LOGGER.info("Starting version " + getVersion());

            // Sanity check that we can load the confidential store. Fail fast if we can't.
            ConfidentialStore.get();

            // initialization consists of ...
            executeReactor(is,
                    pluginManager.initTasks(is),    // loading and preparing plugins
                    loadTasks(),                    // load jobs
                    InitMilestone.ordering()        // forced ordering among key milestones
            );

            // Ensure we reached the final initialization state. Log the error otherwise
            if (initLevel != InitMilestone.COMPLETED) {
                LOGGER.log(SEVERE, "Jenkins initialization has not reached the COMPLETED initialization milestone after the startup. " +
                                "Current state: {0}. " +
                                "It may cause undefined incorrect behavior in Jenkins plugin relying on this state. " +
                                "It is likely an issue with the Initialization task graph. " +
                                "Example: usage of @Initializer(after = InitMilestone.COMPLETED) in a plugin (JENKINS-37759). " +
                                "Please create a bug in Jenkins bugtracker. ",
                        initLevel);
            }


            if (KILL_AFTER_LOAD)
                // TODO cleanUp?
                System.exit(0);
            save();

            launchTcpSlaveAgentListener();

            Timer.get().scheduleAtFixedRate(new SafeTimerTask() {
                @Override
                protected void doRun() throws Exception {
                    trimLabels();
                }
            }, TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(5), TimeUnit.MILLISECONDS);

            updateComputerList();

            { // built-in node is online now, its instance must always exist
                final Computer c = toComputer();
                if (c != null) {
                    for (ComputerListener cl : ComputerListener.all()) {
                        try {
                            cl.onOnline(c, new LogTaskListener(LOGGER, INFO));
                        } catch (Exception e) {
                            // Per Javadoc log exceptions but still go online.
                            // NOTE: this does not include Errors, which indicate a fatal problem
                            LOGGER.log(WARNING, String.format("Exception in onOnline() for the computer listener %s on the built-in node",
                                    cl.getClass()), e);
                        }
                    }
                }
            }

            for (ItemListener l : ItemListener.all()) {
                long itemListenerStart = System.currentTimeMillis();
                try {
                    l.onLoaded();
                } catch (RuntimeException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
                if (LOG_STARTUP_PERFORMANCE)
                    LOGGER.info(String.format("Took %dms for item listener %s startup",
                            System.currentTimeMillis() - itemListenerStart, l.getClass().getName()));
            }

            if (LOG_STARTUP_PERFORMANCE)
                LOGGER.info(String.format("Took %dms for complete Jenkins startup",
                        System.currentTimeMillis() - start));

            STARTUP_MARKER_FILE.on();
        }
    }

    /**
     * Maintains backwards compatibility. Invoked by XStream when this object is de-serialized.
     */
    @SuppressWarnings("unused")
    protected Object readResolve() {
        if (jdks == null) {
            jdks = new ArrayList<>();
        }
        if (SLAVE_AGENT_PORT_ENFORCE) {
            slaveAgentPort = getSlaveAgentPortInitialValue(slaveAgentPort);
        }

        // no longer persisted
        installStateName = null;

        if (nodeRenameMigrationNeeded == null) {
            /* deserializing without a value set means we need to migrate */
            nodeRenameMigrationNeeded = true;
        }
        _setLabelString(label);

        return this;
    }

    /**
     * Retrieve the proxy configuration.
     *
     * @return the proxy configuration
     * @since 2.205
     */
    @CheckForNull
    public ProxyConfiguration getProxy() {
        return proxy;
    }

    /**
     * Set the proxy configuration.
     *
     * @param proxy the proxy to set
     * @since 2.205
     */
    public void setProxy(@CheckForNull ProxyConfiguration proxy) {
        this.proxy = proxy;
    }

    /**
     * Get the Jenkins {@link jenkins.install.InstallState install state}.
     * @return The Jenkins {@link jenkins.install.InstallState install state}.
     */
    @NonNull
    public InstallState getInstallState() {
        if (installState != null) {
            installStateName = installState.name();
            installState = null;
        }
        InstallState is = installStateName != null ? InstallState.valueOf(installStateName) : InstallState.UNKNOWN;
        return is != null ? is : InstallState.UNKNOWN;
    }

    /**
     * Update the current install state. This will invoke state.initializeState()
     * when the state has been transitioned.
     */
    public void setInstallState(@NonNull InstallState newState) {
        String prior = installStateName;
        installStateName = newState.name();
        LOGGER.log(Main.isDevelopmentMode ? Level.INFO : Level.FINE, "Install state transitioning from: {0} to: {1}", new Object[] { prior, installStateName });
        if (!installStateName.equals(prior)) {
            getSetupWizard().onInstallStateUpdate(newState);
            newState.initializeState();
        }
    }

    /**
     * Executes a reactor.
     *
     * @param is
     *      If non-null, this can be consulted for ignoring some tasks. Only used during the initialization of Jenkins.
     */
    private void executeReactor(final InitStrategy is, TaskBuilder... builders) throws IOException, InterruptedException, ReactorException {
        Reactor reactor = new Reactor(builders) {
            /**
             * Sets the thread name to the task for better diagnostics.
             */
            @Override
            protected void runTask(Task task) throws Exception {
                if (is != null && is.skipInitTask(task))  return;

                String taskName = InitReactorRunner.getDisplayName(task);

                Thread t = Thread.currentThread();
                String name = t.getName();
                if (taskName != null)
                    t.setName(taskName);
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) { // full access in the initialization thread
                    long start = System.currentTimeMillis();
                    super.runTask(task);
                    if (LOG_STARTUP_PERFORMANCE)
                        LOGGER.info(String.format("Took %dms for %s by %s",
                                System.currentTimeMillis() - start, taskName, name));
                } catch (Exception | Error x) {
                    if (containsLinkageError(x)) {
                        LOGGER.log(Level.WARNING, taskName + " failed perhaps due to plugin dependency issues", x);
                    } else {
                        throw x;
                    }
                } finally {
                    t.setName(name);
                }
            }

            private boolean containsLinkageError(Throwable x) {
                if (x instanceof LinkageError) {
                    return true;
                }
                Throwable x2 = x.getCause();
                return x2 != null && containsLinkageError(x2);
            }
        };

        new InitReactorRunner() {
            @Override
            protected void onInitMilestoneAttained(InitMilestone milestone) {
                initLevel = milestone;
                getLifecycle().onExtendTimeout(EXTEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (milestone == PLUGINS_PREPARED) {
                    // set up Guice to enable injection as early as possible
                    // before this milestone, ExtensionList.ensureLoaded() won't actually try to locate instances
                    ExtensionList.lookup(ExtensionFinder.class).getComponents();
                }
            }
        }.run(reactor);
    }


    public TcpSlaveAgentListener getTcpSlaveAgentListener() {
        return tcpSlaveAgentListener;
    }

    /**
     * Makes {@link AdjunctManager} URL-bound.
     * The dummy parameter allows us to use different URLs for the same adjunct,
     * for proper cache handling.
     */
    public AdjunctManager getAdjuncts(String dummy) {
        return adjuncts;
    }

    @Exported
    public int getSlaveAgentPort() {
        return slaveAgentPort;
    }

    /**
     * @since 2.24
     */
    public boolean isSlaveAgentPortEnforced() {
        return Jenkins.SLAVE_AGENT_PORT_ENFORCE;
    }

    /**
     * @param port
     *      0 to indicate random available TCP port. -1 to disable this service.
     */
    public void setSlaveAgentPort(int port) throws IOException {
        if (SLAVE_AGENT_PORT_ENFORCE) {
            LOGGER.log(Level.WARNING, "setSlaveAgentPort({0}) call ignored because system property {1} is true", new String[] { Integer.toString(port), Jenkins.class.getName() + ".slaveAgentPortEnforce" });
        } else {
            forceSetSlaveAgentPort(port);
        }
    }

    private void forceSetSlaveAgentPort(int port) throws IOException {
        this.slaveAgentPort = port;
        launchTcpSlaveAgentListener();
    }

    /**
     * Returns the enabled agent protocols.
     *
     * @return the enabled agent protocols.
     * @since 2.16
     */
    @NonNull
    public synchronized Set<String> getAgentProtocols() {
        return AgentProtocol.all().stream().map(AgentProtocol::getName).filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * @deprecated No longer does anything.
     */
    @Deprecated
    public synchronized void setAgentProtocols(@NonNull Set<String> protocols) {
        LOGGER.log(Level.WARNING, null, new IllegalStateException("Jenkins.agentProtocols no longer configurable"));
    }

    private void launchTcpSlaveAgentListener() throws IOException {
        synchronized (tcpSlaveAgentListenerLock) {
            // shutdown previous agent if the port has changed
            if (tcpSlaveAgentListener != null && tcpSlaveAgentListener.configuredPort != slaveAgentPort) {
                tcpSlaveAgentListener.shutdown();
                tcpSlaveAgentListener = null;
            }
            if (slaveAgentPort != -1 && tcpSlaveAgentListener == null) {
                final String administrativeMonitorId = getClass().getName() + ".tcpBind";
                try {
                    tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                    // remove previous monitor in case of previous error
                    AdministrativeMonitor toBeRemoved = null;
                    ExtensionList<AdministrativeMonitor> all = AdministrativeMonitor.all();
                    for (AdministrativeMonitor am : all) {
                        if (administrativeMonitorId.equals(am.id)) {
                            toBeRemoved = am;
                            break;
                        }
                    }
                    all.remove(toBeRemoved);
                } catch (BindException e) {
                    LOGGER.log(Level.WARNING, String.format("Failed to listen to incoming agent connections through port %s. Change the port number", slaveAgentPort), e);
                    new AdministrativeError(administrativeMonitorId,
                            "Failed to listen to incoming agent connections",
                            "Failed to listen to incoming agent connections. <a href='configureSecurity'>Change the inbound TCP port number</a> to solve the problem.", e);
                }
            }
        }
    }

    @Extension
    @Restricted(NoExternalUse.class)
    public static class EnforceSlaveAgentPortAdministrativeMonitor extends AdministrativeMonitor {
        @Inject
        Jenkins j;

        @Override
        public String getDisplayName() {
            return jenkins.model.Messages.EnforceSlaveAgentPortAdministrativeMonitor_displayName();
        }

        public String getSystemPropertyName() {
            return Jenkins.class.getName() + ".slaveAgentPort";
        }

        public int getExpectedPort() {
            int slaveAgentPort = j.slaveAgentPort;
            return Jenkins.getSlaveAgentPortInitialValue(slaveAgentPort);
        }

        @RequirePOST
        public void doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            j.forceSetSlaveAgentPort(getExpectedPort());
            rsp.sendRedirect2(req.getContextPath() + "/manage");
        }

        @Override
        public boolean isActivated() {
            int slaveAgentPort = Jenkins.get().slaveAgentPort;
            return SLAVE_AGENT_PORT_ENFORCE && slaveAgentPort != Jenkins.getSlaveAgentPortInitialValue(slaveAgentPort);
        }
    }

    @Override
    public void setNodeName(String name) {
        throw new UnsupportedOperationException(); // not allowed
    }

    @Override
    public String getNodeDescription() {
        return Messages.Hudson_NodeDescription();
    }

    @Exported
    public String getDescription() {
        return systemMessage;
    }

    @NonNull
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public UpdateCenter getUpdateCenter() {
        return updateCenter;
    }

    /**
     * If usage statistics has been disabled
     *
     * @since 2.226
     */
    @CheckForNull
    public Boolean isNoUsageStatistics() {
        return noUsageStatistics;
    }

    /**
     * If usage statistics are being collected
     *
     * @return {@code true} if usage statistics should be collected.
     *                Defaults to {@code true} when {@link #noUsageStatistics} is not set.
     */
    public boolean isUsageStatisticsCollected() {
        return noUsageStatistics == null || !noUsageStatistics;
    }

    /**
     * Sets the noUsageStatistics flag
     *
     */
    public void setNoUsageStatistics(Boolean noUsageStatistics) throws IOException {
        this.noUsageStatistics = noUsageStatistics;
        save();
    }

    public Api getApi() {
        /* Do not show "REST API" link in footer when on 404 error page */
        final StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            final Object attribute = req.getAttribute("jakarta.servlet.error.message");
            if (attribute != null) {
                return null;
            }
        }
        return new Api(this);
    }

    /**
     * Returns a secret key that survives across container start/stop.
     * <p>
     * This value is useful for implementing some of the security features.
     *
     * @deprecated
     *      Due to the past security advisory, this value should not be used any more to protect sensitive information.
     *      See {@link ConfidentialStore} and {@link ConfidentialKey} for how to store secrets.
     */
    @Deprecated
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Gets {@linkplain #getSecretKey() the secret key} as a key for AES-128.
     * @since 1.308
     * @deprecated
     *       See {@link #getSecretKey()}.
     */
    @Deprecated
    public SecretKey getSecretKeyAsAES128() {
        return Util.toAes128Key(secretKey);
    }

    /**
     * Returns the unique identifier of this Jenkins that has been historically used to identify
     * this Jenkins to the outside world.
     *
     * <p>
     * This form of identifier is weak in that it can be impersonated by others. See
     * <a href="https://github.com/jenkinsci/instance-identity-plugin">the Instance Identity plugin</a> for more modern form of instance ID
     * that can be challenged and verified.
     *
     * @since 1.498
     */
    public String getLegacyInstanceId() {
        return Util.getDigestOf(getSecretKey());
    }

    /**
     * Gets the SCM descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<SCM> getScm(String shortClassName) {
        return findDescriptor(shortClassName, SCM.all());
    }

    /**
     * Gets the repository browser descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<RepositoryBrowser<?>> getRepositoryBrowser(String shortClassName) {
        return findDescriptor(shortClassName, RepositoryBrowser.all());
    }

    /**
     * Gets the builder descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<Builder> getBuilder(String shortClassName) {
        return findDescriptor(shortClassName, Builder.all());
    }

    /**
     * Gets the build wrapper descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<BuildWrapper> getBuildWrapper(String shortClassName) {
        return findDescriptor(shortClassName, BuildWrapper.all());
    }

    /**
     * Gets the publisher descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<Publisher> getPublisher(String shortClassName) {
        return findDescriptor(shortClassName, Publisher.all());
    }

    /**
     * Gets the trigger descriptor by name. Primarily used for making them web-visible.
     */
    public TriggerDescriptor getTrigger(String shortClassName) {
        return (TriggerDescriptor) findDescriptor(shortClassName, Trigger.all());
    }

    /**
     * Gets the retention strategy descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<RetentionStrategy<?>> getRetentionStrategy(String shortClassName) {
        return findDescriptor(shortClassName, RetentionStrategy.all());
    }

    /**
     * Gets the {@link JobPropertyDescriptor} by name. Primarily used for making them web-visible.
     */
    public JobPropertyDescriptor getJobProperty(String shortClassName) {
        // combining these two lines triggers javac bug. See issue JENKINS-610
        Descriptor d = findDescriptor(shortClassName, JobPropertyDescriptor.all());
        return (JobPropertyDescriptor) d;
    }

    /**
     * @deprecated
     *      UI method. Not meant to be used programmatically.
     */
    @Deprecated
    public ComputerSet getComputer() {
        return new ComputerSet();
    }

    /**
     * Only there to bind to /cloud/ URL. Otherwise /cloud/new gets resolved to getCloud("new") by stapler which is not what we want.
     */
    @Restricted(DoNotUse.class)
    public CloudSet getCloud() {
        return new CloudSet();
    }

    /**
     * Exposes {@link Descriptor} by its name to URL.
     *
     * After doing all the {@code getXXX(shortClassName)} methods, I finally realized that
     * this just doesn't scale.
     *
     * @param id
     *      Either {@link Descriptor#getId()} (recommended) or the short name of a {@link Describable} subtype (for compatibility)
     * @throws IllegalArgumentException if a short name was passed which matches multiple IDs (fail fast)
     */
    @SuppressWarnings("rawtypes") // too late to fix
    public Descriptor getDescriptor(String id) {
        Iterable<Descriptor> descriptors = getExtensionList(Descriptor.class);
        for (Descriptor d : descriptors) {
            if (d.getId().equals(id)) {
                return d;
            }
        }
        Descriptor candidate = null;
        for (Descriptor d : descriptors) {
            String name = d.getId();
            if (name.substring(name.lastIndexOf('.') + 1).equals(id)) {
                if (candidate == null) {
                    candidate = d;
                } else {
                    throw new IllegalArgumentException(id + " is ambiguous; matches both " + name + " and " + candidate.getId());
                }
            }
        }
        return candidate;
    }

    /**
     * Alias for {@link #getDescriptor(String)}.
     */
    @Override
    public Descriptor getDescriptorByName(String id) {
        return getDescriptor(id);
    }

    /**
     * Gets the {@link Descriptor} that corresponds to the given {@link Describable} type.
     * <p>
     * If you have an instance of {@code type} and call {@link Describable#getDescriptor()},
     * you'll get the same instance that this method returns.
     */
    @CheckForNull
    public Descriptor getDescriptor(Class<? extends Describable> type) {
        for (Descriptor d : getExtensionList(Descriptor.class))
            if (d.clazz == type)
                return d;
        return null;
    }

    /**
     * Works just like {@link #getDescriptor(Class)} but don't take no for an answer.
     *
     * @throws AssertionError
     *      If the descriptor is missing.
     * @since 1.326
     */
    @NonNull
    public Descriptor getDescriptorOrDie(Class<? extends Describable> type) {
        Descriptor d = getDescriptor(type);
        if (d == null)
            throw new AssertionError(type + " is missing its descriptor");
        return d;
    }

    /**
     * Gets the {@link Descriptor} instance in the current Jenkins by its type.
     */
    public <T extends Descriptor> T getDescriptorByType(Class<T> type) {
        for (Descriptor d : getExtensionList(Descriptor.class))
            if (d.getClass() == type)
                return type.cast(d);
        return null;
    }

    /**
     * Gets the {@link SecurityRealm} descriptors by name. Primarily used for making them web-visible.
     */
    public Descriptor<SecurityRealm> getSecurityRealms(String shortClassName) {
        return findDescriptor(shortClassName, SecurityRealm.all());
    }

    /**
     * Finds a descriptor that has the specified name.
     */
    private <T extends Describable<T>>
    Descriptor<T> findDescriptor(String shortClassName, Collection<? extends Descriptor<T>> descriptors) {
        String name = '.' + shortClassName;
        for (Descriptor<T> d : descriptors) {
            if (d.clazz.getName().endsWith(name))
                return d;
        }
        return null;
    }

    protected void updateNewComputer(Node n) {
        updateNewComputer(n, AUTOMATIC_AGENT_LAUNCH);
    }

    /**
     * Update the list of computers that are running on this Jenkins instance.
     * Consider {@link #updateComputers(Node...)} instead if you know what nodes needs to be updated.
     * @see #updateComputers(Node...)
     */
    protected void updateComputerList() {
        var allNodes = new HashSet<Node>();
        allNodes.add(this);
        allNodes.addAll(getNodes());
        updateComputerList(AUTOMATIC_AGENT_LAUNCH, allNodes);
    }

    /**
     * Update the computers for the given nodes.
     */
    protected void updateComputers(@NonNull Node... nodes) {
        var nodeSet = new HashSet<Node>();
        Collections.addAll(nodeSet, nodes);
        updateComputerList(AUTOMATIC_AGENT_LAUNCH, nodeSet);
    }

    /** @deprecated Use {@link SCMListener#all} instead. */
    @Deprecated
    public CopyOnWriteList<SCMListener> getSCMListeners() {
        return scmListeners;
    }

    /**
     * Gets the plugin object from its short name.
     * This allows URL {@code hudson/plugin/ID} to be served by the views
     * of the plugin class.
     * @param shortName Short name of the plugin
     * @return The plugin singleton or {@code null} if for some reason the plugin is not loaded.
     *         The fact the plugin is loaded does not mean it is enabled and fully initialized for the current Jenkins session.
     *         Use {@link Plugin#getWrapper()} and then {@link PluginWrapper#isActive()} to check it.
     */
    @CheckForNull
    public Plugin getPlugin(String shortName) {
        PluginWrapper p = pluginManager.getPlugin(shortName);
        if (p == null)     return null;
        return p.getPlugin();
    }

    /**
     * Gets the plugin object from its class.
     *
     * <p>
     * This allows easy storage of plugin information in the plugin singleton without
     * every plugin reimplementing the singleton pattern.
     *
     * @param <P> Class of the plugin
     * @param clazz The plugin class (beware class-loader fun, this will probably only work
     * from within the jpi that defines the plugin class, it may or may not work in other cases)
     * @return The plugin singleton or {@code null} if for some reason the plugin is not loaded.
     *         The fact the plugin is loaded does not mean it is enabled and fully initialized for the current Jenkins session.
     *         Use {@link Plugin#getWrapper()} and then {@link PluginWrapper#isActive()} to check it.
     */
    @SuppressWarnings("unchecked")
    @CheckForNull
    public <P extends Plugin> P getPlugin(Class<P> clazz) {
        PluginWrapper p = pluginManager.getPlugin(clazz);
        if (p == null)     return null;
        return (P) p.getPlugin();
    }

    /**
     * Gets the plugin objects from their super-class.
     *
     * @param clazz The plugin class (beware class-loader fun)
     *
     * @return The plugin instances.
     */
    public <P extends Plugin> List<P> getPlugins(Class<P> clazz) {
        List<P> result = new ArrayList<>();
        for (PluginWrapper w : pluginManager.getPlugins(clazz)) {
            result.add((P) w.getPlugin());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Synonym for {@link #getDescription}.
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Gets the markup formatter used in the system.
     *
     * @return
     *      never null.
     * @since 1.391
     */
    public @NonNull MarkupFormatter getMarkupFormatter() {
        MarkupFormatter f = markupFormatter;
        return f != null ? f : new EscapedMarkupFormatter();
    }

    /**
     * Sets the markup formatter used in the system globally.
     *
     * @since 1.391
     */
    public void setMarkupFormatter(MarkupFormatter f) {
        this.markupFormatter = f;
    }

    /**
     * Sets the system message.
     */
    public void setSystemMessage(String message) throws IOException {
        this.systemMessage = message;
        save();
    }

    @StaplerDispatchable
    public FederatedLoginService getFederatedLoginService(String name) {
        for (FederatedLoginService fls : FederatedLoginService.all()) {
            if (fls.getUrlName().equals(name))
                return fls;
        }
        return null;
    }

    public List<FederatedLoginService> getFederatedLoginServices() {
        return FederatedLoginService.all();
    }

    @Override
    public Launcher createLauncher(TaskListener listener) {
        return new LocalLauncher(listener).decorateFor(this);
    }


    @NonNull
    @Override
    public String getFullName() {
        return "";
    }

    @Override
    public String getFullDisplayName() {
        return "";
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * <p>
     * Adding {@link Action} is primarily useful for plugins to contribute
     * an item to the navigation bar of the top page. See existing {@link Action}
     * implementation for it affects the GUI.
     *
     * <p>
     * To register an {@link Action}, implement {@link RootAction} extension point, or write code like
     * {@code Jenkins.get().getActions().add(...)}.
     *
     * @return
     *      Live list where the changes can be made. Can be empty but never null.
     * @since 1.172
     */
    public List<Action> getActions() {
        return actions;
    }

    /**
     * Gets just the immediate children of {@link Jenkins}.
     *
     * @see #getAllItems(Class)
     */
    @Override
    @Exported(name = "jobs")
    public List<TopLevelItem> getItems() {
        return getItems(t -> true);
    }

    /**
     * Gets just the immediate children of {@link Jenkins} based on supplied predicate.
     *
     * @see #getAllItems(Class)
     * @since 2.221
     */
    @Override
    public List<TopLevelItem> getItems(Predicate<TopLevelItem> pred) {
        List<TopLevelItem> viewableItems = new ArrayList<>();
        for (TopLevelItem item : items.values()) {
            if (pred.test(item) && item.hasPermission(Item.READ))
                viewableItems.add(item);
        }
        return viewableItems;
    }

    /**
     * Returns the read-only view of all the {@link TopLevelItem}s keyed by their names.
     * <p>
     * This method is efficient, as it doesn't involve any copying.
     *
     * @since 1.296
     */
    public Map<String, TopLevelItem> getItemMap() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Gets just the immediate children of {@link Jenkins} but of the given type.
     */
    public <T> List<T> getItems(Class<T> type) {
        List<T> r = new ArrayList<>();
        for (TopLevelItem i : getItems(type::isInstance)) {
             r.add(type.cast(i));
         }
        return r;
    }

    /**
     * Gets a list of simple top-level projects.
     * @deprecated This method will ignore Maven and matrix projects, as well as projects inside containers such as folders.
     * You may prefer to call {@link #getAllItems(Class)} on {@link AbstractProject},
     * perhaps also using {@link Util#createSubList} to consider only {@link TopLevelItem}s.
     * (That will also consider the caller's permissions.)
     * If you really want to get just {@link Project}s at top level, ignoring permissions,
     * you can filter the values from {@link #getItemMap} using {@link Util#createSubList}.
     */
    @Deprecated
    public List<Project> getProjects() {
        return Util.createSubList(items.values(), Project.class);
    }

    /**
     * Gets the names of all the {@link Job}s.
     */
    public Collection<String> getJobNames() {
        List<String> names = new ArrayList<>();
        for (Job j : allItems(Job.class))
            names.add(j.getFullName());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Restricted(NoExternalUse.class)
    public ComboBoxModel doFillJobNameItems() {
        return new ComboBoxModel(getJobNames());
    }

    @Override
    public List<Action> getViewActions() {
        return getActions();
    }

    /**
     * Gets the names of all the {@link TopLevelItem}s.
     */
    public Collection<String> getTopLevelItemNames() {
        List<String> names = new ArrayList<>();
        for (TopLevelItem j : items.values())
            names.add(j.getName());
        return names;
    }

    /**
     * Gets a view by the specified name.
     * The method iterates through {@link hudson.model.ViewGroup}s if required.
     * @param name Name of the view
     * @return View instance or {@code null} if it is missing
     */
    @Override
    @CheckForNull
    public View getView(@CheckForNull String name) {
        return viewGroupMixIn.getView(name);
    }

    /**
     * Gets the read-only list of all {@link View}s.
     */
    @Override
    @Exported
    public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    @Override
    public void addView(@NonNull View v) throws IOException {
        viewGroupMixIn.addView(v);
    }

    /**
     * Completely replaces views.
     *
     * <p>
     * This operation is NOT provided as an atomic operation, but rather
     * the sole purpose of this is to define a setter for this to help
     * introspecting code, such as system-config-dsl plugin
     */
    // even if we want to offer this atomic operation, CopyOnWriteArrayList
    // offers no such operation
    public void setViews(Collection<View> views) throws IOException {
        try (BulkChange bc = new BulkChange(this)) {
            this.views.clear();
            for (View v : views) {
                addView(v);
            }
            bc.commit();
        }
    }

    @Override
    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    @Override
    public synchronized void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    @Override
    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    /**
     * Returns the primary {@link View} that renders the top-page of Jenkins.
     */
    @Exported
    @Override
    public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
     }

    public void setPrimaryView(@NonNull View v) {
        this.primaryView = v.getViewName();
    }

    @Override
    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    public void setViewsTabBar(ViewsTabBar viewsTabBar) {
        this.viewsTabBar = viewsTabBar;
    }

    @Override
    public Jenkins getItemGroup() {
        return this;
   }

    @Deprecated
    public MyViewsTabBar getMyViewsTabBar() {
        return myViewsTabBar;
    }

    @Deprecated
    public void setMyViewsTabBar(MyViewsTabBar myViewsTabBar) {
        this.myViewsTabBar = myViewsTabBar;
    }

    /**
     * Returns true if the current running Jenkins is upgraded from a version earlier than the specified version.
     *
     * <p>
     * This method continues to return true until the system configuration is saved, at which point
     * {@link #version} will be overwritten and Jenkins forgets the upgrade history.
     *
     * <p>
     * To handle SNAPSHOTS correctly, pass in "1.N.*" to test if it's upgrading from the version
     * equal or younger than N. So say if you implement a feature in 1.301 and you want to check
     * if the installation upgraded from pre-1.301, pass in "1.300.*"
     *
     * @since 1.301
     */
    public boolean isUpgradedFromBefore(VersionNumber v) {
        try {
            return new VersionNumber(version).isOlderThan(v);
        } catch (IllegalArgumentException e) {
            // fail to parse this version number
            return false;
        }
    }

    /**
     * Gets the read-only list of all {@link Computer}s.
     */
    public Computer[] getComputers() {
        return getComputersCollection().stream().sorted(Comparator.comparing(Computer::getName)).toArray(Computer[]::new);
    }

    @CLIResolver
    public @CheckForNull Computer getComputer(@Argument(required = true, metaVar = "NAME", usage = "Node name") @NonNull String name) {
        if (name.equals("(built-in)")
                || name.equals("(master)")) // backwards compatibility for URLs
            name = "";

        for (Computer c : getComputersCollection()) {
            if (c.getName().equals(name))
                return c;
        }
        return null;
    }

    /**
     * Gets the label that exists on this system by the name.
     *
     * @return null if name is null.
     * @see Label#parseExpression(String) (String)
     */
    @CheckForNull
    public Label getLabel(String expr) {
        if (expr == null)  return null;
        expr = QuotedStringTokenizer.unquote(expr);
        while (true) {
            Label l = labels.get(expr);
            if (l != null)
                return l;

            // non-existent
            try {
                // For the record, this method creates temporary labels but there is a periodic task
                // calling "trimLabels" to remove unused labels running every 5 minutes.
                labels.putIfAbsent(expr, Label.parseExpression(expr));
            } catch (IllegalArgumentException e) {
                // laxly accept it as a single label atom for backward compatibility
                return getLabelAtom(expr);
            }
        }
    }

    /**
     * Returns the label atom of the given name.
     * @return non-null iff name is non-null
     */
    public @Nullable LabelAtom getLabelAtom(@CheckForNull String name) {
        if (name == null)  return null;

        while (true) {
            Label l = labels.get(name);
            if (l != null)
                return (LabelAtom) l;

            // non-existent
            LabelAtom la = new LabelAtom(name);
            // For the record, this method creates temporary labels but there is a periodic task
            // calling "trimLabels" to remove unused labels running every 5 minutes.
            if (labels.putIfAbsent(name, la) == null)
                la.load();
        }
    }

    /**
     * Returns the label atom of the given name, only if it already exists.
     * @return non-null if the label atom already exists.
     */
    @Restricted(NoExternalUse.class)
    public @Nullable LabelAtom tryGetLabelAtom(@NonNull String name) {
        Label label = labels.get(name);
        if (label instanceof LabelAtom) {
            return (LabelAtom) label;
        }
        return null;
    }


    /**
     * Gets all the active labels in the current system.
     */
    public Set<Label> getLabels() {
        Set<Label> r = new TreeSet<>();
        for (Label l : labels.values()) {
            if (!l.isEmpty())
                r.add(l);
        }
        return r;
    }

    @NonNull
    private transient Set<LabelAtom> labelAtomSet;

    @Override
    protected Set<LabelAtom> getLabelAtomSet() {
        return labelAtomSet;
    }

    public Set<LabelAtom> getLabelAtoms() {
        Set<LabelAtom> r = new TreeSet<>();
        for (Label l : labels.values()) {
            if (!l.isEmpty() && l instanceof LabelAtom)
                r.add((LabelAtom) l);
        }
        return r;
    }

    @Override
    public Queue getQueue() {
        return queue;
    }

    @Override
    public String getDisplayName() {
        return Messages.Hudson_DisplayName();
    }

    public List<JDK> getJDKs() {
        return jdks;
    }

    /**
     * Replaces all JDK installations with those from the given collection.
     *
     * Use {@link hudson.model.JDK.DescriptorImpl#setInstallations(JDK...)} to
     * set JDK installations from external code.
     */
    @Restricted(NoExternalUse.class)
    public void setJDKs(Collection<? extends JDK> jdks) {
        this.jdks = new ArrayList<>(jdks);
    }

    /**
     * Gets the JDK installation of the given name, or returns null.
     */
    public JDK getJDK(String name) {
        if (name == null) {
            // if only one JDK is configured, "default JDK" should mean that JDK.
            List<JDK> jdks = getJDKs();
            if (jdks.size() == 1)  return jdks.get(0);
            return null;
        }
        for (JDK j : getJDKs()) {
            if (j.getName().equals(name))
                return j;
        }
        return null;
    }



    /**
     * Gets the agent node of the give name, hooked under this Jenkins.
     */
    public @CheckForNull Node getNode(String name) {
        return nodes.getNode(name);
    }

    @CheckForNull
    @Restricted(Beta.class)
    public Node getOrLoadNode(String nodeName) {
        return getNodesObject().getOrLoad(nodeName);
    }

    /**
     * Gets a {@link Cloud} by {@link Cloud#name its name}, or null.
     */
    public Cloud getCloud(String name) {
        return clouds.getByName(name);
    }

    @Override
    protected ConcurrentMap<Node, Computer> getComputerMap() {
        return computers;
    }

    /**
     * @return the collection of all {@link Computer}s in this instance.
     */
    @Restricted(NoExternalUse.class)
    public Collection<Computer> getComputersCollection() {
        return computers.values();
    }

    /**
     * Returns all {@link Node}s in the system, excluding {@link Jenkins} instance itself which
     * represents the built-in node (in other words, this only returns agents).
     */
    @Override
    @NonNull
    public List<Node> getNodes() {
        return nodes.getNodes();
    }

    /**
     * Get the {@link Nodes} object that handles maintaining individual {@link Node}s.
     * @return The Nodes object.
     */
    @Restricted(NoExternalUse.class)
    public Nodes getNodesObject() {
        // TODO replace this with something better when we properly expose Nodes.
        return nodes;
    }

    /**
     * Adds one more {@link Node} to Jenkins.
     * If a node of the same name already exists then that node will be replaced.
     */
    public void addNode(Node n) throws IOException {
        nodes.addNode(n);
    }

    /**
     * Removes a {@link Node} from Jenkins.
     */
    public void removeNode(@NonNull Node n) throws IOException {
        nodes.removeNode(n);
    }

    /**
     * Unload a node from Jenkins without touching its configuration file.
     */
    @Restricted(Beta.class)
    public void unloadNode(@NonNull Node n) {
        nodes.unload(n);
    }

    /**
     * Saves an existing {@link Node} on disk, called by {@link Node#save()}. This method is preferred in those cases
     * where you need to determine atomically that the node being saved is actually in the list of nodes.
     *
     * @param n the node to be updated.
     * @return {@code true}, if the node was updated. {@code false}, if the node was not in the list of nodes.
     * @throws IOException if the node could not be persisted.
     * @see Nodes#updateNode
     * @since 1.634
     */
    public boolean updateNode(Node n) throws IOException {
        return nodes.updateNode(n);
    }

    public void setNodes(final List<? extends Node> n) throws IOException {
        nodes.setNodes(n);
    }

    @Override
    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        return nodeProperties;
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getGlobalNodeProperties() {
        return globalNodeProperties;
    }

    /**
     * Resets all labels and remove invalid ones.
     *
     * This should be called when the assumptions behind label cache computation changes,
     * but we also call this periodically to self-heal any data out-of-sync issue.
     */
    /*package*/ void trimLabels() {
        trimLabels((Set) null);
    }

    /**
     * Reset labels and remove invalid ones for the given nodes.
     * @param nodes the nodes taken as reference to update labels
     */
    void trimLabels(Node... nodes) {
        Set<LabelAtom> includedLabels = new HashSet<>();
        Arrays.stream(nodes).filter(Objects::nonNull).forEach(n -> includedLabels.addAll(n.drainLabelsToTrim()));
        trimLabels(includedLabels);
    }

    /**
     * Reset labels and remove invalid ones for the given nodes.
     * @param includedLabels the labels taken as reference to update labels. If {@code null}, all labels are considered.
     */
    private void trimLabels(@CheckForNull Set<LabelAtom> includedLabels) {
        Set<Set<LabelAtom>> nodeLabels = new HashSet<>();
        nodeLabels.add(this.getAssignedLabels());
        this.getNodes().forEach(n -> nodeLabels.add(n.getAssignedLabels()));
        for (Iterator<Label> itr = labels.values().iterator(); itr.hasNext();) {
            Label l = itr.next();
            if (includedLabels == null || includedLabels.contains(l) || l.matches(includedLabels)) {
                if (nodeLabels.stream().anyMatch(l::matches) || !l.getClouds().isEmpty()) {
                    // there is at least one static agent or one cloud that currently claims it can handle the label.
                    // if the cloud has been removed, or its labels updated such that it can not handle this, this is handle in later calls
                    // resetLabel will remove the agents, and clouds from the label, and they will be repopulated later.
                    // not checking `cloud.canProvision()` here prevents a potential call that will only be repeated later
                    resetLabel(l);
                } else {
                    itr.remove();
                }
            }
        }
    }

    /**
     * Binds {@link AdministrativeMonitor}s to URL.
     * @param id Monitor ID
     * @return The requested monitor or {@code null} if it does not exist
     */
    @CheckForNull
    public AdministrativeMonitor getAdministrativeMonitor(String id) {
        for (AdministrativeMonitor m : administrativeMonitors)
            if (m.id.equals(id))
                return m;
        return null;
    }

    /**
     * Returns the enabled and activated administrative monitors accessible to the current user.
     *
     * @since 2.64
     */
    public List<AdministrativeMonitor> getActiveAdministrativeMonitors() {
        if (!AdministrativeMonitor.hasPermissionToDisplay()) {
            return Collections.emptyList();
        }
        return administrativeMonitors.stream().filter(m -> {
            try {
                return m.hasRequiredPermission() && m.isEnabled() && m.isActivated();
            } catch (Throwable x) {
                LOGGER.log(Level.WARNING, null, x);
                return false;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public NodeDescriptor getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    public static final class DescriptorImpl extends NodeDescriptor {
        @Extension
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        @Override
        public boolean isInstantiable() {
            return false;
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }

        // to route /descriptor/FQCN/xxx to getDescriptor(FQCN).xxx
        public Object getDynamic(String token) {
            return Jenkins.get().getDescriptor(token);
        }
    }

    /**
     * Gets the system default quiet period.
     */
    public int getQuietPeriod() {
        return quietPeriod != null ? quietPeriod : 5;
    }

    /**
     * Sets the global quiet period.
     *
     * @param quietPeriod
     *      null to the default value.
     */
    public void setQuietPeriod(Integer quietPeriod) throws IOException {
        this.quietPeriod = quietPeriod;
        save();
    }

    /**
     * Gets the global SCM check out retry count.
     */
    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount;
    }

    public void setScmCheckoutRetryCount(int scmCheckoutRetryCount) throws IOException {
        this.scmCheckoutRetryCount = scmCheckoutRetryCount;
        save();
    }

    @Override
    public String getSearchUrl() {
        return "";
    }

    @Override
    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex();

        this.actions.stream().filter(e -> !(e.getIconFileName() == null || e.getUrlName() == null)).forEach(action -> builder.add(new SearchItem() {
            @Override
            public String getSearchName() {
                return action.getDisplayName();
            }

            @Override
            public String getSearchUrl() {
                return action.getUrlName();
            }

            @Override
            public String getSearchIcon() {
                return action.getIconFileName();
            }

            @Override
            public SearchIndex getSearchIndex() {
                return SearchIndex.EMPTY;
            }
        }));

        builder.add(new CollectionSearchIndex<TopLevelItem>() {
                    @Override
                    protected SearchItem get(String key) { return getItemByFullName(key, TopLevelItem.class); }

                    @Override
                    protected Collection<TopLevelItem> all() { return getAllItems(TopLevelItem.class); }

                    @NonNull
                    @Override
                    protected Iterable<TopLevelItem> allAsIterable() {
                        return allItems(TopLevelItem.class);
                    }
                })
                .add(getPrimaryView().makeSearchIndex())
                .add(new CollectionSearchIndex() { // for computers
                    @Override
                    protected Computer get(String key) { return getComputer(key); }

                    @Override
                    protected Collection<Computer> all() { return getComputersCollection(); }
                })
                .add(new CollectionSearchIndex() { // for users
                    @Override
                    protected User get(String key) { return User.get(key, false); }

                    @Override
                    protected Collection<User> all() { return User.getAll(); }
                })
                .add(new CollectionSearchIndex() { // for views
                    @Override
                    protected View get(String key) { return getView(key); }

                    @Override
                    protected Collection<View> all() { return getAllViews(); }
                });
        return builder;
    }

    @Override
    public String getUrlChildPrefix() {
        return "job";
    }

    /**
     * Gets the absolute URL of Jenkins, such as {@code http://localhost/jenkins/}.
     *
     * <p>
     * This method first tries to use the manually configured value, then
     * fall back to {@link #getRootUrlFromRequest}.
     * It is done in this order so that it can work correctly even in the face
     * of a reverse proxy.
     *
     * @return {@code null} if this parameter is not configured by the user and the calling thread is not in an HTTP request;
     *                      otherwise the returned URL will always have the trailing {@code /}
     * @throws IllegalStateException {@link JenkinsLocationConfiguration} cannot be retrieved.
     *                      Jenkins instance may be not ready, or there is an extension loading glitch.
     * @since 1.66
     * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Hyperlinks+in+HTML">Hyperlinks in HTML</a>
     */
    public @Nullable String getRootUrl() throws IllegalStateException {
        final JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        String url = config.getUrl();
        if (url != null) {
            return Util.ensureEndsWith(url, "/");
        }
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null)
            return getRootUrlFromRequest();
        return null;
    }

    /** Exported alias for {@link JenkinsLocationConfiguration#getUrl}. */
    @Exported(name = "url")
    @Restricted(DoNotUse.class)
    @CheckForNull
    public String getConfiguredRootUrl() {
        JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        return config.getUrl();
    }

    /**
     * Is Jenkins running in HTTPS?
     *
     * Note that we can't really trust {@link StaplerRequest2#isSecure()} because HTTPS might be terminated
     * in the reverse proxy.
     */
    public boolean isRootUrlSecure() {
        String url = getRootUrl();
        return url != null && url.startsWith("https");
    }

    /**
     * Gets the absolute URL of Jenkins top page, such as {@code http://localhost/jenkins/}.
     *
     * <p>
     * Unlike {@link #getRootUrl()}, which uses the manually configured value,
     * this one uses the current request to reconstruct the URL. The benefit is
     * that this is immune to the configuration mistake (users often fail to set the root URL
     * correctly, especially when a migration is involved), but the downside
     * is that unless you are processing a request, this method doesn't work.
     *
     * <p>Please note that this will not work in all cases if Jenkins is running behind a
     * reverse proxy which has not been fully configured.
     * Specifically the {@code Host} and {@code X-Forwarded-Proto} headers must be set.
     * <a href="https://www.jenkins.io/doc/book/system-administration/reverse-proxy-configuration-apache/">Reverse proxy - Apache</a>
     * shows some examples of configuration.
     * @since 1.263
     */
    public @NonNull String getRootUrlFromRequest() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            throw new IllegalStateException("cannot call getRootUrlFromRequest from outside a request handling thread");
        }
        StringBuilder buf = new StringBuilder();
        String scheme = getXForwardedHeader(req, "X-Forwarded-Proto", req.getScheme());
        buf.append(scheme).append("://");
        String host = getXForwardedHeader(req, "X-Forwarded-Host", req.getServerName());
        int index = host.lastIndexOf(':');
        int port = req.getServerPort();
        if (index == -1) {
            // Almost everyone else except Nginx put the host and port in separate headers
            buf.append(host);
        } else {
            if (host.startsWith("[") && host.endsWith("]")) {
                // support IPv6 address
                buf.append(host);
            } else {
                // Nginx uses the same spec as for the Host header, i.e. hostname:port
                buf.append(host, 0, index);
                if (index + 1 < host.length()) {
                    try {
                        port = Integer.parseInt(host.substring(index + 1));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                // but if a user has configured Nginx with an X-Forwarded-Port, that will win out.
            }
        }
        String forwardedPort = getXForwardedHeader(req, "X-Forwarded-Port", null);
        if (forwardedPort != null) {
            try {
                port = Integer.parseInt(forwardedPort);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        if (port != ("https".equals(scheme) ? 443 : 80)) {
            buf.append(':').append(port);
        }
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    /**
     * Gets the originating "X-Forwarded-..." header from the request. If there are multiple headers the originating
     * header is the first header. If the originating header contains a comma separated list, the originating entry
     * is the first one.
     * @param req the request
     * @param header the header name
     * @param defaultValue the value to return if the header is absent.
     * @return the originating entry of the header or the default value if the header was not present.
     */
    private static String getXForwardedHeader(StaplerRequest2 req, String header, String defaultValue) {
        String value = req.getHeader(header);
        if (value != null) {
            int index = value.indexOf(',');
            return index == -1 ? value.trim() : value.substring(0, index).trim();
        }
        return defaultValue;
    }

    @Override
    public File getRootDir() {
        return root;
    }

    @Override
    public FilePath getWorkspaceFor(TopLevelItem item) {
        for (WorkspaceLocator l : WorkspaceLocator.all()) {
            FilePath workspace = l.locate(item, this);
            if (workspace != null) {
                return workspace;
            }
        }

        return new FilePath(expandVariablesForDirectory(workspaceDir, item));
    }

    public File getBuildDirFor(Job job) {
        return expandVariablesForDirectory(buildsDir, job);
    }

    /**
     * If the configured buildsDir has it's default value or has been changed.
     *
     * @return true if default value.
     */
    @Restricted(NoExternalUse.class)
    public boolean isDefaultBuildDir() {
        return DEFAULT_BUILDS_DIR.equals(buildsDir);
    }

    @Restricted(NoExternalUse.class)
    boolean isDefaultWorkspaceDir() {
        return OLD_DEFAULT_WORKSPACES_DIR.equals(workspaceDir) || DEFAULT_WORKSPACES_DIR.equals(workspaceDir);
    }

    private File expandVariablesForDirectory(String base, Item item) {
        return new File(expandVariablesForDirectory(base, item.getFullName(), item.getRootDir().getPath()));
    }

    @Restricted(NoExternalUse.class)
    public static String expandVariablesForDirectory(String base, String itemFullName, String itemRootDir) {
        Map<String, String> properties = new HashMap<>();
        properties.put("JENKINS_HOME", Jenkins.get().getRootDir().getPath());
        properties.put("ITEM_ROOTDIR", itemRootDir);
        properties.put("ITEM_FULLNAME", itemFullName); // legacy, deprecated
        properties.put("ITEM_FULL_NAME", itemFullName.replace(':', '$')); // safe, see JENKINS-12251
        return Util.replaceMacro(base, Collections.unmodifiableMap(properties));

    }

    public String getRawWorkspaceDir() {
        return workspaceDir;
    }

    public String getRawBuildsDir() {
        return buildsDir;
    }

    @Restricted(NoExternalUse.class)
    public void setRawBuildsDir(String buildsDir) {
        this.buildsDir = buildsDir;
    }

    @Override public @NonNull FilePath getRootPath() {
        return new FilePath(getRootDir());
    }

    @Override
    public FilePath createPath(String absolutePath) {
        return new FilePath((VirtualChannel) null, absolutePath);
    }

    @Override
    public ClockDifference getClockDifference() {
        return ClockDifference.ZERO;
    }

    @Override
    public Callable<ClockDifference, IOException> getClockDifferenceCallable() {
        return new ClockDifferenceCallable();
    }

    private static class ClockDifferenceCallable extends MasterToSlaveCallable<ClockDifference, IOException> {
        @Override
        public ClockDifference call() throws IOException {
            return new ClockDifference(0);
        }
    }

    /**
     * For binding {@link LogRecorderManager} to "/log".
     * Everything below here is admin-only, so do the check here.
     */
    public LogRecorderManager getLog() {
        checkPermission(SYSTEM_READ);
        return log;
    }

    /**
     * Set the LogRecorderManager.
     *
     * @param log the LogRecorderManager to set
     * @since 2.323
     */
    public void setLog(LogRecorderManager log) {
        checkPermission(ADMINISTER);
        this.log = log;
    }

    /**
     * A convenience method to check if there's some security
     * restrictions in place.
     */
    @Exported
    public boolean isUseSecurity() {
        return securityRealm != SecurityRealm.NO_AUTHENTICATION || authorizationStrategy != AuthorizationStrategy.UNSECURED;
    }

    public boolean isUseProjectNamingStrategy() {
        return projectNamingStrategy != ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY;
    }

    /**
     * If true, all the POST requests to Jenkins would have to have crumb in it to protect
     * Jenkins from CSRF vulnerabilities.
     */
    @Exported
    public boolean isUseCrumbs() {
        return crumbIssuer != null;
    }

    /**
     * Returns the constant that captures the three basic security modes in Jenkins.
     */
    public SecurityMode getSecurity() {
        // fix the variable so that this code works under concurrent modification to securityRealm.
        SecurityRealm realm = securityRealm;

        if (realm == SecurityRealm.NO_AUTHENTICATION)
            return SecurityMode.UNSECURED;
        if (realm instanceof LegacySecurityRealm)
            return SecurityMode.LEGACY;
        return SecurityMode.SECURED;
    }

    /**
     * @return
     *      never null.
     */
    public SecurityRealm getSecurityRealm() {
        return securityRealm;
    }

    /**
     * Sets a security realm.
     * @param securityRealm Security realm to set.
     *                      If {@code null}, {@link SecurityRealm#NO_AUTHENTICATION} will be set.
     */
    public void setSecurityRealm(@CheckForNull SecurityRealm securityRealm) {
        if (securityRealm == null)
            securityRealm = SecurityRealm.NO_AUTHENTICATION;
        this.useSecurity = true;
        IdStrategy oldUserIdStrategy = this.securityRealm == null
                ? securityRealm.getUserIdStrategy() // don't trigger rekey on Jenkins load
                : this.securityRealm.getUserIdStrategy();
        this.securityRealm = securityRealm;
        resetFilter(securityRealm, oldUserIdStrategy);
        saveQuietly();
    }

    /**
     * Reset the filters and proxies for the new {@link SecurityRealm}.
     * @param securityRealm The new security realm
     * @param oldUserIdStrategy The old user id strategy if there was one. Can trigger a rekey if the new user id strategy is different.
     */
    private void resetFilter(@CheckForNull SecurityRealm securityRealm, @CheckForNull IdStrategy oldUserIdStrategy) {
        try {
            HudsonFilter filter = HudsonFilter.get(getServletContext());
            if (filter == null) {
                // Fix for JENKINS-3069: This filter is not necessarily initialized before the servlets.
                // when HudsonFilter does come back, it'll initialize itself.
                LOGGER.fine("HudsonFilter has not yet been initialized: Can't perform security setup for now");
            } else {
                LOGGER.fine("HudsonFilter has been previously initialized: Setting security up");
                filter.reset(securityRealm);
                LOGGER.fine("Security is now fully set up");
            }
            if (oldUserIdStrategy != null && this.securityRealm != null && !oldUserIdStrategy.equals(this.securityRealm.getUserIdStrategy())) {
                User.rekey();
            }
        } catch (ServletException e) {
            // for binary compatibility, this method cannot throw a checked exception
            throw new RuntimeException("Failed to configure filter", e) {};
        }
    }

    /**
     * Sets a new authorization strategy.
     * @param a Authorization strategy to set.
     *          If {@code null}, {@link AuthorizationStrategy#UNSECURED} will be set
     */
    public void setAuthorizationStrategy(@CheckForNull AuthorizationStrategy a) {
        if (a == null)
            a = AuthorizationStrategy.UNSECURED;
        useSecurity = true;
        authorizationStrategy = a;
        saveQuietly();
    }

    public boolean isDisableRememberMe() {
        return disableRememberMe;
    }

    public void setDisableRememberMe(boolean disableRememberMe) {
        this.disableRememberMe = disableRememberMe;
    }

    public void disableSecurity() {
        useSecurity = null;
        setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        authorizationStrategy = AuthorizationStrategy.UNSECURED;
    }

    public void setProjectNamingStrategy(ProjectNamingStrategy ns) {
        if (ns == null) {
            ns = ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY;
        }
        projectNamingStrategy = ns;
    }

    public Lifecycle getLifecycle() {
        return Lifecycle.get();
    }

    /**
     * Gets the dependency injection container that hosts all the extension implementations and other
     * components in Jenkins.
     *
     * @since 1.433
     */
    public @CheckForNull Injector getInjector() {
        return lookup(Injector.class);
    }

    /**
     * An obsolete alias for {@link ExtensionList#lookup}.
     */
    @SuppressWarnings("unchecked")
    public <T> ExtensionList<T> getExtensionList(Class<T> extensionType) {
        ExtensionList<T> extensionList = extensionLists.get(extensionType);
        return extensionList != null ? extensionList : extensionLists.computeIfAbsent(extensionType, key -> ExtensionList.create(this, key));
    }

    /**
     * Formerly used to bind {@link ExtensionList}s to URLs.
     *
     * @since 1.349
     * @deprecated This is no longer supported.
     *      For URL access to descriptors, see {@link hudson.model.DescriptorByNameOwner}.
     *      For URL access to specific other {@link hudson.Extension} annotated elements, create your own {@link hudson.model.Action}, like {@link hudson.console.ConsoleAnnotatorFactory.RootAction}.
     */
    @Deprecated(since = "2.519")
    public ExtensionList getExtensionList(String extensionType) throws ClassNotFoundException {
        return getExtensionList(pluginManager.uberClassLoader.loadClass(extensionType));
    }

    /**
     * Returns {@link ExtensionList} that retains the discovered {@link Descriptor} instances for the given
     * kind of {@link Describable}.
     * <p>Assuming an appropriate {@link Descriptor} subtype, for most purposes you can simply use {@link ExtensionList#lookup}.
     * @return
     *      Can be an empty list but never null.
     */
    @SuppressWarnings("unchecked")
    public @NonNull <T extends Describable<T>, D extends Descriptor<T>> DescriptorExtensionList<T, D> getDescriptorList(Class<T> type) {
        return descriptorLists.computeIfAbsent(type, key -> DescriptorExtensionList.createDescriptorList(this, key));
    }

    /**
     * Refresh {@link ExtensionList}s by adding all the newly discovered extensions.
     *
     * Exposed only for {@link PluginManager#dynamicLoad(File)}.
     */
    public void refreshExtensions() throws ExtensionRefreshException {
        ExtensionList<ExtensionFinder> finders = getExtensionList(ExtensionFinder.class);
        LOGGER.finer(() -> "refreshExtensions " + finders);
        for (ExtensionFinder ef : finders) {
            if (!ef.isRefreshable())
                throw new ExtensionRefreshException(ef + " doesn't support refresh");
        }

        List<ExtensionComponentSet> fragments = new ArrayList<>();

        for (ExtensionFinder ef : finders) {
            LOGGER.finer(() -> "searching " + ef);
            fragments.add(ef.refresh());
        }
        ExtensionComponentSet delta = ExtensionComponentSet.union(fragments).filtered();

        // if we find a new ExtensionFinder, we need it to list up all the extension points as well
        List<ExtensionComponent<ExtensionFinder>> newFinders = new ArrayList<>(delta.find(ExtensionFinder.class));
        while (!newFinders.isEmpty()) {
            ExtensionFinder f = newFinders.remove(newFinders.size() - 1).getInstance();
            LOGGER.finer(() -> "found new ExtensionFinder " + f);

            ExtensionComponentSet ecs = ExtensionComponentSet.allOf(f).filtered();
            newFinders.addAll(ecs.find(ExtensionFinder.class));
            delta = ExtensionComponentSet.union(delta, ecs);
        }

        // we may not have found a new Extension finder but we may be using an extension finder that is extensible
        // e.g. hudson.ExtensionFinder.GuiceFinder is extensible by GuiceExtensionAnnotation which is done by the variant plugin
        // so lets give it one more chance.
        for (ExtensionFinder ef : finders) {
            LOGGER.finer(() -> "searching again in " + ef);
            delta = ExtensionComponentSet.union(delta, ef.refresh().filtered());
        }

        List<ExtensionList> listsToFireOnChangeListeners = new ArrayList<>();
        for (ExtensionList el : extensionLists.values()) {
            if (el.refresh(delta)) {
                listsToFireOnChangeListeners.add(el);
            }
        }
        for (ExtensionList el : descriptorLists.values()) {
            if (el.refresh(delta)) {
                listsToFireOnChangeListeners.add(el);
            }
        }
        // Refresh all extension lists before firing any listeners in case a listener would cause any new extension
        // lists to be forcibly loaded, which may lead to duplicate entries for the same extension object in a list.
        for (var el : listsToFireOnChangeListeners) {
            el.fireOnChangeListeners();
        }

        // TODO: we need some generalization here so that extension points can be notified when a refresh happens?
        for (ExtensionComponent<RootAction> ea : delta.find(RootAction.class)) {
            Action a = ea.getInstance();
            if (!actions.contains(a)) actions.add(a);
        }
    }

    /**
     * Returns the root {@link ACL}.
     *
     * @see AuthorizationStrategy#getRootACL()
     */
    @NonNull
    @Override
    public ACL getACL() {
        return authorizationStrategy.getRootACL();
    }

    /**
     * @return
     *      never null.
     */
    public AuthorizationStrategy getAuthorizationStrategy() {
        return authorizationStrategy;
    }

    /**
     * The strategy used to check the project names.
     * @return never {@code null}
     */
    public ProjectNamingStrategy getProjectNamingStrategy() {
        return projectNamingStrategy == null ? ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY : projectNamingStrategy;
    }

    /**
     * Returns true if Jenkins is quieting down.
     * <p>
     * No further jobs will be executed unless it
     * can be finished while other current pending builds
     * are still in progress.
     */
    @Exported
    public boolean isQuietingDown() {
        return quietDownInfo != null;
    }

    /**
     * Returns if the quietingDown is a safe restart.
     * @since 2.414
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    public boolean isPreparingSafeRestart() {
        QuietDownInfo quietDownInfo = this.quietDownInfo;
        if (quietDownInfo != null) {
            return quietDownInfo.isSafeRestart();
        }
        return false;
    }

    /**
     * Returns quiet down reason if it was indicated.
     * @return
     *      Reason if it was indicated. null otherwise
     *      @since 2.267
     */
    @Exported
    @CheckForNull
    public String getQuietDownReason() {
        final QuietDownInfo info = quietDownInfo;
        return info != null ? info.message : null;
    }

    /**
     * Returns true if the container initiated the termination of the web application.
     */
    public boolean isTerminating() {
        return terminating;
    }

    /**
     * Gets the initialization milestone that we've already reached.
     *
     * @return
     *      {@link InitMilestone#STARTED} even if the initialization hasn't been started, so that this method
     *      never returns null.
     */
    public InitMilestone getInitLevel() {
        return initLevel;
    }

    /**
     * Sets a number of executors.
     * @param n Number of executors
     * @throws IOException Failed to save the configuration
     * @throws IllegalArgumentException Negative value has been passed
     */
    public void setNumExecutors(/* @javax.annotation.Nonnegative*/ int n) throws IOException, IllegalArgumentException {
        if (n < 0) {
            throw new IllegalArgumentException("Incorrect field \"# of executors\": " + n + ". It should be a non-negative number.");
        }
        if (this.numExecutors != n) {
            this.numExecutors = n;
            updateComputers(this);
            save();
        }
    }



    /**
     * {@inheritDoc}.
     *
     * Note that the look up is case-insensitive.
     */
    @Override public TopLevelItem getItem(String name) throws AccessDeniedException {
        if (name == null)    return null;
        TopLevelItem item = items.get(name);
        if (item == null)
            return null;
        if (!item.hasPermission(Item.READ)) {
            if (item.hasPermission(Item.DISCOVER)) {
                throw new AccessDeniedException("Please login to access job " + name);
            }
            return null;
        }
        return item;
    }

    /**
     * Gets the item by its path name from the given context
     *
     * <p><strong>Path Names:</strong>
     * If the name starts from '/', like "/foo/bar/zot", then it's interpreted as absolute.
     * Otherwise, the name should be something like "foo/bar" and it's interpreted like
     * relative path name in the file system is, against the given context.
     *
     * <p>For compatibility, as a fallback when nothing else matches, a simple path
     * like {@code foo/bar} can also be treated with {@link #getItemByFullName}.
     *
     * @param context
     *      null is interpreted as {@link Jenkins}. Base 'directory' of the interpretation.
     * @since 1.406
     */
    public Item getItem(String pathName, ItemGroup context) {
        if (context == null)  context = this;
        if (pathName == null) return null;

        if (pathName.startsWith("/"))   // absolute
            return getItemByFullName(pathName);

        Object/*Item|ItemGroup*/ ctx = context;

        StringTokenizer tokens = new StringTokenizer(pathName, "/");
        while (tokens.hasMoreTokens()) {
            String s = tokens.nextToken();
            if (s.equals("..")) {
                if (ctx instanceof Item) {
                    ctx = ((Item) ctx).getParent();
                    continue;
                }

                ctx = null;    // can't go up further
                break;
            }
            if (s.equals(".")) {
                continue;
            }

            if (ctx instanceof ItemGroup g) {
                Item i = g.getItem(s);
                if (i == null || !i.hasPermission(Item.READ)) { // TODO consider DISCOVER
                    ctx = null;    // can't go up further
                    break;
                }
                ctx = i;
            } else {
                return null;
            }
        }

        if (ctx instanceof Item)
            return (Item) ctx;

        // fall back to the classic interpretation
        return getItemByFullName(pathName);
    }

    public final Item getItem(String pathName, Item context) {
        return getItem(pathName, context != null ? context.getParent() : null);
    }

    public final <T extends Item> T getItem(String pathName, ItemGroup context, @NonNull Class<T> type) {
        Item r = getItem(pathName, context);
        if (type.isInstance(r))
            return type.cast(r);
        return null;
    }

    public final <T extends Item> T getItem(String pathName, Item context, Class<T> type) {
        return getItem(pathName, context != null ? context.getParent() : null, type);
    }

    @Override
    public File getRootDirFor(TopLevelItem child) {
        return getRootDirFor(child.getName());
    }

    private File getRootDirFor(String name) {
        return new File(new File(getRootDir(), "jobs"), name);
    }

    /**
     * Gets the {@link Item} object by its full name.
     * Full names are like path names, where each name of {@link Item} is
     * combined by '/'.
     *
     * @return
     *      null if either such {@link Item} doesn't exist under the given full name,
     *      or it exists but it's no an instance of the given type.
     * @throws AccessDeniedException as per {@link ItemGroup#getItem}
     */
    public @CheckForNull <T extends Item> T getItemByFullName(@NonNull String fullName, Class<T> type) throws AccessDeniedException {
        StringTokenizer tokens = new StringTokenizer(fullName, "/");
        ItemGroup parent = this;

        if (!tokens.hasMoreTokens()) return null;    // for example, empty full name.

        while (true) {
            Item item = parent.getItem(tokens.nextToken());
            if (!tokens.hasMoreTokens()) {
                if (type.isInstance(item))
                    return type.cast(item);
                else
                    return null;
            }

            if (!(item instanceof ItemGroup))
                return null;    // this item can't have any children

            if (!item.hasPermission(Item.READ))
                return null; // TODO consider DISCOVER

            parent = (ItemGroup) item;
        }
    }

    public @CheckForNull Item getItemByFullName(String fullName) {
        return getItemByFullName(fullName, Item.class);
    }

    /**
     * Gets the user of the given name.
     *
     * @return the user of the given name (which may or may not be an id), if that person exists; else null
     * @see User#get(String,boolean)
     * @see User#getById(String, boolean)
     */
    public @CheckForNull User getUser(String name) {
        return User.get(name, User.ALLOW_USER_CREATION_VIA_URL && hasPermission(ADMINISTER));
    }

    @NonNull
    public synchronized TopLevelItem createProject(@NonNull TopLevelItemDescriptor type, @NonNull String name) throws IOException {
        return createProject(type, name, true);
    }

    @NonNull
    @Override
    public synchronized TopLevelItem createProject(@NonNull TopLevelItemDescriptor type, @NonNull String name, boolean notify) throws IOException {
        return itemGroupMixIn.createProject(type, name, notify);
    }

    /**
     * Overwrites the existing item by new one.
     *
     * <p>
     * This is a short cut for deleting an existing job and adding a new one.
     */
    public synchronized void putItem(TopLevelItem item) throws IOException, InterruptedException {
        String name = item.getName();
        TopLevelItem old = items.get(name);
        if (old == item)  return; // noop

        checkPermission(Item.CREATE);
        if (old != null)
            old.delete();
        items.put(name, item);
        ItemListener.fireOnCreated(item);
    }

    /**
     * Creates a new job.
     *
     * <p>
     * This version infers the descriptor from the type of the top-level item.
     *
     * @throws IllegalArgumentException
     *      if the project of the given name already exists.
     */
    @NonNull
    public synchronized <T extends TopLevelItem> T createProject(@NonNull Class<T> type, @NonNull String name) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptor) getDescriptorOrDie(type), name));
    }

    /**
     * Called by {@link Job#renameTo(String)} to update relevant data structure.
     * assumed to be synchronized on Jenkins by the caller.
     */
    @Override
    public void onRenamed(TopLevelItem job, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName, job);

        // For compatibility with old views:
        for (View v : views)
            v.onJobRenamed(job, oldName, newName);
    }

    /**
     * Called in response to {@link Job#doDoDelete(StaplerRequest2, StaplerResponse2)}
     */
    @Override
    public void onDeleted(TopLevelItem item) throws IOException {
        ItemListener.fireOnDeleted(item);

        items.remove(item.getName());
        // For compatibility with old views:
        for (View v : views)
            v.onJobRenamed(item, item.getName(), null);
    }

    @Override public boolean canAdd(TopLevelItem item) {
        return true;
    }

    @Override public synchronized <I extends TopLevelItem> I add(I item, String name) throws IOException, IllegalArgumentException {
        if (items.containsKey(name)) {
            throw new IllegalArgumentException("already an item '" + name + "'");
        }
        items.put(name, item);
        return item;
    }

    @Override public void remove(TopLevelItem item) throws IOException, IllegalArgumentException {
        items.remove(item.getName());
    }

    public FingerprintMap getFingerprintMap() {
        return fingerprintMap;
    }

    // if no finger print matches, display "not found page".
    @StaplerDispatchable
    public Object getFingerprint(String md5sum) throws IOException {
        Fingerprint r = fingerprintMap.get(md5sum);
        if (r == null)     return new NoFingerprintMatch(md5sum);
        else            return r;
    }

    /**
     * Gets a {@link Fingerprint} object if it exists.
     * Otherwise null.
     */
    public Fingerprint _getFingerprint(String md5sum) throws IOException {
        return fingerprintMap.get(md5sum);
    }

    /**
     * The file we save our configuration.
     */
    @Restricted(NoExternalUse.class)
    protected XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(root, "config.xml"));
    }

    @Override
    public int getNumExecutors() {
        return numExecutors;
    }

    @Override
    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode m) throws IOException {
        this.mode = m;
        save();
    }

    @Override
    public String getLabelString() {
        return fixNull(label).trim();
    }

    @Override
    public void setLabelString(String label) throws IOException {
        _setLabelString(label);
        save();
    }

    private void _setLabelString(String label) {
        this.label = label;
        if (Jenkins.getInstanceOrNull() != null) { // avoid on unit tests
            this.labelAtomSet = Collections.unmodifiableSet(Label.parse(label));
        }
    }

    @NonNull
    @Override
    public LabelAtom getSelfLabel() {
        if (nodeNameAndSelfLabelOverride != null) {
            return getLabelAtom(nodeNameAndSelfLabelOverride);
        }
        if (getRenameMigrationDone()) {
            return getLabelAtom("built-in");
        }
        return getLabelAtom("master");
    }

    /* package */ boolean getRenameMigrationDone() {
        if (nodeRenameMigrationNeeded == null) {
            /* This is exceptionally unlikely to occur since we replace 'null' with 'true' on save */
            return true;
        }
        return !nodeRenameMigrationNeeded;
    }

    /* package */ void performRenameMigration() throws IOException {
        this.nodeRenameMigrationNeeded = false;
        this.save();
        this.trimLabels();
    }

    @Override
    @NonNull
    public Computer createComputer() {
        return new Hudson.MasterComputer();
    }

    @Override
    public void load() throws IOException {
        XmlFile cfg = getConfigFile();
        if (cfg.exists()) {
            // reset some data that may not exist in the disk file
            // so that we can take a proper compensation action later.
            String originalPrimaryView = primaryView;
            List<View> originalViews = new ArrayList<>(views);
            primaryView = null;
            views.clear();
            try {
                // load from disk
                cfg.unmarshal(Jenkins.this);
            } catch (IOException | RuntimeException x) {
                primaryView = originalPrimaryView;
                views.clear();
                views.addAll(originalViews);
                throw x;
            }
        }
        // initialize views by inserting the default view if necessary
        // this is both for clean Jenkins and for backward compatibility.
        if (views.isEmpty() || primaryView == null) {
            View v = new AllView(AllView.DEFAULT_VIEW_NAME);
            setViewOwner(v);
            views.add(0, v);
            primaryView = v.getViewName();
        }
        primaryView = AllView.migrateLegacyPrimaryAllViewLocalizedName(views, primaryView);
        clouds.setOwner(this);
        configLoaded = true;
        try {
            checkRawBuildsDir(buildsDir);
            setBuildsAndWorkspacesDir();
            resetFilter(securityRealm, null);
        } catch (InvalidBuildsDir invalidBuildsDir) {
            throw new IOException(invalidBuildsDir);
        }
        updateComputers(this);
    }

    private void setBuildsAndWorkspacesDir() throws IOException, InvalidBuildsDir {
        boolean mustSave = false;
        String newBuildsDir = SystemProperties.getString(BUILDS_DIR_PROP);
        boolean freshStartup = STARTUP_MARKER_FILE.isOff();
        if (newBuildsDir != null && !buildsDir.equals(newBuildsDir)) {

            checkRawBuildsDir(newBuildsDir);
            Level level = freshStartup ? Level.INFO : Level.WARNING;
            LOGGER.log(level, "Changing builds directories from {0} to {1}. Beware that no automated data migration will occur.",
                       new String[]{buildsDir, newBuildsDir});
            buildsDir = newBuildsDir;
            mustSave = true;
        } else if (!isDefaultBuildDir()) {
            LOGGER.log(Level.INFO, "Using non default builds directories: {0}.", buildsDir);
        }

        String newWorkspacesDir = SystemProperties.getString(WORKSPACES_DIR_PROP);
        if (newWorkspacesDir != null && !workspaceDir.equals(newWorkspacesDir)) {
            Level level = freshStartup ? Level.INFO : Level.WARNING;
            LOGGER.log(level, "Changing workspaces directories from {0} to {1}. Beware that no automated data migration will occur.",
                       new String[]{workspaceDir, newWorkspacesDir});
            workspaceDir = newWorkspacesDir;
            mustSave = true;
        } else if (!isDefaultWorkspaceDir()) {
            LOGGER.log(Level.INFO, "Using non default workspaces directories: {0}.", workspaceDir);
        }

        if (mustSave) {
            save();
        }
    }

    /**
     * Checks the correctness of the newBuildsDirValue for use as {@link #buildsDir}.
     * @param newBuildsDirValue the candidate newBuildsDirValue for updating {@link #buildsDir}.
     */
    @VisibleForTesting
    /*private*/ static void checkRawBuildsDir(String newBuildsDirValue) throws InvalidBuildsDir {

        // do essentially what expandVariablesForDirectory does, without an Item
        String replacedValue = expandVariablesForDirectory(newBuildsDirValue,
                                                           "doCheckRawBuildsDir-Marker:foo",
                                                           Jenkins.get().getRootDir().getPath() + "/jobs/doCheckRawBuildsDir-Marker$foo");

        File replacedFile = new File(replacedValue);
        if (!replacedFile.isAbsolute()) {
            throw new InvalidBuildsDir(newBuildsDirValue + " does not resolve to an absolute path");
        }

        if (!replacedValue.contains("doCheckRawBuildsDir-Marker")) {
            throw new InvalidBuildsDir(newBuildsDirValue + " does not contain ${ITEM_FULL_NAME} or ${ITEM_ROOTDIR}, cannot distinguish between projects");
        }

        if (replacedValue.contains("doCheckRawBuildsDir-Marker:foo")) {
            // make sure platform can handle colon
            try {
                File tmp = File.createTempFile("Jenkins-doCheckRawBuildsDir", "foo:bar");
                Files.delete(tmp.toPath());
            } catch (IOException | InvalidPathException e) {
                throw (InvalidBuildsDir) new InvalidBuildsDir(newBuildsDirValue +  " contains ${ITEM_FULLNAME} but your system does not support it (JENKINS-12251). Use ${ITEM_FULL_NAME} instead").initCause(e);
            }
        }

        File d = new File(replacedValue);
        if (!d.isDirectory()) {
            // if dir does not exist (almost guaranteed) need to make sure nearest existing ancestor can be written to
            do {
                d = d.getParentFile();
            } while (!d.exists());
            if (!d.canWrite()) {
                throw new InvalidBuildsDir(newBuildsDirValue +  " does not exist and probably cannot be created");
            }
        }
    }

    private synchronized TaskBuilder loadTasks() throws IOException {
        File projectsDir = new File(root, "jobs");
        if (!projectsDir.getCanonicalFile().isDirectory() && !projectsDir.mkdirs()) {
            if (projectsDir.exists())
                throw new IOException(projectsDir + " is not a directory");
            throw new IOException("Unable to create " + projectsDir + "\nPermission issue? Please create this directory manually.");
        }
        File[] subdirs = projectsDir.listFiles();

        final Set<String> loadedNames = Collections.synchronizedSet(new HashSet<>());

        TaskGraphBuilder g = new TaskGraphBuilder();
        Handle loadJenkins = g.requires(EXTENSIONS_AUGMENTED).attains(SYSTEM_CONFIG_LOADED).add("Loading global config", new Executable() {
            @Override
            public void run(Reactor session) throws Exception {
                load();
                // if we are loading old data that doesn't have this field
                if (slaves != null && !slaves.isEmpty() && nodes.isLegacy()) {
                    nodes.setNodes(slaves);
                    slaves = null;
                } else {
                    nodes.load();
                }
            }
        });

        List<Handle> loadJobs = new ArrayList<>();
        for (final File subdir : subdirs) {
            loadJobs.add(g.requires(loadJenkins).requires(SYSTEM_CONFIG_ADAPTED).attains(JOB_LOADED).notFatal().add("Loading item " + subdir.getName(), new Executable() {
                @Override
                public void run(Reactor session) throws Exception {
                    if (!Items.getConfigFile(subdir).exists()) {
                        //Does not have job config file, so it is not a jenkins job hence skip it
                        return;
                    }
                    TopLevelItem item = (TopLevelItem) Items.load(Jenkins.this, subdir);
                    items.put(item.getName(), item);
                    loadedNames.add(item.getName());
                }
            }));
        }

        g.requires(loadJobs.toArray(new Handle[0])).attains(JOB_LOADED).add("Cleaning up obsolete items deleted from the disk", new Executable() {
            @Override
            public void run(Reactor reactor) {
                // anything we didn't load from disk, throw them away.
                // doing this after loading from disk allows newly loaded items
                // to inspect what already existed in memory (in case of reloading)

                // retainAll doesn't work well because of CopyOnWriteMap implementation, so remove one by one
                // hopefully there shouldn't be too many of them.
                for (String name : items.keySet()) {
                    if (!loadedNames.contains(name))
                        items.remove(name);
                }
            }
        });

        g.requires(JOB_CONFIG_ADAPTED).attains(COMPLETED).add("Finalizing set up", new Executable() {
            @Override
            public void run(Reactor session) throws Exception {
                rebuildDependencyGraph();

                { // recompute label objects - populates the labels mapping.
                    for (Node slave : nodes.getNodes())
                        // Note that not all labels are visible until the agents have connected.
                        slave.getAssignedLabels();
                    getAssignedLabels();
                }

                if (useSecurity != null && !useSecurity) {
                    // forced reset to the unsecure mode.
                    // this works as an escape hatch for people who locked themselves out.
                    authorizationStrategy = AuthorizationStrategy.UNSECURED;
                    setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
                } else {
                    // read in old data that doesn't have the security field set
                    if (authorizationStrategy == null) {
                        if (useSecurity == null)
                            authorizationStrategy = AuthorizationStrategy.UNSECURED;
                        else
                            authorizationStrategy = new LegacyAuthorizationStrategy();
                    }
                    if (securityRealm == null) {
                        if (useSecurity == null)
                            setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
                        else
                            setSecurityRealm(new LegacySecurityRealm());
                    }
                }

                // Allow the disabling system property to interfere here
                setCrumbIssuer(getCrumbIssuer());

                // auto register root actions
                for (Action a : getExtensionList(RootAction.class))
                    if (!actions.contains(a)) actions.add(a);

                setupWizard = ExtensionList.lookupSingleton(SetupWizard.class);
                getInstallState().initializeState();
            }
        });

        return g;
    }

    /**
     * Save the settings to a file.
     */
    @Override
    public synchronized void save() throws IOException {
        InitMilestone currentMilestone = initLevel;

        if (!configLoaded) {
            // someone is trying to save the config before all extensions are loaded (and possibly after as the task
            // may run in parallel with other tasks.  OMG...!!! this is generally very bad and can lead to dataloss
            LOGGER.log(Level.SEVERE,
                       "An attempt to save Jenkins'' global configuration before it has been loaded has been "
                       + "made during milestone " + currentMilestone
                       + ".  This is indicative of a bug in the caller and may lead to full or partial loss of "
                       + "configuration.",
                       new IllegalStateException("call trace"));
            // at this point we may want to terminate but the save may be called from a different thread and we
            // can not call System.halt() because we could be running in a container :(
            // for now just deny the save (the data will be replaced when we do load anyway
            throw new IllegalStateException("An attempt to save the global configuration was made before it was loaded");
        }

        if (BulkChange.contains(this)) {
            return;
        }
        if (currentMilestone == InitMilestone.COMPLETED) {
            LOGGER.log(FINE, "setting version {0} to {1}", new Object[] {version, VERSION});
            version = VERSION;
        } else {
            LOGGER.log(FINE, "refusing to set version {0} to {1} during {2}", new Object[] {version, VERSION, currentMilestone});
        }

        if (nodeRenameMigrationNeeded == null) {
            /*
            If we initialized this object bypassing #readResolve, i.e. a new instance,
            we need to persist this value, otherwise on restart we'd flag this as migration needed.
             */
            nodeRenameMigrationNeeded = false;
        }

        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
    }

    /**
     * Called to shut down the system.
     */
    public void cleanUp() {
        if (theInstance != this && theInstance != null) {
            LOGGER.log(Level.WARNING, "This instance is no longer the singleton, ignoring cleanUp()");
            return;
        }
        synchronized (Jenkins.class) {
            if (cleanUpStarted) {
                LOGGER.log(Level.WARNING, "Jenkins.cleanUp() already started, ignoring repeated cleanUp()");
                return;
            }
            cleanUpStarted = true;
        }
        try {
            getLifecycle().onStatusUpdate("Stopping Jenkins");

            final List<Throwable> errors = new ArrayList<>();

            fireBeforeShutdown(errors);

            _cleanUpRunTerminators(errors);

            terminating = true;

            final Set<Future<?>> pending = _cleanUpDisconnectComputers(errors);

            _cleanUpCancelDependencyGraphCalculation();

            _cleanUpInterruptReloadThread(errors);

            _cleanUpShutdownTriggers(errors);

            _cleanUpShutdownTimer(errors);

            _cleanUpShutdownTcpSlaveAgent(errors);

            _cleanUpShutdownPluginManager(errors);

            _cleanUpPersistQueue(errors);

            _cleanUpShutdownThreadPoolForLoad(errors);

            _cleanUpAwaitDisconnects(errors, pending);

            _cleanUpPluginServletFilters(errors);

            _cleanUpReleaseAllLoggers(errors);

            getLifecycle().onStatusUpdate("Jenkins stopped");

            if (!errors.isEmpty()) {
                StringBuilder message = new StringBuilder("Unexpected issues encountered during cleanUp: ");
                Iterator<Throwable> iterator = errors.iterator();
                message.append(iterator.next().getMessage());
                while (iterator.hasNext()) {
                    message.append("; ");
                    message.append(iterator.next().getMessage());
                }
                iterator = errors.iterator();
                RuntimeException exception = new RuntimeException(message.toString(), iterator.next());
                while (iterator.hasNext()) {
                    exception.addSuppressed(iterator.next());
                }
                throw exception;
            }
        } finally {
            theInstance = null;
            if (JenkinsJVM.isJenkinsJVM()) {
                JenkinsJVMAccess._setJenkinsJVM(oldJenkinsJVM);
            }
            ClassFilterImpl.unregister();
        }
    }

    private void fireBeforeShutdown(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Notifying termination");
        for (ItemListener l : ItemListener.all()) {
            try {
                l.onBeforeShutdown();
            } catch (OutOfMemoryError e) {
                // we should just propagate this, no point trying to log
                throw e;
            } catch (LinkageError e) {
                LOGGER.log(Level.WARNING, e, () -> "ItemListener " + l + ": " + e.getMessage());
                // safe to ignore and continue for this one
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, e, () -> "ItemListener " + l + ": " + e.getMessage());
                // save for later
                errors.add(e);
            }
        }
    }

    private void _cleanUpRunTerminators(List<Throwable> errors) {
        try {
            final TerminatorFinder tf = new TerminatorFinder(
                    pluginManager != null ? pluginManager.uberClassLoader : Thread.currentThread().getContextClassLoader());
            new Reactor(tf).execute(Runnable::run, new ReactorListener() {
                final Level level = Level.parse(SystemProperties.getString(Jenkins.class.getName() + "." + "termLogLevel", "FINE"));

                @Override
                public void onTaskStarted(Task t) {
                    LOGGER.log(level, "Started {0}", InitReactorRunner.getDisplayName(t));
                }

                @Override
                public void onTaskCompleted(Task t) {
                    LOGGER.log(level, "Completed {0}", InitReactorRunner.getDisplayName(t));
                }

                @Override
                public void onTaskFailed(Task t, Throwable err, boolean fatal) {
                    LOGGER.log(SEVERE, err, () -> "Failed " + InitReactorRunner.getDisplayName(t));
                }

                @Override
                public void onAttained(Milestone milestone) {
                    Level lv = level;
                    String s = "Attained " + milestone.toString();
                    if (milestone instanceof TermMilestone && !Main.isUnitTest) {
                        lv = Level.INFO; // noteworthy milestones --- at least while we debug problems further
                        s = milestone.toString();
                    }
                    LOGGER.log(lv, s);
                }
            });
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to execute termination", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to execute termination", e);
            // save for later
            errors.add(e);
        }
    }

    private Set<Future<?>> _cleanUpDisconnectComputers(final List<Throwable> errors) {
        LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Starting node disconnection");
        final Set<Future<?>> pending = new HashSet<>();
        // JENKINS-28840 we know we will be interrupting all the Computers so get the Queue lock once for all
        Queue.withLock(() -> {
            for (Computer c : getComputersCollection()) {
                try {
                    c.interrupt();
                    c.setNumExecutors(0);
                    if (Main.isUnitTest && c instanceof SlaveComputer sc) {
                        sc.closeLog(); // help TemporaryDirectoryAllocator.dispose esp. on Windows
                    }
                    pending.add(c.disconnect(null));
                } catch (OutOfMemoryError e) {
                    // we should just propagate this, no point trying to log
                    throw e;
                } catch (LinkageError e) {
                    LOGGER.log(Level.WARNING, e, () -> "Could not disconnect " + c + ": " + e.getMessage());
                    // safe to ignore and continue for this one
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, e, () -> "Could not disconnect " + c + ": " + e.getMessage());
                    // save for later
                    errors.add(e);
                }
            }
        });
        return pending;
    }

    private void _cleanUpInterruptReloadThread(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Interrupting reload thread");
        try {
            interruptReloadThread();
        } catch (SecurityException e) {
            LOGGER.log(WARNING, "Not permitted to interrupt reload thread", e);
            errors.add(e);
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to interrupt reload thread", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to interrupt reload thread", e);
            // save for later
            errors.add(e);
        }
    }

    private void _cleanUpShutdownTriggers(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Shutting down triggers");
        try {
            final java.util.Timer timer = Trigger.timer;
            if (timer != null) {
                final CountDownLatch latch = new CountDownLatch(1);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        timer.cancel();
                        latch.countDown();
                    }
                }, 0);
                if (latch.await(10, TimeUnit.SECONDS)) {
                    LOGGER.log(Level.FINE, "Triggers shut down successfully");
                } else {
                    timer.cancel();
                    LOGGER.log(Level.INFO, "Gave up waiting for triggers to finish running");
                }
            }
            Trigger.timer = null;
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to shut down triggers", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to shut down triggers", e);
            // save for later
            errors.add(e);
        }
    }

    private void _cleanUpShutdownTimer(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Shutting down timer");
        try {
            Timer.shutdown();
        } catch (SecurityException e) {
            LOGGER.log(WARNING, "Not permitted to shut down Timer", e);
            errors.add(e);
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to shut down Timer", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to shut down Timer", e);
            // save for later
            errors.add(e);
        }
    }

    private void _cleanUpShutdownTcpSlaveAgent(List<Throwable> errors) {
        if (tcpSlaveAgentListener != null) {
            LOGGER.log(FINE, "Shutting down TCP/IP agent listener");
            try {
                tcpSlaveAgentListener.shutdown();
            } catch (OutOfMemoryError e) {
                // we should just propagate this, no point trying to log
                throw e;
            } catch (LinkageError e) {
                LOGGER.log(SEVERE, "Failed to shut down TCP/IP agent listener", e);
                // safe to ignore and continue for this one
            } catch (Throwable e) {
                LOGGER.log(SEVERE, "Failed to shut down TCP/IP agent listener", e);
                // save for later
                errors.add(e);
            }
        }
    }

    private void _cleanUpShutdownPluginManager(List<Throwable> errors) {
        if (pluginManager != null) { // be defensive. there could be some ugly timing related issues
            LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Stopping plugin manager");
            try {
                pluginManager.stop();
            } catch (OutOfMemoryError e) {
                // we should just propagate this, no point trying to log
                throw e;
            } catch (LinkageError e) {
                LOGGER.log(SEVERE, "Failed to stop plugin manager", e);
                // safe to ignore and continue for this one
            } catch (Throwable e) {
                LOGGER.log(SEVERE, "Failed to stop plugin manager", e);
                // save for later
                errors.add(e);
            }
        }
    }

    private void _cleanUpPersistQueue(List<Throwable> errors) {
        if (getRootDir().exists()) {
            // if we are aborting because we failed to create JENKINS_HOME,
            // don't try to save. JENKINS-536
            LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Persisting build queue");
            try {
                getQueue().save();
            } catch (OutOfMemoryError e) {
                // we should just propagate this, no point trying to log
                throw e;
            } catch (LinkageError e) {
                LOGGER.log(SEVERE, "Failed to persist build queue", e);
                // safe to ignore and continue for this one
            } catch (Throwable e) {
                LOGGER.log(SEVERE, "Failed to persist build queue", e);
                // save for later
                errors.add(e);
            }
        }
    }

    private void _cleanUpShutdownThreadPoolForLoad(List<Throwable> errors) {
        LOGGER.log(FINE, "Shutting down Jenkins load thread pool");
        try {
            threadPoolForLoad.shutdown();
        } catch (SecurityException e) {
            LOGGER.log(WARNING, "Not permitted to shut down Jenkins load thread pool", e);
            errors.add(e);
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to shut down Jenkins load thread pool", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to shut down Jenkins load thread pool", e);
            // save for later
            errors.add(e);
        }
    }

    private void _cleanUpAwaitDisconnects(List<Throwable> errors, Set<Future<?>> pending) {
        if (!pending.isEmpty()) {
            LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Waiting for node disconnection completion");
        }
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        for (Future<?> f : pending) {
            try {
                long remaining = end - System.nanoTime();
                if (remaining <= 0) {
                    LOGGER.warning("Ran out of time waiting for agents to disconnect");
                    break;
                }
                f.get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;  // someone wants us to die now. quick!
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Failed to shut down remote computer connection cleanly", e);
            } catch (TimeoutException e) {
                LOGGER.log(Level.WARNING, "Failed to shut down remote computer connection within 10 seconds", e);
            } catch (OutOfMemoryError e) {
                // we should just propagate this, no point trying to log
                throw e;
            } catch (LinkageError e) {
                LOGGER.log(Level.WARNING, "Failed to shut down remote computer connection", e);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected error while waiting for remote computer connection disconnect", e);
                errors.add(e);
            }
        }
    }

    private void _cleanUpPluginServletFilters(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Stopping filters");
        try {
            PluginServletFilter.cleanUp();
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to stop filters", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to stop filters", e);
            // save for later
            errors.add(e);
        }
    }

    private void _cleanUpReleaseAllLoggers(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Releasing all loggers");
        try {
            LogFactory.releaseAll();
        } catch (OutOfMemoryError e) {
            // we should just propagate this, no point trying to log
            throw e;
        } catch (LinkageError e) {
            LOGGER.log(SEVERE, "Failed to release all loggers", e);
            // safe to ignore and continue for this one
        } catch (Throwable e) {
            LOGGER.log(SEVERE, "Failed to release all loggers", e);
            // save for later
            errors.add(e);
        }
    }

    private void _cleanUpCancelDependencyGraphCalculation() {
        synchronized (dependencyGraphLock) {
            LOGGER.log(Level.FINE, "Canceling internal dependency graph calculation");
            if (scheduledFutureDependencyGraph != null && !scheduledFutureDependencyGraph.isDone()) {
                scheduledFutureDependencyGraph.cancel(true);
            }
            if (calculatingFutureDependencyGraph != null && !calculatingFutureDependencyGraph.isDone()) {
                calculatingFutureDependencyGraph.cancel(true);
            }
        }
    }

    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            String url = a.getUrlName();
            if (url == null)  continue;
            if (url.equals(token) || url.equals('/' + token))
                return a;
        }
        for (Action a : getManagementLinks())
            if (Objects.equals(a.getUrlName(), token))
                return a;
        return null;
    }


//
//
// actions
//
//
    /**
     * Accepts submission from the configuration page.
     */
    @POST
    public synchronized void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        try (BulkChange bc = new BulkChange(this)) {
            checkPermission(MANAGE);

            JSONObject json = req.getSubmittedForm();

            systemMessage = Util.nullify(req.getParameter("system_message"));

            boolean result = true;
            for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigUnclassified())
                result &= configureDescriptor(req, json, d);

            save();
            updateComputers(this);
            if (result)
                FormApply.success(req.getContextPath() + '/').generateResponse(req, rsp, null);
            else
                FormApply.success("configure").generateResponse(req, rsp, null);    // back to config

            bc.commit();
        }
    }

    /**
     * Gets the {@link CrumbIssuer} currently in use.
     *
     * @return null if none is in use.
     */
    @CheckForNull
    public CrumbIssuer getCrumbIssuer() {
        return GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION ? null : crumbIssuer;
    }

    public void setCrumbIssuer(CrumbIssuer issuer) {
        crumbIssuer = issuer;
    }

    public synchronized void doTestPost(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.sendRedirect("foo");
    }

    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d) throws FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    /**
     * Accepts the new description.
     */
    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    @RequirePOST
    public synchronized HttpRedirect doQuietDown() {
        try {
            return doQuietDown(false, 0, null);
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e); // impossible
        }
    }

    /**
     * Quiet down Jenkins - preparation for a restart
     * Presented for compatibility.
     *
     * @param block Block until the system really quiets down and no builds are running
     * @param timeout If non-zero, only block up to the specified number of milliseconds
     * @deprecated since 2.267; use {@link #doQuietDown(boolean, int, String, boolean)} instead.
     */
    @Deprecated
    public synchronized HttpRedirect doQuietDown(boolean block, int timeout) {
        try {
            return doQuietDown(block, timeout, null);
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e); // impossible
        }
    }

    /**
     * Quiet down Jenkins - preparation for a restart
     *
     * @param block Block until the system really quiets down and no builds are running
     * @param timeout If non-zero, only block up to the specified number of milliseconds
     * @param message Quiet reason that will be visible to user
     * @deprecated use {@link #doQuietDown(boolean, int, String, boolean)} instead.
     */
    @Deprecated(since = "2.414")
    public HttpRedirect doQuietDown(boolean block,
                                    int timeout,
                                    @CheckForNull String message) throws InterruptedException, IOException {

        return doQuietDown(block, timeout, message, false);
    }

    /**
     * Quiet down Jenkins - preparation for a restart
     *
     * @param block Block until the system really quiets down and no builds are running
     * @param timeout If non-zero, only block up to the specified number of milliseconds
     * @param message Quiet reason that will be visible to user
     * @param safeRestart If the quietDown is for a safeRestart
     * @since 2.414
     */
    @RequirePOST
    public HttpRedirect doQuietDown(@QueryParameter boolean block,
                                    @QueryParameter int timeout,
                                    @QueryParameter @CheckForNull String message,
                                    @QueryParameter boolean safeRestart) throws InterruptedException, IOException {
        synchronized (this) {
            checkPermission(MANAGE);
            quietDownInfo = new QuietDownInfo(message, safeRestart);
        }
        if (block) {
            long waitUntil = timeout;
            if (timeout > 0) waitUntil += System.currentTimeMillis();
            while (isQuietingDown()
                   && (timeout <= 0 || System.currentTimeMillis() < waitUntil)
                   && !RestartListener.isAllReady()) {
                TimeUnit.SECONDS.sleep(1);
            }
        }
        return new HttpRedirect(".");
    }

    /**
     * Cancel previous quiet down Jenkins - preparation for a restart
     */
    @RequirePOST
    public synchronized HttpRedirect doCancelQuietDown() {
        checkPermission(MANAGE);
        quietDownInfo = null;
        getQueue().scheduleMaintenance();
        return new HttpRedirect(".");
    }

    @POST
    public HttpResponse doToggleCollapse() throws ServletException, IOException {
        final StaplerRequest2 request = Stapler.getCurrentRequest2();
        final String paneId = request.getParameter("paneId");

        PaneStatusProperties.forCurrentUser().toggleCollapsed(paneId);

        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Backward compatibility. Redirect to the thread dump.
     */
    // TODO annotate @GET once baseline includes Stapler version XXX
    public void doClassicThreadDump(StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.sendRedirect2("threadDump");
    }

    /**
     * Obtains the thread dump of all agents (including the controller/built-in node.)
     *
     * <p>
     * Since this is for diagnostics, it has a built-in precautionary measure against hang agents.
     */
    public Map<String, Map<String, String>> getAllThreadDumps() throws IOException, InterruptedException {
        checkPermission(ADMINISTER);

        // issue the requests all at once
        Map<String, Future<Map<String, String>>> future = new HashMap<>();

        for (Computer c : getComputers()) {
            try {
                future.put(c.getName(), RemotingDiagnostics.getThreadDumpAsync(c.getChannel()));
            } catch (Exception e) {
                LOGGER.info("Failed to get thread dump for node " + c.getName() + ": " + e.getMessage());
            }
        }
        if (toComputer() == null) {
            future.put("master", RemotingDiagnostics.getThreadDumpAsync(FilePath.localChannel)); // TODO(terminology) Built-in node? Controller? How is this used?
        }

        // if the result isn't available in 5 sec, ignore that.
        // this is a precaution against hang nodes
        long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);

        Map<String, Map<String, String>> r = new HashMap<>();
        for (Map.Entry<String, Future<Map<String, String>>> e : future.entrySet()) {
            try {
                r.put(e.getKey(), e.getValue().get(endTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS));
            } catch (Exception x) {
                r.put(e.getKey(), Map.of("Failed to retrieve thread dump", Functions.printThrowable(x)));
            }
        }
        return Collections.unmodifiableSortedMap(new TreeMap<>(r));
    }

    @Override
    @RequirePOST
    public synchronized TopLevelItem doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        return itemGroupMixIn.createTopLevelItem(req, rsp);
    }

    /**
     * @since 1.319
     */
    @Override
    public TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        return itemGroupMixIn.createProjectFromXML(name, xml);
    }


    @Override
    public <T extends TopLevelItem> T copy(T src, String name) throws IOException {
        return itemGroupMixIn.copy(src, name);
    }

    // a little more convenient overloading that assumes the caller gives us the right type
    // (or else it will fail with ClassCastException)
    public <T extends AbstractProject<?, ?>> T copy(T src, String name) throws IOException {
        return (T) copy((TopLevelItem) src, name);
    }

    @POST
    public synchronized void doCreateView(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
        checkPermission(View.CREATE);
        addView(View.create(req, rsp, this));
    }

    /**
     * Check if the given name is suitable as a name
     * for job, view, etc.
     *
     * @throws Failure
     *      if the given name is not good
     */
    public static void checkGoodName(String name) throws Failure {
        if (name == null || name.isEmpty())
            throw new Failure(Messages.Hudson_NoName());

        if (".".equals(name.trim()))
            throw new Failure(Messages.Jenkins_NotAllowedName("."));
        if ("..".equals(name.trim()))
            throw new Failure(Messages.Jenkins_NotAllowedName(".."));
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new Failure(Messages.Hudson_ControlCodeNotAllowed(toPrintableName(name)));
            }
            if ("?*/\\%!@#$^&|<>[]:;".indexOf(ch) != -1)
                throw new Failure(Messages.Hudson_UnsafeChar(ch));
        }

        if (SystemProperties.getBoolean(NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP, true)) {
            // SECURITY-2424 on Windows the trailing dot can be used to create ambiguity
            if (name.trim().endsWith(".")) {
                throw new Failure(Messages.Hudson_TrailingDot());
            }
        }

        // looks good
    }

    private static String toPrintableName(String name) {
        StringBuilder printableName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch))
                printableName.append("\\u").append((int) ch).append(';');
            else
                printableName.append(ch);
        }
        return printableName.toString();
    }

    /**
     * Checks if the user was successfully authenticated.
     *
     * @see BasicAuthenticationFilter
     */
    public void doSecured(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        // TODO fire something in SecurityListener? (seems to be used only for REST calls when LegacySecurityRealm is active)

        if (req.getUserPrincipal() == null) {
            // authentication must have failed
            rsp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // the user is now authenticated, so send him back to the target
        String path = req.getContextPath() + req.getOriginalRestOfPath();
        String q = req.getQueryString();
        if (q != null)
            path += '?' + q;

        rsp.sendRedirect2(path);
    }

    /**
     * Called once the user logs in. Just forward to the top page.
     * Used only by {@link LegacySecurityRealm}.
     */
    public void doLoginEntry(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.getUserPrincipal() == null) {
            rsp.sendRedirect2("noPrincipal");
            return;
        }

        // TODO fire something in SecurityListener?

        String from = req.getParameter("from");
        if (from != null && from.startsWith("/") && !from.equals("/loginError")) {
            rsp.sendRedirect2(from);    // I'm bit uncomfortable letting users redirected to other sites, make sure the URL falls into this domain
            return;
        }

        /* TODO unclear what the Spring Security equivalent is; check AbstractAuthenticationProcessingFilter, SavedRequest
        String url = AbstractProcessingFilter.obtainFullRequestUrl(req);
        if (url!=null) {
            // if the login redirect is initiated by Acegi
            // this should send the user back to where s/he was from.
            rsp.sendRedirect2(url);
            return;
        }
        */

        rsp.sendRedirect2(".");
    }

    /**
     * Logs out the user.
     *
     * @since 2.475
     */
    public void doLogout(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String user = getAuthentication2().getName();
        securityRealm.doLogout(req, rsp);
        SecurityListener.fireLoggedOut(user);
    }

    /**
     * @deprecated use {@link #doLogout(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doLogout(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doLogout(req != null ? StaplerRequest.toStaplerRequest2(req) : null, rsp != null ? StaplerResponse.toStaplerResponse2(rsp) : null);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Serves jar files for inbound agents.
     */
    public Slave.JnlpJar getJnlpJars(String fileName) {
        return new Slave.JnlpJar(fileName);
    }

    public Slave.JnlpJar doJnlpJars(StaplerRequest2 req) {
        return new Slave.JnlpJar(req.getRestOfPath().substring(1));
    }

    /**
     * Reloads the configuration.
     */
    @RequirePOST
    public synchronized HttpResponse doReload() throws IOException {
        checkPermission(MANAGE);
        getLifecycle().onReload(getAuthentication2().getName(), null);

        // engage "loading ..." UI and then run the actual task in a separate thread
        WebApp.get(getServletContext()).setApp(new HudsonIsLoading());

        new Thread("Jenkins config reload thread") {
            @Override
            public void run() {
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                    reload();
                    getLifecycle().onReady();
                } catch (Exception e) {
                    LOGGER.log(SEVERE, "Failed to reload Jenkins config", e);
                    new JenkinsReloadFailed(e).publish(getServletContext(), root);
                }
            }
        }.start();

        return HttpResponses.redirectViaContextPath("/");
    }

    /**
     * Reloads the configuration synchronously.
     * Beware that this calls neither {@link ItemListener#onLoaded} nor {@link Initializer}s.
     */
    public void reload() throws IOException, InterruptedException, ReactorException {
        queue.save();
        executeReactor(null, loadTasks());

        // Ensure we reached the final initialization state. Log the error otherwise
        if (initLevel != InitMilestone.COMPLETED) {
            LOGGER.log(SEVERE, "Jenkins initialization has not reached the COMPLETED initialization milestone after the configuration reload. " +
                            "Current state: {0}. " +
                            "It may cause undefined incorrect behavior in Jenkins plugin relying on this state. " +
                            "It is likely an issue with the Initialization task graph. " +
                            "Example: usage of @Initializer(after = InitMilestone.COMPLETED) in a plugin (JENKINS-37759). " +
                            "Please create a bug in Jenkins bugtracker.",
                    initLevel);
        }

        User.reload();
        queue.load();
        WebApp.get(getServletContext()).setApp(this);
    }

    /**
     * Do a finger-print check.
     */
    @RequirePOST
    public void doDoFingerprintCheck(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        // Parse the request
        try (MultipartFormDataParser p = new MultipartFormDataParser(req, 10)) {
            if (isUseCrumbs() && !getCrumbIssuer().validateCrumb(req, p)) {
                // TODO investigate whether this check can be removed
                rsp.sendError(HttpServletResponse.SC_FORBIDDEN, "No crumb found");
            }
            rsp.sendRedirect2(req.getContextPath() + "/fingerprint/" +
                Util.getDigestOf(p.getFileItem2("name").getInputStream()) + '/');
        }
    }

    /**
     * For debugging. Expose URL to perform GC.
     */
    @RequirePOST
    @SuppressFBWarnings(value = "DM_GC", justification = "for debugging")
    public void doGc(StaplerResponse2 rsp) throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        System.gc();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("GCed");
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws IOException, JellyException {
        ContextMenu menu = new ContextMenu().from(this, request, response);
        for (MenuItem i : menu.items) {
            if (i.url.equals(request.getContextPath() + "/manage")) {
                // add "Manage Jenkins" subitems
                i.subMenu = new ContextMenu().from(ExtensionList.lookupSingleton(ManageJenkinsAction.class), request, response, "index");
            }
        }
        return menu;
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ContextMenu menu = new ContextMenu();
        for (View view : getViews()) {
            menu.add(view.getViewUrl(), view.getDisplayName());
        }
        return menu;
    }

    /**
     * Obtains the heap dump.
     */
    public HeapDump getHeapDump() throws IOException {
        return new HeapDump(this, FilePath.localChannel);
    }

    /**
     * Simulates OutOfMemoryError.
     * Useful to make sure OutOfMemoryHeapDump setting.
     */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @RequirePOST
    public void doSimulateOutOfMemory() throws IOException {
        checkPermission(ADMINISTER);

        System.out.println("Creating artificial OutOfMemoryError situation");
        List<Object> args = new ArrayList<>();
        //noinspection InfiniteLoopStatement
        while (true)
            args.add(new byte[1024 * 1024]);
    }

    /**
     * Binds /userContent/... to $JENKINS_HOME/userContent.
     */
    public DirectoryBrowserSupport doUserContent() {
        return new DirectoryBrowserSupport(this, getRootPath().child("userContent"), "User content", "folder.png", true);
    }

    /**
     * Perform a restart of Jenkins, if we can.
     *
     * This first replaces "app" to {@link HudsonIsRestarting}
     */
    @CLIMethod(name = "restart")
    public void doRestart(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(MANAGE);
        if (req != null && req.getMethod().equals("GET")) {
            req.getView(this, "_restart.jelly").forward(req, rsp);
            return;
        }

        if (req == null || req.getMethod().equals("POST")) {
            restart();
        }

        if (rsp != null) {
            rsp.sendRedirect2(".");
        }
    }

    /**
     * Serve a custom 404 error page, configured in web.xml.
     */
    @WebMethod(name = "404")
    @Restricted(NoExternalUse.class)
    public void generateNotFoundResponse(StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
        if (ResourceDomainConfiguration.isResourceRequest(req)) {
            rsp.forward(this, "_404_simple", req);
        } else {
            final Object attribute = req.getAttribute(ErrorAttributeFilter.USER_ATTRIBUTE);
            if (attribute instanceof Authentication) {
                try (ACLContext unused = ACL.as2((Authentication) attribute)) {
                    rsp.forward(this, "_404", req);
                }
            } else {
                rsp.forward(this, "_404", req);
            }
        }
    }

    /**
     * Queues up a safe restart of Jenkins.
     * Builds that cannot continue while the controller is not running have to finish or pause before it can proceed.
     * No new builds will be started. No new jobs are accepted.
     *
     * @deprecated use {@link #doSafeRestart(StaplerRequest2, String)} instead.
     *
     */
    @Deprecated(since = "2.414")
    public HttpResponse doSafeRestart(StaplerRequest req) throws IOException, ServletException, RestartNotSupportedException {
        return doSafeRestart(req != null ? StaplerRequest.toStaplerRequest2(req) : null, null);
    }

    /**
     * Queues up a safe restart of Jenkins. Jobs have to finish or pause before it can proceed. No new jobs are accepted.
     *
     * @since 2.475
     */
    public HttpResponse doSafeRestart(StaplerRequest2 req, @QueryParameter("message") String message) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(MANAGE);
        if (req != null && req.getMethod().equals("GET")) {
            return HttpResponses.forwardToView(this, "_safeRestart.jelly");
        }

        if (req != null && req.getParameter("cancel") != null) {
            return doCancelQuietDown();
        }

        if (req == null || req.getMethod().equals("POST")) {
            safeRestart(message);
        }

        return HttpResponses.redirectToDot();
    }

    /**
     * @deprecated use {@link #doSafeRestart(StaplerRequest2, String)}
     * @since 2.414
     */
    @Deprecated
    @StaplerNotDispatchable
    public HttpResponse doSafeRestart(StaplerRequest req, @QueryParameter("message") String message) throws IOException, javax.servlet.ServletException, RestartNotSupportedException {
        try {
            return doSafeRestart(req != null ? StaplerRequest.toStaplerRequest2(req) : null, message);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private static Lifecycle restartableLifecycle() throws RestartNotSupportedException {
        if (Main.isUnitTest) {
            throw new RestartNotSupportedException("Restarting the controller JVM is not supported in JenkinsRule-based tests");
        }
        Lifecycle lifecycle = Lifecycle.get();
        lifecycle.verifyRestartable();
        return lifecycle;
    }

    /**
     * Performs a restart.
     */
    public void restart() throws RestartNotSupportedException {
        final Lifecycle lifecycle = restartableLifecycle();
        getServletContext().setAttribute("app", new HudsonIsRestarting());

        new Thread("restart thread") {
            final String exitUser = getAuthentication2().getName();
            @Override
            public void run() {
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                    // give some time for the browser to load the "reloading" page
                    lifecycle.onStatusUpdate("Restart in 5 seconds");
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                    lifecycle.onStop(exitUser, null);
                    Listeners.notify(RestartListener.class, true, RestartListener::onRestart);
                    lifecycle.restart();
                } catch (InterruptedException | InterruptedIOException e) {
                    LOGGER.log(Level.WARNING, "Interrupted while trying to restart Jenkins", e);
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Jenkins", e);
                }
            }
        }.start();
    }

    /**
     * Queues up a restart to be performed once there are no builds currently running.
     * @since 1.332
     * @deprecated use {@link #safeRestart(String)} instead.
     */
    @Deprecated(since = "2.414")
    public void safeRestart() throws RestartNotSupportedException {
        safeRestart(null);
    }

    /**
     * Queues up a restart to be performed once there are no builds currently running.
     * @param message the message to show to users in the shutdown banner.
     * @since 2.414
     */
    public void safeRestart(String message) throws RestartNotSupportedException {
        final Lifecycle lifecycle = restartableLifecycle();
        // Quiet down so that we won't launch new builds.
        quietDownInfo = new QuietDownInfo(message, true);

        new Thread("safe-restart thread") {
            final String exitUser = getAuthentication2().getName();
            @Override
            public void run() {
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {

                    // Wait 'til we have no active executors.
                    doQuietDown(true, 0, message, true);
                    // Make sure isQuietingDown is still true.
                    if (isQuietingDown()) {
                        getServletContext().setAttribute("app", new HudsonIsRestarting(true));
                        // give some time for the browser to load the "reloading" page
                        lifecycle.onStatusUpdate("Restart in 10 seconds");
                        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                        lifecycle.onStop(exitUser, null);
                        Listeners.notify(RestartListener.class, true, RestartListener::onRestart);
                        lifecycle.restart();
                    } else {
                        lifecycle.onStatusUpdate("Safe-restart mode cancelled");
                    }
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Jenkins", e);
                }
            }
        }.start();
    }

    @Extension @Restricted(NoExternalUse.class)
    public static class MasterRestartNotifyier extends RestartListener {

        @Override
        public void onRestart() {
            Computer computer = Jenkins.get().toComputer();
            if (computer == null) return;
            RestartCause cause = new RestartCause();
            Listeners.notify(ComputerListener.class, true, l -> l.onOffline(computer, cause));
        }

        @Override
        public boolean isReadyToRestart() throws IOException, InterruptedException {
            return true;
        }

        private static class RestartCause extends OfflineCause.SimpleOfflineCause {
            protected RestartCause() {
                super(Messages._Jenkins_IsRestarting());
            }
        }
    }

    /**
     * Shutdown the system.
     * @since 1.161
     */
    @CLIMethod(name = "shutdown")
    @RequirePOST
    public void doExit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        final String exitUser = getAuthentication2().getName();
        final String exitAddr = req != null ? req.getRemoteAddr() : null;
        if (rsp != null) {
            rsp.setStatus(HttpServletResponse.SC_OK);
            rsp.setContentType("text/plain");
            try (PrintWriter w = rsp.getWriter()) {
                w.println("Shutting down");
            }
        }

        new Thread("exit thread") {
            @Override
            @SuppressFBWarnings(value = "DM_EXIT", justification = "Exit is really intended.")
            public void run() {
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                    getLifecycle().onStop(exitUser, exitAddr);

                    cleanUp();
                    System.exit(0);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Failed to shut down Jenkins", e);
                }
            }
        }.start();
    }

    /**
     * Shutdown the system safely.
     * @since 1.332
     */
    @CLIMethod(name = "safe-shutdown")
    @RequirePOST
    public HttpResponse doSafeExit(StaplerRequest2 req) throws IOException {
        checkPermission(ADMINISTER);
        quietDownInfo = new QuietDownInfo();
        final String exitUser = getAuthentication2().getName();
        final String exitAddr = req != null ? req.getRemoteAddr() : null;
        new Thread("safe-exit thread") {
            @Override
            @SuppressFBWarnings(value = "DM_EXIT", justification = "Exit is really intended.")
            public void run() {
                try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                    getLifecycle().onStop(exitUser, exitAddr);
                    // Wait 'til we have no active executors.
                    doQuietDown(true, 0, null);
                    // Make sure isQuietingDown is still true.
                    if (isQuietingDown()) {
                        cleanUp();
                        System.exit(0);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to shut down Jenkins", e);
                }
            }
        }.start();

        return HttpResponses.plainText("Shutting down as soon as all jobs are complete");
    }

    /**
     * @deprecated use {@link #doSafeExit(StaplerRequest2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public HttpResponse doSafeExit(StaplerRequest req) throws IOException {
        return doSafeExit(req != null ? StaplerRequest.toStaplerRequest2(req) : null);
    }

    /**
     * Gets the {@link Authentication} object that represents the user
     * associated with the current request.
     * @since 2.266
     */
    public static @NonNull Authentication getAuthentication2() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        // on Tomcat while serving the login page, this is null despite the fact
        // that we have filters. Looking at the stack trace, Tomcat doesn't seem to
        // run the request through filters when this is the login request.
        // see http://www.nabble.com/Matrix-authorization-problem-tp14602081p14886312.html
        if (a == null)
            a = ANONYMOUS2;
        return a;
    }

    /**
     * @deprecated use {@link #getAuthentication2}
     */
    @Deprecated
    public static @NonNull org.acegisecurity.Authentication getAuthentication() {
        return org.acegisecurity.Authentication.fromSpring(getAuthentication2());
    }

    /**
     * For system diagnostics.
     * Run arbitrary Groovy script.
     */
    public void doScript(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        _doScript(req, rsp, req.getView(this, "_script.jelly"), FilePath.localChannel, getACL());
    }

    /**
     * @deprecated use {@link #doScript(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            _doScript(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), StaplerRequest.toStaplerRequest2(req).getView(this, "_script.jelly"), FilePath.localChannel, getACL());
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Run arbitrary Groovy script and return result as plain text.
     */
    public void doScriptText(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        _doScript(req, rsp, req.getView(this, "_scriptText.jelly"), FilePath.localChannel, getACL());
    }

    /**
     * @deprecated use {@link #doScriptText(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doScriptText(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            _doScript(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), StaplerRequest.toStaplerRequest2(req).getView(this, "_scriptText.jelly"), FilePath.localChannel, getACL());
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * @since 2.475
     */
    public static void _doScript(StaplerRequest2 req, StaplerResponse2 rsp, RequestDispatcher view, VirtualChannel channel, ACL acl) throws IOException, ServletException {
        // ability to run arbitrary script is dangerous
        acl.checkPermission(ADMINISTER);

        String text = req.getParameter("script");
        if (text != null) {
            if (!"POST".equals(req.getMethod())) {
                throw HttpResponses.error(HttpURLConnection.HTTP_BAD_METHOD, "requires POST");
            }

            if (channel == null) {
                throw HttpResponses.error(HttpURLConnection.HTTP_NOT_FOUND, "Node is offline");
            }

            try {
                req.setAttribute("output",
                        RemotingDiagnostics.executeGroovy(text, channel));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServletException(e);
            }
        }

        view.forward(req, rsp);
    }

    /**
     * @deprecated use {@link #_doScript(StaplerRequest2, StaplerResponse2, RequestDispatcher, VirtualChannel, ACL)}
     * @since 1.509.1
     */
    @Deprecated
    public static void _doScript(StaplerRequest req, StaplerResponse rsp, javax.servlet.RequestDispatcher view, VirtualChannel channel, ACL acl) throws IOException, javax.servlet.ServletException {
        try {
            _doScript(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), RequestDispatcherWrapper.toJakartaRequestDispatcher(view), channel, acl);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Evaluates the Jelly script submitted by the client.
     *
     * This is useful for system administration as well as unit testing.
     */
    @RequirePOST
    public void doEval(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(ADMINISTER);
        req.getWebApp().getDispatchValidator().allowDispatch(req, rsp);
        try {
            MetaClass mc = req.getWebApp().getMetaClass(getClass());
            Script script = mc.classLoader.loadTearOff(JellyClassLoaderTearOff.class).createContext().compileScript(new InputSource(req.getReader()));
            new JellyRequestDispatcher(this, script).forward(req, rsp);
        } catch (JellyException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Sign up for the user account.
     */
    public void doSignup(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (getSecurityRealm().allowsSignup()) {
            req.getView(getSecurityRealm(), "signup.jelly").forward(req, rsp);
            return;
        }
        req.getView(SecurityRealm.class, "signup.jelly").forward(req, rsp);
    }

    /**
     * Changes the icon size by changing the cookie
     */
    @SuppressFBWarnings(value = "INSECURE_COOKIE", justification = "TODO needs triage")
    public void doIconSize(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String qs = req.getQueryString();
        if (qs == null)
            throw new ServletException();
        Cookie cookie = new Cookie("iconSize", Functions.validateIconSize(qs));
        cookie.setMaxAge(/* ~4 mo. */9999999); // JENKINS-762
        cookie.setSecure(req.isSecure());
        cookie.setHttpOnly(true);
        rsp.addCookie(cookie);
        String ref = req.getHeader("Referer");
        if (ref == null)   ref = ".";
        rsp.sendRedirect2(ref);
    }

    @RequirePOST
    public void doFingerprintCleanup(StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        FingerprintCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    @RequirePOST
    public void doWorkspaceCleanup(StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        WorkspaceCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    /**
     * If the user chose the default JDK, make sure we got 'java' in PATH.
     */
    public FormValidation doDefaultJDKCheck(StaplerRequest2 request, @QueryParameter String value) {
        if (!JDK.isDefaultName(value))
            // assume the user configured named ones properly in system config ---
            // or else system config should have reported form field validation errors.
            return FormValidation.ok();

        // default JDK selected. Does such java really exist?
        if (JDK.isDefaultJDKValid(Jenkins.this))
            return FormValidation.ok();
        else
            return FormValidation.errorWithMarkup(Messages.Hudson_NoJavaInPath(request.getContextPath()));
    }

    /**
     * Checks if a top-level view with the given name exists and
     * make sure that the name is good as a view name.
     */
    public FormValidation doCheckViewName(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String name = fixEmpty(value);
        if (name == null)
            return FormValidation.ok();

        // already exists?
        if (getView(name) != null)
            return FormValidation.error(Messages.Hudson_ViewAlreadyExists(name));

        // good view name?
        try {
            checkGoodName(name);
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }

        return FormValidation.ok();
    }

    /**
     * Checks if a top-level view with the given name exists.
     * @deprecated 1.512
     */
    @Deprecated
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = fixEmpty(value);
        if (view == null) return FormValidation.ok();

        if (getView(view) == null)
            return FormValidation.ok();
        else
            return FormValidation.error(Messages.Hudson_ViewAlreadyExists(view));
    }

    /**
     * Serves static resources placed along with Jelly view files.
     * <p>
     * This method can serve a lot of files, so care needs to be taken
     * to make this method secure. It's not clear to me what's the best
     * strategy here, though the current implementation is based on
     * file extensions.
     */
    public void doResources(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        // cut off the "..." portion of /resources/.../path/to/file
        // as this is only used to make path unique (which in turn
        // allows us to set a long expiration date
        path = path.substring(path.indexOf('/', 1) + 1);

        int idx = path.lastIndexOf('.');
        String extension = path.substring(idx + 1);
        if (ALLOWED_RESOURCE_EXTENSIONS.contains(extension)) {
            URL url = pluginManager.uberClassLoader.getResource(path);
            if (url != null) {
                long expires = MetaClass.NO_CACHE ? 0 : TimeUnit.DAYS.toMillis(365);
                rsp.serveFile(req, url, expires);
                return;
            }
        }
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Extension list that {@link #doResources(StaplerRequest2, StaplerResponse2)} can serve.
     * This set is mutable to allow plugins to add additional extensions.
     */
    @SuppressFBWarnings(value = "MS_MUTABLE_COLLECTION_PKGPROTECT", justification = "mutable to allow plugins to add additional extensions")
    public static final Set<String> ALLOWED_RESOURCE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "js|css|jpeg|jpg|png|gif|html|htm".split("\\|")
    ));

    /**
     * Checks if container uses UTF-8 to decode URLs. See
     * http://wiki.jenkins-ci.org/display/JENKINS/Tomcat#Tomcat-i18n
     * @deprecated use {@link URICheckEncodingMonitor#doCheckURIEncoding(StaplerRequest2)}
     */
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.37")
    @Deprecated
    public FormValidation doCheckURIEncoding(StaplerRequest2 request) throws IOException {
        return ExtensionList.lookupSingleton(URICheckEncodingMonitor.class).doCheckURIEncoding(request);
    }

    /**
     * Does not check when system default encoding is "ISO-8859-1".
     * @deprecated use {@link URICheckEncodingMonitor#isCheckEnabled()}
     */
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.37")
    @Deprecated
    public static boolean isCheckURIEncodingEnabled() {
        return ExtensionList.lookupSingleton(URICheckEncodingMonitor.class).isCheckEnabled();
    }

    public Future<DependencyGraph> getFutureDependencyGraph() {
        synchronized (dependencyGraphLock) {
            // Scheduled future will be the most recent one --> Return
            if (scheduledFutureDependencyGraph != null) {
                return scheduledFutureDependencyGraph;
            }

            // Calculating future will be the most recent one --> Return
            if (calculatingFutureDependencyGraph != null) {
                return calculatingFutureDependencyGraph;
            }

            // No scheduled or calculating future --> Already completed dependency graph is the most recent one
            return CompletableFuture.completedFuture(dependencyGraph);
        }
    }

    /**
     * Rebuilds the dependency map.
     */
    public void rebuildDependencyGraph() {
        DependencyGraph graph = new DependencyGraph();
        graph.build();
        // volatile acts a as a memory barrier here and therefore guarantees
        // that graph is fully build, before it's visible to other threads
        dependencyGraph = graph;
    }

    /**
     * Rebuilds the dependency map asynchronously.
     *
     * <p>
     * This would keep the UI thread more responsive and helps avoid the deadlocks,
     * as dependency graph recomputation tends to touch a lot of other things.
     *
     * @since 1.522
     */
    public Future<DependencyGraph> rebuildDependencyGraphAsync() {
        synchronized (dependencyGraphLock) {
            // Collect calls to this method to avoid unnecessary calculation of the dependency graph
            if (scheduledFutureDependencyGraph != null) {
                return scheduledFutureDependencyGraph;
            }
            // Schedule new calculation
            return scheduledFutureDependencyGraph = scheduleCalculationOfFutureDependencyGraph(500, TimeUnit.MILLISECONDS);
        }
    }

    private Future<DependencyGraph> scheduleCalculationOfFutureDependencyGraph(int delay, TimeUnit unit) {
        return Timer.get().schedule(() -> {
            // Wait for the currently running calculation to finish without blocking rebuildDependencyGraphAsync()
            Future<DependencyGraph> temp = null;
            synchronized (dependencyGraphLock) {
                if (calculatingFutureDependencyGraph != null) {
                    temp = calculatingFutureDependencyGraph;
                }
            }

            if (temp != null) {
                temp.get();
            }

            synchronized (dependencyGraphLock) {
                // Scheduled future becomes the currently calculating future
                calculatingFutureDependencyGraph = scheduledFutureDependencyGraph;
                scheduledFutureDependencyGraph = null;
            }

            rebuildDependencyGraph();

            synchronized (dependencyGraphLock) {
                calculatingFutureDependencyGraph = null;
            }

            return dependencyGraph;
        }, delay, unit);
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    // for Jelly
    public List<ManagementLink> getManagementLinks() {
        return ManagementLink.all();
    }

    // for Jelly
    @Restricted(NoExternalUse.class)
    public Map<ManagementLink.Category, List<ManagementLink>> getCategorizedManagementLinks() {
        Map<ManagementLink.Category, List<ManagementLink>> byCategory = new TreeMap<>();
        for (ManagementLink link : ManagementLink.all()) {
            if (link.getIconFileName() == null) {
                continue;
            }
            if (!Jenkins.get().hasPermission(link.getRequiredPermission())) {
                continue;
            }
            byCategory.computeIfAbsent(link.getCategory(), c -> new ArrayList<>()).add(link);
        }
        return byCategory;
    }

    /**
     * If set, a currently active setup wizard - e.g. installation
     *
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public SetupWizard getSetupWizard() {
        return setupWizard;
    }

    /**
     * Exposes the current user to {@code /me} URL.
     */
    public User getMe() {
        User u = User.current();
        if (u == null)
            throw new AccessDeniedException("/me is not available when not logged in");
        return u;
    }

    /**
     * Gets the {@link Widget}s registered on this object.
     *
     * <p>
     * Plugins who wish to contribute boxes on the side panel can add widgets
     * by {@code getWidgets().add(new MyWidget())} from {@link Plugin#start()}.
     */
    @StaplerDispatchable // some plugins use this to add views to widgets
    public List<Widget> getWidgets() {
        return widgets;
    }

    @Override
    public Object getTarget() {
        try {
            checkPermission(READ);
        } catch (AccessDeniedException e) {
            if (!isSubjectToMandatoryReadPermissionCheck(Stapler.getCurrentRequest2().getRestOfPath())) {
                return this;
            }

            throw e;
        }
        return this;
    }

    /**
     * Test a path to see if it is subject to mandatory read permission checks by container-managed security
     * @param restOfPath the URI, excluding the Jenkins root URI and query string
     * @return true if the path is subject to mandatory read permission checks
     * @since 2.37
     */
    public boolean isSubjectToMandatoryReadPermissionCheck(String restOfPath) {
        for (String name : ALWAYS_READABLE_PATHS) {
            if (restOfPath.startsWith("/" + name + "/") || restOfPath.equals("/" + name)) {
                return false;
            }
        }

        for (String name : getUnprotectedRootActions()) {
            if (restOfPath.startsWith("/" + name + "/") || restOfPath.equals("/" + name)) {
                return false;
            }
        }

        // TODO SlaveComputer.doSlaveAgentJnlp; there should be an annotation to request unprotected access
        if ((isAgentJnlpPath(restOfPath, "jenkins") || isAgentJnlpPath(restOfPath, "slave"))
            && "true".equals(Stapler.getCurrentRequest2().getParameter("encrypt"))) {
            return false;
        }

        return true;
    }

    private boolean isAgentJnlpPath(String restOfPath, String prefix) {
        return restOfPath.matches("(/manage)?/computer/[^/]+/" + prefix + "-agent[.]jnlp");
    }

    /**
     * Gets a list of unprotected root actions.
     * These URL prefixes should be exempted from access control checks by container-managed security.
     * Ideally would be synchronized with {@link #getTarget}.
     * @return a list of {@linkplain Action#getUrlName URL names}
     * @since 1.495
     */
    public Collection<String> getUnprotectedRootActions() {
        Set<String> names = new TreeSet<>();
        names.add("jnlpJars"); // TODO cleaner to refactor doJnlpJars into a URA (see also JENKINS-44100)
        // TODO consider caching (expiring cache when actions changes)
        for (Action a : getActions()) {
            if (a instanceof UnprotectedRootAction) {
                String url = a.getUrlName();
                if (url == null) continue;
                names.add(url);
            }
        }
        return names;
    }

    /**
     * Fallback to the primary view.
     */
    @Override
    public View getStaplerFallback() {
        return getPrimaryView();
    }

    /**
     * This method checks all existing jobs to see if displayName is
     * unique. It does not check the displayName against the displayName of the
     * job that the user is configuring though to prevent a validation warning
     * if the user sets the displayName to what it currently is.
     */
    boolean isDisplayNameUnique(ItemGroup<?> itemGroup, String displayName, String currentJobName) {

        Collection<TopLevelItem> itemCollection = (Collection<TopLevelItem>) itemGroup.getItems(t -> t instanceof TopLevelItem);

        // if there are a lot of projects, we'll have to store their
        // display names in a HashSet or something for a quick check
        for (TopLevelItem item : itemCollection) {
            if (item.getName().equals(currentJobName)) {
                // we won't compare the candidate displayName against the current
                // item. This is to prevent an validation warning if the user
                // sets the displayName to what the existing display name is
                continue;
            }
            else if (displayName.equals(item.getDisplayName())) {
                return false;
            }
        }

        return true;
    }

    /**
     * True if there is no item in Jenkins that has this name
     * @param name The name to test
     * @param currentJobName The name of the job that the user is configuring
     */
    boolean isNameUnique(ItemGroup<?> itemGroup, String name, String currentJobName) {
        Item item = itemGroup.getItem(name);

        if (null == item) {
            // the candidate name didn't return any items so the name is unique
            return true;
        }
        else if (item.getName().equals(currentJobName)) {
            // the candidate name returned an item, but the item is the item
            // that the user is configuring so this is ok
            return true;
        }
        else {
            // the candidate name returned an item, so it is not unique
            return false;
        }
    }

    /**
     * Checks to see if the candidate displayName collides with any
     * existing display names or project names
     * @param displayName The display name to test
     * @param jobName The name of the job the user is configuring
     *
     * @deprecated use {@link TopLevelItemDescriptor#doCheckDisplayNameOrNull(TopLevelItem, String)}
     */
    @Deprecated
    public FormValidation doCheckDisplayName(@QueryParameter String displayName,
            @QueryParameter String jobName) {
        displayName = displayName.trim();

        LOGGER.fine(() -> "Current job name is " + jobName);

        if (!isNameUnique(this, displayName, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_NameNotUniqueWarning(displayName));
        }
        else if (!isDisplayNameUnique(this, displayName, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_DisplayNameNotUniqueWarning(displayName));
        }
        else {
            return FormValidation.ok();
        }
    }

    /**
     * Checks to see if the candidate displayName collides with any
     * existing display names or project names in the items parent group
     * @param displayName The display name to test
     * @param item The item to check for duplicates
     */
    @Restricted(NoExternalUse.class)
    public FormValidation checkDisplayName(String displayName,
                                           TopLevelItem item) {
        displayName = displayName.trim();
        String jobName = item.getName();

        LOGGER.fine(() -> "Current job name is " + jobName);

        if (!isNameUnique(item.getParent(), displayName, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_NameNotUniqueWarning(displayName));
        }
        else if (!isDisplayNameUnique(item.getParent(), displayName, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_DisplayNameNotUniqueWarning(displayName));
        }
        else {
            return FormValidation.ok();
        }
    }

    public static class MasterComputer extends Computer {
        protected MasterComputer() {
            super(Jenkins.get());
        }

        /**
         * Returns "" to match with {@link Jenkins#getNodeName()}.
         */
        @Override
        @NonNull
        public String getName() {
            return "";
        }

        @Override
        public boolean isConnecting() {
            return false;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Hudson_Computer_DisplayName();
        }

        @Override
        public String getCaption() {
            return Messages.Hudson_Computer_Caption();
        }

        @Override
        @NonNull
        public String getUrl() {
            return "computer/(built-in)/";
        }

        @Override
        public RetentionStrategy getRetentionStrategy() {
            return RetentionStrategy.NOOP;
        }

        /**
         * Will always keep this guy alive so that it can function as a fallback to
         * execute {@link FlyweightTask}s. See JENKINS-7291.
         */
        @Override
        protected boolean isAlive() {
            return true;
        }

        @Override
        public Boolean isUnix() {
            return !Functions.isWindows();
        }

        /**
         * Report an error.
         */
        @Override
        public HttpResponse doDoDelete() throws IOException {
            throw HttpResponses.status(SC_BAD_REQUEST);
        }

        @Override
        @POST
        public void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, FormException {
            checkPermission(ADMINISTER);

            Jenkins jenkins = Jenkins.get();

            try (BulkChange bc = new BulkChange(jenkins)) {
                JSONObject json = req.getSubmittedForm();

                try {
                    // For compatibility reasons, this value is stored in Jenkins
                    String num = json.getString("numExecutors");
                    if (!num.matches("\\d+")) {
                        throw new Descriptor.FormException(Hudson_Computer_IncorrectNumberOfExecutors(), "numExecutors");
                    }

                    jenkins.setNumExecutors(json.getInt("numExecutors"));
                    if (req.hasParameter("builtin.mode")) {
                        jenkins.setMode(Mode.valueOf(req.getParameter("builtin.mode")));
                    } else {
                        jenkins.setMode(Mode.NORMAL);
                    }

                    jenkins.setLabelString(json.optString("labelString", ""));
                } catch (IOException e) {
                    throw new Descriptor.FormException(e, "numExecutors");
                }

                jenkins.getNodeProperties().rebuild(req, json.optJSONObject("nodeProperties"), NodeProperty.all());

                bc.commit();
            }

            jenkins.updateComputers(jenkins);

            Computer computer = jenkins.toComputer();
            if (computer == null) {
                throw new IllegalStateException("Cannot find the computer object for the controller node");
            }
            FormApply.success(req.getContextPath() + '/' + computer.getUrl()).generateResponse(req, rsp, null);
        }

        @WebMethod(name = "config.xml")
        @Override
        public void doConfigDotXml(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            throw HttpResponses.status(SC_BAD_REQUEST);
        }

        @Override
        public boolean hasPermission(Permission permission) {
            // no one should be allowed to delete the master.
            // this hides the "delete" link from the /computer/(built-in)/ page.
            if (permission == Computer.DELETE)
                return false;
            // Configuration of master node requires ADMINISTER permission
            return super.hasPermission(permission == Computer.CONFIGURE ? Jenkins.ADMINISTER : permission);
        }

        @Override
        public VirtualChannel getChannel() {
            return FilePath.localChannel;
        }

        @Override
        public Charset getDefaultCharset() {
            return Charset.defaultCharset();
        }

        @Override
        public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
            return logRecords;
        }

        @Override
        @RequirePOST
        public void doLaunchSlaveAgent(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            // this computer never returns null from channel, so
            // this method shall never be invoked.
            rsp.sendError(SC_NOT_FOUND);
        }

        @Override
        protected Future<?> _connect(boolean forceReconnect) {
            return Futures.precomputed(null);
        }

        /**
         * {@link LocalChannel} instance that can be used to execute programs locally.
         *
         * @deprecated as of 1.558
         *      Use {@link FilePath#localChannel}
         */
        @Deprecated
        public static final LocalChannel localChannel = FilePath.localChannel;
    }

    /**
     * Shortcut for {@code Jenkins.getInstanceOrNull()?.lookup.get(type)}
     */
    public static @CheckForNull <T> T lookup(Class<T> type) {
        Jenkins j = Jenkins.getInstanceOrNull();
        return j != null ? j.lookup.get(type) : null;
    }

    /**
     * Live view of recent {@link LogRecord}s produced by Jenkins.
     */
    @SuppressFBWarnings(value = "MS_CANNOT_BE_FINAL", justification = "cannot be made immutable without breaking compatibility")
    public static List<LogRecord> logRecords = Collections.emptyList(); // initialized to dummy value to avoid NPE

    /**
     * Thread-safe reusable {@link XStream}.
     */
    public static final XStream XSTREAM;

    /**
     * Alias to {@link #XSTREAM} so that one can access additional methods on {@link XStream2} more easily.
     */
    public static final XStream2 XSTREAM2;

    private static final int TWICE_CPU_NUM = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    /**
     * Thread pool used to load configuration in parallel, to improve the start up time.
     * <p>
     * The idea here is to overlap the CPU and I/O, so we want more threads than CPU numbers.
     */
    /*package*/ final transient ExecutorService threadPoolForLoad = new ThreadPoolExecutor(
        TWICE_CPU_NUM, TWICE_CPU_NUM,
        5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new NamingThreadFactory(new DaemonThreadFactory(), "Jenkins load"));


    private static void computeVersion(ServletContext context) {
        // set the version
        Properties props = new Properties();
        try (InputStream is = Jenkins.class.getResourceAsStream("jenkins-version.properties")) {
            if (is != null)
                props.load(is);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Failed to load jenkins-version.properties");
        }
        String ver = props.getProperty("version");
        if (ver == null)   ver = UNCOMPUTED_VERSION;
        if (Main.isDevelopmentMode && "${project.version}".equals(ver)) {
            // in dev mode, unable to get version (ahem Eclipse)
            try {
                File dir = new File(".").getAbsoluteFile();
                while (dir != null) {
                    File pom = new File(dir, "pom.xml");
                    if (pom.exists() && "pom".equals(XMLUtils.getValue("/project/artifactId", pom))) {
                        pom =  pom.getCanonicalFile();
                        LOGGER.info("Reading version from: " + pom.getAbsolutePath());
                        ver = XMLUtils.getValue("/project/version", pom);
                        break;
                    }
                    dir = dir.getParentFile();
                }
                LOGGER.info("Jenkins is in dev mode, using version: " + ver);
            } catch (Exception e) {
                LOGGER.log(WARNING, e, () -> "Unable to read Jenkins version: " + e.getMessage());
            }
        }

        VERSION = ver;
        context.setAttribute("version", ver);

        CHANGELOG_URL = props.getProperty("changelog.url");

        VERSION_HASH = Util.getDigestOf(ver).substring(0, 8);
        SESSION_HASH = Util.getDigestOf(ver + System.currentTimeMillis()).substring(0, 8);

        if (ver.equals(UNCOMPUTED_VERSION) || SystemProperties.getBoolean("hudson.script.noCache"))
            RESOURCE_PATH = "";
        else
            RESOURCE_PATH = "/static/" + SESSION_HASH;

        VIEW_RESOURCE_PATH = "/resources/" + SESSION_HASH;
    }

    /**
     * The version number before it is "computed" (by a call to computeVersion()).
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public static final String UNCOMPUTED_VERSION = "?";

    /**
     * Version number of this Jenkins.
     */
    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String VERSION = UNCOMPUTED_VERSION;

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public static String CHANGELOG_URL;

    /**
     * Parses {@link #VERSION} into {@link VersionNumber}, or null if it's not parseable as a version number
     * (such as when Jenkins is run with {@code mvn jetty:run})
     */
    public @CheckForNull static VersionNumber getVersion() {
        return toVersion(VERSION);
    }

    /**
     * Get the stored version of Jenkins, as stored by
     * {@link #doConfigSubmit(org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)}.
     * <p>
     * Parses the version into {@link VersionNumber}, or null if it's not parseable as a version number
     * (such as when Jenkins is run with {@code mvn jetty:run})
     * @since 2.0
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull static VersionNumber getStoredVersion() {
        return toVersion(Jenkins.get().version);
    }

    /**
     * Parses a version string into {@link VersionNumber}, or null if it's not parseable as a version number
     * (such as when Jenkins is run with {@code mvn jetty:run})
     */
    private static @CheckForNull VersionNumber toVersion(@CheckForNull String versionString) {
        if (versionString == null) {
            return null;
        }

        try {
            return new VersionNumber(versionString);
        } catch (NumberFormatException e) {
            try {
                // for non-released version of Jenkins, this looks like "1.345 (private-foobar), so try to approximate.
                int idx = versionString.indexOf(' ');
                if (idx > 0) {
                    return new VersionNumber(versionString.substring(0, idx));
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }

            // totally unparseable
            return null;
        } catch (IllegalArgumentException e) {
            // totally unparseable
            return null;
        }
    }

    @Restricted(NoExternalUse.class)
    public boolean shouldShowStackTrace() {
        // Used by oops.jelly
        return Boolean.getBoolean(Jenkins.class.getName() + ".SHOW_STACK_TRACE");
    }

    /**
     * Hash of {@link #VERSION}.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public static String VERSION_HASH;

    /**
     * Unique random token that identifies the current session.
     * Used to make {@link #RESOURCE_PATH} unique so that we can set long "Expires" header.
     *
     * We used to use {@link #VERSION_HASH}, but making this session local allows us to
     * reuse the same {@link #RESOURCE_PATH} for static resources in plugins.
     */
    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String SESSION_HASH;

    /**
     * Prefix to static resources like images and javascripts in the war file.
     * Either "" or strings like "/static/VERSION", which avoids Jenkins to pick up
     * stale cache when the user upgrades to a different version.
     * <p>
     * Value computed in {@link WebAppMain}.
     */
    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String RESOURCE_PATH = "";

    /**
     * Prefix to resources alongside view scripts.
     * Strings like "/resources/VERSION", which avoids Jenkins to pick up
     * stale cache when the user upgrades to a different version.
     * <p>
     * Value computed in {@link WebAppMain}.
     */
    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String VIEW_RESOURCE_PATH = "/resources/TBD";

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean PARALLEL_LOAD = SystemProperties.getBoolean(Jenkins.class.getName() + "." + "parallelLoad", true);
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean KILL_AFTER_LOAD = SystemProperties.getBoolean(Jenkins.class.getName() + "." + "killAfterLoad", false);
    /**
     * @deprecated No longer used.
     */
    @Deprecated
    public static boolean FLYWEIGHT_SUPPORT = true;

    /**
     * Tentative switch to activate the concurrent build behavior.
     * When we merge this back to the trunk, this allows us to keep
     * this feature hidden for a while until we iron out the kinks.
     * @see AbstractProject#isConcurrentBuild()
     * @deprecated as of 1.464
     *      This flag will have no effect.
     */
    @Restricted(NoExternalUse.class)
    @Deprecated
    public static boolean CONCURRENT_BUILD = true;

    /**
     * Switch to enable people to use a shorter workspace name.
     */
    private static final String WORKSPACE_DIRNAME = SystemProperties.getString(Jenkins.class.getName() + "." + "workspaceDirName", "workspace");

    /**
     * Name of the system property escape hatch for SECURITY-2424. It allows to have back the legacy (and vulnerable)
     * behavior allowing a "good name" to end with a dot. This could be used to exploit two names colliding in the file
     * system to extract information. The files ending with a dot are only a problem on Windows.
     *
     * The default value is true.
     *
     * For detailed documentation: <a href="https://docs.microsoft.com/en-us/troubleshoot/windows-client/shell-experience/file-folder-name-whitespace-characters">Support for Whitespace characters in File and Folder names for Windows</a>
     * @see #checkGoodName(String)
     */
    @Restricted(NoExternalUse.class)
    public static final String NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP = Jenkins.class.getName() + "." + "nameValidationRejectsTrailingDot";

    /**
     * Default value of job's builds dir.
     * @see #getRawBuildsDir()
     */
    private static final String DEFAULT_BUILDS_DIR = "${ITEM_ROOTDIR}/builds";
    /**
     * Old layout for workspaces.
     * @see #DEFAULT_WORKSPACES_DIR
     */
    private static final String OLD_DEFAULT_WORKSPACES_DIR = "${ITEM_ROOTDIR}/" + WORKSPACE_DIRNAME;

    /**
     * Default value for the workspace's directories layout.
     * @see #workspaceDir
     */
    private static final String DEFAULT_WORKSPACES_DIR = "${JENKINS_HOME}/workspace/${ITEM_FULL_NAME}";

    /**
     * System property name to set {@link #buildsDir}.
     * @see #getRawBuildsDir()
     */
    static final String BUILDS_DIR_PROP = Jenkins.class.getName() + ".buildsDir";

    /**
     * System property name to set {@link #workspaceDir}.
     * @see #getRawWorkspaceDir()
     */
    static final String WORKSPACES_DIR_PROP = Jenkins.class.getName() + ".workspacesDir";


    /**
     * Automatically try to launch an agent when Jenkins is initialized or a new agent computer is created.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean AUTOMATIC_AGENT_LAUNCH = SystemProperties.getBoolean(Jenkins.class.getName() + ".automaticAgentLaunch", true);

    /**
     * The amount of time by which to extend the startup notification timeout as each initialization milestone is attained.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* not final */ int EXTEND_TIMEOUT_SECONDS = (int) SystemProperties.getDuration(Jenkins.class.getName() + ".extendTimeoutSeconds", ChronoUnit.SECONDS, Duration.ofSeconds(15)).toSeconds();

    private static final Logger LOGGER = Logger.getLogger(Jenkins.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final PermissionGroup PERMISSIONS = Permission.HUDSON_PERMISSIONS;
    public static final Permission ADMINISTER = Permission.HUDSON_ADMINISTER;

    /**
     * This permission grants access to parts of the Jenkins system configuration.
     *
     * <p>Only features that won't have an impact on Jenkins' overall security and stability should have their
     * permission requirement lowered from {@link #ADMINISTER} to {@code MANAGE}.
     * For example, many scripting and code execution features (e.g., configuring master agents, paths to tools on master, etc.)
     * are unsafe to make available to users with only this permission,
     * as they could be used to bypass permission enforcement and elevate permissions.</p>
     *
     * <p>This permission is disabled by default and support for it considered experimental.
     * Administrators can set the system property {@code jenkins.security.ManagePermission} to enable it.</p>
     *
     * @since 2.222
     */
    public static final Permission MANAGE = new Permission(PERMISSIONS, "Manage",
            Messages._Jenkins_Manage_Description(),
            ADMINISTER,
            SystemProperties.getBoolean("jenkins.security.ManagePermission"),
            new PermissionScope[]{PermissionScope.JENKINS});

    /**
     * Allows read-only access to large parts of the system configuration.
     *
     * When combined with {@link #MANAGE}, it is expected that everything is shown as if only {@link #SYSTEM_READ} was granted,
     * but that only options editable by users with {@link #MANAGE} are editable.
     */
    public static final Permission SYSTEM_READ = new Permission(PERMISSIONS, "SystemRead",
            Messages._Jenkins_SystemRead_Description(),
            ADMINISTER,
            SystemProperties.getBoolean("jenkins.security.SystemReadPermission"),
            new PermissionScope[]{PermissionScope.JENKINS});

    @Restricted(NoExternalUse.class) // called by jelly
    public static final Permission[] MANAGE_AND_SYSTEM_READ =
            new Permission[] { MANAGE, SYSTEM_READ };

    public static final Permission READ = new Permission(PERMISSIONS, "Read", Messages._Hudson_ReadPermission_Description(), Permission.READ, PermissionScope.JENKINS);
    /** @deprecated in Jenkins 2.222 use {@link Jenkins#ADMINISTER} instead */
    @Deprecated
    public static final Permission RUN_SCRIPTS = new Permission(PERMISSIONS, "RunScripts", Messages._Hudson_RunScriptsPermission_Description(), ADMINISTER, PermissionScope.JENKINS);

    /**
     * Urls that are always visible without READ permission.
     *
     * <p>See also:{@link #getUnprotectedRootActions}.
     */
    private static final Set<String> ALWAYS_READABLE_PATHS = new HashSet<>(Arrays.asList(
        "404", // Web method
        "_404", // .jelly
        "_404_simple", // .jelly
        "login", // .jelly
        "loginError", // .jelly
        "logout", // #doLogout
        "accessDenied", // .jelly
        "adjuncts", // #getAdjuncts
        "error", // AbstractModelObject/error.jelly
        "oops", // .jelly
        "signup", // #doSignup
        "tcpSlaveAgentListener", // #getTcpSlaveAgentListener
        "federatedLoginService", // #getFederatedLoginService
        "securityRealm" // #getSecurityRealm
    ));

    static {
        final String paths = SystemProperties.getString(Jenkins.class.getName() + ".additionalReadablePaths");
        if (paths != null) {
            LOGGER.info(() -> "SECURITY-2047 override: Adding the following paths to ALWAYS_READABLE_PATHS: " + paths);
            ALWAYS_READABLE_PATHS.addAll(Arrays.stream(paths.split(",")).map(String::trim).collect(Collectors.toSet()));
        }
    }

    /**
     * {@link Authentication} object that represents the anonymous user.
     * Because Spring Security creates its own {@link AnonymousAuthenticationToken} instances, the code must not
     * expect the singleton semantics. This is just a convenient instance.
     *
     * @since 2.266
     */
    public static final Authentication ANONYMOUS2 =
            new AnonymousAuthenticationToken(
                    "anonymous",
                    "anonymous",
                    Set.of(new SimpleGrantedAuthority("anonymous")));

    /**
     * @deprecated use {@link #ANONYMOUS2}
     * @since 1.343
     */
    @Deprecated
    public static final org.acegisecurity.Authentication ANONYMOUS =
            new org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken(
                    "anonymous",
                    "anonymous",
                    new org.acegisecurity.GrantedAuthority[] {
                        new org.acegisecurity.GrantedAuthorityImpl("anonymous"),
                    });

    static {
        try {
            XSTREAM = XSTREAM2 = new XStream2();

            XSTREAM.alias("jenkins", Jenkins.class);
            XSTREAM.alias("slave", DumbSlave.class);
            XSTREAM.alias("jdk", JDK.class);
            // for backward compatibility with <1.75, recognize the tag name "view" as well.
            XSTREAM.alias("view", ListView.class);
            XSTREAM.alias("listView", ListView.class);
            XSTREAM2.addCriticalField(Jenkins.class, "securityRealm");
            XSTREAM2.addCriticalField(Jenkins.class, "authorizationStrategy");
            // this seems to be necessary to force registration of converter early enough
            Mode.class.getEnumConstants();

            // double check that initialization order didn't do any harm
            assert PERMISSIONS != null;
            assert ADMINISTER != null;

        } catch (RuntimeException | Error e) {
            // when loaded on an agent and this fails, subsequent NoClassDefFoundError will fail to chain the cause.
            // see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=8051847
            // As we don't know where the first exception will go, let's also send this to logging so that
            // we have a known place to look at.
            LOGGER.log(SEVERE, "Failed to load Jenkins.class", e);
            throw e;
        }
    }

    private static final class JenkinsJVMAccess extends JenkinsJVM {
        private static void _setJenkinsJVM(boolean jenkinsJVM) {
            JenkinsJVM.setJenkinsJVM(jenkinsJVM);
        }
    }

    private static final class QuietDownInfo {
        @CheckForNull
        final String message;

        private boolean safeRestart;

        QuietDownInfo() {
            this(null, false);
        }

        QuietDownInfo(final String message) {
            this(message, false);
        }

        QuietDownInfo(final String message, final boolean safeRestart) {
                this.message = message;
                this.safeRestart = safeRestart;
        }


        boolean isSafeRestart() {
            return safeRestart;
        }

        void setSafeRestart(boolean safeRestart) {
            this.safeRestart = safeRestart;
        }
    }
}
