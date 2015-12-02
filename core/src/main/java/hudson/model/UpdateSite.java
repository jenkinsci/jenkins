/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Seiji Sogabe,
 *                          Andrew Bayer
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

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.HttpResponses;
import hudson.util.TextFile;
import static hudson.util.TimeUnit2.*;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.model.DownloadSettings;
import jenkins.util.JSONSignatureValidator;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Source of the update center information, like "http://jenkins-ci.org/update-center.json"
 *
 * <p>
 * Jenkins can have multiple {@link UpdateSite}s registered in the system, so that it can pick up plugins
 * from different locations.
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 * @since 1.333
 */
@ExportedBean
public class UpdateSite {
    /**
     * What's the time stamp of data file?
     * 0 means never.
     */
    private transient volatile long dataTimestamp;

    /**
     * When was the last time we asked a browser to check the data for us?
     * 0 means never.
     *
     * <p>
     * There's normally some delay between when we send HTML that includes the check code,
     * until we get the data back, so this variable is used to avoid asking too many browseres
     * all at once.
     */
    private transient volatile long lastAttempt;

    /**
     * If the attempt to fetch data fails, we progressively use longer time out before retrying,
     * to avoid overloading the server.
     */
    private transient volatile long retryWindow;

    /**
     * lastModified time of the data file when it was last read.
     */
    private transient long dataLastReadFromFile;

    /**
     * Latest data as read from the data file.
     */
    private transient Data data;

    /**
     * ID string for this update source.
     */
    private final String id;

    /**
     * Path to <tt>update-center.json</tt>, like <tt>http://jenkins-ci.org/update-center.json</tt>.
     */
    private final String url;



    public UpdateSite(String id, String url) {
        this.id = id;
        this.url = url;
    }

    /**
     * Get ID string.
     */
    @Exported
    public String getId() {
        return id;
    }

    @Exported
    public long getDataTimestamp() {
        assert dataTimestamp >= 0;
        return dataTimestamp;
    }

    /**
     * Update the data file from the given URL if the file
     * does not exist, or is otherwise due for update.
     * Accepted formats are JSONP or HTML with {@code postMessage}, not raw JSON.
     * @param signatureCheck whether to enforce the signature (may be off only for testing!)
     * @return null if no updates are necessary, or the future result
     * @since 1.502
     */
    public @CheckForNull Future<FormValidation> updateDirectly(final boolean signatureCheck) {
        if (! getDataFile().exists() || isDue()) {
            return Jenkins.getInstance().getUpdateCenter().updateService.submit(new Callable<FormValidation>() {
                @Override public FormValidation call() throws Exception {
                    return updateDirectlyNow(signatureCheck);
                }
            });
        } else {
            return null;
        }
    }

    @Restricted(NoExternalUse.class)
    public @Nonnull FormValidation updateDirectlyNow(boolean signatureCheck) throws IOException {
        return updateData(DownloadService.loadJSON(new URL(getUrl() + "?id=" + URLEncoder.encode(getId(), "UTF-8") + "&version=" + URLEncoder.encode(Jenkins.VERSION, "UTF-8"))), signatureCheck);
    }
    
    /**
     * This is the endpoint that receives the update center data file from the browser.
     */
    public FormValidation doPostBack(StaplerRequest req) throws IOException, GeneralSecurityException {
        DownloadSettings.checkPostBackAccess();
        return updateData(IOUtils.toString(req.getInputStream(),"UTF-8"), true);
    }

    private FormValidation updateData(String json, boolean signatureCheck)
            throws IOException {

        dataTimestamp = System.currentTimeMillis();

        JSONObject o = JSONObject.fromObject(json);

        try {
            int v = o.getInt("updateCenterVersion");
            if (v != 1) {
                throw new IllegalArgumentException("Unrecognized update center version: " + v);
            }
        } catch (JSONException x) {
            throw new IllegalArgumentException("Could not find (numeric) updateCenterVersion in " + json, x);
        }

        if (signatureCheck) {
            FormValidation e = verifySignature(o);
            if (e.kind!=Kind.OK) {
                LOGGER.severe(e.toString());
                return e;
            }
        }

        LOGGER.info("Obtained the latest update center data file for UpdateSource " + id);
        retryWindow = 0;
        getDataFile().write(json);
        return FormValidation.ok();
    }

    public FormValidation doVerifySignature() throws IOException {
        return verifySignature(getJSONObject());
    }

    /**
     * Verifies the signature in the update center data file.
     */
    private FormValidation verifySignature(JSONObject o) throws IOException {
        return getJsonSignatureValidator().verifySignature(o);
    }

    /**
     * Let sub-classes of UpdateSite provide their own signature validator.
     * @return the signature validator.
     */
    @Nonnull
    protected JSONSignatureValidator getJsonSignatureValidator() {
        return new JSONSignatureValidator("update site '"+id+"'");
    }

    /**
     * Returns true if it's time for us to check for new version.
     */
    public boolean isDue() {
        if(neverUpdate)     return false;
        if(dataTimestamp == 0)
            dataTimestamp = getDataFile().file.lastModified();
        long now = System.currentTimeMillis();
        
        retryWindow = Math.max(retryWindow,SECONDS.toMillis(15));
        
        boolean due = now - dataTimestamp > DAY && now - lastAttempt > retryWindow;
        if(due) {
            lastAttempt = now;
            retryWindow = Math.min(retryWindow*2, HOURS.toMillis(1)); // exponential back off but at most 1 hour
        }
        return due;
    }

    /**
     * Invalidates the cached data and force retrieval.
     *
     * @since 1.432
     */
    @RequirePOST
    public HttpResponse doInvalidateData() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        dataTimestamp = 0;
        return HttpResponses.ok();
    }

    /**
     * Loads the update center data, if any and if modified since last read.
     *
     * @return  null if no data is available.
     */
    public Data getData() {
        TextFile df = getDataFile();
        if (df.exists() && dataLastReadFromFile != df.file.lastModified()) {
            JSONObject o = getJSONObject();
            if (o!=null) {
                data = new Data(o);
                dataLastReadFromFile = df.file.lastModified();
            } else {
                data = null;
            }
        }
        return data;
    }

    /**
     * Gets the raw update center JSON data.
     */
    public JSONObject getJSONObject() {
        TextFile df = getDataFile();
        if(df.exists()) {
            try {
                return JSONObject.fromObject(df.read());
            } catch (JSONException e) {
                LOGGER.log(Level.SEVERE,"Failed to parse "+df,e);
                df.delete(); // if we keep this file, it will cause repeated failures
                return null;
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
    @Exported
    public List<Plugin> getAvailables() {
        List<Plugin> r = new ArrayList<Plugin>();
        Data data = getData();
        if(data==null)     return Collections.emptyList();
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

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns an "always up" server for Internet connectivity testing, or null if we are going to skip the test.
     */
    @Exported
    public String getConnectionCheckUrl() {
        Data dt = getData();
        if(dt==null)    return "http://www.google.com/";
        return dt.connectionCheckUrl;
    }

    /**
     * This is where we store the update center data.
     */
    private TextFile getDataFile() {
        return new TextFile(new File(Jenkins.getInstance().getRootDir(),
                                     "updates/" + getId()+".json"));
    }
    
    /**
     * Returns the list of plugins that are updates to currently installed ones.
     *
     * @return
     *      can be empty but never null.
     */
    @Exported
    public List<Plugin> getUpdates() {
        Data data = getData();
        if(data==null)      return Collections.emptyList(); // fail to determine
        
        List<Plugin> r = new ArrayList<Plugin>();
        for (PluginWrapper pw : Jenkins.getInstance().getPluginManager().getPlugins()) {
            Plugin p = pw.getUpdateInfo();
            if(p!=null) r.add(p);
        }
        
        return r;
    }
    
    /**
     * Does any of the plugin has updates?
     */
    @Exported
    public boolean hasUpdates() {
        Data data = getData();
        if(data==null)      return false;
        
        for (PluginWrapper pw : Jenkins.getInstance().getPluginManager().getPlugins()) {
            if(!pw.isBundled() && pw.getUpdateInfo()!=null)
                // do not advertize updates to bundled plugins, since we generally want users to get them
                // as a part of jenkins.war updates. This also avoids unnecessary pinning of plugins. 
                return true;
        }
        return false;
    }
    
    
    /**
     * Exposed to get rid of hardcoding of the URL that serves up update-center.json
     * in Javascript.
     */
    @Exported
    public String getUrl() {
        return url;
    }

    /**
     * Where to actually download the update center?
     *
     * @deprecated
     *      Exposed only for UI.
     */
    @Deprecated
    public String getDownloadUrl() {
        /*
            HACKISH:

            Loading scripts in HTTP from HTTPS pages cause browsers to issue a warning dialog.
            The elegant way to solve the problem is to always load update center from HTTPS,
            but our backend mirroring scheme isn't ready for that. So this hack serves regular
            traffic in HTTP server, and only use HTTPS update center for Jenkins in HTTPS.

            We'll monitor the traffic to see if we can sustain this added traffic.
         */
        if (url.equals("http://updates.jenkins-ci.org/update-center.json") && Jenkins.getInstance().isRootUrlSecure())
            return "https"+url.substring(4);
        return url;
    }

    /**
     * Is this the legacy default update center site?
     */
    public boolean isLegacyDefault() {
        return id.equals(UpdateCenter.ID_DEFAULT) && url.startsWith("http://hudson-ci.org/") || url.startsWith("http://updates.hudson-labs.org/");
    }

    /**
     * In-memory representation of the update center data.
     */
    public final class Data {
        /**
         * The {@link UpdateSite} ID.
         */
        public final String sourceId;

        /**
         * The latest jenkins.war.
         */
        public final Entry core;
        /**
         * Plugins in the repository, keyed by their artifact IDs.
         */
        public final Map<String,Plugin> plugins = new TreeMap<String,Plugin>(String.CASE_INSENSITIVE_ORDER);

        /**
         * If this is non-null, Jenkins is going to check the connectivity to this URL to make sure
         * the network connection is up. Null to skip the check.
         */
        public final String connectionCheckUrl;

        Data(JSONObject o) {
            this.sourceId = (String)o.get("id");
            JSONObject c = o.optJSONObject("core");
            if (c!=null) {
                core = new Entry(sourceId, c, url);
            } else {
                core = null;
            }
            for(Map.Entry<String,JSONObject> e : (Set<Map.Entry<String,JSONObject>>)o.getJSONObject("plugins").entrySet()) {
                plugins.put(e.getKey(),new Plugin(sourceId, e.getValue()));
            }

            connectionCheckUrl = (String)o.get("connectionCheckUrl");
        }

        /**
         * Is there a new version of the core?
         */
        public boolean hasCoreUpdates() {
            return core != null && core.isNewerThan(Jenkins.VERSION);
        }

        /**
         * Do we support upgrade?
         */
        public boolean canUpgrade() {
            return Lifecycle.get().canRewriteHudsonWar();
        }
    }

    @ExportedBean
    public static class Entry {
        /**
         * {@link UpdateSite} ID.
         */
        @Exported
        public final String sourceId;

        /**
         * Artifact ID.
         */
        @Exported
        public final String name;
        /**
         * The version.
         */
        @Exported
        public final String version;
        /**
         * Download URL.
         */
        @Exported
        public final String url;


        // non-private, non-final for test
        @Restricted(NoExternalUse.class)
        /* final */ String sha1;

        public Entry(String sourceId, JSONObject o) {
            this(sourceId, o, null);
        }

        Entry(String sourceId, JSONObject o, String baseURL) {
            this.sourceId = sourceId;
            this.name = o.getString("name");
            this.version = o.getString("version");

            // Trim this to prevent issues when the other end used Base64.encodeBase64String that added newlines
            // to the end in old commons-codec. Not the case on updates.jenkins-ci.org, but let's be safe.
            this.sha1 = Util.fixEmptyAndTrim(o.optString("sha1"));

            String url = o.getString("url");
            if (!URI.create(url).isAbsolute()) {
                if (baseURL == null) {
                    throw new IllegalArgumentException("Cannot resolve " + url + " without a base URL");
                }
                url = URI.create(baseURL).resolve(url).toString();
            }
            this.url = url;
        }

        /**
         * The base64 encoded binary SHA-1 checksum of the file.
         * Can be null if not provided by the update site.
         * @since TODO
         */
        // TODO @Exported assuming we want this in the API
        // TODO No new API in LTS, remove for mainline
        @Restricted(NoExternalUse.class)
        public String getSha1() {
            return sha1;
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

        public Api getApi() {
            return new Api(this);
        }

    }

    public final class Plugin extends Entry {
        /**
         * Optional URL to the Wiki page that discusses this plugin.
         */
        @Exported
        public final String wiki;
        /**
         * Human readable title of the plugin, taken from Wiki page.
         * Can be null.
         *
         * <p>
         * beware of XSS vulnerability since this data comes from Wiki
         */
        @Exported
        public final String title;
        /**
         * Optional excerpt string.
         */
        @Exported
        public final String excerpt;
        /**
         * Optional version # from which this plugin release is configuration-compatible.
         */
        @Exported
        public final String compatibleSinceVersion;
        /**
         * Version of Jenkins core this plugin was compiled against.
         */
        @Exported
        public final String requiredCore;
        /**
         * Categories for grouping plugins, taken from labels assigned to wiki page.
         * Can be null.
         */
        @Exported
        public final String[] categories;

        /**
         * Dependencies of this plugin.
         */
        @Exported
        public final Map<String,String> dependencies = new HashMap<String,String>();
        
        /**
         * Optional dependencies of this plugin.
         */
        @Exported
        public final Map<String,String> optionalDependencies = new HashMap<String,String>();

        @DataBoundConstructor
        public Plugin(String sourceId, JSONObject o) {
            super(sourceId, o, UpdateSite.this.url);
            this.wiki = get(o,"wiki");
            this.title = get(o,"title");
            this.excerpt = get(o,"excerpt");
            this.compatibleSinceVersion = get(o,"compatibleSinceVersion");
            this.requiredCore = get(o,"requiredCore");
            this.categories = o.has("labels") ? (String[])o.getJSONArray("labels").toArray(new String[0]) : null;
            for(Object jo : o.getJSONArray("dependencies")) {
                JSONObject depObj = (JSONObject) jo;
                // Make sure there's a name attribute, that that name isn't maven-plugin - we ignore that one -
                // and that the optional value isn't true.
                if (get(depObj,"name")!=null
                    && !get(depObj,"name").equals("maven-plugin")) {
                    if (get(depObj, "optional").equals("false")) {
                        dependencies.put(get(depObj, "name"), get(depObj, "version"));
                    } else {
                        optionalDependencies.put(get(depObj, "name"), get(depObj, "version"));
                    }
                }
                
            }

        }

        private String get(JSONObject o, String prop) {
            if(o.has(prop))
                return o.getString(prop);
            else
                return null;
        }

        public String getDisplayName() {
            String displayName;
            if(title!=null)
                displayName = title;
            else
                displayName = name;
            return StringUtils.removeStart(displayName, "Jenkins ");
        }

        /**
         * If some version of this plugin is currently installed, return {@link PluginWrapper}.
         * Otherwise null.
         */
        @Exported
        public PluginWrapper getInstalled() {
            PluginManager pm = Jenkins.getInstance().getPluginManager();
            return pm.getPlugin(name);
        }

        /**
         * If the plugin is already installed, and the new version of the plugin has a "compatibleSinceVersion"
         * value (i.e., it's only directly compatible with that version or later), this will check to
         * see if the installed version is older than the compatible-since version. If it is older, it'll return false.
         * If it's not older, or it's not installed, or it's installed but there's no compatibleSinceVersion
         * specified, it'll return true.
         */
        @Exported
        public boolean isCompatibleWithInstalledVersion() {
            PluginWrapper installedVersion = getInstalled();
            if (installedVersion != null) {
                if (compatibleSinceVersion != null) {
                    if (new VersionNumber(installedVersion.getVersion())
                            .isOlderThan(new VersionNumber(compatibleSinceVersion))) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Returns a list of dependent plugins which need to be installed or upgraded for this plugin to work.
         */
        @Exported
        public List<Plugin> getNeededDependencies() {
            List<Plugin> deps = new ArrayList<Plugin>();

            for(Map.Entry<String,String> e : dependencies.entrySet()) {
                Plugin depPlugin = Jenkins.getInstance().getUpdateCenter().getPlugin(e.getKey());
                if (depPlugin == null) {
                    LOGGER.log(Level.WARNING, "Could not find dependency {0} of {1}", new Object[] {e.getKey(), name});
                    continue;
                }
                VersionNumber requiredVersion = new VersionNumber(e.getValue());
                
                // Is the plugin installed already? If not, add it.
                PluginWrapper current = depPlugin.getInstalled();

                if (current ==null) {
                    deps.add(depPlugin);
                }
                // If the dependency plugin is installed, is the version we depend on newer than
                // what's installed? If so, upgrade.
                else if (current.isOlderThan(requiredVersion)) {
                    deps.add(depPlugin);
                }
            }

            for(Map.Entry<String,String> e : optionalDependencies.entrySet()) {
                Plugin depPlugin = Jenkins.getInstance().getUpdateCenter().getPlugin(e.getKey());
                if (depPlugin == null) {
                    continue;
                }
                VersionNumber requiredVersion = new VersionNumber(e.getValue());

                PluginWrapper current = depPlugin.getInstalled();

                // If the optional dependency plugin is installed, is the version we depend on newer than
                // what's installed? If so, upgrade.
                if (current != null && current.isOlderThan(requiredVersion)) {
                    deps.add(depPlugin);
                }
            }

            return deps;
        }
        
        public boolean isForNewerHudson() {
            try {
                return requiredCore!=null && new VersionNumber(requiredCore).isNewerThan(
                  new VersionNumber(Jenkins.VERSION.replaceFirst("SHOT *\\(private.*\\)", "SHOT")));
            } catch (NumberFormatException nfe) {
                return true;  // If unable to parse version
            }
        }

        public VersionNumber getNeededDependenciesRequiredCore() {
            VersionNumber versionNumber = null;
            try {
                versionNumber = requiredCore == null ? null : new VersionNumber(requiredCore);
            } catch (NumberFormatException nfe) {
                // unable to parse version
            }
            for (Plugin p: getNeededDependencies()) {
                VersionNumber v = p.getNeededDependenciesRequiredCore();
                if (versionNumber == null || v.isNewerThan(versionNumber)) versionNumber = v;
            }
            return versionNumber;
        }

        public boolean isNeededDependenciesForNewerJenkins() {
            for (Plugin p: getNeededDependencies()) {
                if (p.isForNewerHudson() || p.isNeededDependenciesForNewerJenkins()) return true;
            }
            return false;
        }

        /**
         * If at least some of the plugin's needed dependencies are already installed, and the new version of the
         * needed dependencies plugin have a "compatibleSinceVersion"
         * value (i.e., it's only directly compatible with that version or later), this will check to
         * see if the installed version is older than the compatible-since version. If it is older, it'll return false.
         * If it's not older, or it's not installed, or it's installed but there's no compatibleSinceVersion
         * specified, it'll return true.
         */
        public boolean isNeededDependenciesCompatibleWithInstalledVersion() {
            for (Plugin p: getNeededDependencies()) {
                if (!p.isCompatibleWithInstalledVersion() || !p.isNeededDependenciesCompatibleWithInstalledVersion())
                    return false;
            }
            return true;
        }

        /**
         * @deprecated as of 1.326
         *      Use {@link #deploy()}.
         */
        @Deprecated
        public void install() {
            deploy();
        }

        public Future<UpdateCenterJob> deploy() {
            return deploy(false);
        }

        /**
         * Schedules the installation of this plugin.
         *
         * <p>
         * This is mainly intended to be called from the UI. The actual installation work happens
         * asynchronously in another thread.
         *
         * @param dynamicLoad
         *      If true, the plugin will be dynamically loaded into this Jenkins. If false,
         *      the plugin will only take effect after the reboot.
         *      See {@link UpdateCenter#isRestartRequiredForCompletion()}
         */
        public Future<UpdateCenterJob> deploy(boolean dynamicLoad) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
            for (Plugin dep : getNeededDependencies()) {
                UpdateCenter.InstallationJob job = uc.getJob(dep);
                if (job == null || job.status instanceof UpdateCenter.DownloadJob.Failure) {
                    LOGGER.log(Level.WARNING, "Adding dependent install of " + dep.name + " for plugin " + name);
                    dep.deploy(dynamicLoad);
                } else {
                    LOGGER.log(Level.WARNING, "Dependent install of " + dep.name + " for plugin " + name + " already added, skipping");
                }
            }
            return uc.addJob(uc.new InstallationJob(this, UpdateSite.this, Jenkins.getAuthentication(), dynamicLoad));
        }

        /**
         * Schedules the downgrade of this plugin.
         */
        public Future<UpdateCenterJob> deployBackup() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
            return uc.addJob(uc.new PluginDowngradeJob(this, UpdateSite.this, Jenkins.getAuthentication()));
        }
        /**
         * Making the installation web bound.
         */
        @RequirePOST
        public HttpResponse doInstall() throws IOException {
            deploy(false);
            return HttpResponses.redirectTo("../..");
        }

        @RequirePOST
        public HttpResponse doInstallNow() throws IOException {
            deploy(true);
            return HttpResponses.redirectTo("../..");
        }

        /**
         * Performs the downgrade of the plugin.
         */
        @RequirePOST
        public HttpResponse doDowngrade() throws IOException {
            deployBackup();
            return HttpResponses.redirectTo("../..");
        }
    }

    private static final long DAY = DAYS.toMillis(1);

    private static final Logger LOGGER = Logger.getLogger(UpdateSite.class.getName());

    // The name uses UpdateCenter for compatibility reason.
    public static boolean neverUpdate = Boolean.getBoolean(UpdateCenter.class.getName()+".never");

}
