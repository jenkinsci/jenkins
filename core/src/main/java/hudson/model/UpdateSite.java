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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jenkins.util.MemoryReductionUtil.getPresizedMutableMap;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import jenkins.plugins.DetachedPluginsUtil;
import jenkins.security.UpdateSiteWarningsConfiguration;
import jenkins.security.UpdateSiteWarningsMonitor;
import jenkins.util.JSONSignatureValidator;
import jenkins.util.PluginLabelUtil;
import jenkins.util.SystemProperties;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
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
     * until we get the data back, so this variable is used to avoid asking too many browsers
     * all at once.
     */
    private transient volatile long lastAttempt;

    /**
     * If the attempt to fetch data fails, we progressively use longer time out before retrying,
     * to avoid overloading the server.
     */
    private transient volatile long retryWindow;

    /**
     * Latest data as read from the data file.
     */
    private transient Data data;

    /**
     * ID string for this update source.
     */
    private final String id;

    /**
     * Path to {@code update-center.json}, like {@code http://jenkins-ci.org/update-center.json}.
     */
    private final String url;

    /**
     * the prefix for the signature validator name
     */
    private static final String signatureValidatorPrefix = "update site";

    private static final Set<String> warnedMissing = Collections.synchronizedSet(new HashSet<>());

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
     * @return null if no updates are necessary, or the future result
     * @since 2.222
     */
    public @CheckForNull Future<FormValidation> updateDirectly() {
        return updateDirectly(DownloadService.signatureCheck);
    }

    /**
     * Update the data file from the given URL if the file
     * does not exist, or is otherwise due for update.
     * Accepted formats are JSONP or HTML with {@code postMessage}, not raw JSON.
     * @param signatureCheck whether to enforce the signature (may be off only for testing!)
     * @return null if no updates are necessary, or the future result
     * @since 1.502
     * @deprecated use {@linkplain #updateDirectly()}
     */
    @Deprecated
    public @CheckForNull Future<FormValidation> updateDirectly(final boolean signatureCheck) {
        if (! getDataFile().exists() || isDue()) {
            return Jenkins.get().getUpdateCenter().updateService.submit(new Callable<>() {
                @Override public FormValidation call() throws Exception {
                    return updateDirectlyNow(signatureCheck);
                }
            });
        } else {
            return null;
        }
    }

    /**
     * Opens a connection to the given URL
     * @param src the url to connect to
     * @return A {@code URLConnection} for the given src URL
     * @since TODO
     */
    public URLConnection connect(URL src) throws IOException {
        return ProxyConfiguration.open(src);
    }

    /**
     * Validate the URL of the resource before downloading it.
     *
     * @param src The location of the resource on the network
     * @throws IOException if the validation fails
     * @since TODO
     */
    public void preValidate(URL src) throws IOException {
        // no validation needed in the default setup
    }

    /**
     * Forces an update of the data file from the configured URL, irrespective of the last time the data was retrieved.
     * @return A {@code FormValidation} indicating the if the update metadata was successfully downloaded from the configured update site
     * @since 2.222
     * @throws IOException if there was an error downloading or saving the file.
     */
    public @NonNull FormValidation updateDirectlyNow() throws IOException {
        return updateDirectlyNow(DownloadService.signatureCheck);
    }

    @Restricted(NoExternalUse.class)
    public @NonNull FormValidation updateDirectlyNow(boolean signatureCheck) throws IOException {
        return updateData(DownloadService.loadJSON(new URL(getUrl() + "?id=" + URLEncoder.encode(getId(), StandardCharsets.UTF_8) + "&version=" + URLEncoder.encode(Jenkins.VERSION, StandardCharsets.UTF_8))), signatureCheck);
    }

    protected FormValidation updateData(String json, boolean signatureCheck)
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
            FormValidation e = verifySignatureInternal(o);
            if (e.kind != FormValidation.Kind.OK) {
                LOGGER.severe(e.toString());
                return e;
            }
        }

        LOGGER.fine(() -> "Obtained the latest update center data file for UpdateSource " + id);
        retryWindow = 0;
        getDataFile().write(json);
        data = new Data(o);
        return FormValidation.ok();
    }

    public FormValidation doVerifySignature() throws IOException {
        return verifySignatureInternal(getJSONObject());
    }

    /**
     * Extension point to allow implementations of {@link UpdateSite} to create a custom
     * {@link UpdateCenter.InstallationJob}.
     *
     * @param plugin      the plugin to create the {@link UpdateCenter.InstallationJob} for.
     * @param uc          the {@link UpdateCenter}.
     * @param dynamicLoad {@code true} if the plugin should be attempted to be dynamically loaded.
     * @return the {@link UpdateCenter.InstallationJob}.
     * @since 2.9
     */
    protected UpdateCenter.InstallationJob createInstallationJob(Plugin plugin, UpdateCenter uc, boolean dynamicLoad) {
        return uc.new InstallationJob(plugin, this, Jenkins.getAuthentication2(), dynamicLoad);
    }

    /**
     * Verifies the signature in the update center data file.
     */
    @Restricted(NoExternalUse.class)
    public final FormValidation verifySignatureInternal(JSONObject o) throws IOException {
        return getJsonSignatureValidator().verifySignature(o);
    }

    /**
     * Let sub-classes of UpdateSite provide their own signature validator.
     * @return the signature validator.
     * @deprecated use {@link #getJsonSignatureValidator(String)} instead.
     */
    @Deprecated
    @NonNull
    protected JSONSignatureValidator getJsonSignatureValidator() {
        return getJsonSignatureValidator(null);
    }

    /**
     * Let sub-classes of UpdateSite provide their own signature validator.
     * @param name the name for the JSON signature Validator object.
     *              if name is null, then the default name will be used,
     *              which is "update site" followed by the update site id
     * @return the signature validator.
     * @since 2.21
     */
    @NonNull
    protected JSONSignatureValidator getJsonSignatureValidator(@CheckForNull String name) {
        if (name == null) {
            name = signatureValidatorPrefix + " '" + id + "'";
        }
        return new JSONSignatureValidator(name);
    }

    /**
     * Returns true if it's time for us to check for new version.
     */
    public synchronized boolean isDue() {
        if (neverUpdate)     return false;
        if (dataTimestamp == 0)
            dataTimestamp = getDataFile().file.lastModified();
        long now = System.currentTimeMillis();

        retryWindow = Math.max(retryWindow, SECONDS.toMillis(15));

        boolean due = now - dataTimestamp > DAY && now - lastAttempt > retryWindow;
        if (due) {
            lastAttempt = now;
            retryWindow = Math.min(retryWindow * 2, HOURS.toMillis(1)); // exponential back off but at most 1 hour
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
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        dataTimestamp = 0;
        data = null;
        return HttpResponses.ok();
    }

    /**
     * Loads the update center data, if any.
     *
     * @return  null if no data is available.
     */
    @CheckForNull
    public Data getData() {
        if (data == null) {
            JSONObject o = getJSONObject();
            if (o != null) {
                data = new Data(o);
            }
        }
        return data;
    }

    /**
     * Whether {@link #getData} might be blocking.
     */
    // Internal use only
    boolean hasUnparsedData() {
        return data == null && getDataFile().exists();
    }

    /**
     * Gets the raw update center JSON data.
     */
    public JSONObject getJSONObject() {
        TextFile df = getDataFile();
        if (df.exists()) {
            long start = System.nanoTime();
            try {
                JSONObject o = JSONObject.fromObject(df.read());
                LOGGER.fine(() -> String.format("Loaded and parsed %s in %.01fs", df, (System.nanoTime() - start) / 1_000_000_000.0));
                return o;
            } catch (JSONException | IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to parse " + df, e);
                try {
                    df.delete(); // if we keep this file, it will cause repeated failures
                } catch (IOException e2) {
                    LOGGER.log(Level.SEVERE, "Failed to delete " + df, e2);
                }
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
        List<Plugin> r = new ArrayList<>();
        Data data = getData();
        if (data == null)     return Collections.emptyList();
        for (Plugin p : data.plugins.values()) {
            if (p.getInstalled() == null)
                r.add(p);
        }
        r.sort((plugin, t1) -> {
            final int pop = t1.popularity.compareTo(plugin.popularity);
            if (pop != 0) {
                return pop; // highest popularity first
            }
            return plugin.getDisplayName().compareTo(t1.getDisplayName());
        });
        return r;
    }

    /**
     * Gets the information about a specific plugin.
     *
     * @param artifactId
     *      The short name of the plugin. Corresponds to {@link PluginWrapper#getShortName()}.
     *
     * @return
     *      {@code null} if no such information is found.
     */
    @CheckForNull
    public Plugin getPlugin(String artifactId) {
        Data dt = getData();
        if (dt == null)    return null;
        return dt.plugins.get(artifactId);
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Gets a URL for the Internet connection check.
     * @return  an "always up" server for Internet connectivity testing, or {@code null} if we are going to skip the test.
     */
    @Exported
    @CheckForNull
    public String getConnectionCheckUrl() {
        Data dt = getData();
        if (dt == null)    return "http://www.google.com/";
        return dt.connectionCheckUrl;
    }

    /**
     * This is where we store the update center data.
     */
    private TextFile getDataFile() {
        return new TextFile(new File(Jenkins.get().getRootDir(),
                                     "updates/" + getId() + ".json"));
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
        if (data == null)      return Collections.emptyList(); // fail to determine

        List<Plugin> r = new ArrayList<>();
        for (PluginWrapper pw : Jenkins.get().getPluginManager().getPlugins()) {
            Plugin p = pw.getUpdateInfo();
            if (p != null) r.add(p);
        }

        return r;
    }

    /**
     * Does any of the plugin has updates?
     */
    @Exported
    public boolean hasUpdates() {
        Data data = getData();
        if (data == null)      return false;

        for (PluginWrapper pw : Jenkins.get().getPluginManager().getPlugins()) {
            if (!pw.isBundled() && pw.getUpdateInfo() != null)
                // do not advertize updates to bundled plugins, since we generally want users to get them
                // as a part of jenkins.war updates. This also avoids unnecessary pinning of plugins.
                return true;
        }
        return false;
    }


    /**
     * Exposed to get rid of hardcoding of the URL that serves up update-center.json
     * in JavaScript.
     */
    @Exported
    public String getUrl() {
        return url;
    }

    /**
     *
     * @return the URL used by {@link jenkins.install.SetupWizard} for suggested plugins to install at setup time
     * @since 2.446
     */
    @Exported
    public String getSuggestedPluginsUrl() {
        String updateCenterJsonUrl = getUrl();
        return updateCenterJsonUrl.replace("/update-center.json", "/platform-plugins.json");
    }


    /**
     * URL which exposes the metadata location in a specific update site.
     * @param downloadable the downloadable id of a specific metatadata json (e.g. hudson.tasks.Maven.MavenInstaller.json)
     * @return the location
     * @since 2.20
     */
    @CheckForNull
    @Restricted(NoExternalUse.class)
    public String getMetadataUrlForDownloadable(String downloadable) {
        String siteUrl = getUrl();
        String updateSiteMetadataUrl = null;
        int baseUrlEnd = siteUrl.indexOf("update-center.json");
        if (baseUrlEnd != -1) {
            String siteBaseUrl = siteUrl.substring(0, baseUrlEnd);
            updateSiteMetadataUrl = siteBaseUrl + "updates/" + downloadable;
        } else {
            LOGGER.log(Level.WARNING, "Url {0} does not look like an update center:", siteUrl);
        }
        return updateSiteMetadataUrl;
    }

    /**
     * Where to actually download the update center?
     *
     * @deprecated
     *      Exposed only for UI.
     */
    @Deprecated
    public String getDownloadUrl() {
        return url;
    }

    /**
     * Is this the legacy default update center site?
     * @since 1.357
     */
    @Restricted(NoExternalUse.class)
    public boolean isLegacyDefault() {
        return isJenkinsCI();
    }

    private boolean isJenkinsCI() {
        return url != null
                && UpdateCenter.PREDEFINED_UPDATE_SITE_ID.equals(id)
                && url.startsWith("http://updates.jenkins-ci.org/");
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
        public final Map<String, Plugin> plugins = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        /**
         * List of warnings (mostly security) published with the update site.
         *
         * @since 2.40
         */
        private final Set<Warning> warnings = new HashSet<>();

        /**
         * Mapping of plugin IDs to deprecation notices
         *
         * @since 2.246
         */
        private final Map<String, Deprecation> deprecations = new HashMap<>();

        /**
         * If this is non-null, Jenkins is going to check the connectivity to this URL to make sure
         * the network connection is up. Null to skip the check.
         */
        public final String connectionCheckUrl;

        Data(JSONObject o) {
            this.sourceId = Util.intern((String) o.get("id"));
            JSONObject c = o.optJSONObject("core");
            if (c != null) {
                core = new Entry(sourceId, c, url);
            } else {
                core = null;
            }

            JSONArray w = o.optJSONArray("warnings");
            if (w != null) {
                for (int i = 0; i < w.size(); i++) {
                    try {
                        warnings.add(new Warning(w.getJSONObject(i)));
                    } catch (JSONException ex) {
                        LOGGER.log(Level.WARNING, "Failed to parse JSON for warning", ex);
                    }
                }
            }

            JSONObject deprecations = o.optJSONObject("deprecations");
            if (deprecations != null) {
                for (Iterator it = deprecations.keys(); it.hasNext(); ) {
                    try {
                        String pluginId = it.next().toString();
                        JSONObject entry = deprecations.getJSONObject(pluginId); // additional level of indirection to support future extensibility
                        if (entry != null) {
                            String referenceUrl = entry.getString("url");
                            if (referenceUrl != null) {
                                this.deprecations.put(pluginId, new Deprecation(referenceUrl));
                            }
                        }
                    } catch (RuntimeException ex) {
                        LOGGER.log(Level.WARNING, "Failed to parse JSON for deprecation", ex);
                    }
                }
            }

            for (Map.Entry<String, JSONObject> e : (Set<Map.Entry<String, JSONObject>>) o.getJSONObject("plugins").entrySet()) {
                Plugin p = new Plugin(sourceId, e.getValue());
                // JENKINS-33308 - include implied dependencies for older plugins that may need them
                List<PluginWrapper.Dependency> implicitDeps = DetachedPluginsUtil.getImpliedDependencies(p.name, p.requiredCore);
                if (!implicitDeps.isEmpty()) {
                    for (PluginWrapper.Dependency dep : implicitDeps) {
                        if (!p.dependencies.containsKey(dep.shortName)) {
                            p.dependencies.put(dep.shortName, dep.version);
                        }
                    }
                }
                plugins.put(Util.intern(e.getKey()), p);

                // compatibility with update sites that have no separate 'deprecated' top-level entry.
                // Also do this even if there are deprecations to potentially allow limiting the top-level entry to overridden URLs.
                if (p.hasCategory("deprecated")) {
                    if (!this.deprecations.containsKey(p.name)) {
                        this.deprecations.put(p.name, new Deprecation(p.wiki));
                    }
                }
            }

            connectionCheckUrl = (String) o.get("connectionCheckUrl");
        }

        /**
         * Returns the set of warnings
         * @return the set of warnings
         * @since 2.40
         */
        @Restricted(NoExternalUse.class)
        public Set<Warning> getWarnings() {
            return this.warnings;
        }

        /**
         * Returns the deprecations provided by the update site
         * @return the deprecations provided by the update site
         * @since 2.246
         */
        @Restricted(NoExternalUse.class)
        public Map<String, Deprecation> getDeprecations() {
            return this.deprecations;
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

        /**
         * Size of the file in bytes, or {@code null} if unknown.
         */
        private final Long size;

        // non-private, non-final for test
        @Restricted(NoExternalUse.class)
        /* final */ String sha1;

        @Restricted(NoExternalUse.class)
        /* final */ String sha256;

        @Restricted(NoExternalUse.class)
        /* final */ String sha512;

        public Entry(String sourceId, JSONObject o) {
            this(sourceId, o, null);
        }

        Entry(String sourceId, JSONObject o, String baseURL) {
            this.sourceId = sourceId;
            this.name = Util.intern(o.getString("name"));
            this.version = Util.intern(o.getString("version"));

            // Trim this to prevent issues when the other end used Base64.encodeBase64String that added newlines
            // to the end in old commons-codec. Not the case on updates.jenkins-ci.org, but let's be safe.
            this.sha1 = Util.fixEmptyAndTrim(o.optString("sha1"));
            this.sha256 = Util.fixEmptyAndTrim(o.optString("sha256"));
            this.sha512 = Util.fixEmptyAndTrim(o.optString("sha512"));

            Long fileSize = null;
            if (o.has("size")) {
                fileSize = o.getLong("size");
            }
            this.size = fileSize;

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
         * @since 1.641 (and 1.625.3 LTS)
         */
        // TODO @Exported assuming we want this in the API
        public String getSha1() {
            return sha1;
        }

        /**
         * The base64 encoded SHA-256 checksum of the file.
         * Can be null if not provided by the update site.
         * @since 2.130
         */
        public String getSha256() {
            return sha256;
        }

        /**
         * The base64 encoded SHA-512 checksum of the file.
         * Can be null if not provided by the update site.
         * @since 2.130
         */
        public String getSha512() {
            return sha512;
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

        /**
         * Size of the file being advertised in bytes, or {@code null} if unspecified/unknown.
         * @return size of the file if known, {@code null} otherwise.
         *
         * @since 2.325
         */
        // @Exported -- TODO unsure
        @Restricted(NoExternalUse.class)
        public Long getFileSize() {
            return size;
        }
    }

    /**
     * A version range for {@code Warning}s indicates which versions of a given plugin are affected
     * by it.
     *
     * {@link #name}, {@link #firstVersion} and {@link #lastVersion} fields are only used for administrator notices.
     *
     * The {@link #pattern} is used to determine whether a given warning applies to the current installation.
     *
     * @since 2.40
     */
    @Restricted(NoExternalUse.class)
    public static final class WarningVersionRange {
        /**
         * Human-readable English name for this version range, e.g. 'regular', 'LTS', '2.6 line'.
         */
        @Nullable
        public final String name;

        /**
         * First version in this version range to be subject to the warning.
         */
        @Nullable
        public final String firstVersion;

        /**
         * Last version in this version range to be subject to the warning.
         */
        @Nullable
        public final String lastVersion;

        /**
         * Regular expression pattern for this version range that matches all included version numbers.
         */
        @NonNull
        private final Pattern pattern;

        public WarningVersionRange(JSONObject o) {
            this.name = Util.fixEmpty(o.optString("name"));
            this.firstVersion = Util.intern(Util.fixEmpty(o.optString("firstVersion")));
            this.lastVersion = Util.intern(Util.fixEmpty(o.optString("lastVersion")));
            Pattern p;
            try {
                p = Pattern.compile(o.getString("pattern"));
            } catch (PatternSyntaxException ex) {
                LOGGER.log(Level.WARNING, "Failed to compile pattern '" + o.getString("pattern") + "', using '.*' instead", ex);
                p = Pattern.compile(".*");
            }
            this.pattern = p;
        }

        public boolean includes(VersionNumber number) {
            return pattern.matcher(number.toString()).matches();
        }
    }

    /**
     * Represents a deprecation of a certain component. Jenkins project policy determines exactly what it means.
     *
     * @since 2.246
     */
    @Restricted(NoExternalUse.class)
    public static final class Deprecation {
        /**
         * URL for this deprecation.
         *
         * Jenkins will show a link to this URL when displaying the deprecation message.
         */
        public final String url;

        public Deprecation(String url) {
            this.url = url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Deprecation that = (Deprecation) o;
            return Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url);
        }
    }

    @Restricted(NoExternalUse.class)
    public enum WarningType {
        CORE,
        PLUGIN,
        UNKNOWN
    }

    /**
     * Represents a warning about a certain component, mostly related to known security issues.
     *
     * @see UpdateSiteWarningsConfiguration
     * @see jenkins.security.UpdateSiteWarningsMonitor
     *
     * @since 2.40
     */
    @Restricted(NoExternalUse.class)
    public final class Warning {

        /**
         * The type classifier for this warning.
         */
        @NonNull
        public /* final */ WarningType type;

        /**
         * The globally unique ID of this warning.
         *
         * <p>This is typically the CVE identifier or SECURITY issue (Jenkins project);
         * possibly with a unique suffix (e.g. artifactId) if either applies to multiple components.</p>
         */
        @Exported
        @NonNull
        public final String id;

        /**
         * The name of the affected component.
         * <ul>
         *   <li>If type is 'core', this is 'core' by convention.
         *   <li>If type is 'plugin', this is the artifactId of the affected plugin
         * </ul>
         */
        @Exported
        @NonNull
        public final String component;

        /**
         * A short, English language explanation for this warning.
         */
        @Exported
        @NonNull
        public final String message;

        /**
         * A URL with more information about this, typically a security advisory. For use in administrator notices
         * only, so
         */
        @Exported
        @NonNull
        public final String url;

        /**
         * A list of named version ranges specifying which versions of the named component this warning applies to.
         *
         * If this list is empty, all versions of the component are considered to be affected by this warning.
         */
        @Exported
        @NonNull
        public final List<WarningVersionRange> versionRanges;

        /**
         *
         * @param o the {@link JSONObject} representing the warning
         * @throws JSONException if the argument does not match the expected format
         */
        @Restricted(NoExternalUse.class)
        public Warning(JSONObject o) {
            try {
                this.type = WarningType.valueOf(o.getString("type").toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                this.type = WarningType.UNKNOWN;
            }
            this.id = o.getString("id");
            this.component = Util.intern(o.getString("name"));
            this.message = o.getString("message");
            this.url = o.getString("url");

            if (o.has("versions")) {
                JSONArray versions = o.getJSONArray("versions");
                List<WarningVersionRange> ranges = new ArrayList<>(versions.size());
                for (int i = 0; i < versions.size(); i++) {
                    WarningVersionRange range = new WarningVersionRange(versions.getJSONObject(i));
                    ranges.add(range);
                }
                this.versionRanges = Collections.unmodifiableList(ranges);
            } else {
                this.versionRanges = Collections.emptyList();
            }
        }

        /**
         * Two objects are considered equal if they are the same type and have the same ID.
         *
         * @param o the other object
         * @return true iff this object and the argument are considered equal
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Warning)) return false;

            Warning warning = (Warning) o;

            return id.equals(warning.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        public boolean isPluginWarning(@NonNull String pluginName) {
            return type == WarningType.PLUGIN && pluginName.equals(this.component);
        }

        /**
         * Returns true if this warning is relevant to the current configuration
         * @return true if this warning is relevant to the current configuration
         */
        public boolean isRelevant() {
            switch (this.type) {
                case CORE:
                    VersionNumber current = Jenkins.getVersion();
                    return isRelevantToVersion(current);
                case PLUGIN:

                    // check whether plugin is installed
                    PluginWrapper plugin = Jenkins.get().getPluginManager().getPlugin(this.component);
                    if (plugin == null) {
                        return false;
                    }

                    // check whether warning is relevant to installed version
                    VersionNumber currentCore = plugin.getVersionNumber();
                    if (!isRelevantToVersion(currentCore)) {
                        return false;
                    }
                    return true;
                case UNKNOWN:
                default:
                    return false;
            }
        }

        /**
         * Returns whether this warning is fixable by updating the affected component.
         * @return {@code true} if the warning does not apply to the latest offered version of core or the affected plugin;
         * {@code false} if it does; and {@code null} when the affected component isn't being offered, or it's a warning
         * for something other than core or a plugin.
         */
        @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL")
        public Boolean isFixable() {
            final Data data = UpdateSite.this.data;
            if (data == null) {
                return null;
            }
            switch (this.type) {
                case CORE: {
                    final Entry core = data.core;
                    if (core == null) {
                        return null;
                    }
                    final VersionNumber latestCoreVersion = new VersionNumber(core.version);
                    return !isRelevantToVersion(latestCoreVersion);
                }
                case PLUGIN: {
                    final Entry plugin = data.plugins.get(component);
                    if (plugin == null) {
                        return null;
                    }
                    final VersionNumber latestCoreVersion = new VersionNumber(plugin.version);
                    return !isRelevantToVersion(latestCoreVersion);
                }
                default:
                    return null;
            }
        }

        public boolean isRelevantToVersion(@NonNull VersionNumber version) {
            if (this.versionRanges.isEmpty()) {
                // no version ranges specified, so all versions are affected
                return true;
            }

            for (UpdateSite.WarningVersionRange range : this.versionRanges) {
                if (range.includes(version)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String get(JSONObject o, String prop) {
        if (o.has(prop))
            return o.getString(prop);
        else
            return null;
    }

    static final Predicate<Object> IS_DEP_PREDICATE = x -> x instanceof JSONObject && get((JSONObject) x, "name") != null;
    static final Predicate<Object> IS_NOT_OPTIONAL = x -> "false".equals(get((JSONObject) x, "optional"));

    /**
     * Metadata for one issue tracker provided by the update site.
     */
    @Restricted(NoExternalUse.class)
    public static final class IssueTracker {
        /**
         * A string specifying the type of issue tracker.
         */
        public final String type;
        /**
         * Issue tracker URL that can be used to view previously reported issues.
         */
        public final String viewUrl;
        /**
         * Issue tracker URL that can be used to report a new issue.
         */
        @CheckForNull
        public final String reportUrl;

        public IssueTracker(@NonNull String type, @NonNull String viewUrl, @CheckForNull String reportUrl) {
            this.type = type;
            this.viewUrl = viewUrl;
            this.reportUrl = reportUrl;
        }

        private static IssueTracker createFromJSONObject(Object o) {
            if (o instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) o;
                if (jsonObject.has("type") && jsonObject.has("viewUrl") && jsonObject.has("reportUrl")) {
                    return new IssueTracker(jsonObject.getString("type"), jsonObject.getString("viewUrl"), jsonObject.getString("reportUrl"));
                }
            }
            return null;
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
         * Can be {@code null} if the update center does not return categories.
         */
        @Exported
        @CheckForNull
        public final String[] categories;

        /**
         * Dependencies of this plugin, a name -&gt; version mapping.
         */
        @Exported
        public final Map<String, String> dependencies;

        /**
         * Optional dependencies of this plugin.
         */
        @Exported
        public final Map<String, String> optionalDependencies;

        /**
         * Set of plugins, this plugin is a incompatible dependency to.
         */
        private Set<Plugin> incompatibleParentPlugins;

        /**
         * Date when this plugin was released.
         * @since 2.224
         */
        @Exported
        public final Date releaseTimestamp;

        /**
         * Popularity of this plugin.
         *
         * @since 2.233
         */
        @Restricted(NoExternalUse.class)
        public final Double popularity;

        /**
         * Issue trackers associated with this plugin.
         * This list is sorted by preference in descending order, meaning a UI
         * supporting only one issue tracker should reference the first one
         * supporting the desired behavior (like having a {@code reportUrl}).
         */
        @Restricted(NoExternalUse.class)
        public IssueTracker[] issueTrackers;

        @DataBoundConstructor
        public Plugin(String sourceId, JSONObject o) {
            super(sourceId, o, UpdateSite.this.url);
            this.wiki = get(o, "wiki");
            this.title = get(o, "title");
            this.excerpt = get(o, "excerpt");
            this.compatibleSinceVersion = Util.intern(get(o, "compatibleSinceVersion"));
            this.requiredCore = Util.intern(get(o, "requiredCore"));
            final String releaseTimestamp = get(o, "releaseTimestamp");
            Date date = null;
            if (releaseTimestamp != null) {
                try {
                    date = Date.from(Instant.parse(releaseTimestamp));
                } catch (RuntimeException ex) {
                    LOGGER.log(Level.FINE, "Failed to parse releaseTimestamp for " + title + " from " + sourceId, ex);
                }
            }
            final String popularityFromJson = get(o, "popularity");
            double popularity = 0.0;
            if (popularityFromJson != null) {
                try {
                    popularity = Double.parseDouble(popularityFromJson);
                } catch (NumberFormatException nfe) {
                    LOGGER.log(Level.FINE, "Failed to parse popularity: '" + popularityFromJson + "' for plugin " + this.title);
                }
            }
            this.popularity = popularity;
            this.releaseTimestamp = date;
            this.categories = o.has("labels") ? PluginLabelUtil.canonicalLabels(o.getJSONArray("labels")) : null;
            this.issueTrackers = o.has("issueTrackers") ? o.getJSONArray("issueTrackers").stream().map(IssueTracker::createFromJSONObject).filter(Objects::nonNull).toArray(IssueTracker[]::new) : null;

            JSONArray ja = o.getJSONArray("dependencies");
            int depCount = (int) ja.stream().filter(IS_DEP_PREDICATE.and(IS_NOT_OPTIONAL)).count();
            int optionalDepCount = (int) ja.stream().filter(IS_DEP_PREDICATE.and(IS_NOT_OPTIONAL.negate())).count();
            dependencies = getPresizedMutableMap(depCount);
            optionalDependencies = getPresizedMutableMap(optionalDepCount);

            for (Object jo : o.getJSONArray("dependencies")) {
                JSONObject depObj = (JSONObject) jo;
                // Make sure there's a name attribute and that the optional value isn't true.
                String depName = Util.intern(get(depObj, "name"));
                if (depName != null) {
                    if (get(depObj, "optional").equals("false")) {
                        dependencies.put(depName, Util.intern(get(depObj, "version")));
                    } else {
                        optionalDependencies.put(depName, Util.intern(get(depObj, "version")));
                    }
                }
            }

        }

        @Restricted(NoExternalUse.class)
        public boolean isDeprecated() {
            return getDeprecation() != null;
        }

        @Restricted(NoExternalUse.class)
        public UpdateSite.Deprecation getDeprecation() {
            return Jenkins.get().getUpdateCenter().getSite(sourceId).getData().getDeprecations().get(this.name);
        }

        public String getDisplayName() {
            String displayName;
            if (title != null)
                displayName = title;
            else
                displayName = name;
            String removePrefix = "Jenkins ";
            if (displayName != null && displayName.startsWith(removePrefix)) {
                return displayName.substring(removePrefix.length());
            }
            return displayName;
        }

        /**
         * If some version of this plugin is currently installed, return {@link PluginWrapper}.
         * Otherwise null.
         */
        @Exported
        public PluginWrapper getInstalled() {
            PluginManager pm = Jenkins.get().getPluginManager();
            return pm.getPlugin(name);
        }

        /**
         * Returns true if the plugin and its dependencies are fully compatible with the current installation
         * This is set to restricted for now, since it is only being used by Jenkins UI or Restful API at the moment.
         *
         * @since 2.175
         */
        @Restricted(NoExternalUse.class)
        @Exported
        public boolean isCompatible() {
            return isCompatible(new PluginManager.MetadataCache());
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public boolean isCompatible(PluginManager.MetadataCache cache) {
            return isCompatibleWithInstalledVersion() && !isForNewerHudson() &&
                    isNeededDependenciesCompatibleWithInstalledVersion(cache) &&
                    !isNeededDependenciesForNewerJenkins(cache);
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
            List<Plugin> deps = new ArrayList<>();

            for (Map.Entry<String, String> e : dependencies.entrySet()) {
                VersionNumber requiredVersion = e.getValue() != null ? new VersionNumber(e.getValue()) : null;
                Plugin depPlugin = Jenkins.get().getUpdateCenter().getPlugin(e.getKey(), requiredVersion);
                if (depPlugin == null) {
                    LOGGER.log(warnedMissing.add(e.getKey()) ? Level.WARNING : Level.FINE, "Could not find dependency {0} of {1}", new Object[] {e.getKey(), name});
                    continue;
                }

                // Is the plugin installed already? If not, add it.
                PluginWrapper current = depPlugin.getInstalled();

                if (current == null) {
                    deps.add(depPlugin);
                }
                // If the dependency plugin is installed, is the version we depend on newer than
                // what's installed? If so, upgrade.
                else if (current.isOlderThan(requiredVersion)) {
                    deps.add(depPlugin);
                }
                // JENKINS-34494 - or if the plugin is disabled, this will allow us to enable it
                else if (!current.isEnabled()) {
                    deps.add(depPlugin);
                }
            }

            for (Map.Entry<String, String> e : optionalDependencies.entrySet()) {
                VersionNumber requiredVersion = e.getValue() != null ? new VersionNumber(e.getValue()) : null;
                Plugin depPlugin = Jenkins.get().getUpdateCenter().getPlugin(e.getKey(), requiredVersion);
                if (depPlugin == null) {
                    continue;
                }

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
                return requiredCore != null && new VersionNumber(requiredCore).isNewerThan(
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
            for (Plugin p : getNeededDependencies()) {
                VersionNumber v = p.getNeededDependenciesRequiredCore();
                if (versionNumber == null || v.isNewerThan(versionNumber)) versionNumber = v;
            }
            return versionNumber;
        }

        public boolean isNeededDependenciesForNewerJenkins() {
            return isNeededDependenciesForNewerJenkins(new PluginManager.MetadataCache());
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public boolean isNeededDependenciesForNewerJenkins(PluginManager.MetadataCache cache) {
            return cache.of("isNeededDependenciesForNewerJenkins:" + name, Boolean.class, () -> {
                for (Plugin p : getNeededDependencies()) {
                    if (p.isForNewerHudson() || p.isNeededDependenciesForNewerJenkins()) {
                        return true;
                    }
                }
                return false;
            });
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
            return isNeededDependenciesCompatibleWithInstalledVersion(new PluginManager.MetadataCache());
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public boolean isNeededDependenciesCompatibleWithInstalledVersion(PluginManager.MetadataCache cache) {
            return getDependenciesIncompatibleWithInstalledVersion(cache).isEmpty();
        }

        /**
         * Returns true if and only if this update addressed a currently active security vulnerability.
         *
         * @return true if and only if this update addressed a currently active security vulnerability.
         */
        @Restricted(NoExternalUse.class) // Jelly
        public boolean fixesSecurityVulnerabilities() {
            final PluginWrapper installed = getInstalled();
            if (installed == null) {
                return false;
            }
            boolean allWarningsStillApply = true;
            for (Warning warning : ExtensionList.lookupSingleton(UpdateSiteWarningsMonitor.class).getActivePluginWarningsByPlugin().getOrDefault(installed, Collections.emptyList())) {
                boolean thisWarningApplies = false;
                for (WarningVersionRange range : warning.versionRanges) {
                    if (range.includes(new VersionNumber(version))) {
                        thisWarningApplies = true;
                    }
                }
                if (!thisWarningApplies) {
                    allWarningsStillApply = false;
                }
            }
            return !allWarningsStillApply;
        }

        /**
         * Get the list of incompatible dependencies (if there are any, as determined by isNeededDependenciesCompatibleWithInstalledVersion)
         *
         * @since 2.203
         */
        @Restricted(NoExternalUse.class) // table.jelly
        @SuppressWarnings("unchecked")
        public List<Plugin> getDependenciesIncompatibleWithInstalledVersion(PluginManager.MetadataCache cache) {
            return cache.of("getDependenciesIncompatibleWithInstalledVersion:" + name, List.class, () -> {
                List<Plugin> incompatiblePlugins = new ArrayList<>();
                for (Plugin p : getNeededDependencies()) {
                    if (!p.isCompatibleWithInstalledVersion() || !p.isNeededDependenciesCompatibleWithInstalledVersion()) {
                        incompatiblePlugins.add(p);
                    }
                }
                return incompatiblePlugins;
            });
        }

        public void setIncompatibleParentPlugins(Set<Plugin> incompatibleParentPlugins) {
            this.incompatibleParentPlugins = incompatibleParentPlugins;
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public Set<Plugin> getIncompatibleParentPlugins() {
            return this.incompatibleParentPlugins;
        }

        @Restricted(NoExternalUse.class) // table.jelly
        public boolean hasIncompatibleParentPlugins() {
            return this.incompatibleParentPlugins != null && !this.incompatibleParentPlugins.isEmpty();
        }

        /**
         * @since 2.40
         */
        @CheckForNull
        @Restricted(NoExternalUse.class)
        public Set<Warning> getWarnings() {
            UpdateSiteWarningsConfiguration configuration = ExtensionList.lookupSingleton(UpdateSiteWarningsConfiguration.class);
            Set<Warning> warnings = new HashSet<>();

            for (Warning warning : configuration.getAllWarnings()) {
                if (configuration.isIgnored(warning)) {
                    // warning is currently being ignored
                    continue;
                }
                if (!warning.isPluginWarning(this.name)) {
                    // warning is not about this plugin
                    continue;
                }

                if (!warning.isRelevantToVersion(new VersionNumber(this.version))) {
                    // warning is not relevant to this version
                    continue;
                }
                warnings.add(warning);
            }

            return warnings;
        }

        /**
         * Checks whether a plugin has a desired category
         * @since 2.272
         */
        public boolean hasCategory(String category) {
            if (categories == null) {
                return false;
            }
            // TODO: cache it in a hashset for performance improvements
            return Arrays.asList(categories).contains(category);
        }

        /**
         * Get categories stream for further search.
         * @since 2.272
         */
        public Stream<String> getCategoriesStream() {
            return categories != null ? Arrays.stream(categories) : Stream.empty();
        }

        /**
         * @since 2.40
         */
        @Restricted(DoNotUse.class)
        public boolean hasWarnings() {
            return !getWarnings().isEmpty();
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
            return deploy(dynamicLoad, null, null, false);
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
         * @param correlationId A correlation ID to be set on the job.
         * @param batch if defined, a list of plugins to add to, which will be started later
         * @param hasEnabledDependents
         *      If true, this plugin will be enabled if this plugin is disabled.
         *      If false, this plugin will remain the current status.
         */
        @Restricted(NoExternalUse.class)
        public Future<UpdateCenterJob> deploy(boolean dynamicLoad, @CheckForNull UUID correlationId, @CheckForNull List<PluginWrapper> batch, boolean hasEnabledDependents) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.get().getUpdateCenter();
            PluginWrapper pw = getInstalled();
            for (Plugin dep : getNeededDependencies()) {
                UpdateCenter.InstallationJob job = uc.getJob(dep);
                if (job == null || job.status instanceof UpdateCenter.DownloadJob.Failure) {
                    LOGGER.log(Level.INFO, "Adding dependent install of " + dep.name + " for plugin " + name);
                    if (pw == null) {
                        dep.deploy(dynamicLoad, /* UpdateCenterPluginInstallTest.test_installKnownPlugins specifically asks that these not be correlated */ null, batch, true);
                    } else {
                        dep.deploy(dynamicLoad, null, batch, pw.isEnabled());
                    }
                } else {
                    LOGGER.log(Level.FINE, "Dependent install of {0} for plugin {1} already added, skipping", new Object[] {dep.name, name});
                }
            }
            if (pw != null) { // JENKINS-34494 - check for this plugin being disabled
                Future<UpdateCenterJob> enableJob = null;
                if (!pw.isEnabled() && hasEnabledDependents) {
                    UpdateCenter.EnableJob job = uc.new EnableJob(UpdateSite.this, null, this, dynamicLoad);
                    job.setCorrelationId(correlationId);
                    enableJob = uc.addJob(job);
                }
                if (pw.getVersionNumber().equals(new VersionNumber(version))) {
                    return enableJob != null ? enableJob : uc.addJob(uc.new NoOpJob(UpdateSite.this, null, this));
                }
            }
            UpdateCenter.InstallationJob job = createInstallationJob(this, uc, dynamicLoad);
            job.setCorrelationId(correlationId);
            job.setBatch(batch);
            return uc.addJob(job);
        }

        /**
         * Schedules the downgrade of this plugin.
         */
        public Future<UpdateCenterJob> deployBackup() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.get().getUpdateCenter();
            return uc.addJob(uc.new PluginDowngradeJob(this, UpdateSite.this, Jenkins.getAuthentication2()));
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
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean neverUpdate = SystemProperties.getBoolean(UpdateCenter.class.getName() + ".never");

}
