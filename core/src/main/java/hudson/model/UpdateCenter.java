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

import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.XmlFile;
import static hudson.init.InitMilestone.PLUGINS_STARTED;
import hudson.init.Initializer;
import hudson.lifecycle.Lifecycle;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.model.UpdateSite.Data;
import hudson.model.UpdateSite.Plugin;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.AtmostOneThreadExecutor;
import hudson.security.ACL;
import hudson.util.DaemonThreadFactory;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.NamingThreadFactory;
import hudson.util.IOException2;
import hudson.util.IOUtils;
import hudson.util.PersistedList;
import hudson.util.XStream2;
import jenkins.RestartRequiredException;
import jenkins.model.Jenkins;
import jenkins.util.io.OnMaster;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLHandshakeException;
import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;


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
 *
 * @author Kohsuke Kawaguchi
 * @since 1.220
 */
@ExportedBean
public class UpdateCenter extends AbstractModelObject implements Saveable, OnMaster {
	
    private static final String UPDATE_CENTER_URL = System.getProperty(UpdateCenter.class.getName()+".updateCenterUrl","http://updates.jenkins-ci.org/");

    /**
     * {@linkplain UpdateSite#getId() ID} of the default update site.
     * @since 1.483
     */
    public static final String ID_DEFAULT = "default";

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
    private final Vector<UpdateCenterJob> jobs = new Vector<UpdateCenterJob>();

    /**
     * {@link UpdateSite}s from which we've already installed a plugin at least once.
     * This is used to skip network tests.
     */
    private final Set<UpdateSite> sourcesUsed = new HashSet<UpdateSite>();

    /**
     * List of {@link UpdateSite}s to be used.
     */
    private final PersistedList<UpdateSite> sites = new PersistedList<UpdateSite>(this);

    /**
     * Update center configuration data
     */
    private UpdateCenterConfiguration config;

    private boolean requiresRestart;

    public UpdateCenter() {
        configure(new UpdateCenterConfiguration());
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
        if (config!=null) {
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
    public List<UpdateCenterJob> getJobs() {
        synchronized (jobs) {
            return new ArrayList<UpdateCenterJob>(jobs);
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
                if (job.id==id)
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
                InstallationJob ij = (InstallationJob)job;
                if (ij.plugin.name.equals(plugin.name) && ij.plugin.sourceId.equals(plugin.sourceId))
                    return ij;
            }
        return null;
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
                return (HudsonUpgradeJob)job;
        return null;
    }

    /**
     * Returns the list of {@link UpdateSite}s to be used.
     * This is a live list, whose change will be persisted automatically.
     *
     * @return
     *      can be empty but never null.
     */
    public PersistedList<UpdateSite> getSites() {
        return sites;
    }

    /**
     * The same as {@link #getSites()} but for REST API.
     */
    @Exported(name="sites")
    public List<UpdateSite> getSiteList() {
        return sites.toList();
    }

    /**
     * Alias for {@link #getById}.
     */
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
            if (s.getDataTimestamp()>newestTs) {
                newestTs = s.getDataTimestamp();
            }
        }
        if (newestTs == 0) {
            return Messages.UpdateCenter_n_a();
        }
        return Util.getPastTimeString(System.currentTimeMillis()-newestTs);
    }

    /**
     * Gets {@link UpdateSite} by its ID.
     * Used to bind them to URL.
     */
    public UpdateSite getById(String id) {
        for (UpdateSite s : sites) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Gets the {@link UpdateSite} from which we receive updates for <tt>jenkins.war</tt>.
     *
     * @return
     *      null if no such update center is provided.
     */
    public UpdateSite getCoreSource() {
        for (UpdateSite s : sites) {
            Data data = s.getData();
            if (data!=null && data.core!=null)
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
    public String getDefaultBaseUrl() {
        return config.getUpdateCenterUrl();
    }

    /**
     * Gets the plugin with the given name from the first {@link UpdateSite} to contain it.
     */
    public @CheckForNull Plugin getPlugin(String artifactId) {
        for (UpdateSite s : sites) {
            Plugin p = s.getPlugin(artifactId);
            if (p!=null) return p;
        }
        return null;
    }

    /**
     * Schedules a Jenkins upgrade.
     */
    @RequirePOST
    public void doUpgrade(StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        HudsonUpgradeJob job = new HudsonUpgradeJob(getCoreSource(), Jenkins.getAuthentication());
        if(!Lifecycle.get().canRewriteHudsonWar()) {
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
    public HttpResponse doInvalidateData() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        for (UpdateSite site : sites) {
            site.doInvalidateData();
        }

        return HttpResponses.ok();
    }


    /**
     * Schedules a Jenkins restart.
     */
    public void doSafeRestart(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        synchronized (jobs) {
            if (!isRestartScheduled()) {
                Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
                addJob(new RestartJenkinsJob(getCoreSource()));
                LOGGER.info("Scheduling Jenkins reboot");
            }
        }
        response.sendRedirect2(".");
    }
    
    /**
     * Cancel all scheduled jenkins restarts
     */
    public void doCancelRestart(StaplerResponse response) throws IOException, ServletException {
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
    public void doDowngrade(StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if(!isDowngradable()) {
            sendError("Jenkins downgrade is not possible, probably backup does not exist");
            return;
        }

        HudsonDowngradeJob job = new HudsonDowngradeJob(getCoreSource(), Jenkins.getAuthentication());
        LOGGER.info("Scheduling the core downgrade");
        addJob(job);
        rsp.sendRedirect2(".");
    }

    /**
     * Performs hudson downgrade.
     */
    public void doRestart(StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        HudsonDowngradeJob job = new HudsonDowngradeJob(getCoreSource(), Jenkins.getAuthentication());
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
            JarFile backupWar = new JarFile(new File(Lifecycle.get().getHudsonWar() + ".bak"));
            try {
                Attributes attrs = backupWar.getManifest().getMainAttributes();
                String v = attrs.getValue("Jenkins-Version");
                if (v==null)    v = attrs.getValue("Hudson-Version");
                return v;
            } finally {
                backupWar.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read backup version ", e);
            return null;}

    }

    /*package*/ synchronized Future<UpdateCenterJob> addJob(UpdateCenterJob job) {
        // the first job is always the connectivity check
        if (sourcesUsed.add(job.site))
            new ConnectionCheckJob(job.site).submit();
        return job.submit();
    }

    public String getDisplayName() {
        return "Update center";
    }

    public String getSearchUrl() {
        return "updateCenter";
    }

    /**
     * Saves the configuration info to the disk.
     */
    public synchronized void save() {
        if(BulkChange.contains(this))   return;
        try {
            getConfigFile().write(sites);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save "+getConfigFile(),e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     */
    public synchronized void load() throws IOException {
        UpdateSite defaultSite = new UpdateSite(ID_DEFAULT, config.getUpdateCenterUrl() + "update-center.json");
        XmlFile file = getConfigFile();
        if(file.exists()) {
            try {
                sites.replaceBy(((PersistedList)file.unmarshal(sites)).toList());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load "+file, e);
            }
            for (UpdateSite site : sites) {
                // replace the legacy site with the new site
                if (site.isLegacyDefault()) {
                    sites.remove(site);
                    sites.add(defaultSite);
                    break;
                }
            }
        } else {
            if (sites.isEmpty()) {
                // If there aren't already any UpdateSources, add the default one.
                // to maintain compatibility with existing UpdateCenterConfiguration, create the default one as specified by UpdateCenterConfiguration
                sites.add(defaultSite);
            }
        }
    }

    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM,new File(Jenkins.getInstance().root,
                                    UpdateCenter.class.getName()+".xml"));
    }

    public List<Plugin> getAvailables() {
        Map<String,Plugin> pluginMap = new LinkedHashMap<String, Plugin>();
        for (UpdateSite site : sites) {
            for (Plugin plugin: site.getAvailables()) {
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

        return new ArrayList<Plugin>(pluginMap.values());
    }

    /**
     * Returns a list of plugins that should be shown in the "available" tab, grouped by category.
     * A plugin with multiple categories will appear multiple times in the list.
     */
    public PluginEntry[] getCategorizedAvailables() {
        TreeSet<PluginEntry> entries = new TreeSet<PluginEntry>();
        for (Plugin p : getAvailables()) {
            if (p.categories==null || p.categories.length==0)
                entries.add(new PluginEntry(p, getCategoryDisplayName(null)));
            else
                for (String c : p.categories)
                    entries.add(new PluginEntry(p, getCategoryDisplayName(c)));
        }
        return entries.toArray(new PluginEntry[entries.size()]);
    }

    private static String getCategoryDisplayName(String category) {
        if (category==null)
            return Messages.UpdateCenter_PluginCategory_misc();
        try {
            return (String)Messages.class.getMethod(
                    "UpdateCenter_PluginCategory_" + category.replace('-', '_')).invoke(null);
        } catch (Exception ex) {
            return Messages.UpdateCenter_PluginCategory_unrecognized(category);
        }
    }

    public List<Plugin> getUpdates() {
        Map<String,Plugin> pluginMap = new LinkedHashMap<String, Plugin>();
        for (UpdateSite site : sites) {
            for (Plugin plugin: site.getUpdates()) {
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

        return new ArrayList<Plugin>(pluginMap.values());
    }
    
    /**
     * Ensure that all UpdateSites are up to date, without requiring a user to
     * browse to the instance.
     * 
     * @return a list of {@link FormValidation} for each updated Update Site
     * @throws ExecutionException 
     * @throws InterruptedException 
     * @since 1.501
     * 
     */
    public List<FormValidation> updateAllSites() throws InterruptedException, ExecutionException {
        List <Future<FormValidation>> futures = new ArrayList<Future<FormValidation>>();
        for (UpdateSite site : getSites()) {
            Future<FormValidation> future = site.updateDirectly(DownloadService.signatureCheck);
            if (future != null) {
                futures.add(future);
            }
        }
        
        List<FormValidation> results = new ArrayList<FormValidation>(); 
        for (Future<FormValidation> f : futures) {
            results.add(f.get());
        }
        return results;
    }


    /**
     * {@link AdministrativeMonitor} that checks if there's Jenkins update.
     */
    @Extension
    public static final class CoreUpdateMonitor extends AdministrativeMonitor {
        public boolean isActivated() {
            Data data = getData();
            return data!=null && data.hasCoreUpdates();
        }

        public Data getData() {
            UpdateSite cs = Jenkins.getInstance().getUpdateCenter().getCoreSource();
            if (cs!=null)   return cs.getData();
            return null;
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
    @SuppressWarnings({"UnusedDeclaration"})
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
            testConnection(new URL(updateCenterUrl + "?uctest"));
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
        public File download(DownloadJob job, URL src) throws IOException {
            MessageDigest sha1 = null;
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException ignored) {
                // Irrelevant as the Java spec says SHA-1 must exist. Still, if this fails
                // the DownloadJob will just have computedSha1 = null and that is expected
                // to be handled by caller
            }

            CountingInputStream in = null;
            OutputStream out = null;
            URLConnection con = null;
            try {
                con = connect(job,src);
                int total = con.getContentLength();
                in = new CountingInputStream(con.getInputStream());
                byte[] buf = new byte[8192];
                int len;

                File dst = job.getDestination();
                File tmp = new File(dst.getPath()+".tmp");
                out = new FileOutputStream(tmp);
                if (sha1 != null) {
                    out = new DigestOutputStream(out, sha1);
                }

                LOGGER.info("Downloading "+job.getName());
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                t.setName(oldName + ": " + src);
                try {
                    while((len=in.read(buf))>=0) {
                        out.write(buf,0,len);
                        job.status = job.new Installing(total==-1 ? -1 : in.getCount()*100/total);
                    }
                } catch (IOException e) {
                    throw new IOException("Failed to load "+src+" to "+tmp,e);
                } finally {
                    IOUtils.closeQuietly(out);
                    t.setName(oldName);
                }

                if (total!=-1 && total!=tmp.length()) {
                    // don't know exactly how this happens, but report like
                    // http://www.ashlux.com/wordpress/2009/08/14/hudson-and-the-sonar-plugin-fail-maveninstallation-nosuchmethoderror/
                    // indicates that this kind of inconsistency can happen. So let's be defensive
                    throw new IOException("Inconsistent file length: expected "+total+" but only got "+tmp.length());
                }

                if (sha1 != null) {
                    byte[] digest = sha1.digest();
                    // need to trim because commons-codec 1.4 used in test chunked output and adds \r\n at the end
                    job.computedSHA1 = Base64.encodeBase64String(digest).trim();
                }
                return tmp;
            } catch (IOException e) {
                // assist troubleshooting in case of e.g. "too many redirects" by printing actual URL
                String extraMessage = "";
                if (con != null && con.getURL() != null && !src.toString().equals(con.getURL().toString())) {
                    // Two URLs are considered equal if different hosts resolve to same IP. Prefer to log in case of string inequality,
                    // because who knows how the server responds to different host name in the request header?
                    // Also, since it involved name resolution, it'd be an expensive operation.
                    extraMessage = " (redirected to: " + con.getURL() + ")";
                }
                throw new IOException2("Failed to download from "+src+extraMessage,e);
            } finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }
        }

        /**
         * Connects to the given URL for downloading the binary. Useful for tweaking
         * how the connection gets established.
         */
        protected URLConnection connect(DownloadJob job, URL src) throws IOException {
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
         *      is now a part of the <tt>update-center.json</tt> file. See
         *      <tt>http://jenkins-ci.org/update-center.json</tt> as an example.
         */
        public String getConnectionCheckUrl() {
            return "http://www.google.com";
        }

        /**
         * Returns the URL of the server that hosts the update-center.json
         * file.
         *
         * @deprecated as of 1.333
         *      With the introduction of multiple update center capability, this information
         *      is now moved to {@link UpdateSite}.
         * @return
         *      Absolute URL that ends with '/'.
         */
        public String getUpdateCenterUrl() {
            return UPDATE_CENTER_URL;
        }

        /**
         * Returns the URL of the server that hosts plugins and core updates.
         *
         * @deprecated as of 1.333
         *      <tt>update-center.json</tt> is now signed, so we don't have to further make sure that
         *      we aren't downloading from anywhere unsecure.
         */
        public String getPluginRepositoryBaseUrl() {
            return "http://jenkins-ci.org/";
        }


        private void testConnection(URL url) throws IOException {
            try {
                Util.copyStreamAndClose(ProxyConfiguration.open(url).getInputStream(),new NullOutputStream());
            } catch (SSLHandshakeException e) {
                if (e.getMessage().contains("PKIX path building failed"))
                   // fix up this crappy error message from JDK
                    throw new IOException("Failed to validate the SSL certificate of "+url,e);
            }
        }
    }

    /**
     * Things that {@link UpdateCenter#installerService} executes.
     *
     * This object will have the <tt>row.jelly</tt> which renders the job on UI.
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
        public final UpdateSite site;

        /**
         * If this job fails, set to the error.
         */
        protected Throwable error;

        protected UpdateCenterJob(UpdateSite site) {
            this.site = site;
        }

        public Api getApi() {
            return new Api(this);
        }

        /**
         * @deprecated as of 1.326
         *      Use {@link #submit()} instead.
         */
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
            LOGGER.fine("Scheduling "+this+" to installerService");
            jobs.add(this);
            return installerService.submit(this,this);
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
         @Exported(inline=true)
        public volatile RestartJenkinsJobStatus status = new Pending();
        
        /**
         * Cancel job
         */     
        public synchronized boolean cancel() {
            if (status instanceof Pending) {
                status = new Canceled();
                return true;
            }
            return false;
        }
        
        public RestartJenkinsJob(UpdateSite site) {
            super(site);
        }

        public synchronized void run() {
            if (!(status instanceof Pending)) {
                return;
            }
            status = new Running();
            try {
                Jenkins.getInstance().safeRestart();
            } catch (RestartNotSupportedException exception) {
                // ignore if restart is not allowed
                status = new Failure();
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
        private final Vector<String> statuses= new Vector<String>();

        public ConnectionCheckJob(UpdateSite site) {
            super(site);
        }

        public void run() {
            if (ID_UPLOAD.equals(site.getId())) {
                return;
            }
            LOGGER.fine("Doing a connectivity check");
            try {
                String connectionCheckUrl = site.getConnectionCheckUrl();
                if (connectionCheckUrl!=null) {
                    statuses.add(Messages.UpdateCenter_Status_CheckingInternet());
                    try {
                        config.checkConnection(this, connectionCheckUrl);
                    } catch (IOException e) {
                        if(e.getMessage().contains("Connection timed out")) {
                            // Google can't be down, so this is probably a proxy issue
                            statuses.add(Messages.UpdateCenter_Status_ConnectionFailed(connectionCheckUrl));
                            return;
                        }
                    }
                }

                statuses.add(Messages.UpdateCenter_Status_CheckingJavaNet());
                config.checkUpdateCenter(this, site.getUrl());

                statuses.add(Messages.UpdateCenter_Status_Success());
            } catch (UnknownHostException e) {
                statuses.add(Messages.UpdateCenter_Status_UnknownHostException(e.getMessage()));
                addStatus(e);
                error = e;
            } catch (IOException e) {
                statuses.add(Functions.printThrowable(e));
                error = e;
            }
        }

        private void addStatus(UnknownHostException e) {
            statuses.add("<pre>"+ Functions.xmlEscape(Functions.printThrowable(e))+"</pre>");
        }

        public String[] getStatuses() {
            synchronized (statuses) {
                return statuses.toArray(new String[statuses.size()]);
            }
        }
    }

    /**
     * Base class for a job that downloads a file from the Jenkins project.
     */
    public abstract class DownloadJob extends UpdateCenterJob {
        /**
         * Immutable object representing the current state of this job.
         */
        @Exported(inline=true)
        public volatile InstallationStatus status = new Pending();

        /**
         * Where to download the file from.
         */
        protected abstract URL getURL() throws MalformedURLException;

        /**
         * Where to download the file to.
         */
        protected abstract File getDestination();

        @Exported
        public abstract String getName();

        /**
         * Called when the whole thing went successfully.
         */
        protected abstract void onSuccess();

        /**
         * During download, an attempt is made to compute the SHA-1 checksum of the file.
         *
         * @since TODO
         */
        // TODO no new API in LTS, but remove for mainline
        @Restricted(NoExternalUse.class)
        @CheckForNull
        protected String getComputedSHA1() {
            return computedSHA1;
        }

        private String computedSHA1;

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

        public void run() {
            try {
                LOGGER.info("Starting the installation of "+getName()+" on behalf of "+getUser().getName());

                _run();

                LOGGER.info("Installation successful: "+getName());
                status = new Success();
                onSuccess();
            } catch (InstallationStatus e) {
                status = e;
                if (status.isSuccess()) onSuccess();
                requiresRestart |= status.requiresRestart();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to install "+getName(),e);
                status = new Failure(e);
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
        protected void replace(File dst, File src) throws IOException {
            File bak = Util.changeExtension(dst,".bak");
            bak.delete();
            dst.renameTo(bak);
            dst.delete(); // any failure up to here is no big deal
            if(!src.renameTo(dst)) {
                throw new IOException("Failed to rename "+src+" to "+dst);
            }
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
     * If expectedSHA1 is non-null, ensure that actualSha1 is the same value, otherwise throw.
     *
     * Utility method for InstallationJob and HudsonUpgradeJob.
     *
     * @throws IOException when checksums don't match, or actual checksum was null.
     */
    private void verifyChecksums(String expectedSHA1, String actualSha1, File downloadedFile) throws IOException {
        if (expectedSHA1 != null) {
            if (actualSha1 == null) {
                // refuse to install if SHA-1 could not be computed
                throw new IOException("Failed to compute SHA-1 of downloaded file, refusing installation");
            }
            if (!expectedSHA1.equals(actualSha1)) {
                throw new IOException("Downloaded file " + downloadedFile.getAbsolutePath() + " does not match expected SHA-1, expected " + expectedSHA1 + ", actual " + actualSha1);
                // keep 'downloadedFile' around for investigating what's going on
            }
        }
    }

    /**
     * Represents the state of the installation activity of one plugin.
     */
    public final class InstallationJob extends DownloadJob {
        /**
         * What plugin are we trying to install?
         */
        @Exported
        public final Plugin plugin;

        private final PluginManager pm = Jenkins.getInstance().getPluginManager();

        /**
         * True to load the plugin into this Jenkins, false to wait until restart.
         */
        private final boolean dynamicLoad;

        /**
         * @deprecated as of 1.442
         */
        public InstallationJob(Plugin plugin, UpdateSite site, Authentication auth) {
            this(plugin,site,auth,false);
        }

        public InstallationJob(Plugin plugin, UpdateSite site, Authentication auth, boolean dynamicLoad) {
            super(site, auth);
            this.plugin = plugin;
            this.dynamicLoad = dynamicLoad;
        }

        protected URL getURL() throws MalformedURLException {
            return new URL(plugin.url);
        }

        protected File getDestination() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".jpi");
        }
        
        private File getLegacyDestination() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".hpi");
        }

        public String getName() {
            return plugin.getDisplayName();
        }

        @Override
        public void _run() throws IOException, InstallationStatus {
            super._run();

            // if this is a bundled plugin, make sure it won't get overwritten
            PluginWrapper pw = plugin.getInstalled();
            if (pw!=null && pw.isBundled()) {
                SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
                try {
                    pw.doPin();
                } finally {
                    SecurityContextHolder.setContext(oldContext);
                }
            }

            if (dynamicLoad) {
                try {
                    pm.dynamicLoad(getDestination());
                } catch (RestartRequiredException e) {
                    throw new SuccessButRequiresRestart(e.message);
                } catch (Exception e) {
                    throw new IOException("Failed to dynamically deploy this plugin",e);
                }
            } else {
                throw new SuccessButRequiresRestart(Messages._UpdateCenter_DownloadButNotActivated());
            }
        }

        protected void onSuccess() {
            pm.pluginUploaded = true;
        }

        @Override
        public String toString() {
            return super.toString()+"[plugin="+plugin.title+"]";
        }
        
        /**
         * Called when the download is completed to overwrite
         * the old file with the new file.
         */
        @Override
        protected void replace(File dst, File src) throws IOException {

            verifyChecksums(plugin.getSha1(), getComputedSHA1(), src);

            File bak = Util.changeExtension(dst, ".bak");
            bak.delete();

            final File legacy = getLegacyDestination();
            if (legacy.exists()) {
                if (!legacy.renameTo(bak)) {
                    legacy.delete();
                }
            }
            if (dst.exists()) {
                if (!dst.renameTo(bak)) {
                    dst.delete();
                }
            }

            if(!src.renameTo(dst)) {
                throw new IOException("Failed to rename "+src+" to "+dst);
            }
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

        private final PluginManager pm = Jenkins.getInstance().getPluginManager();

        public PluginDowngradeJob(Plugin plugin, UpdateSite site, Authentication auth) {
            super(site, auth);
            this.plugin = plugin;
        }

        protected URL getURL() throws MalformedURLException {
            return new URL(plugin.url);
        }

        protected File getDestination() {
            File baseDir = pm.rootDir;
            final File legacy = new File(baseDir, plugin.name + ".hpi");
            if(legacy.exists()){
            	return legacy;
            }
            return new File(baseDir, plugin.name + ".jpi");
        }

        protected File getBackup() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".bak");
        }

        public String getName() {
            return plugin.getDisplayName();
        }

        @Override
        public void run() {
            try {
                LOGGER.info("Starting the downgrade of "+getName()+" on behalf of "+getUser().getName());

                _run();

                LOGGER.info("Downgrade successful: "+getName());
                status = new Success();
                onSuccess();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to downgrade "+getName(),e);
                status = new Failure(e);
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
        protected void replace(File dst, File backup) throws IOException {
            dst.delete(); // any failure up to here is no big deal
            if(!backup.renameTo(dst)) {
                throw new IOException("Failed to rename "+backup+" to "+dst);
            }
        }

        protected void onSuccess() {
            pm.pluginUploaded = true;
        }

        @Override
        public String toString() {
            return super.toString()+"[plugin="+plugin.title+"]";
        }
    }

    /**
     * Represents the state of the upgrade activity of Jenkins core.
     */
    public final class HudsonUpgradeJob extends DownloadJob {
        public HudsonUpgradeJob(UpdateSite site, Authentication auth) {
            super(site, auth);
        }

        protected URL getURL() throws MalformedURLException {
            return new URL(site.getData().core.url);
        }

        protected File getDestination() {
            return Lifecycle.get().getHudsonWar();
        }

        public String getName() {
            return "jenkins.war";
        }

        protected void onSuccess() {
            status = new Success();
        }

        @Override
        protected void replace(File dst, File src) throws IOException {
            String expectedSHA1 = site.getData().core.getSha1();
            verifyChecksums(expectedSHA1, getComputedSHA1(), src);
            Lifecycle.get().rewriteHudsonWar(src);
        }
    }

    public final class HudsonDowngradeJob extends DownloadJob {
        public HudsonDowngradeJob(UpdateSite site, Authentication auth) {
            super(site, auth);
        }

        protected URL getURL() throws MalformedURLException {
            return new URL(site.getData().core.url);
        }

        protected File getDestination() {
            return Lifecycle.get().getHudsonWar();
        }

        public String getName() {
            return "jenkins.war";
        }
        protected void onSuccess() {
            status = new Success();
        }
        @Override
        public void run() {
            try {
                LOGGER.info("Starting the downgrade of "+getName()+" on behalf of "+getUser().getName());

                _run();

                LOGGER.info("Downgrading successful: "+getName());
                status = new Success();
                onSuccess();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to downgrade "+getName(),e);
                status = new Failure(e);
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

    public static final class PluginEntry implements Comparable<PluginEntry> {
        public Plugin plugin;
        public String category;
        private PluginEntry(Plugin p, String c) { plugin = p; category = c; }

        public int compareTo(PluginEntry o) {
            int r = category.compareTo(o.category);
            if (r==0) r = plugin.name.compareToIgnoreCase(o.plugin.name);
            return r;
        }
    }

    /**
     * Adds the update center data retriever to HTML.
     */
    @Extension
    public static class PageDecoratorImpl extends PageDecorator {
    }

    /**
     * Initializes the update center.
     *
     * This has to wait until after all plugins load, to let custom UpdateCenterConfiguration take effect first.
     */
    @Initializer(after=PLUGINS_STARTED, fatal=false)
    public static void init(Jenkins h) throws IOException {
        h.getUpdateCenter().load();
    }

    /**
     * Sequence number generator.
     */
    private static final AtomicInteger iota = new AtomicInteger();

    private static final Logger LOGGER = Logger.getLogger(UpdateCenter.class.getName());

    /**
     * @deprecated as of 1.333
     *      Use {@link UpdateSite#neverUpdate}
     */
    public static boolean neverUpdate = Boolean.getBoolean(UpdateCenter.class.getName()+".never");

    public static final XStream2 XSTREAM = new XStream2();

    static {
        XSTREAM.alias("site",UpdateSite.class);
        XSTREAM.alias("sites",PersistedList.class);
    }
}
