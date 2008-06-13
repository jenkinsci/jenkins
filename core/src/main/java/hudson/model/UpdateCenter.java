package hudson.model;

import hudson.Functions;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.util.DaemonThreadFactory;
import hudson.util.TextFile;
import static hudson.util.TimeUnit2.DAYS;
import net.sf.json.JSONObject;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLConnection;
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
 *
 * @author Kohsuke Kawaguchi
 * @since 1.220
 */
public class UpdateCenter implements ModelObject {
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
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
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

        @DataBoundConstructor
        public Plugin(JSONObject o) {
            super(o);
            this.wiki = get(o,"wiki");
            this.title = get(o,"title");
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

            // the first job is always the connectivity check
            if(jobs.size()==0)
                new ConnectionCheckJob().schedule();

            LOGGER.info("Scheduling the installation of "+getDisplayName());
            UpdateCenter.InstallationJob job = new InstallationJob(this);
            job.schedule();
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
     * Things that {@link UpdateCenter#installerService} executes.
     *
     * This object will have the <tt>row.jelly</tt> which renders the job on UI.
     */
    public abstract class UpdateCenterJob implements Runnable {
        public void schedule() {
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
            try {
                statuses.add("Checking internet connectivity");
                testConnection(new URL("http://www.google.com/"));

                statuses.add("Checking java.net connectivity");
                testConnection(new URL("https://hudson.dev.java.net/?uctest"));

                statuses.add("Success");
            } catch (IOException e) {
                statuses.add(Functions.printThrowable(e));
            }
        }

        public String[] getStatuses() {
            synchronized (statuses) {
                return statuses.toArray(new String[statuses.size()]);
            }
        }

        private void testConnection(URL url) throws IOException {
            InputStream in = url.openConnection(Hudson.getInstance().createProxy()).getInputStream();
            IOUtils.copy(in,new ByteArrayOutputStream());
            in.close();
        }
    }


    /**
     * Represents the state of the installation activity of one plugin.
     */
    public final class InstallationJob extends UpdateCenterJob {
        /**
         * What plugin are we trying to install?
         */
        public final Plugin plugin;
        /**
         * Unique ID that identifies this job.
         */
        public final int id = iota.incrementAndGet();
        /**
         * Immutable object representing the current state of this job.
         */
        public volatile InstallationStatus status = new Pending();

        public InstallationJob(Plugin plugin) {
            this.plugin = plugin;
        }

        public void run() {
            try {
                LOGGER.info("Starting the installation of "+plugin.name);

                // for security reasons, only install from hudson.dev.java.net for now, which is also conveniently
                // https to guarantee transport level security.
                if(!plugin.url.startsWith("https://hudson.dev.java.net/")) {
                    throw new IOException("Installation from non-official repository at "+plugin.url+" is not support yet");
                }

                // In the future if we are to open up update center to 3rd party, we need more elaborate scheme
                // like signing to ensure the safety of the bits.
                URLConnection con = new URL(plugin.url).openConnection(Hudson.getInstance().createProxy());
                int total = con.getContentLength();
                CountingInputStream in = new CountingInputStream(con.getInputStream());
                byte[] buf = new byte[8192];
                int len;

                PluginManager pm = Hudson.getInstance().getPluginManager();
                File baseDir = pm.rootDir;
                File target = new File(baseDir, plugin.name + ".tmp");
                OutputStream out = new FileOutputStream(target);

                LOGGER.info("Downloading "+plugin.name);
                while((len=in.read(buf))>=0) {
                    out.write(buf,0,len);
                    status = new Installing(total==-1 ? -1 : in.getCount()*100/total);
                }

                in.close();
                out.close();

                File hpi = new File(baseDir, plugin.name + ".hpi");
                hpi.delete();
                if(!target.renameTo(hpi)) {
                    throw new IOException("Failed to rename "+target+" to "+hpi);
                }

                LOGGER.info("Installation successful: "+plugin.name);
                pm.pluginUploaded = true;
                status = new Success();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to install "+plugin.name,e);
                status = new Failure(e);
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
     * Sequence number generator.
     */
    private static final AtomicInteger iota = new AtomicInteger();

    private static final long DAY = DAYS.toMillis(1);

    private static final Logger LOGGER = Logger.getLogger(UpdateCenter.class.getName());
}
