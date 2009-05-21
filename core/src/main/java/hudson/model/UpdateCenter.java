/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Seiji Sogabe
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

import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.ProxyConfiguration;
import hudson.Extension;
import hudson.lifecycle.Lifecycle;
import hudson.util.DaemonThreadFactory;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import hudson.util.IOException2;
import static hudson.util.TimeUnit2.DAYS;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class UpdateCenter extends AbstractModelObject {
    /**
     * What's the time stamp of data file?
     */
    private long dataTimestamp = -1;

    /**
     * When was the last time we asked a browser to check the data for us?
     *
     * <p>
     * There's normally some delay between when we send HTML that includes the check code,
     * until we get the data back, so this variable is used to avoid asking too many browseres
     * all at once.
     */
    private volatile long lastAttempt = -1;

    /**
     * {@link ExecutorService} that performs installation.
     */
    private final ExecutorService installerService = Executors.newSingleThreadExecutor(
        new DaemonThreadFactory(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("Update center installer thread");
                return t;
            }
        }));

    /**
     * List of created {@link UpdateCenterJob}s. Access needs to be synchronized.
     */
    private final Vector<UpdateCenterJob> jobs = new Vector<UpdateCenterJob>();

    /**
     * Update center configuration data
     */
    private UpdateCenterConfiguration config;
    
    /**
     * Create update center to get plugins/updates from hudson.dev.java.net
     */
    public UpdateCenter(Hudson parent) {
        configure(new UpdateCenterConfiguration());
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
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
        if(neverUpdate)     return false;
        if(dataTimestamp==-1)
            dataTimestamp = getDataFile().file.lastModified();
        long now = System.currentTimeMillis();
        boolean due = now - dataTimestamp > DAY && now - lastAttempt > 15000;
        if(due)     lastAttempt = now;
        return due;
    }

    /**
     * Returns the list of {@link UpdateCenterJob} representing scheduled installation attempts.
     *
     * @return
     *      can be empty but never null. Oldest entries first.
     */
    public List<UpdateCenterJob> getJobs() {
        synchronized (jobs) {
            return new ArrayList<UpdateCenterJob>(jobs);
        }
    }

    /**
     * Gets the string representing how long ago the data was obtained.
     */
    public String getLastUpdatedString() {
        if(dataTimestamp<0)     return "N/A";
        return Util.getPastTimeString(System.currentTimeMillis()-dataTimestamp);
    }

    /**
     * This is the endpoint that receives the update center data file from the browser.
     */
    public void doPostBack(StaplerRequest req) throws IOException {
        dataTimestamp = System.currentTimeMillis();
        String p = req.getParameter("json");
        JSONObject o = JSONObject.fromObject(p);
                
        int v = o.getInt("updateCenterVersion");
        if(v !=1) {
            LOGGER.warning("Unrecognized update center version: "+v);
            return;
        }

        LOGGER.info("Obtained the latest update center data file");
        getDataFile().write(p);
    }

    /**
     * Schedules a Hudson upgrade.
     */
    public void doUpgrade(StaplerResponse rsp) throws IOException, ServletException {
        requirePOST();
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        HudsonUpgradeJob job = new HudsonUpgradeJob(Hudson.getAuthentication());
        if(!Lifecycle.get().canRewriteHudsonWar()) {
            sendError("Hudson upgrade not supported in this running mode");
            return;
        }

        LOGGER.info("Scheduling the core upgrade");
        addJob(job);
        rsp.sendRedirect2(".");
    }

    private void addJob(UpdateCenterJob job) {
        // the first job is always the connectivity check
        if(jobs.size()==0)
            new ConnectionCheckJob().schedule();
        job.schedule();
    }

    /**
     * Loads the update center data, if any.
     *
     * @return  null if no data is available.
     */
    public Data getData() {
        TextFile df = getDataFile();
        if(df.exists()) {
            try {
                return new Data(JSONObject.fromObject(df.read()));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Failed to parse "+df,e);
                df.delete(); // if we keep this file, it will cause repeated failures
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Returns a list of plugins that should be shown in the "available" tab.
     * These are "all plugins - installed plugins".
     */
    public List<Plugin> getAvailables() {
        List<Plugin> r = new ArrayList<Plugin>();
        Data data = getData();
        if(data ==null)     return Collections.emptyList();
        for (Plugin p : data.plugins.values()) {
            if(p.getInstalled()==null)
                r.add(p);
        }
        return r;
    }

    /**
     * Gets the information about a specific plugin.
     *
     * @param artifactId
     *      The short name of the plugin. Corresponds to {@link PluginWrapper#getShortName()}.
     *
     * @return
     *      null if no such information is found.
     */
    public Plugin getPlugin(String artifactId) {
        Data dt = getData();
        if(dt==null)    return null;
        return dt.plugins.get(artifactId);
    }

    /**
     * This is where we store the update center data.
     */
    private TextFile getDataFile() {
        return new TextFile(new File(Hudson.getInstance().root,"update-center.json"));
    }

    /**
     * Returns the list of plugins that are updates to currently installed ones.
     *
     * @return
     *      can be empty but never null.
     */
    public List<Plugin> getUpdates() {
        Data data = getData();
        if(data==null)      return Collections.emptyList(); // fail to determine

        List<Plugin> r = new ArrayList<Plugin>();
        for (PluginWrapper pw : Hudson.getInstance().getPluginManager().getPlugins()) {
            Plugin p = pw.getUpdateInfo();
            if(p!=null) r.add(p);
        }

        return r;
    }

    /**
     * Does any of the plugin has updates?
     */
    public boolean hasUpdates() {
        Data data = getData();
        if(data==null)      return false;

        for (PluginWrapper pw : Hudson.getInstance().getPluginManager().getPlugins()) {
            if(pw.getUpdateInfo() !=null) return true;
        }
        return false;
    }

    public String getDisplayName() {
        return "Update center";
    }

    public String getSearchUrl() {
        return "updateCenter";
    }

    /**
     * Exposed to get rid of hardcoding of the URL that serves up update-center.json
     * in Javascript.
     */
    public String getUrl() {
        return config.getUpdateCenterUrl();
    }

    /**
     * {@link AdministrativeMonitor} that checks if there's Hudson update.
     */
    @Extension
    public static final class CoreUpdateMonitor extends AdministrativeMonitor {
        public boolean isActivated() {
            Data data = getData();
            return data!=null && data.hasCoreUpdates();
        }

        public Data getData() {
            return Hudson.getInstance().getUpdateCenter().getData();
        }
    }

    /**
     * In-memory representation of the update center data.
     */
    public final class Data {
        /**
         * The latest hudson.war.
         */
        public final Entry core;
        /**
         * Plugins in the official repository, keyed by their artifact IDs.
         */
        public final Map<String,Plugin> plugins = new TreeMap<String,Plugin>(String.CASE_INSENSITIVE_ORDER);
        
        Data(JSONObject o) {
            core = new Entry(o.getJSONObject("core"));
            for(Map.Entry<String,JSONObject> e : (Set<Map.Entry<String,JSONObject>>)o.getJSONObject("plugins").entrySet()) {
                plugins.put(e.getKey(),new Plugin(e.getValue()));
            }
        }

        /**
         * Is there a new version of the core?
         */
        public boolean hasCoreUpdates() {
            return core.isNewerThan(Hudson.VERSION);
        }

        /**
         * Do we support upgrade?
         */
        public boolean canUpgrade() {
            return Lifecycle.get().canRewriteHudsonWar();
        }
    }

    public static class Entry {
        /**
         * Artifact ID.
         */
        public final String name;
        /**
         * The version.
         */
        public final String version;
        /**
         * Download URL.
         */
        public final String url;

        public Entry(JSONObject o) {
            this.name = o.getString("name");
            this.version = o.getString("version");
            this.url = o.getString("url");
        }

        /**
         * Checks if the specified "current version" is older than the version of this entry.
         *
         * @param currentVersion
         *      The string that represents the version number to be compared.
         * @return
         *      true if the version listed in this entry is newer.
         *      false otherwise, including the situation where the strings couldn't be parsed as version numbers.
         */
        public boolean isNewerThan(String currentVersion) {
            try {
                return new VersionNumber(currentVersion).compareTo(new VersionNumber(version)) < 0;
            } catch (IllegalArgumentException e) {
                // couldn't parse as the version number.
                return false;
            }
        }
    }

    public final class Plugin extends Entry {
        /**
         * Optional URL to the Wiki page that discusses this plugin.
         */
        public final String wiki;
        /**
         * Human readable title of the plugin, taken from Wiki page.
         * Can be null.
         *
         * <p>
         * beware of XSS vulnerability since this data comes from Wiki 
         */
        public final String title;
        /**
         * Optional excerpt string.
         */
        public final String excerpt;

        @DataBoundConstructor
        public Plugin(JSONObject o) {
            super(o);
            this.wiki = get(o,"wiki");
            this.title = get(o,"title");
            this.excerpt = get(o,"excerpt");
        }

        private String get(JSONObject o, String prop) {
            if(o.has(prop))
                return o.getString(prop);
            else
                return null;
        }

        public String getDisplayName() {
            if(title!=null) return title;
            return name;
        }

        /**
         * If some version of this plugin is currently installed, return {@link PluginWrapper}.
         * Otherwise null.
         */
        public PluginWrapper getInstalled() {
            PluginManager pm = Hudson.getInstance().getPluginManager();
            return pm.getPlugin(name);
        }

        /**
         * Schedules the installation of this plugin.
         *
         * <p>
         * This is mainly intended to be called from the UI. The actual installation work happens
         * asynchronously in another thread.
         */
        public void install() {
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            addJob(new InstallationJob(this, Hudson.getAuthentication()));
        }

        /**
         * Making the installation web bound.
         */
        public void doInstall(StaplerResponse rsp) throws IOException {
            install();
            rsp.sendRedirect2("../..");
        }
    }

    /**
     * Configuration data for controlling the update center's behaviors. The update
     * center's defaults will check internet connectivity by trying to connect
     * to www.google.com; will download plugins, the plugin catalog and updates
     * from hudson.dev.java.net; and will install plugins with file system
     * operations.
     * 
     * @since 1.266
     */
    public static class UpdateCenterConfiguration implements ExtensionPoint {
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
         * Validate the URL of the resource before downloading it. The default
         * implementation enforces that the base of the resource URL starts
         * with the string returned by {@link #getPluginRepositoryBaseUrl()}.
         * 
         * @param job The download job that is invoking this strategy. This job is
         *          responsible for managing the status of the download and installation.
         * @param src The location of the resource on the network
         * @throws IOException if the validation fails
         */
        public void preValidate(DownloadJob job, URL src) throws IOException {
            // In the future if we are to open up update center to 3rd party, we need more elaborate scheme
            // like signing to ensure the safety of the bits.
            if(!src.toExternalForm().startsWith(getPluginRepositoryBaseUrl())) {
                throw new IOException("Installation of plugin from "+src+" is not allowed");
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
        public File download(DownloadJob job, URL src) throws IOException {
            URLConnection con = ProxyConfiguration.open(src);
            int total = con.getContentLength();
            CountingInputStream in = new CountingInputStream(con.getInputStream());
            byte[] buf = new byte[8192];
            int len;

            File dst = job.getDestination();
            File tmp = new File(dst.getPath()+".tmp");
            OutputStream out = new FileOutputStream(tmp);

            LOGGER.info("Downloading "+job.getName());
            try {
                while((len=in.read(buf))>=0) {
                    out.write(buf,0,len);
                    job.status = job.new Installing(total==-1 ? -1 : in.getCount()*100/total);
                }
            } catch (IOException e) {
                throw new IOException2("Failed to load "+src+" to "+tmp,e);
            }

            in.close();
            out.close();
            
            return tmp;
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
         * Returns an "always up" server for Internet connectivity testing
         */
        public String getConnectionCheckUrl() {
            return "http://www.google.com";
        }
        
        /**
         * Returns the URL of the server that hosts the update-center.json
         * file.
         *
         * @return
         *      Absolute URL that ends with '/'.
         */
        public String getUpdateCenterUrl() {
            return "https://hudson.dev.java.net/";
        }
        
        /**
         * Returns the URL of the server that hosts plugins and core updates.
         */
        public String getPluginRepositoryBaseUrl() {
            return "https://hudson.dev.java.net/";
        }
        
        
        private void testConnection(URL url) throws IOException {
            InputStream in = ProxyConfiguration.open(url).getInputStream();
            IOUtils.copy(in,new ByteArrayOutputStream());
            in.close();
        }                    
    }
    
    /**
     * Things that {@link UpdateCenter#installerService} executes.
     *
     * This object will have the <tt>row.jelly</tt> which renders the job on UI.
     */
    public abstract class UpdateCenterJob implements Runnable {
        public void schedule() {
            LOGGER.fine("Scheduling "+this+" to installerService");
            jobs.add(this);
            installerService.submit(this);
        }
    }

    /**
     * Tests the internet connectivity.
     */
    public final class ConnectionCheckJob extends UpdateCenterJob {
        private final Vector<String> statuses= new Vector<String>();

        public void run() {
            LOGGER.fine("Doing a connectivity check");
            try {
                String connectionCheckUrl = config.getConnectionCheckUrl();
                
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

                statuses.add(Messages.UpdateCenter_Status_CheckingJavaNet());
                config.checkUpdateCenter(this, config.getUpdateCenterUrl());

                statuses.add(Messages.UpdateCenter_Status_Success());
            } catch (UnknownHostException e) {
                statuses.add(Messages.UpdateCenter_Status_UnknownHostException(e.getMessage()));
                addStatus(e);
            } catch (IOException e) {
                statuses.add(Functions.printThrowable(e));
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
     * Base class for a job that downloads a file from the Hudson project.
     */
    public abstract class DownloadJob extends UpdateCenterJob {
        /**
         * Unique ID that identifies this job.
         */
        public final int id = iota.incrementAndGet();
        /**
         * Immutable object representing the current state of this job.
         */
        public volatile InstallationStatus status = new Pending();

        /**
         * Where to download the file from.
         */
        protected abstract URL getURL() throws MalformedURLException;

        /**
         * Where to download the file to.
         */
        protected abstract File getDestination();

        public abstract String getName();

        /**
         * Called when the whole thing went successfully.
         */
        protected abstract void onSuccess();

        
        private Authentication authentication;
        
        /**
         * Get the user that initiated this job
         */
        public Authentication getUser()
        {
            return this.authentication;
        }
        
        protected DownloadJob(Authentication authentication)
        {
            this.authentication = authentication;
        }
        
        public void run() {
            try {
                LOGGER.info("Starting the installation of "+getName()+" on behalf of "+getUser().getName());

                URL src = getURL();

                config.preValidate(this, src);

                File dst = getDestination();
                File tmp = config.download(this, src);
                
                config.postValidate(this, tmp);
                config.install(this, tmp, dst);
                
                LOGGER.info("Installation successful: "+getName());
                status = new Success();
                onSuccess();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to install "+getName(),e);
                status = new Failure(e);
            }
        }

        /**
         * Called when the download is completed to overwrite
         * the old file with the new file.
         */
        protected void replace(File dst, File src) throws IOException {
            dst.delete();
            if(!src.renameTo(dst)) {
                throw new IOException("Failed to rename "+src+" to "+dst);
            }
        }

        /**
         * Indicates the status or the result of a plugin installation.
         * <p>
         * Instances of this class is immutable.
         */
        public abstract class InstallationStatus {
            public final int id = iota.incrementAndGet();
        }

        /**
         * Indicates that the installation of a plugin failed.
         */
        public class Failure extends InstallationStatus {
            public final Throwable problem;

            public Failure(Throwable problem) {
                this.problem = problem;
            }

            public String getStackTrace() {
                return Functions.printThrowable(problem);
            }
        }

        /**
         * Indicates that the plugin was successfully installed.
         */
        public class Success extends InstallationStatus {
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
     * Represents the state of the installation activity of one plugin.
     */
    public final class InstallationJob extends DownloadJob {
        /**
         * What plugin are we trying to install?
         */
        public final Plugin plugin;

        private final PluginManager pm = Hudson.getInstance().getPluginManager();

        public InstallationJob(Plugin plugin, Authentication auth) {
            super(auth);
            this.plugin = plugin;
        }

        protected URL getURL() throws MalformedURLException {
            return new URL(plugin.url);
        }

        protected File getDestination() {
            File baseDir = pm.rootDir;
            return new File(baseDir, plugin.name + ".hpi");
        }

        public String getName() {
            return plugin.getDisplayName();
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
     * Represents the state of the upgrade activity of Hudson core.
     */
    public final class HudsonUpgradeJob extends DownloadJob {
        public HudsonUpgradeJob(Authentication auth) {
            super(auth);
        }

        protected URL getURL() throws MalformedURLException {
            return new URL(getData().core.url);
        }

        protected File getDestination() {
            return Lifecycle.get().getHudsonWar();
        }

        public String getName() {
            return "hudson.war";
        }

        protected void onSuccess() {
            status = new Success();
        }

        @Override
        protected void replace(File dst, File src) throws IOException {
            Lifecycle.get().rewriteHudsonWar(src);
        }
    }

    /**
     * Adds the update center data retriever to HTML.
     */
    @Extension
    public static class PageDecoratorImpl extends PageDecorator {
        public PageDecoratorImpl() {
            super(PageDecoratorImpl.class);
        }
    }

    /**
     * Sequence number generator.
     */
    private static final AtomicInteger iota = new AtomicInteger();

    private static final long DAY = DAYS.toMillis(1);

    private static final Logger LOGGER = Logger.getLogger(UpdateCenter.class.getName());

    public static boolean neverUpdate = Boolean.getBoolean(UpdateCenter.class.getName()+".never");
}
