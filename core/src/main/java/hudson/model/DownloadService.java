/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListListener;
import hudson.ExtensionPoint;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.FormValidation;
import hudson.util.TextFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Service for plugins to periodically retrieve update data files
 * (like the one in the update center) through browsers.
 *
 * <p>
 * Because the retrieval of the file goes through XmlHttpRequest,
 * we cannot reliably pass around binary.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DownloadService {

    /**
     * the prefix for the signature validator name
     */
    private static final String signatureValidatorPrefix = "downloadable";
    /**
     * Builds up an HTML fragment that starts all the download jobs.
     *
     * @deprecated browser-based download has been disabled
     */

    @Deprecated
    public String generateFragment() {
        return "";
    }

    /**
     * Gets {@link Downloadable} by its ID.
     * Used to bind them to URL.
     */
    public Downloadable getById(String id) {
        for (Downloadable d : Downloadable.all())
            if (d.getId().equals(id))
                return d;
        return null;
    }

    /**
     * Loads JSON from a JSONP URL.
     * Metadata for downloadables and update centers is offered in two formats, both designed for download from the browser (a feature since removed):
     * HTML using {@code postMessage} for newer browsers, and JSONP as a fallback.
     * Confusingly, the JSONP files are given the {@code *.json} file extension, when they are really JavaScript and should be {@code *.js}.
     * This method extracts the JSON from a JSONP URL, since that is what we actually want when we download from the server.
     * (Currently the true JSON is not published separately, and extracting from the {@code *.json.html} is more work.)
     * @param src a URL to a JSONP file (typically including {@code id} and {@code version} query parameters)
     * @return the embedded JSON text
     * @throws IOException if either downloading or processing failed
     */
    @Restricted(NoExternalUse.class)
    public static String loadJSON(URL src) throws IOException {
        URLConnection con = ProxyConfiguration.open(src);
        if (con instanceof HttpURLConnection) {
            // prevent problems from misbehaving plugins disabling redirects by default
            ((HttpURLConnection) con).setInstanceFollowRedirects(true);
        }
        try (InputStream is = con.getInputStream()) {
            String jsonp = IOUtils.toString(is, StandardCharsets.UTF_8);
            int start = jsonp.indexOf('{');
            int end = jsonp.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return jsonp.substring(start, end + 1);
            } else {
                throw new IOException("Could not find JSON in " + src);
            }
        }
    }

    /**
     * Loads JSON from a JSON-with-{@code postMessage} URL.
     * @param src a URL to a JSON HTML file (typically including {@code id} and {@code version} query parameters)
     * @return the embedded JSON text
     * @throws IOException if either downloading or processing failed
     */
    @Restricted(NoExternalUse.class)
    public static String loadJSONHTML(URL src) throws IOException {
        URLConnection con = ProxyConfiguration.open(src);
        if (con instanceof HttpURLConnection) {
            // prevent problems from misbehaving plugins disabling redirects by default
            ((HttpURLConnection) con).setInstanceFollowRedirects(true);
        }
        try (InputStream is = con.getInputStream()) {
            String jsonp = IOUtils.toString(is, StandardCharsets.UTF_8);
            String preamble = "window.parent.postMessage(JSON.stringify(";
            int start = jsonp.indexOf(preamble);
            int end = jsonp.lastIndexOf("),'*');");
            if (start >= 0 && end > start) {
                return jsonp.substring(start + preamble.length(), end).trim();
            } else {
                throw new IOException("Could not find JSON in " + src);
            }
        }
    }

    /**
     * This installs itself as a listener to changes to the Downloadable extension list and will download the metadata
     * for any newly added Downloadables.
     */
    @Restricted(NoExternalUse.class)
    public static class DownloadableListener extends ExtensionListListener {

        /**
         * Install this listener to the Downloadable extension list after all extensions have been loaded; we only
         * care about those that are added after initialization
         */
        @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
        public static void installListener() {
            ExtensionList.lookup(Downloadable.class).addListener(new DownloadableListener());
        }

        /**
         * Look for Downloadables that have no data, and update them.
         */
        @Override
        public void onChange() {
            for (Downloadable d : Downloadable.all()) {
                TextFile f = d.getDataFile();
                if (f == null || !f.exists()) {
                    LOGGER.log(Level.FINE, "Updating metadata for " + d.getId());
                    try {
                        d.updateNow();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to update metadata for " + d.getId(), e);
                    }
                } else {
                    LOGGER.log(Level.FINER, "Skipping update of metadata for " + d.getId());
                }
            }
        }

        private static final Logger LOGGER = Logger.getLogger(DownloadableListener.class.getName());
    }

    /**
     * Represents a periodically updated JSON data file obtained from a remote URL.
     *
     * <p>
     * This mechanism is one of the basis of the update center, which involves fetching
     * up-to-date data file.
     *
     * @since 1.305
     */
    public static class Downloadable implements ExtensionPoint {
        private final String id;
        private final String url;
        private final long interval;
        private volatile long due = 0;

        /**
         * Creates a new downloadable.
         *
         * @param id The ID to use.
         * @param url
         *      URL relative to {@link UpdateCenter#getDefaultBaseUrl()}.
         *      So if this string is "foo.json", the ultimate URL will be
         *      something like "http://updates.jenkins-ci.org/updates/foo.json"
         *
         *      For security and privacy reasons, we don't allow the retrieval
         *      from random locations.
         * @param interval The interval, in milliseconds, between attempts to update this downloadable's data.
         */
        public Downloadable(@NonNull String id, @NonNull String url, long interval) {
            this.id = id;
            this.url = url;
            this.interval = interval;
        }

        /**
         * Creates a new downloadable.
         * This will generate an ID based on this downloadable's class (using {@link #idFor(Class)}. The URL will be set
         * to that ID, with an added {@code .json} extension, and the default interval will be used.
         */
        public Downloadable() {
            this.id = Downloadable.idFor(this.getClass());
            this.url = this.id + ".json";
            this.interval = DEFAULT_INTERVAL;
        }

        /**
         * Creates a new downloadable.
         * This will generate an ID based on the specified class (using {@link #idFor(Class)}. The URL will be set to
         * that ID, with an added {@code .json} extension, and the default interval will be used.
         *
         * @param clazz The class to use to generate the ID.
         */
        public Downloadable(@NonNull Class<?> clazz) {
            this(Downloadable.idFor(clazz));
        }

        /**
         * Creates a new downloadable with a specific ID. The URL will be set to that ID, with an added {@code .json}
         * extension, and the default interval will be used.
         *
         * @param id The ID to use.
         */
        public Downloadable(@NonNull String id) {
            this(id, id + ".json");
        }

        /**
         * Creates a new downloadable with a specific ID and URL. The default interval will be used.
         *
         * @param id  The ID to use.
         * @param url URL relative to {@link UpdateCenter#getDefaultBaseUrl()}. So if this string is "foo.json", the
         *            ultimate URL will be something like "http://updates.jenkins-ci.org/updates/foo.json".
         *            <p>
         *            For security and privacy reasons, we don't allow the retrieval from random locations.
         */
        public Downloadable(@NonNull String id, @NonNull String url) {
            this(id, url, DEFAULT_INTERVAL);
        }

        @NonNull
        public String getId() {
            return id;
        }

        /**
         * Generates an ID based on a class.
         *
         * @param clazz The class to use to generate an ID.
         * @return The ID generated based on the specified class.
         *
         * @since 2.244
         */
        @NonNull
        public static String idFor(@NonNull Class<?> clazz) {
            return clazz.getName().replace('$', '.');
        }

        /**
         * URL to download.
         */
        public String getUrl() {
            return Jenkins.get().getUpdateCenter().getDefaultBaseUrl() + "updates/" + url;
        }

        /**
         * URLs to download from.
         */
        public List<String> getUrls() {
            List<String> updateSites = new ArrayList<>();
            for (UpdateSite site : Jenkins.get().getUpdateCenter().getSiteList()) {
                String siteUrl = site.getUrl();
                int baseUrlEnd = siteUrl.indexOf("update-center.json");
                if (baseUrlEnd != -1) {
                    String siteBaseUrl = siteUrl.substring(0, baseUrlEnd);
                    updateSites.add(siteBaseUrl + "updates/" + url);
                } else {
                    LOGGER.log(Level.WARNING, "Url {0} does not look like an update center:", siteUrl);
                }
            }
            return updateSites;
        }

        /**
         * How often do we retrieve the new image?
         *
         * @return
         *      number of milliseconds between retrieval.
         */
        public long getInterval() {
            return interval;
        }

        /**
         * This is where the retrieved file will be stored.
         */
        public TextFile getDataFile() {
            return new TextFile(new File(Jenkins.get().getRootDir(), "updates/" + id));
        }

        /**
         * When shall we retrieve this file next time?
         */
        public long getDue() {
            if (due == 0)
                // if the file doesn't exist, this code should result
                // in a very small (but >0) due value, which should trigger
                // the retrieval immediately.
                due = getDataFile().file.lastModified() + interval;
            return due;
        }

        /**
         * Loads the current file into JSON and returns it, or null
         * if no data exists.
         */
        public JSONObject getData() throws IOException {
            TextFile df = getDataFile();
            if (df.exists())
                try {
                    return JSONObject.fromObject(df.read());
                } catch (JSONException e) {
                    IOException ioe = new IOException("Failed to parse " + df + " into JSON", e);
                    try {
                        df.delete(); // if we keep this file, it will cause repeated failures
                    } catch (IOException e2) {
                        ioe.addSuppressed(e2);
                    }
                    throw ioe;
                }
            return null;
        }

        private FormValidation load(String json, long dataTimestamp) throws IOException {
            TextFile df = getDataFile();
            df.write(json);
            Files.setLastModifiedTime(Util.fileToPath(df.file), FileTime.fromMillis(dataTimestamp));
            LOGGER.info("Obtained the updated data file for " + id);
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation updateNow() throws IOException {
            List<JSONObject> jsonList = new ArrayList<>();
            boolean toolInstallerMetadataExists = false;
            for (UpdateSite updatesite : Jenkins.get().getUpdateCenter().getSiteList()) {
                String site = updatesite.getMetadataUrlForDownloadable(url);
                if (site == null) {
                    return FormValidation.warning("The update site " + updatesite.getId() + " does not look like an update center");
                }
                String jsonString;
                try {
                    jsonString = loadJSONHTML(new URL(site + ".html?id=" + URLEncoder.encode(getId(), StandardCharsets.UTF_8) + "&version=" + URLEncoder.encode(Jenkins.VERSION, StandardCharsets.UTF_8)));
                    toolInstallerMetadataExists = true;
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Could not load json from " + site, e);
                    continue;
                }
                JSONObject o = JSONObject.fromObject(jsonString);
                if (signatureCheck) {
                    FormValidation e = updatesite.getJsonSignatureValidator(signatureValidatorPrefix + " '" + id + "'").verifySignature(o);
                    if (e.kind != FormValidation.Kind.OK) {
                        LOGGER.log(Level.WARNING, "signature check failed for " + site, e);
                        continue;
                    }
                }
                jsonList.add(o);
            }
            if (jsonList.isEmpty() && toolInstallerMetadataExists) {
                return FormValidation.warning("None of the tool installer metadata passed the signature check");
            } else if (!toolInstallerMetadataExists) {
                LOGGER.log(Level.WARNING, "No tool installer metadata found for " + id);
                return FormValidation.ok();
            }
            JSONObject reducedJson = reduce(jsonList);
            return load(reducedJson.toString(), System.currentTimeMillis());
        }

        /**
         * Function that takes multiple JSONObjects and returns a single one.
         * @param jsonList to be processed
         * @return a single JSONObject
         */
        public JSONObject reduce(List<JSONObject> jsonList) {
            return jsonList.get(0);
        }

        /**
         * check if the list of update center entries has duplicates
         * @param genericList list of entries coming from multiple update centers
         * @param comparator the unique ID of an entry
         * @param <T> the generic class
         * @return true if the list has duplicates, false otherwise
         */
        public static <T> boolean hasDuplicates(List<T> genericList, String comparator) {
            if (genericList.isEmpty()) {
                return false;
            }
            Field field;
            try {
                field = genericList.get(0).getClass().getDeclaredField(comparator);
            } catch (NoSuchFieldException e) {
                LOGGER.warning("comparator: " + comparator + "does not exist for " + genericList.get(0).getClass() + ", " + e);
                return false;
            }
            for (int i = 0; i < genericList.size(); i++) {
                T data1 = genericList.get(i);
                for (int j = i + 1; j < genericList.size(); j++) {
                    T data2 = genericList.get(j);
                    try {
                        if (field.get(data1).equals(field.get(data2))) {
                            return true;
                        }
                    } catch (IllegalAccessException e) {
                        LOGGER.warning("could not access field: " + comparator + ", " + e);
                    }
                }
            }
            return false;
        }

        /**
         * Returns all the registered {@link Downloadable}s.
         */
        @NonNull
        public static ExtensionList<Downloadable> all() {
            return ExtensionList.lookup(Downloadable.class);
        }

        /**
         * Returns the {@link Downloadable} that has an ID associated with the specified class (as computed via
         * {@link #idFor(Class)}).
         *
         * @param clazz The class to use to determine the downloadable's ID.
         *
         * @since 2.244
         */
        @CheckForNull
        public static Downloadable get(@NonNull Class<?> clazz) {
            return Downloadable.get(Downloadable.idFor(clazz));
        }

        /**
         * Returns the {@link Downloadable} that has the given ID.
         *
         * @param id The ID to look for.
         */
        @CheckForNull
        public static Downloadable get(String id) {
            for (Downloadable d : all()) {
                if (d.id.equals(id))
                    return d;
            }
            return null;
        }

        private static final Logger LOGGER = Logger.getLogger(Downloadable.class.getName());
        private static final long DEFAULT_INTERVAL =
                SystemProperties.getLong(Downloadable.class.getName() + ".defaultInterval", DAYS.toMillis(1));
    }

    // TODO this was previously referenced in the browser-based download, but should probably be checked for the server-based download
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean neverUpdate = SystemProperties.getBoolean(DownloadService.class.getName() + ".never");

    /**
     * May be used to temporarily disable signature checking on {@link DownloadService} and {@link UpdateCenter}.
     * Useful when upstream signatures are broken, such as due to expired certificates.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean signatureCheck = !SystemProperties.getBoolean(DownloadService.class.getName() + ".noSignatureCheck");
}
