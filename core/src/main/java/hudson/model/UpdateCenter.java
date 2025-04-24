/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Seiji Sogabe
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

package hudson.model;

import static hudson.init.InitMilestone.PLUGINS_STARTED;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.Initializer;
import hudson.lifecycle.Lifecycle;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.model.UpdateSite.Data;
import hudson.model.UpdateSite.Plugin;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.AtmostOneThreadExecutor;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.util.DaemonThreadFactory;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.NamingThreadFactory;
import hudson.util.PersistedList;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLHandshakeException;
import jenkins.MissingDependencyException;
import jenkins.RestartRequiredException;
import jenkins.install.InstallUtil;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.security.stapler.StaplerDispatchable;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.jenkinsci.Symbol;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.core.Authentication;

/**
 * Controls update center capability.
 *
 * <p>
 * The main job of this class is to keep track of the latest update center metadata file, and perform installations.
 * Much of the UI about choosing plugins to install is done in {@link PluginManager}.
 * <p>
 * The update center can be configured to contact alternate servers for updates
 * and plugins, and to use alternate strategies for downloading, installing
 * and updating components. See the Javadocs for {@link UpdateCenterConfiguration}
 * for more information.
 * <p>
 * <b>Extending Update Centers</b>. The update center in {@code Jenkins} can be replaced by defining a
 * System Property ({@code hudson.model.UpdateCenter.className}). See {@link #createUpdateCenter(hudson.model.UpdateCenter.UpdateCenterConfiguration)}.
 * This className should be available on early startup, so it cannot come only from a library
 * (e.g. Jenkins module or Extra library dependency in the WAR file project).
 * Plugins cannot be used for such purpose.
 * In order to be correctly instantiated, the class definition must have two constructors:
 * {@link #UpdateCenter()} and {@link #UpdateCenter(hudson.model.UpdateCenter.UpdateCenterConfiguration)}.
 * If the class does not comply with the requirements, a fallback to the default UpdateCenter will be performed.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.220
 */
@ExportedBean
public class UpdateCenter extends AbstractModelObject implements Loadable, Saveable, OnMaster, StaplerProxy {

    private static final Logger LOGGER;
    private static final String UPDATE_CENTER_URL;

    /**
     * Read timeout when downloading plugins, defaults to 1 minute
     */
    private static final int PLUGIN_DOWNLOAD_READ_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(SystemProperties.getInteger(UpdateCenter.class.getName() + ".pluginDownloadReadTimeoutSeconds", 60));

    public static final String PREDEFINED_UPDATE_SITE_ID = "default";

    /**
     * {@linkplain UpdateSite#getId() ID} of the default update site.
     * @since 1.483; configurable via system property since 2.4
     */
    public static final String ID_DEFAULT = SystemProperties.getString(UpdateCenter.class.getName() + ".defaultUpdateSiteId", PREDEFINED_UPDATE_SITE_ID);

    @Restricted(NoExternalUse.class)
    public static final String ID_UPLOAD = "_upload";

    /**
     * {@link ExecutorService} that performs installation.
     * @since 1.501
     */
    private final ExecutorService installerService = new AtmostOneThreadExecutor(
        new NamingThreadFactory(new DaemonThreadFactory(), "Update center installer thread"));

    /**
     * An {@link ExecutorService} for updating UpdateSites.
     */
    protected final ExecutorService updateService = Executors.newCachedThreadPool(
        new NamingThreadFactory(new DaemonThreadFactory(), "Update site data downloader"));

    /**
     * List of created {@link UpdateCenterJob}s. Access needs to be synchronized.
     */
    private final Vector<UpdateCenterJob> jobs = new Vector<>();

    /**
     * {@link UpdateSite}s from which we've already installed a plugin at least once.
     * This is used to skip network tests.
     */
    private final Set<UpdateSite> sourcesUsed = new HashSet<>();

    /**
     * List of {@link UpdateSite}s to be used.
     */
    private final PersistedList<UpdateSite> sites = new PersistedList<>(this);

    /**
     * Update center configuration data
     */
    private UpdateCenterConfiguration config;

    private boolean requiresRestart;

    /** @see #isSiteDataReady */
    private transient volatile boolean siteDataLoading;

    static {
        Logger logger = Logger.getLogger(UpdateCenter.class.getName());
        LOGGER = logger;
        String ucOverride = SystemProperties.getString(UpdateCenter.class.getName() + ".updateCenterUrl");
        if (ucOverride != null) {
            logger.log(Level.INFO, "Using a custom update center defined by the system property: {0}", ucOverride);
            UPDATE_CENTER_URL = ucOverride;
        } else {
            UPDATE_CENTER_URL = "https://updates.jenkins.io/";
        }
    }

    /**
     * Simple connection status enum.
     */
    @Restricted(NoExternalUse.class)
    enum ConnectionStatus {
        /**
         * Connection status has not started yet.
         */
        PRECHECK,
        /**
         * Connection status check has been skipped.
         * As example, it may happen if there is no connection check URL defined for the site.
         * @since 2.4
         */
        SKIPPED,
        /**
         * Connection status is being checked at this time.
         */
        CHECKING,
        /**
         * Connection status was not checked.
         */
        UNCHECKED,
        /**
         * Connection is ok.
         */
        OK,
        /**
         * Connection status check failed.
         */
        FAILED;

        static final String INTERNET = "internet";
        static final String UPDATE_SITE = "updatesite";
    }

    public UpdateCenter() {
        configure(new UpdateCenterConfiguration());
    }

    UpdateCenter(@NonNull UpdateCenterConfiguration configuration) {
        configure(configuration);
    }

    /**
     * Creates an update center.
     * @param config Requested configuration. May be {@code null} if defaults should be used
     * @return Created Update center. {@link UpdateCenter} by default, but may be overridden
     * @since 2.4
     */
    @NonNull
    public static UpdateCenter createUpdateCenter(@CheckForNull UpdateCenterConfiguration config) {
        String requiredClassName = SystemProperties.getString(UpdateCenter.class.getName() + ".className", null);
        if (requiredClassName == null) {
            // Use the default Update Center
            LOGGER.log(Level.FINE, "Using the default Update Center implementation");
            return createDefaultUpdateCenter(config);
        }

        LOGGER.log(Level.FINE, "Using the custom update center: {0}", requiredClassName);
        try {
            final Class<?> clazz = Class.forName(requiredClassName).asSubclass(UpdateCenter.class);
            if (!UpdateCenter.class.isAssignableFrom(clazz)) {
                LOGGER.log(Level.SEVERE, "The specified custom Update Center {0} is not an instance of {1}. Falling back to default.",
                        new Object[] {requiredClassName, UpdateCenter.class.getName()});
                return createDefaultUpdateCenter(config);
            }
            final Class<? extends UpdateCenter> ucClazz = clazz.asSubclass(UpdateCenter.class);
            final Constructor<? extends UpdateCenter> defaultConstructor = ucClazz.getConstructor();
            final Constructor<? extends UpdateCenter> configConstructor = ucClazz.getConstructor(UpdateCenterConfiguration.class);
            LOGGER.log(Level.FINE, "Using the constructor {0} Update Center configuration for {1}",
                    new Object[] {config != null ? "with" : "without", requiredClassName});
            return config != null ? configConstructor.newInstance(config) : defaultConstructor.newInstance();
        } catch (ClassCastException e) {
            // Should never happen
            LOGGER.log(WARNING, "UpdateCenter class {0} does not extend hudson.model.UpdateCenter. Using default.", requiredClassName);
        } catch (NoSuchMethodException e) {
            LOGGER.log(WARNING, String.format("UpdateCenter class %s does not define one of the required constructors. Using default", requiredClassName), e);
        } catch (Exception e) {
            LOGGER.log(WARNING, String.format("Unable to instantiate custom plugin manager [%s]. Using default.", requiredClassName), e);
        }
        return createDefaultUpdateCenter(config);
    }

    @NonNull
    private static UpdateCenter createDefaultUpdateCenter(@CheckForNull UpdateCenterConfiguration config) {
        return config != null ? new UpdateCenter(config) : new UpdateCenter();
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Configures update center to get plugins/updates from alternate servers,
     * and optionally using alternate strategies for downloading, installing
     * and upgrading.
     *
     * @param config Configuration data
     * @see UpdateCenterConfiguration
     */
    public void configure(UpdateCenterConfiguration config) {
        if (config != null) {
            this.config = config;
        }
    }

    /**
     * Returns the list of {@link UpdateCenterJob} representing scheduled installation attempts.
     *
     * @return
     *      can be empty but never null. Oldest entries first.
     */
    @Exported
    @StaplerDispatchable
    public List<UpdateCenterJob> getJobs() {
        synchronized (jobs) {
            return new ArrayList<>(jobs);
        }
    }

    /**
     * Gets a job by its ID.
     *
     * Primarily to make {@link UpdateCenterJob} bound to URL.
     */
    public UpdateCenterJob getJob(int id) {
        synchronized (jobs) {
            for (UpdateCenterJob job : jobs) {
                if (job.id == id)
                    return job;
            }
        }
        return null;
    }

    /**
     * Returns latest install/upgrade job for the given plugin.
     * @return InstallationJob or null if not found
     */
    public InstallationJob getJob(Plugin plugin) {
        List<UpdateCenterJob> jobList = getJobs();
        Collections.reverse(jobList);
        for (UpdateCenterJob job : jobList)
            if (job instanceof InstallationJob) {
                InstallationJob ij = (InstallationJob) job;
                if (ij.plugin.name.equals(plugin.name) && ij.plugin.sourceId.equals(plugin.sourceId))
                    return ij;
            }
        return null;
    }

    @Restricted(NoExternalUse.class)
    public Badge getBadge() {
        if (!isSiteDataReady()) {
            // Do not display message during this page load, but possibly later.
            return null;
        }
        List<Plugin> plugins = getUpdates();
        int size = plugins.size();
        if (size > 0) {
            StringBuilder tooltip = new StringBuilder();
            Badge.Severity severity = Badge.Severity.WARNING;
            int securityFixSize = (int) plugins.stream().filter(Plugin::fixesSecurityVulnerabilities).count();
            int incompatibleSize = (int) plugins.stream().filter(plugin -> !plugin.isCompatibleWithInstalledVersion()).count();
            if (size > 1) {
                tooltip.append(jenkins.management.Messages.PluginsLink_updatesAvailable(size));
            } else {
                tooltip.append(jenkins.management.Messages.PluginsLink_updateAvailable());
            }
            switch (incompatibleSize) {
                case 0:
                    break;
                case 1:
                    tooltip.append("\n").append(jenkins.management.Messages.PluginsLink_incompatibleUpdateAvailable());
                    break;
                default:
                    tooltip.append("\n").append(jenkins.management.Messages.PluginsLink_incompatibleUpdatesAvailable(incompatibleSize));
                    break;
            }
            switch (securityFixSize) {
                case 0:
                    break;
                case 1:
                    tooltip.append("\n").append(jenkins.management.Messages.PluginsLink_securityUpdateAvailable());
                    severity = Badge.Severity.DANGER;
                    break;
                default:
                    tooltip.append("\n").append(jenkins.management.Messages.PluginsLink_securityUpdatesAvailable(securityFixSize));
                    severity = Badge.Severity.DANGER;
                    break;
            }
            return new Badge(Integer.toString(size), tooltip.toString(), severity);
        }
        return null;

    }

    /**
     * Get the current connection status.
     * <p>
     * Supports a "siteId" request parameter, defaulting to {@link #ID_DEFAULT} for the default
     * update site.
     *
     * @return The current connection status.
     */
    @Restricted(DoNotUse.class)
    public HttpResponse doConnectionStatus(StaplerRequest2 request) {
        Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
        try {
            String siteId = request.getParameter("siteId");
            if (siteId == null) {
                siteId = ID_DEFAULT;
            } else if (siteId.equals("default")) {
                // If the request explicitly requires the default ID, ship it
                siteId = ID_DEFAULT;
            }
            ConnectionCheckJob checkJob = getConnectionCheckJob(siteId);
            if (checkJob == null) {
                UpdateSite site = getSite(siteId);
                if (site != null) {
                    checkJob = addConnectionCheckJob(site);
                }
            }
            if (checkJob != null) {
                boolean isOffline = false;
                for (ConnectionStatus status : checkJob.connectionStates.values()) {
                    if (ConnectionStatus.FAILED.equals(status)) {
                        isOffline = true;
                        break;
                    }
                }
                if (isOffline) {
                    // retry connection states if determined to be offline
                    checkJob.run();
                    isOffline = false;
                    for (ConnectionStatus status : checkJob.connectionStates.values()) {
                        if (ConnectionStatus.FAILED.equals(status)) {
                            isOffline = true;
                            break;
                        }
                    }
                    if (!isOffline) { // also need to download the metadata
                        updateAllSites();
                    }
                }
                return HttpResponses.okJSON(checkJob.connectionStates);
            } else {
                return HttpResponses.errorJSON(String.format("Cannot check connection status of the update site with ID='%s'"
                        + ". This update center cannot be resolved", siteId));
            }
        } catch (Exception e) {
            return HttpResponses.errorJSON(String.format("ERROR: %s", e.getMessage()));
        }
    }

    /**
     * Called to determine if there was an incomplete installation, what the statuses of the plugins are
     */
    @Restricted(DoNotUse.class) // WebOnly
    public HttpResponse doIncompleteInstallStatus() {
        try {
            Map<String, String> jobs = InstallUtil.getPersistedInstallStatus();
            if (jobs == null) {
                jobs = Collections.emptyMap();
            }
            return HttpResponses.okJSON(jobs);
        } catch (RuntimeException e) {
            return HttpResponses.errorJSON(String.format("ERROR: %s", e.getMessage()));
        }
    }

    /**
     * Called to persist the currently installing plugin states. This allows
     * us to support install resume if Jenkins is restarted while plugins are
     * being installed.
     */
    @Restricted(NoExternalUse.class)
    public synchronized void persistInstallStatus() {
        List<UpdateCenterJob> jobs = getJobs();

        boolean activeInstalls = false;
        for (UpdateCenterJob job : jobs) {
            if (job instanceof InstallationJob) {
                InstallationJob installationJob = (InstallationJob) job;
                if (!installationJob.status.isSuccess()) {
            activeInstalls = true;
                }
            }
        }

        if (activeInstalls) {
        InstallUtil.persistInstallStatus(jobs); // save this info
        }
        else {
        InstallUtil.clearInstallStatus(); // clear this info
        }
    }

    /**
     * Get the current installation status of a plugin set.
     * <p>
     * Supports a "correlationId" request parameter if you only want to get the
     * install status of a set of plugins requested for install through
     * {@link PluginManager#doInstallPlugins(org.kohsuke.stapler.StaplerRequest2)}.
     *
     * @return The current installation status of a plugin set.
     */
    @Restricted(DoNotUse.class)
    public HttpResponse doInstallStatus(StaplerRequest2 request) {
        try {
            String correlationId = request.getParameter("correlationId");
            Map<String, Object> response = new HashMap<>();
            response.put("state", Jenkins.get().getInstallState().name());
            List<Map<String, String>> installStates = new ArrayList<>();
            response.put("jobs", installStates);
            List<UpdateCenterJob> jobCopy = getJobs();

            for (UpdateCenterJob job : jobCopy) {
                if (job instanceof InstallationJob) {
                    UUID jobCorrelationId = job.getCorrelationId();
                    if (correlationId == null || (jobCorrelationId != null && correlationId.equals(jobCorrelationId.toString()))) {
                        InstallationJob installationJob = (InstallationJob) job;
                        Map<String, String> pluginInfo = new LinkedHashMap<>();
                        pluginInfo.put("name", installationJob.plugin.name);
                        pluginInfo.put("version", installationJob.plugin.version);
                        pluginInfo.put("title", installationJob.plugin.title);
                        pluginInfo.put("installStatus", installationJob.status.getType());
                        pluginInfo.put("requiresRestart", Boolean.toString(installationJob.status.requiresRestart()));
                        if (jobCorrelationId != null) {
                            pluginInfo.put("correlationId", jobCorrelationId.toString());
                        }
                        installStates.add(pluginInfo);
                    }
                }
            }
            return HttpResponses.okJSON(JSONObject.fromObject(response));
        } catch (RuntimeException e) {
            return HttpResponses.errorJSON(String.format("ERROR: %s", e.getMessage()));
        }
    }

    /**
     * Returns latest Jenkins upgrade job.
     * @return HudsonUpgradeJob or null if not found
     */
    public HudsonUpgradeJob getHudsonJob() {
        List<UpdateCenterJob> jobList = getJobs();
        Collections.reverse(jobList);
        for (UpdateCenterJob job : jobList)
            if (job instanceof HudsonUpgradeJob)
                return (HudsonUpgradeJob) job;
        return null;
    }

    /**
     * Returns the list of {@link UpdateSite}s to be used.
     * This is a live list, whose change will be persisted automatically.
     *
     * @return
     *      can be empty but never null.
     */
    @StaplerDispatchable // referenced by _api.jelly
    public PersistedList<UpdateSite> getSites() {
        return sites;
    }

    /**
     * Whether it is <em>probably</em> safe to call all {@link UpdateSite#getData} without blocking.
     * @return true if all data is <em>currently</em> ready (or absent);
     *         false if some is not ready now (but it will be loaded in the background)
     */
    @Restricted(NoExternalUse.class)
    public boolean isSiteDataReady() {
        if (sites.stream().anyMatch(UpdateSite::hasUnparsedData)) {
            if (!siteDataLoading) {
                siteDataLoading = true;
                Timer.get().submit(() -> {
                    sites.forEach(UpdateSite::getData);
                    siteDataLoading = false;
                });
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * The same as {@link #getSites()} but for REST API.
     */
    @Exported(name = "sites")
    public List<UpdateSite> getSiteList() {
        return sites.toList();
    }

    /**
     * Alias for {@link #getById}.
     * @param id ID of the update site to be retrieved
     * @return Discovered {@link UpdateSite}. {@code null} if it cannot be found
     */
    @CheckForNull
    public UpdateSite getSite(String id) {
        return getById(id);
    }

    /**
     * Gets the string representing how long ago the data was obtained.
     * Will be the newest of all {@link UpdateSite}s.
     */
    public String getLastUpdatedString() {
        long newestTs = 0;
        for (UpdateSite s : sites) {
            if (s.getDataTimestamp() > newestTs) {
                newestTs = s.getDataTimestamp();
            }
        }
        if (newestTs == 0) {
            return Messages.UpdateCenter_n_a();
        }
        return Util.getTimeSpanString(System.currentTimeMillis() - newestTs);
    }

    /**
     * Gets {@link UpdateSite} by its ID.
     * Used to bind them to URL.
     * @param id ID of the update site to be retrieved
     * @return Discovered {@link UpdateSite}. {@code null} if it cannot be found
     */
    @CheckForNull
    public UpdateSite getById(String id) {
        for (UpdateSite s : sites) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Gets the {@link UpdateSite} from which we receive updates for {@code jenkins.war}.
     *
     * @return
     *      {@code null} if no such update center is provided.
     */
    @CheckForNull
    public UpdateSite getCoreSource() {
        for (UpdateSite s : sites) {
            Data data = s.getData();
            if (data != null && data.core != null)
                return s;
        }
        return null;
    }

    /**
     * Gets the default base URL.
     *
     * @deprecated
     *      TODO: revisit tool update mechanism, as that should be de-centralized, too. In the mean time,
     *      please try not to use this method, and instead ping us to get this part completed.
     */
    @Deprecated
    public String getDefaultBaseUrl() {
        return config.getUpdateCenterUrl();
    }

    /**
     * Gets the plugin with the given name from the first {@link UpdateSite} to contain it.
     * @return Discovered {@link Plugin}. {@code null} if it cannot be found
     */
    public @CheckForNull Plugin getPlugin(String artifactId) {
        for (UpdateSite s : sites) {
            Plugin p = s.getPlugin(artifactId);
            if (p != null) return p;
        }
        return null;
    }

    /**
     * Gets the plugin with the given name from the first {@link UpdateSite} to contain it.
     * @return Discovered {@link Plugin}. {@code null} if it cannot be found
     */
    public @CheckForNull Plugin getPlugin(String artifactId, @CheckForNull VersionNumber minVersion) {
        if (minVersion == null) {
            return getPlugin(artifactId);
        }
        for (UpdateSite s : sites) {
            Plugin p = s.getPlugin(artifactId);
            if (checkMinVersion(p, minVersion)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Gets plugin info from all available sites
     * @return list of plugins
     */
    @Restricted(NoExternalUse.class)
    public @NonNull List<Plugin> getPluginFromAllSites(String artifactId,
            @CheckForNull VersionNumber minVersion) {
        ArrayList<Plugin> result = new ArrayList<>();
        for (UpdateSite s : sites) {
            Plugin p = s.getPlugin(artifactId);
            if (checkMinVersion(p, minVersion)) {
                result.add(p);
            }
        }
        return result;
    }

    private boolean checkMinVersion(@CheckForNull Plugin p, @CheckForNull VersionNumber minVersion) {
        return p != null
                && (minVersion == null || !minVersion.isNewerThan(new VersionNumber(p.version)));
    }

    /**
     * Schedules a Jenkins upgrade.
     */
    @RequirePOST
    public void doUpgrade(StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        HudsonUpgradeJob job = new HudsonUpgradeJob(getCoreSource(), Jenkins.getAuthentication2());
        if (!Lifecycle.get().canRewriteHudsonWar()) {
            sendError("Jenkins upgrade not supported in this running mode");
            return;
        }

        LOGGER.info("Scheduling the core upgrade");
        addJob(job);
        rsp.sendRedirect2(".");
    }

    /**
     * Invalidates the update center JSON data for all the sites and force re-retrieval.
     *
     * @since 1.432
     */
    @RequirePOST
    public HttpResponse doInvalidateData() {
        for (UpdateSite site : sites) {
            site.doInvalidateData();
        }

        return HttpResponses.ok();
    }


    /**
     * Schedules a Jenkins restart.
     */
    @RequirePOST
    public void doSafeRestart(StaplerRequest2 request, StaplerResponse2 response) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        synchronized (jobs) {
            if (!isRestartScheduled()) {
                addJob(new RestartJenkinsJob(getCoreSource()));
                LOGGER.info("Scheduling Jenkins reboot");
            }
        }
        response.sendRedirect2(".");
    }

    /**
     * Cancel all scheduled jenkins restarts
     */
    @RequirePOST
    public void doCancelRestart(StaplerResponse2 response) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        synchronized (jobs) {
            for (UpdateCenterJob job : jobs) {
                if (job instanceof RestartJenkinsJob) {
                    if (((RestartJenkinsJob) job).cancel()) {
                        LOGGER.info("Scheduled Jenkins reboot unscheduled");
                    }
                }
            }
        }
        response.sendRedirect2(".");
    }

    /**
     * If any of the executed {@link UpdateCenterJob}s requires a restart
     * to take effect, this method returns true.
     *
     * <p>
     * This doesn't necessarily mean the user has scheduled or initiated
     * the restart operation.
     *
     * @see #isRestartScheduled()
     */
    @Exported
    public boolean isRestartRequiredForCompletion() {
        return requiresRestart;
    }

    /**
     * Checks if the restart operation is scheduled
     * (which means in near future Jenkins will restart by itself)
     *
     * @see #isRestartRequiredForCompletion()
     */
    public boolean isRestartScheduled() {
        for (UpdateCenterJob job : getJobs()) {
            if (job instanceof RestartJenkinsJob) {
                RestartJenkinsJob.RestartJenkinsJobStatus status = ((RestartJenkinsJob) job).status;
                if (status instanceof RestartJenkinsJob.Pending
                        || status instanceof RestartJenkinsJob.Running) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if backup of jenkins.war exists on the hard drive
     */
    public boolean isDowngradable() {
        return new File(Lifecycle.get().getHudsonWar() + ".bak").exists();
    }

    /**
     * Performs hudson downgrade.
     */
    @RequirePOST
    public void doDowngrade(StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (!isDowngradable()) {
            sendError("Jenkins downgrade is not possible, probably backup does not exist");
            return;
        }

        HudsonDowngradeJob job = new HudsonDowngradeJob(getCoreSource(), Jenkins.getAuthentication2());
        LOGGER.info("Scheduling the core downgrade");
        addJob(job);
        rsp.sendRedirect2(".");
    }

    /**
     * Performs hudson downgrade.
     */
    @RequirePOST
    public void doRestart(StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        HudsonDowngradeJob job = new HudsonDowngradeJob(getCoreSource(), Jenkins.getAuthentication2());
        LOGGER.info("Scheduling the core downgrade");

        addJob(job);
        rsp.sendRedirect2(".");
    }

    /**
     * Returns String with version of backup .war file,
     * if the file does not exists returns null
     */
    public String getBackupVersion() {
        try {
            try (JarFile backupWar = new JarFile(new File(Lifecycle.get().getHudsonWar() + ".bak"))) {
                Attributes attrs = backupWar.getManifest().getMainAttributes();
                String v = attrs.getValue("Jenkins-Version");
                if (v == null)   v = attrs.getValue("Hudson-Version");
                return v;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read backup version ", e);
            return null;
        }

    }

    @Restricted(NoExternalUse.class)
    public synchronized Future<UpdateCenterJob> addJob(UpdateCenterJob job) {
        if (job.site != null) {
            addConnectionCheckJob(job.site);
        }
        return job.submit();
    }

    private @NonNull ConnectionCheckJob addConnectionCheckJob(@NonNull UpdateSite site) {
        // Create a connection check job if the site was not already in the sourcesUsed set i.e. the first
        // job (in the jobs list) relating to a site must be the connection check job.
        if (sourcesUsed.add(site)) {
            ConnectionCheckJob connectionCheckJob = newConnectionCheckJob(site);
            connectionCheckJob.submit();
            return connectionCheckJob;
        } else {
            // Find the existing connection check job for that site and return it.
            ConnectionCheckJob connectionCheckJob = getConnectionCheckJob(site);
            if (connectionCheckJob != null) {
                return connectionCheckJob;
            } else {
                throw new IllegalStateException("Illegal addition of an UpdateCenter job without calling UpdateCenter.addJob. " +
                        "No ConnectionCheckJob found for the site.");
            }
        }
    }

    /**
     * Create a {@link ConnectionCheckJob} for the specified update site.
     * <p>
     * Does not start/submit the job.
     * @param site The site  for which the Job is to be created.
     * @return A {@link ConnectionCheckJob} for the specified update site.
     */
    @Restricted(NoExternalUse.class)
    ConnectionCheckJob newConnectionCheckJob(UpdateSite site) {
        return new ConnectionCheckJob(site);
    }

    private @CheckForNull ConnectionCheckJob getConnectionCheckJob(@NonNull String siteId) {
        UpdateSite site = getSite(siteId);
        if (site == null) {
            return null;
        }
        return getConnectionCheckJob(site);
    }

    private @CheckForNull ConnectionCheckJob getConnectionCheckJob(@NonNull UpdateSite site) {
        synchronized (jobs) {
            for (UpdateCenterJob job : jobs) {
                if (job instanceof ConnectionCheckJob && job.site != null && job.site.getId().equals(site.getId())) {
                    return (ConnectionCheckJob) job;
                }
            }
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.UpdateCenter_DisplayName();
    }

    @Override
    public String getSearchUrl() {
        return "updateCenter";
    }

    /**
     * Saves the configuration info to the disk.
     */
    @Override
    public synchronized void save() {
        if (BulkChange.contains(this))   return;
        try {
            getConfigFile().write(sites);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     */
    @Override
    public synchronized void load() throws IOException {
        XmlFile file = getConfigFile();
        if (file.exists()) {
            try {
                sites.replaceBy(((PersistedList) file.unmarshal(sites)).toList());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + file, e);
            }
            boolean defaultSiteExists = false;
            for (UpdateSite site : sites) {
                // replace the legacy site with the new site
                if (site.isLegacyDefault()) {
                    sites.remove(site);
                } else if (ID_DEFAULT.equals(site.getId())) {
                    defaultSiteExists = true;
                }
            }
            if (!defaultSiteExists) {
                sites.add(createDefaultUpdateSite());
            }
        } else {
            if (sites.isEmpty()) {
                // If there aren't already any UpdateSources, add the default one.
                // to maintain compatibility with existing UpdateCenterConfiguration, create the default one as specified by UpdateCenterConfiguration
                sites.add(createDefaultUpdateSite());
            }
        }
        siteDataLoading = false;
    }

    protected UpdateSite createDefaultUpdateSite() {
        return new UpdateSite(PREDEFINED_UPDATE_SITE_ID, config.getUpdateCenterUrl() + "update-center.json");
    }

    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(Jenkins.get().root,
                                    UpdateCenter.class.getName() + ".xml"));
    }

    @Exported
    public List<Plugin> getAvailables() {
        Map<String, Plugin> pluginMap = new LinkedHashMap<>();
        for (UpdateSite site : sites) {
            for (Plugin plugin : site.getAvailables()) {
                final Plugin existing = pluginMap.get(plugin.name);
                if (existing == null) {
                    pluginMap.put(plugin.name, plugin);
                } else if (!existing.version.equals(plugin.version)) {
                    // allow secondary update centers to publish different versions
                    // TODO refactor to consolidate multiple versions of the same plugin within the one row
                    final String altKey = plugin.name + ":" + plugin.version;
                    if (!pluginMap.containsKey(altKey)) {
                        pluginMap.put(altKey, plugin);
                    }
                }
            }
        }

        return new ArrayList<>(pluginMap.values());
    }

    /**
     * Returns a list of plugins that should be shown in the "available" tab, grouped by category.
     * A plugin with multiple categories will appear multiple times in the list.
     * @deprecated use {@link #getAvailables()}
     */
    @Deprecated
    public PluginEntry[] getCategorizedAvailables() {
        TreeSet<PluginEntry> entries = new TreeSet<>();
        for (Plugin p : getAvailables()) {
            if (p.categories == null || p.categories.length == 0)
                entries.add(new PluginEntry(p, getCategoryDisplayName(null)));
            else
                for (String c : p.categories)
                    entries.add(new PluginEntry(p, getCategoryDisplayName(c)));
        }
        return entries.toArray(new PluginEntry[0]);
    }

    @Restricted(NoExternalUse.class) // Jelly only
    public static String getCategoryDisplayName(String category) {
        if (category == null)
            return Messages.UpdateCenter_PluginCategory_misc();
        try {
            return (String) Messages.class.getMethod(
                    "UpdateCenter_PluginCategory_" + category.replace('-', '_')).invoke(null);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            return category;
        }
    }

    public List<Plugin> getUpdates() {
        Map<String, Plugin> pluginMap = new LinkedHashMap<>();
        final Map<String, Set<Plugin>> incompatiblePluginMap = new LinkedHashMap<>();
        final PluginManager.MetadataCache cache = new PluginManager.MetadataCache();

        for (UpdateSite site : sites) {
            for (Plugin plugin : site.getUpdates()) {
                final Plugin existing = pluginMap.get(plugin.name);
                if (existing == null) {
                    pluginMap.put(plugin.name, plugin);

                    if (!plugin.isNeededDependenciesCompatibleWithInstalledVersion()) {
                       for (Plugin incompatiblePlugin : plugin.getDependenciesIncompatibleWithInstalledVersion(cache)) {
                           incompatiblePluginMap.computeIfAbsent(incompatiblePlugin.name, _ignored -> new HashSet<>()).add(plugin);
                       }
                    }
                } else if (!existing.version.equals(plugin.version)) {
                    // allow secondary update centers to publish different versions
                    // TODO refactor to consolidate multiple versions of the same plugin within the one row
                    final String altKey = plugin.name + ":" + plugin.version;
                    if (!pluginMap.containsKey(altKey)) {
                        pluginMap.put(altKey, plugin);
                    }
                }
            }
        }

        incompatiblePluginMap.forEach((key, incompatiblePlugins) -> pluginMap.computeIfPresent(key, (_ignored, plugin) -> {
            plugin.setIncompatibleParentPlugins(incompatiblePlugins);
            return plugin;
        }));

        return new ArrayList<>(pluginMap.values());
    }

    // for Jelly
    @Restricted(NoExternalUse.class)
    public boolean hasIncompatibleUpdates(PluginManager.MetadataCache cache) {
        return getUpdates().stream().anyMatch(plugin -> !plugin.isCompatible(cache));
    }

    /**
     * Ensure that all UpdateSites are up to date, without requiring a user to
     * browse to the instance.
     *
     * @return a list of {@link FormValidation} for each updated Update Site
     * @since 1.501
     */
    public List<FormValidation> updateAllSites() throws InterruptedException, ExecutionException {
        List<Future<FormValidation>> futures = new ArrayList<>();
        for (UpdateSite site : getSites()) {
            Future<FormValidation> future = site.updateDirectly();
            if (future != null) {
                futures.add(future);
            }
        }

        List<FormValidation> results = new ArrayList<>();
        for (Future<FormValidation> f : futures) {
            results.add(f.get());
        }
        return results;
    }

    /**
     * {@link AdministrativeMonitor} that checks if there's Jenkins update.
     */
    @Extension @Symbol("coreUpdate")
    public static final class CoreUpdateMonitor extends AdministrativeMonitor {

        @Override
        public String getDisplayName() {
            return Messages.UpdateCenter_CoreUpdateMonitor_DisplayName();
        }

        @Override
        public boolean isActivated() {
            if (!Jenkins.get().getUpdateCenter().isSiteDataReady()) {
                // Do not display monitor during this page load, but possibly later.
                return false;
            }
            Data data = getData();
            return data != null && data.hasCoreUpdates();
        }

        public Data getData() {
            UpdateSite cs = Jenkins.get().getUpdateCenter().getCoreSource();
            if (cs != null)   return cs.getData();
            return null;
        }

        @Override
        public Permission getRequiredPermission() {
            return Jenkins.SYSTEM_READ;
        }
    }


    /**
     * Strategy object for controlling the update center's behaviors.
     *
     * <p>
     * Until 1.333, this extension point used to control the configuration of
     * where to get updates (hence the name of this class), but with the introduction
     * of multiple update center sites capability, that functionality is achieved by
     * simply installing another {@link UpdateSite}.
     *
     * <p>
     * See {@link UpdateSite} for how to manipulate them programmatically.
     *
     * @since 1.266
     */
    @SuppressWarnings("UnusedDeclaration")
    public static class UpdateCenterConfiguration implements ExtensionPoint {
        /**
         * Creates default update center configuration - uses settings for global update center.
         */
        public UpdateCenterConfiguration() {
        }

        /**
         * Check network connectivity by trying to establish a connection to
         * the host in connectionCheckUrl.
         *
         * @param job The connection checker that is invoking this strategy.
         * @param connectionCheckUrl A string containing the URL of a domain
         *          that is assumed to be always available.
         * @throws IOException if a connection can't be established
         */
        public void checkConnection(ConnectionCheckJob job, String connectionCheckUrl) throws IOException {
            testConnection(new URL(connectionCheckUrl));
        }

        /**
         * Check connection to update center server.
         *
         * @param job The connection checker that is invoking this strategy.
         * @param updateCenterUrl A sting containing the URL of the update center host.
         * @throws IOException if a connection to the update center server can't be established.
         */
        public void checkUpdateCenter(ConnectionCheckJob job, String updateCenterUrl) throws IOException {
            testConnection(toUpdateCenterCheckUrl(updateCenterUrl));
        }

        /**
         * Converts an update center URL into the URL to use for checking its connectivity.
         * @param updateCenterUrl the URL to convert.
         * @return the converted URL.
         * @throws MalformedURLException if the supplied URL is malformed.
         */
        static URL toUpdateCenterCheckUrl(String updateCenterUrl) throws MalformedURLException {
            URL url;
            if (updateCenterUrl.startsWith("http://") || updateCenterUrl.startsWith("https://")) {
                url = new URL(updateCenterUrl + (updateCenterUrl.indexOf('?') == -1 ? "?uctest" : "&uctest"));
            } else {
                url = new URL(updateCenterUrl);
            }
            return url;
        }

        /**
         * Validate the URL of the resource before downloading it.
         *
         * @param job The download job that is invoking this strategy. This job is
         *          responsible for managing the status of the download and installation.
         * @param src The location of the resource on the network
         * @throws IOException if the validation fails
         */
        public void preValidate(DownloadJob job, URL src) throws IOException {
            if (job.site != null) {
                job.site.preValidate(src);
            }
        }

        /**
         * Validate the resource after it has been downloaded, before it is
         * installed. The default implementation does nothing.
         *
         * @param job The download job that is invoking this strategy. This job is
         *          responsible for managing the status of the download and installation.
         * @param src The location of the downloaded resource.
         * @throws IOException if the validation fails.
         */
        public void postValidate(DownloadJob job, File src) throws IOException {
        }

        /**
         * Download a plugin or core upgrade in preparation for installing it
         * into its final location. Implementations will normally download the
         * resource into a temporary location and hand off a reference to this
         * location to the install or upgrade strategy to move into the final location.
         *
         * @param job The download job that is invoking this strategy. This job is
         *          responsible for managing the status of the download and installation.
         * @param src The URL to the resource to be downloaded.
         * @return A File object that describes the downloaded resource.
         * @throws IOException if there were problems downloading the resource.
         * @see DownloadJob
         */
        @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_SHA1", justification = "SHA-1 is only used as a fallback if SHA-256/SHA-512 are not available")
        public File download(DownloadJob job, URL src) throws IOException {
            MessageDigest sha1 = null;
            MessageDigest sha256 = null;
            MessageDigest sha512 = null;
            try {
                // Java spec says SHA-1 and SHA-256 exist, and SHA-512 might not, so one try/catch block should be fine
                sha1 = MessageDigest.getInstance("SHA-1");
                sha256 = MessageDigest.getInstance("SHA-256");
                sha512 = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException nsa) {
                LOGGER.log(Level.WARNING, "Failed to instantiate message digest algorithm, may only have weak or no verification of downloaded file", nsa);
            }

            URLConnection con = null;
            try {
                con = connect(job, src);
                //JENKINS-34174 - set timeout for downloads, may hang indefinitely
                // particularly noticeable during 2.0 install when downloading
                // many plugins
                con.setReadTimeout(PLUGIN_DOWNLOAD_READ_TIMEOUT);

                long total;
                final long sizeFromMetadata = job.getContentLength();
                if (sizeFromMetadata == -1) {
                    // Update site does not advertise a file size, so fall back to download file size, if any
                    total = con.getContentLength();
                } else {
                    total = sizeFromMetadata;
                }
                byte[] buf = new byte[8192];
                int len;

                File dst = job.getDestination();
                File tmp = new File(dst.getPath() + ".tmp");

                LOGGER.info("Downloading " + job.getName());
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                t.setName(oldName + ": " + src);
                try (OutputStream _out = Files.newOutputStream(tmp.toPath());
                     OutputStream out =
                             sha1 != null ? new DigestOutputStream(
                                     sha256 != null ? new DigestOutputStream(
                                             sha512 != null ? new DigestOutputStream(_out, sha512) : _out, sha256) : _out, sha1) : _out;
                     InputStream in = con.getInputStream();
                     CountingInputStream cin = new CountingInputStream(in)) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        var sourceUrlString = getSourceUrl(src, con);
                        LOGGER.fine(() -> "Downloading " + job.getName() + " from " + sourceUrlString);
                    }
                    while ((len = cin.read(buf)) >= 0) {
                        out.write(buf, 0, len);
                        final int count = cin.getCount();
                        job.status = job.new Installing(total == -1 ? -1 : ((int) (count * 100 / total)));
                        if (total != -1 && total < count) {
                            throw new IOException("Received more data than expected. Expected " + total + " bytes but got " + count + " bytes (so far), aborting download.");
                        }
                    }
                } catch (IOException | InvalidPathException e) {
                    throw new IOException("Failed to load " + src + " to " + tmp, e);
                } finally {
                    t.setName(oldName);
                }

                if (total != -1 && total != tmp.length()) {
                    // don't know exactly how this happens, but report like
                    // http://www.ashlux.com/wordpress/2009/08/14/hudson-and-the-sonar-plugin-fail-maveninstallation-nosuchmethoderror/
                    // indicates that this kind of inconsistency can happen. So let's be defensive
                    throw new IOException("Inconsistent file length: expected " + total + " but only got " + tmp.length());
                }

                if (sha1 != null) {
                    byte[] digest = sha1.digest();
                    job.computedSHA1 = Base64.getEncoder().encodeToString(digest);
                }
                if (sha256 != null) {
                    byte[] digest = sha256.digest();
                    job.computedSHA256 = Base64.getEncoder().encodeToString(digest);
                }
                if (sha512 != null) {
                    byte[] digest = sha512.digest();
                    job.computedSHA512 = Base64.getEncoder().encodeToString(digest);
                }
                return tmp;
            } catch (IOException e) {
                // assist troubleshooting in case of e.g. "too many redirects" by printing actual URL
                throw new IOException("Failed to download from " + getSourceUrl(src, con), e);
            }
        }

        private static String getSourceUrl(@NonNull URL src, @CheckForNull URLConnection connection) {
            var sourceUrlString = src.toExternalForm();
            if (connection != null) {
                var connectionURL = connection.getURL();
                if (connectionURL != null) {
                    var finalUrlString = connectionURL.toExternalForm();
                    if (!sourceUrlString.equals(finalUrlString)) {
                        return sourceUrlString + " → " + finalUrlString;
                    }
                }
            }
            return sourceUrlString;
        }

        /**
         * Connects to the given URL for downloading the binary. Useful for tweaking
         * how the connection gets established.
         */
        protected URLConnection connect(DownloadJob job, URL src) throws IOException {
            if (job.site != null) {
                return job.site.connect(src);
            }
            // fall back to just using the normal ProxyConfiguration if the site is null
            return ProxyConfiguration.open(src);
        }

        /**
         * Called after a plugin has been downloaded to move it into its final
         * location. The default implementation is a file rename.
         *
         * @param job The install job that is invoking this strategy.
         * @param src The temporary location of the plugin.
         * @param dst The final destination to install the plugin to.
         * @throws IOException if there are problems installing the resource.
         */
        public void install(DownloadJob job, File src, File dst) throws IOException {
            job.replace(dst, src);
        }

        /**
         * Called after an upgrade has been downloaded to move it into its final
         * location. The default implementation is a file rename.
         *
         * @param job The upgrade job that is invoking this strategy.
         * @param src The temporary location of the upgrade.
         * @param dst The final destination to install the upgrade to.
         * @throws IOException if there are problems installing the resource.
         */
        public void upgrade(DownloadJob job, File src, File dst) throws IOException {
            job.replace(dst, src);
        }

        /**
         * Returns an "always up" server for Internet connectivity testing.
         *
         * @deprecated as of 1.333
         *      With the introduction of multiple update center capability, this information
         *      is now a part of the {@code update-center.json} file. See
         *      {@code http://jenkins-ci.org/update-center.json} as an example.
         */
        @Deprecated
        public String getConnectionCheckUrl() {
            return "http://www.google.com";
        }

        /**
         * Returns the URL of the server that hosts the update-center.json
         * file.
         *
         * @return
         *      Absolute URL that ends with '/'.
         * @deprecated as of 1.333
         *      With the introduction of multiple update center capability, this information
         *      is now moved to {@link UpdateSite}.
         */
        @Deprecated
        public String getUpdateCenterUrl() {
            return UPDATE_CENTER_URL;
        }

        /**
         * Returns the URL of the server that hosts plugins and core updates.
         *
         * @deprecated as of 1.333
         *      {@code update-center.json} is now signed, so we don't have to further make sure that
         *      we aren't downloading from anywhere unsecure.
         */
        @Deprecated
        public String getPluginRepositoryBaseUrl() {
            return "http://jenkins-ci.org/";
        }


        private void testConnection(URL url) throws IOException {
            try {
                URLConnection connection = ProxyConfiguration.open(url);

                if (connection instanceof HttpURLConnection) {
                    int responseCode = ((HttpURLConnection) connection).getResponseCode();
                    if (HttpURLConnection.HTTP_OK != responseCode) {
                        throw new HttpRetryException("Invalid response code (" + responseCode + ") from URL: " + url, responseCode);
                    }
                } else {
                    try (InputStream is = connection.getInputStream(); OutputStream os = OutputStream.nullOutputStream()) {
                        IOUtils.copy(is, os);
                    }
                }
            } catch (SSLHandshakeException e) {
                if (e.getMessage().contains("PKIX path building failed"))
                   // fix up this crappy error message from JDK
                    throw new IOException("Failed to validate the SSL certificate of " + url, e);
            }
        }
    }

    /**
     * Things that {@link UpdateCenter#installerService} executes.
     *
     * This object will have the {@code row.jelly} which renders the job on UI.
     */
    @ExportedBean
    public abstract class UpdateCenterJob implements Runnable {
        /**
         * Unique ID that identifies this job.
         *
         * @see UpdateCenter#getJob(int)
         */
        @Exported
        public final int id = iota.incrementAndGet();

        /**
         * Which {@link UpdateSite} does this belong to?
         */
        public final @CheckForNull UpdateSite site;

        /**
         * Simple correlation ID that can be used to associated a batch of jobs e.g. the
         * installation of a set of plugins.
         */
        private UUID correlationId = null;

        /**
         * If this job fails, set to the error.
         */
        protected Throwable error;

        protected UpdateCenterJob(@CheckForNull UpdateSite site) {
            this.site = site;
        }

        public Api getApi() {
            return new Api(this);
        }

        public UUID getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(UUID correlationId) {
            if (this.correlationId != null) {
                throw new IllegalStateException("Illegal call to set the 'correlationId'. Already set.");
            }
            this.correlationId = correlationId;
        }

        /**
         * @deprecated as of 1.326
         *      Use {@link #submit()} instead.
         */
        @Deprecated
        public void schedule() {
            submit();
        }

        @Exported
        public String getType() {
            return getClass().getSimpleName();
        }

        /**
         * Schedules this job for an execution
         * @return
         *      {@link Future} to keeps track of the status of the execution.
         */
        public Future<UpdateCenterJob> submit() {
            LOGGER.fine("Scheduling " + this + " to installerService");
            // TODO: seems like this access to jobs should be synchronized, no?
            // It might get synch'd accidentally via the addJob method, but that wouldn't be good.
            jobs.add(this);
            return installerService.submit(this, this);
        }

        @Exported
        public String getErrorMessage() {
            return error != null ? error.getMessage() : null;
        }

        public Throwable getError() {
            return error;
        }
    }

    /**
     * Restarts jenkins.
     */
    public class RestartJenkinsJob extends UpdateCenterJob {
         /**
         * Immutable state of this job.
         */
         @Exported(inline = true)
        public volatile RestartJenkinsJobStatus status = new RestartJenkinsJob.Pending();

        /**
         * The name of the user that started this job
         */
        private String authentication;

        /**
         * Cancel job
         */
        public synchronized boolean cancel() {
            if (status instanceof RestartJenkinsJob.Pending) {
                status = new RestartJenkinsJob.Canceled();
                return true;
            }
            return false;
        }

        public RestartJenkinsJob(UpdateSite site) {
            super(site);
            this.authentication = Jenkins.getAuthentication2().getName();
        }

        @Override
        public synchronized void run() {
            if (!(status instanceof RestartJenkinsJob.Pending)) {
                return;
            }
            status = new RestartJenkinsJob.Running();
            try {
                // safeRestart records the current authentication for the log, so set it to the managing user
                try (ACLContext acl = ACL.as(User.get(authentication, false, Collections.emptyMap()))) {
                    Jenkins.get().safeRestart();
                }
            } catch (RestartNotSupportedException exception) {
                // ignore if restart is not allowed
                status = new RestartJenkinsJob.Failure();
                error = exception;
            }
        }

        @ExportedBean
        public abstract class RestartJenkinsJobStatus {
            @Exported
            public final int id = iota.incrementAndGet();

        }

        public class Pending extends RestartJenkinsJobStatus {
            @Exported
            public String getType() {
                return getClass().getSimpleName();
            }
        }

        public class Running extends RestartJenkinsJobStatus {

        }

        public class Failure extends RestartJenkinsJobStatus {

        }

        public class Canceled extends RestartJenkinsJobStatus {

        }
    }

    /**
     * Tests the internet connectivity.
     */
    public final class ConnectionCheckJob extends UpdateCenterJob {
        private final Vector<String> statuses = new Vector<>();

        final Map<String, ConnectionStatus> connectionStates = new ConcurrentHashMap<>();

        public ConnectionCheckJob(UpdateSite site) {
            super(site);
            connectionStates.put(ConnectionStatus.INTERNET, ConnectionStatus.PRECHECK);
            connectionStates.put(ConnectionStatus.UPDATE_SITE, ConnectionStatus.PRECHECK);
        }

        @Override
        public void run() {
            connectionStates.put(ConnectionStatus.INTERNET, ConnectionStatus.UNCHECKED);
            connectionStates.put(ConnectionStatus.UPDATE_SITE, ConnectionStatus.UNCHECKED);
            if (site == null || ID_UPLOAD.equals(site.getId())) {
                return;
            }
            LOGGER.fine("Doing a connectivity check");
            Future<?> internetCheck = null;
            try {
                final String connectionCheckUrl = site.getConnectionCheckUrl();
                if (connectionCheckUrl != null) {
                    connectionStates.put(ConnectionStatus.INTERNET, ConnectionStatus.CHECKING);
                    statuses.add(Messages.UpdateCenter_Status_CheckingInternet());
                    // Run the internet check in parallel
                    internetCheck = updateService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                config.checkConnection(ConnectionCheckJob.this, connectionCheckUrl);
                            } catch (Exception e) {
                                if (e.getMessage().contains("Connection timed out")) {
                                    // Google can't be down, so this is probably a proxy issue
                                    connectionStates.put(ConnectionStatus.INTERNET, ConnectionStatus.FAILED);
                                    statuses.add(Messages.UpdateCenter_Status_ConnectionFailed(Functions.xmlEscape(connectionCheckUrl), Jenkins.get().getRootUrl()));
                                    return;
                                }
                            }
                            connectionStates.put(ConnectionStatus.INTERNET, ConnectionStatus.OK);
                        }
                    });
                } else {
                    LOGGER.log(WARNING, "Update site ''{0}'' does not declare the connection check URL. "
                            + "Skipping the network availability check.", site.getId());
                    connectionStates.put(ConnectionStatus.INTERNET, ConnectionStatus.SKIPPED);
                }

                connectionStates.put(ConnectionStatus.UPDATE_SITE, ConnectionStatus.CHECKING);
                statuses.add(Messages.UpdateCenter_Status_CheckingJavaNet());

                config.checkUpdateCenter(this, site.getUrl());

                connectionStates.put(ConnectionStatus.UPDATE_SITE, ConnectionStatus.OK);
                statuses.add(Messages.UpdateCenter_Status_Success());
            } catch (UnknownHostException e) {
                connectionStates.put(ConnectionStatus.UPDATE_SITE, ConnectionStatus.FAILED);
                statuses.add(Messages.UpdateCenter_Status_UnknownHostException(Functions.xmlEscape(e.getMessage()), Jenkins.get().getRootUrl()));
                addStatus(e);
                error = e;
            } catch (Exception e) {
                connectionStates.put(ConnectionStatus.UPDATE_SITE, ConnectionStatus.FAILED);
                addStatus(e);
                error = e;
            }

            if (internetCheck != null) {
                try {
                    // Wait for internet check to complete
                    internetCheck.get();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error completing internet connectivity check: " + e.getMessage(), e);
                }
            }
        }

        private void addStatus(Throwable e) {
            statuses.add("<pre>" + Functions.xmlEscape(Functions.printThrowable(e)) + "</pre>");
        }

        public String[] getStatuses() {
            synchronized (statuses) {
                return statuses.toArray(new String[0]);
            }
        }


    }

    /**
     * Enables a required plugin, provides feedback in the update center
     */
    public class EnableJob extends InstallationJob {
        public EnableJob(UpdateSite site, Authentication auth, @NonNull Plugin plugin, boolean dynamicLoad) {
            super(plugin, site, auth, dynamicLoad);
        }

        public Plugin getPlugin() {
            return plugin;
        }

        @Override
        public void run() {
            try {
                PluginWrapper installed = plugin.getInstalled();
                synchronized (installed) {
                    if (!installed.isEnabled()) {
                        try {
                            installed.enable();
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Failed to enable " + plugin.getDisplayName(), e);
                            error = e;
                            status = new DownloadJob.Failure(e);
                        }

                        if (dynamicLoad) {
                            try {
                                // remove the existing, disabled inactive plugin to force a new one to load
                                pm.dynamicLoad(getDestination(), true, null);
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "Failed to dynamically load " + plugin.getDisplayName(), e);
                                error = e;
                                requiresRestart = true;
                                status = new DownloadJob.Failure(e);
                            }
                        } else {
                            requiresRestart = true;
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "An unexpected error occurred while attempting to enable " + plugin.getDisplayName(), e);
                error = e;
                requiresRestart = true;
                status = new DownloadJob.Failure(e);
            }
            if (status instanceof DownloadJob.Pending) {
                status = new DownloadJob.Success();
            }
        }
    }

    /**
     * A no-op, e.g. this plugin is already installed
     */
    public class NoOpJob extends EnableJob {
        public NoOpJob(UpdateSite site, Authentication auth, @NonNull Plugin plugin) {
            super(site, auth, plugin, false);
        }

        @Override
        public void run() {
            // do nothing
            status = new DownloadJob.Success();
        }
    }

    @Restricted(NoExternalUse.class)
    /*package*/ interface WithComputedChecksums {
        String getComputedSHA1();

        String getComputedSHA256();

        String getComputedSHA512();
    }

    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_SHA1", justification = "SHA-1 is only used as a fallback if SHA-256/SHA-512 are not available")
    private static class FileWithComputedChecksums implements WithComputedChecksums {

        private final File file;

        private String computedSHA1;
        private String computedSHA256;
        private String computedSHA512;

        FileWithComputedChecksums(File file) {
            this.file = Objects.requireNonNull(file);
        }

        @Override
        public synchronized String getComputedSHA1() {
            if (computedSHA1 != null) {
                return computedSHA1;
            }

            MessageDigest messageDigest;
            try {
                messageDigest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException(e);
            }
            computedSHA1 = computeDigest(messageDigest);
            return computedSHA1;
        }

        @Override
        public synchronized String getComputedSHA256() {
            if (computedSHA256 != null) {
                return computedSHA256;
            }

            MessageDigest messageDigest;
            try {
                messageDigest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException(e);
            }
            computedSHA256 = computeDigest(messageDigest);
            return computedSHA256;
        }

        @Override
        public synchronized String getComputedSHA512() {
            if (computedSHA512 != null) {
                return computedSHA512;
            }

            MessageDigest messageDigest;
            try {
                messageDigest = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException(e);
            }
            computedSHA512 = computeDigest(messageDigest);
            return computedSHA512;
        }

        private String computeDigest(MessageDigest digest) {
            try (InputStream is = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(is)) {
                byte[] buffer = new byte[1024];
                int read = bis.read(buffer, 0, buffer.length);
                while (read > -1) {
                    digest.update(buffer, 0, read);
                    read = bis.read(buffer, 0, buffer.length);
                }
                return Base64.getEncoder().encodeToString(digest.digest());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Base class for a job that downloads a file from the Jenkins project.
     */
    public abstract class DownloadJob extends UpdateCenterJob implements WithComputedChecksums {
        /**
         * Immutable object representing the current state of this job.
         */
        @Exported(inline = true)
        public volatile InstallationStatus status = new DownloadJob.Pending();

        /**
         * Where to download the file from.
         */
        protected abstract URL getURL() throws MalformedURLException;

        /**
         * Where to download the file to.
         */
        protected abstract File getDestination();

        /**
         * Code name used for logging.
         */
        @Exported
        public abstract String getName();

        /**
         * Display name used for the GUI.
         * @since 2.189
         */
        public String getDisplayName() {
            return getName();
        }

        /**
         * Called when the whole thing went successfully.
         */
        protected abstract void onSuccess();

        /**
         * During download, an attempt is made to compute the SHA-1 checksum of the file.
         * This is the base64 encoded SHA-1 checksum.
         *
         * @since 1.641
         */
        @Override
        @CheckForNull
        public String getComputedSHA1() {
            return computedSHA1;
        }

        private String computedSHA1;

        /**
         * Base64 encoded SHA-256 checksum of the downloaded file, if it could be computed.
         *
         * @since 2.130
         */
        @Override
        @CheckForNull
        public String getComputedSHA256() {
            return computedSHA256;
        }

        private String computedSHA256;

        /**
         * Base64 encoded SHA-512 checksum of the downloaded file, if it could be computed.
         *
         * @since 2.130
         */
        @Override
        @CheckForNull
        public String getComputedSHA512() {
            return computedSHA512;
        }

        private String computedSHA512;

        private Authentication authentication;

        /**
         * Get the user that initiated this job
         */
        public Authentication getUser() {
            return this.authentication;
        }

        protected DownloadJob(UpdateSite site, Authentication authentication) {
            super(site);
            this.authentication = authentication;
        }

        @Override
        public void run() {
            try {
                LOGGER.info("Starting the installation of " + getName() + " on behalf of " + getUser().getName());

                _run();

                LOGGER.info("Installation successful: " + getName());
                status = new DownloadJob.Success();
                onSuccess();
            } catch (InstallationStatus e) {
                status = e;
                if (status.isSuccess()) onSuccess();
                requiresRestart |= status.requiresRestart();
            } catch (MissingDependencyException e) {
                LOGGER.log(Level.SEVERE, "Failed to install {0}: {1}", new Object[] { getName(), e.getMessage() });
                status = new DownloadJob.Failure(e);
                error = e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to install " + getName(), e);
                status = new DownloadJob.Failure(e);
                error = e;
            }
        }

        protected void _run() throws IOException, InstallationStatus {
            URL src = getURL();

            config.preValidate(this, src);

            File dst = getDestination();
            File tmp = config.download(this, src);

            config.postValidate(this, tmp);
            config.install(this, tmp, dst);
        }

        /**
         * Called when the download is completed to overwrite
         * the old file with the new file.
         */
        protected synchronized void replace(File dst, File src) throws IOException {
            File bak = Util.changeExtension(dst, ".bak");
            moveAtomically(dst, bak);
            moveAtomically(src, dst);
        }

        /**
         * Indicate the expected size of the download as provided in update site
         * metadata.
         *
         * @return the expected size, or -1 if unknown.
         * @since 2.325
         */
        public long getContentLength() {
            return -1;
        }

        /**
         * Indicates the status or the result of a plugin installation.
         * <p>
         * Instances of this class is immutable.
         */
        @ExportedBean
        public abstract class InstallationStatus extends Throwable {
            public final int id = iota.incrementAndGet();

            @Exported
            public boolean isSuccess() {
                return false;
            }

            @Exported
            public final String getType() {
                return getClass().getSimpleName();
            }

            /**
             * Indicates that a restart is needed to complete the tasks.
             */
            public boolean requiresRestart() {
                return false;
            }
        }

        /**
         * Indicates that the installation of a plugin failed.
         */
        public class Failure extends InstallationStatus {
            public final Throwable problem;

            public Failure(Throwable problem) {
                this.problem = problem;
            }

            public String getProblemStackTrace() {
                return Functions.printThrowable(problem);
            }
        }

        /**
         * Indicates that the installation was successful but a restart is needed.
         */
        public class SuccessButRequiresRestart extends Success {
            private final Localizable message;

            public SuccessButRequiresRestart(Localizable message) {
                this.message = message;
            }

            @Override
            public String getMessage() {
                return message.toString();
            }

            @Override
            public boolean requiresRestart() {
                return true;
            }
        }

        /**
         * Indicates that the plugin was successfully installed.
         */
        public class Success extends InstallationStatus {
            @Override public boolean isSuccess() {
                return true;
            }
        }

        /**
         * Indicates that the plugin was successfully installed.
         */
        public class Skipped extends InstallationStatus {
            @Override public boolean isSuccess() {
                return true;
            }
        }

        /**
         * Indicates that the plugin is waiting for its turn for installation.
         */
        public class Pending extends InstallationStatus {
        }

        /**
         * Installation of a plugin is in progress.
         */
        public class Installing extends InstallationStatus {
            /**
             * % completed download, or -1 if the percentage is not known.
             */
            public final int percentage;

            public Installing(int percentage) {
                this.percentage = percentage;
            }
        }
    }

    /**
     * Compare the provided values and return the appropriate {@link VerificationResult}.
     *
     */
    private static VerificationResult verifyChecksums(String expectedDigest, String actualDigest, boolean caseSensitive) {
        if (expectedDigest == null) {
            return VerificationResult.NOT_PROVIDED;
        }

        if (actualDigest == null) {
            return VerificationResult.NOT_COMPUTED;
        }

        if (caseSensitive) {
            if (MessageDigest.isEqual(expectedDigest.getBytes(StandardCharsets.US_ASCII), actualDigest.getBytes(StandardCharsets.US_ASCII))) {
                return VerificationResult.PASS;
            }
        } else {
            if (MessageDigest.isEqual(expectedDigest.toLowerCase().getBytes(StandardCharsets.US_ASCII), actualDigest.toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                return VerificationResult.PASS;
            }
        }

        return VerificationResult.FAIL;
    }

    private enum VerificationResult {
        PASS,
        NOT_PROVIDED,
        NOT_COMPUTED,
        FAIL
    }

    /**
     * Throws an {@code IOException} with a message about {@code actual} not matching {@code expected} for {@code file} when using {@code algorithm}.
     */
    private static void throwVerificationFailure(String expected, String actual, File file, String algorithm) throws IOException {
        throw new IOException("Downloaded file " + file.getAbsolutePath() + " does not match expected " + algorithm + ", expected '" + expected + "', actual '" + actual + "'");
    }

    /**
     * Implements the checksum verification logic with fallback to weaker algorithm for {@link DownloadJob}.
     * @param job The job downloading the file to check
     * @param entry The metadata entry for the file to check
     * @param file The downloaded file
     * @throws IOException thrown when one of the checks failed, or no checksum could be computed.
     */
    @VisibleForTesting
    @Restricted(NoExternalUse.class)
    /* package */ static void verifyChecksums(WithComputedChecksums job, UpdateSite.Entry entry, File file) throws IOException {
        VerificationResult result512 = verifyChecksums(entry.getSha512(), job.getComputedSHA512(), false);
        switch (result512) {
            case PASS:
                // this has passed so no reason to check the weaker checksums
                return;
            case FAIL:
                throwVerificationFailure(entry.getSha512(), job.getComputedSHA512(), file, "SHA-512");
                break;
            case NOT_COMPUTED:
                LOGGER.log(WARNING, "Attempt to verify a downloaded file (" + file.getName() + ") using SHA-512 failed since it could not be computed. Falling back to weaker algorithms. Update your JRE.");
                break;
            case NOT_PROVIDED:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + result512);
        }

        VerificationResult result256 = verifyChecksums(entry.getSha256(), job.getComputedSHA256(), false);
        switch (result256) {
            case PASS:
                return;
            case FAIL:
                throwVerificationFailure(entry.getSha256(), job.getComputedSHA256(), file, "SHA-256");
                break;
            case NOT_COMPUTED:
            case NOT_PROVIDED:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + result256);
        }

        if (result512 == VerificationResult.NOT_PROVIDED && result256 == VerificationResult.NOT_PROVIDED) {
            LOGGER.log(INFO, "Attempt to verify a downloaded file (" + file.getName() + ") using SHA-512 or SHA-256 failed since your configured update site does not provide either of those checksums. Falling back to SHA-1.");
        }

        VerificationResult result1 = verifyChecksums(entry.getSha1(), job.getComputedSHA1(), true);
        switch (result1) {
            case PASS:
                return;
            case FAIL:
                throwVerificationFailure(entry.getSha1(), job.getComputedSHA1(), file, "SHA-1");
                break;
            case NOT_COMPUTED:
                throw new IOException("Failed to compute SHA-1 of downloaded file, refusing installation");
            case NOT_PROVIDED:
                throw new IOException("Unable to confirm integrity of downloaded file, refusing installation");
            default:
                throw new AssertionError("Unknown verification result: " + result1);
        }
    }

    /**
     * Represents the state of the installation activity of one plugin.
     */
    public class InstallationJob extends DownloadJob {
        /**
         * What plugin are we trying to install?
         */
        @Exported
        public final Plugin plugin;

        protected final PluginManager pm = Jenkins.get().getPluginManager();

        /**
         * True to load the plugin into this Jenkins, false to wait until restart.
         */
        protected final boolean dynamicLoad;

        @CheckForNull List<PluginWrapper> batch;

        /**
         * @deprecated as of 1.442
         */
        @Deprecated
        public InstallationJob(Plugin plugin, UpdateSite site, Authentication auth) {
            this(plugin, site, auth, false);
        }

        /**
         * @deprecated use {@link #InstallationJob(UpdateSite.Plugin, UpdateSite, Authentication, boolean)}
         */
        @Deprecated
        public InstallationJob(Plugin plugin, UpdateSite site, org.acegisecurity.Authentication auth, boolean dynamicLoad) {
            this(plugin, site, auth.toSpring(), dynamicLoad);
        }

        public InstallationJob(Plugin plugin, UpdateSite site, Authentication auth, boolean dynamicLoad) {
            super(site, auth);
            this.plugin = plugin;
            this.dynamicLoad = dynamicLoad;
        }

        @Override
        protected URL getURL() throws MalformedURLException {
            return new URL(plugin.url);
        }

        @Override
        protected File getDestination() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".jpi");
        }

        private File getLegacyDestination() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".hpi");
        }

        @Override
        public String getName() {
            return plugin.name;
        }

        @Override
        public String getDisplayName() {
            return plugin.getDisplayName();
        }

        @Override
        public long getContentLength() {
            final Long size = plugin.getFileSize();
            return size == null ? -1 : size;
        }

        @Override
        public void _run() throws IOException, InstallationStatus {
            if (wasInstalled()) {
                // Do this first so we can avoid duplicate downloads, too
                // check to see if the plugin is already installed at the same version and skip it
                LOGGER.info("Skipping duplicate install of: " + plugin.getDisplayName() + "@" + plugin.version);
                return;
            }
            try {
                File cached = getCached(this);
                if (cached != null) {
                    File dst = getDestination();

                    // A bit naive, but following the corresponding logic in UpdateCenterConfiguration#download...
                    File tmp = new File(dst.getPath() + ".tmp");
                    Files.copy(cached.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    config.postValidate(this, tmp);

                    /*
                     * Will unfortunately validate the checksum a second time, but this should still be faster than
                     * network I/O and at least allows us to reuse code...
                     */
                    config.install(this, tmp, dst);
                } else {
                    super._run();
                }

                // if this is a bundled plugin, make sure it won't get overwritten
                PluginWrapper pw = plugin.getInstalled();
                if (pw != null && pw.isBundled()) {
                    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                        pw.doPin();
                    }
                }

                if (dynamicLoad) {
                    try {
                        pm.dynamicLoad(getDestination(), false, batch);
                    } catch (RestartRequiredException e) {
                        throw new DownloadJob.SuccessButRequiresRestart(e.message);
                    } catch (Exception e) {
                        throw new IOException("Failed to dynamically deploy this plugin", e);
                    }
                } else {
                    throw new DownloadJob.SuccessButRequiresRestart(Messages._UpdateCenter_DownloadButNotActivated());
                }
            } finally {
                synchronized (this) {
                    // There may be other threads waiting on completion
                    LOGGER.fine("Install complete for: " + plugin.getDisplayName() + "@" + plugin.version);
                    // some status other than Installing or Downloading needs to be set here
                    // {@link #isAlreadyInstalling()}, it will be overwritten by {@link DownloadJob#run()}
                    status = new DownloadJob.Skipped();
                    notifyAll();
                }
            }
        }

        /**
         * If we happen to have the file already in the {@coode WEB-INF/detached-plugins} directory and it happens to
         * match the checksum we were expecting, then save ourselves a trip to the download site. This method is
         * best-effort, and if anything goes wrong we simply fall back to the standard download path.
         *
         * @return The cached file, or null for a cache miss
         */
        @CheckForNull
        private File getCached(DownloadJob job) {
            URL src;
            try {
                /*
                 * Could make PluginManager#getDetachedLocation public and consume it here, but this method is
                 * best-effort anyway.
                 */
                src = Jenkins.get().getServletContext().getResource(String.format("/WEB-INF/detached-plugins/%s.hpi", plugin.name));
            } catch (MalformedURLException e) {
                return null;
            }

            if (src == null || !"file".equals(src.getProtocol())) {
                return null;
            }

            try {
                config.preValidate(this, src);
            } catch (IOException e) {
                return null;
            }

            File cached;
            try {
                cached = new File(src.toURI());
            } catch (URISyntaxException e) {
                return null;
            }

            if (!cached.isFile()) {
                return null;
            }

            WithComputedChecksums withComputedChecksums = new FileWithComputedChecksums(cached);
            try {
                verifyChecksums(withComputedChecksums, plugin, cached);
            } catch (IOException | UncheckedIOException | UnsupportedOperationException e) {
                return null;
            }

            // Allow us to reuse UpdateCenter.InstallationJob#replace.
            job.computedSHA1 = withComputedChecksums.getComputedSHA1();
            job.computedSHA256 = withComputedChecksums.getComputedSHA256();
            job.computedSHA512 = withComputedChecksums.getComputedSHA512();

            return cached;
        }

        /**
         * Indicates there is another installation job for this plugin
         * @since 2.1
         */
        protected boolean wasInstalled() {
            synchronized (UpdateCenter.this) {
                for (UpdateCenterJob job : getJobs()) {
                    if (job == this) {
                        // oldest entries first, if we reach this instance,
                        // we need it to continue installing
                        return false;
                    }
                    if (job instanceof InstallationJob) {
                        InstallationJob ij = (InstallationJob) job;
                        if (ij.plugin.equals(plugin) && ij.plugin.version.equals(plugin.version)) {
                            // wait until other install is completed
                            synchronized (ij) {
                                if (ij.status instanceof DownloadJob.Installing || ij.status instanceof DownloadJob.Pending) {
                                    try {
                                        LOGGER.fine("Waiting for other plugin install of: " + plugin.getDisplayName() + "@" + plugin.version);
                                        ij.wait();
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                // Must check for success, otherwise may have failed installation
                                if (ij.status instanceof DownloadJob.Success) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                return false;
            }
        }

        @Override
        protected void onSuccess() {
            pm.pluginUploaded = true;
        }

        @Override
        public String toString() {
            return super.toString() + "[plugin=" + plugin.title + "]";
        }

        /**
         * Called when the download is completed to overwrite
         * the old file with the new file.
         */
        @Override
        protected void replace(File dst, File src) throws IOException {
            if (site == null || !site.getId().equals(ID_UPLOAD)) {
                verifyChecksums(this, plugin, src);
            }

            synchronized (this) {
                File bak = Util.changeExtension(dst, ".bak");

                final File legacy = getLegacyDestination();
                if (Files.exists(Util.fileToPath(legacy))) {
                    moveAtomically(legacy, bak);
                }
                if (Files.exists(Util.fileToPath(dst))) {
                    moveAtomically(dst, bak);
                }

                moveAtomically(src, dst);
            }
        }

        void setBatch(List<PluginWrapper> batch) {
            this.batch = batch;
        }

    }

    @Restricted(NoExternalUse.class)
    public final class CompleteBatchJob extends UpdateCenterJob {

        private final List<PluginWrapper> batch;
        private final long start;
        @Exported(inline = true)
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public volatile CompleteBatchJobStatus status = new CompleteBatchJob.Pending();

        public CompleteBatchJob(List<PluginWrapper> batch, long start, UUID correlationId) {
            super(getCoreSource());
            this.batch = batch;
            this.start = start;
            setCorrelationId(correlationId);
        }

        @Override
        public void run() {
            LOGGER.info("Completing installing of plugin batch…");
            status = new CompleteBatchJob.Running();
            try {
                Jenkins.get().getPluginManager().start(batch);
                status = new CompleteBatchJob.Success();
            } catch (Exception x) {
                status = new CompleteBatchJob.Failure(x);
                LOGGER.log(Level.WARNING, "Failed to start some plugins", x);
            }
            LOGGER.log(INFO, "Completed installation of {0} plugins in {1}", new Object[] {batch.size(), Util.getTimeSpanString((System.nanoTime() - start) / 1_000_000)});
        }

        @ExportedBean
        public abstract class CompleteBatchJobStatus {
            @Exported
            public final int id = iota.incrementAndGet();
        }

        public class Pending extends CompleteBatchJobStatus {}

        public class Running extends CompleteBatchJobStatus {}

        public class Success extends CompleteBatchJobStatus {}

        public class Failure extends CompleteBatchJobStatus {
            Failure(Throwable problemStackTrace) {
                this.problemStackTrace = problemStackTrace;
            }

            public final Throwable problemStackTrace;
        }

    }

    /**
     * Represents the state of the downgrading activity of plugin.
     */
    public final class PluginDowngradeJob extends DownloadJob {
        /**
         * What plugin are we trying to install?
         */
        public final Plugin plugin;

        private final PluginManager pm = Jenkins.get().getPluginManager();

        /**
         * @deprecated use {@link #PluginDowngradeJob(UpdateSite.Plugin, UpdateSite, Authentication)}
         */
        @Deprecated
        public PluginDowngradeJob(Plugin plugin, UpdateSite site, org.acegisecurity.Authentication auth) {
            this(plugin, site, auth.toSpring());
        }


        public PluginDowngradeJob(Plugin plugin, UpdateSite site, Authentication auth) {
            super(site, auth);
            this.plugin = plugin;
        }

        @Override
        protected URL getURL() throws MalformedURLException {
            return new URL(plugin.url);
        }

        @Override
        protected File getDestination() {
            File baseDir = pm.rootDir;
            final File legacy = new File(baseDir, plugin.name + ".hpi");
            if (legacy.exists()) {
                return legacy;
            }
            return new File(baseDir, plugin.name + ".jpi");
        }

        protected File getBackup() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".bak");
        }

        @Override
        public String getName() {
            return plugin.name;
        }

        @Override
        public String getDisplayName() {
            return plugin.getDisplayName();
        }

        @Override
        public void run() {
            try {
                LOGGER.info("Starting the downgrade of " + getName() + " on behalf of " + getUser().getName());

                _run();

                LOGGER.info("Downgrade successful: " + getName());
                status = new Success();
                onSuccess();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to downgrade " + getName(), e);
                status = new DownloadJob.Failure(e);
                error = e;
            }
        }

        @Override
        protected void _run() throws IOException {
            File dst = getDestination();
            File backup = getBackup();

            config.install(this, backup, dst);
        }

        /**
         * Called to overwrite
         * current version with backup file
         */
        @Override
        protected synchronized void replace(File dst, File backup) throws IOException {
            moveAtomically(backup, dst);
        }

        @Override
        protected void onSuccess() {
            pm.pluginUploaded = true;
        }

        @Override
        public String toString() {
            return super.toString() + "[plugin=" + plugin.title + "]";
        }
    }

    /**
     * Represents the state of the upgrade activity of Jenkins core.
     */
    public final class HudsonUpgradeJob extends DownloadJob {

        /**
          * @deprecated use {@link #HudsonUpgradeJob(UpdateSite, Authentication)}
         */
        @Deprecated
        public HudsonUpgradeJob(UpdateSite site, org.acegisecurity.Authentication auth) {
            super(site, auth.toSpring());
        }

        public HudsonUpgradeJob(UpdateSite site, Authentication auth) {
            super(site, auth);
        }

        @Override
        protected URL getURL() throws MalformedURLException {
            if (site == null) {
                throw new MalformedURLException("no update site defined");
            }
            return new URL(site.getData().core.url);
        }

        @Override
        protected File getDestination() {
            return Lifecycle.get().getHudsonWar();
        }

        @Override
        public String getName() {
            return "jenkins.war";
        }

        @Override
        protected void onSuccess() {
            status = new DownloadJob.Success();
        }

        @Override
        protected void replace(File dst, File src) throws IOException {
            if (site == null) {
                throw new IOException("no update site defined");
            }
            verifyChecksums(this, site.getData().core, src);
            Lifecycle.get().rewriteHudsonWar(src);
        }
    }

    public final class HudsonDowngradeJob extends DownloadJob {

        /**
         * @deprecated use {@link #HudsonDowngradeJob(UpdateSite, Authentication)}
         */
        @Deprecated
        public HudsonDowngradeJob(UpdateSite site, org.acegisecurity.Authentication auth) {
            super(site, auth.toSpring());
        }

        public HudsonDowngradeJob(UpdateSite site, Authentication auth) {
            super(site, auth);
        }

        @Override
        protected URL getURL() throws MalformedURLException {
            if (site == null) {
                throw new MalformedURLException("no update site defined");
            }
            return new URL(site.getData().core.url);
        }

        @Override
        protected File getDestination() {
            return Lifecycle.get().getHudsonWar();
        }

        @Override
        public String getName() {
            return "jenkins.war";
        }

        @Override
        protected void onSuccess() {
            status = new DownloadJob.Success();
        }

        @Override
        public void run() {
            try {
                LOGGER.info("Starting the downgrade of " + getName() + " on behalf of " + getUser().getName());

                _run();

                LOGGER.info("Downgrading successful: " + getName());
                status = new DownloadJob.Success();
                onSuccess();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to downgrade " + getName(), e);
                status = new DownloadJob.Failure(e);
                error = e;
            }
        }

        @Override
        protected void _run() throws IOException {

            File backup = new File(Lifecycle.get().getHudsonWar() + ".bak");
            File dst = getDestination();

            config.install(this, backup, dst);
        }

        @Override
        protected void replace(File dst, File src) throws IOException {
            Lifecycle.get().rewriteHudsonWar(src);
        }
    }

    @Deprecated
    public static final class PluginEntry implements Comparable<PluginEntry> {
        public Plugin plugin;
        public String category;

        private PluginEntry(Plugin p, String c) {
            plugin = p;
            category = c;
        }

        @Override
        public int compareTo(PluginEntry o) {
            int r = category.compareTo(o.category);
            if (r == 0) r = plugin.name.compareToIgnoreCase(o.plugin.name);
            if (r == 0) r = new VersionNumber(plugin.version).compareTo(new VersionNumber(o.plugin.version));
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PluginEntry that = (PluginEntry) o;

            if (!category.equals(that.category)) {
                return false;
            }
            if (!plugin.name.equals(that.plugin.name)) {
                return false;
            }
            return plugin.version.equals(that.plugin.version);
        }

        @Override
        public int hashCode() {
            int result = category.hashCode();
            result = 31 * result + plugin.name.hashCode();
            result = 31 * result + plugin.version.hashCode();
            return result;
        }
    }

    /**
     * Initializes the update center.
     *
     * This has to wait until after all plugins load, to let custom UpdateCenterConfiguration take effect first.
     */
    @Initializer(after = PLUGINS_STARTED, fatal = false)
    public static void init(Jenkins h) throws IOException {
        h.getUpdateCenter().load();
    }

    @Restricted(NoExternalUse.class)
    public static void updateAllSitesNow() {
        for (UpdateSite site : Jenkins.get().getUpdateCenter().getSites()) {
            try {
                site.updateDirectlyNow();
            } catch (IOException e) {
                LOGGER.log(WARNING, MessageFormat.format("Failed to update the update site ''{0}''. " +
                        "Plugin upgrades may fail.", site.getId()), e);
            }
        }
    }

    @Restricted(NoExternalUse.class)
    public static void updateDefaultSite() {
        final UpdateSite site = Jenkins.get().getUpdateCenter().getSite(UpdateCenter.ID_DEFAULT);
        if (site == null) {
            LOGGER.log(Level.SEVERE, "Upgrading Jenkins. Cannot retrieve the default Update Site ''{0}''. "
                    + "Plugin installation may fail.", UpdateCenter.ID_DEFAULT);
            return;
        }
        try {
            // Need to do the following because the plugin manager will attempt to access
            // $JENKINS_HOME/updates/$ID_DEFAULT.json. Needs to be up to date.
            site.updateDirectlyNow();
        } catch (Exception e) {
            LOGGER.log(WARNING, "Upgrading Jenkins. Failed to update the default Update Site '" + UpdateCenter.ID_DEFAULT +
                    "'. Plugin upgrades may fail.", e);
        }
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
        }
        return this;
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(UpdateCenter.class.getName() + ".skipPermissionCheck");


    /**
     * Sequence number generator.
     */
    private static final AtomicInteger iota = new AtomicInteger();

    /**
     * @deprecated as of 1.333
     *      Use {@link UpdateSite#neverUpdate}
     */
    @Deprecated
    public static boolean neverUpdate = SystemProperties.getBoolean(UpdateCenter.class.getName() + ".never");

    public static final XStream2 XSTREAM = new XStream2();

    static {
        XSTREAM.alias("site", UpdateSite.class);
        XSTREAM.alias("sites", PersistedList.class);
    }

    private static void moveAtomically(File src, File target) throws IOException {
        try {
            Files.move(Util.fileToPath(src), Util.fileToPath(target), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOGGER.log(Level.WARNING, "Atomic move not supported. Falling back to non-atomic move.", e);
            try {
                Files.move(Util.fileToPath(src), Util.fileToPath(target), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                e2.addSuppressed(e);
                throw e2;
            }
        }
    }
}
