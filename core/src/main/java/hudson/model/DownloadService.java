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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListListener;
import hudson.ExtensionPoint;
import hudson.ProxyConfiguration;
import jenkins.util.SystemProperties;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.QuotedStringTokenizer;
import hudson.util.TextFile;
import static hudson.util.TimeUnit2.DAYS;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.DownloadSettings;
import jenkins.model.Jenkins;
import jenkins.util.JSONSignatureValidator;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
public class DownloadService extends PageDecorator {
    /**
     * Builds up an HTML fragment that starts all the download jobs.
     */
    public String generateFragment() {
        if (!DownloadSettings.usePostBack()) {
            return "";
        }
    	if (neverUpdate) return "";
        if (doesNotSupportPostMessage())  return "";

        StringBuilder buf = new StringBuilder();
        if(Jenkins.getInstance().hasPermission(Jenkins.READ)) {
            long now = System.currentTimeMillis();
            for (Downloadable d : Downloadable.all()) {
                if(d.getDue()<now && d.lastAttempt+10*1000<now) {
                    buf.append("<script>")
                       .append("Behaviour.addLoadEvent(function() {")
                       .append("  downloadService.download(")
                       .append(QuotedStringTokenizer.quote(d.getId()))
                       .append(',')
                       .append(QuotedStringTokenizer.quote(mapHttps(d.getUrl())))
                       .append(',')
                       .append("{version:"+QuotedStringTokenizer.quote(Jenkins.VERSION)+'}')
                       .append(',')
                       .append(QuotedStringTokenizer.quote(Stapler.getCurrentRequest().getContextPath()+'/'+getUrl()+"/byId/"+d.getId()+"/postBack"))
                       .append(',')
                       .append("null);")
                       .append("});")
                       .append("</script>");
                    d.lastAttempt = now;
                }
            }
        }
        return buf.toString();
    }

    private boolean doesNotSupportPostMessage() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req==null)      return false;

        String ua = req.getHeader("User-Agent");
        if (ua==null)       return false;

        // according to http://caniuse.com/#feat=x-doc-messaging, IE <=7 doesn't support pstMessage
        // see http://www.useragentstring.com/pages/Internet%20Explorer/ for user agents

        // we want to err on the cautious side here.
        // Because of JENKINS-15105, we can't serve signed metadata from JSON, which means we need to be
        // using a modern browser as a vehicle to request these data. This check is here to prevent Jenkins
        // from using older browsers that are known not to support postMessage as the vehicle.
        return ua.contains("Windows") && (ua.contains(" MSIE 5.") || ua.contains(" MSIE 6.") || ua.contains(" MSIE 7."));
    }

    private String mapHttps(String url) {
        /*
            HACKISH:

            Loading scripts in HTTP from HTTPS pages cause browsers to issue a warning dialog.
            The elegant way to solve the problem is to always load update center from HTTPS,
            but our backend mirroring scheme isn't ready for that. So this hack serves regular
            traffic in HTTP server, and only use HTTPS update center for Jenkins in HTTPS.

            We'll monitor the traffic to see if we can sustain this added traffic.
         */
        if (url.startsWith("http://updates.jenkins-ci.org/") && Jenkins.getInstance().isRootUrlSecure())
            return "https"+url.substring(4);
        return url;
    }

    /**
     * Gets {@link Downloadable} by its ID.
     * Used to bind them to URL.
     */
    public Downloadable getById(String id) {
        for (Downloadable d : Downloadable.all())
            if(d.getId().equals(id))
                return d;
        return null;
    }

    /**
     * Loads JSON from a JSONP URL.
     * Metadata for downloadables and update centers is offered in two formats, both designed for download from the browser (predating {@link DownloadSettings}):
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
        InputStream is = ProxyConfiguration.open(src).getInputStream();
        try {
            String jsonp = IOUtils.toString(is, "UTF-8");
            int start = jsonp.indexOf('{');
            int end = jsonp.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return jsonp.substring(start, end + 1);
            } else {
                throw new IOException("Could not find JSON in " + src);
            }
        } finally {
            is.close();
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
        InputStream is = ProxyConfiguration.open(src).getInputStream();
        try {
            String jsonp = IOUtils.toString(is, "UTF-8");
            String preamble = "window.parent.postMessage(JSON.stringify(";
            int start = jsonp.indexOf(preamble);
            int end = jsonp.lastIndexOf("),'*');");
            if (start >= 0 && end > start) {
                return jsonp.substring(start + preamble.length(), end).trim();
            } else {
                throw new IOException("Could not find JSON in " + src);
            }
        } finally {
            is.close();
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
        private volatile long due=0;
        private volatile long lastAttempt=Long.MIN_VALUE;

        /**
         *
         * @param url
         *      URL relative to {@link UpdateCenter#getDefaultBaseUrl()}.
         *      So if this string is "foo.json", the ultimate URL will be
         *      something like "http://updates.jenkins-ci.org/updates/foo.json"
         *
         *      For security and privacy reasons, we don't allow the retrieval
         *      from random locations.
         */
        public Downloadable(String id, String url, long interval) {
            this.id = id;
            this.url = url;
            this.interval = interval;
        }

        public Downloadable() {
            this.id = getClass().getName().replace('$','.');
            this.url = this.id+".json";
            this.interval = DEFAULT_INTERVAL;
        }

        /**
         * Uses the class name as an ID.
         */
        public Downloadable(Class id) {
            this(id.getName().replace('$','.'));
        }

        public Downloadable(String id) {
            this(id,id+".json");
        }

        public Downloadable(String id, String url) {
            this(id,url, DEFAULT_INTERVAL);
        }

        public String getId() {
            return id;
        }

        /**
         * URL to download.
         */
        public String getUrl() {
            return Jenkins.getInstance().getUpdateCenter().getDefaultBaseUrl()+"updates/"+url;
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
            return new TextFile(new File(Jenkins.getInstance().getRootDir(),"updates/"+id));
        }

        /**
         * When shall we retrieve this file next time?
         */
        public long getDue() {
            if(due==0)
                // if the file doesn't exist, this code should result
                // in a very small (but >0) due value, which should trigger
                // the retrieval immediately.
                due = getDataFile().file.lastModified()+interval;
            return due;
        }

        /**
         * Loads the current file into JSON and returns it, or null
         * if no data exists.
         */
        public JSONObject getData() throws IOException {
            TextFile df = getDataFile();
            if(df.exists())
                try {
                    return JSONObject.fromObject(df.read());
                } catch (JSONException e) {
                    df.delete(); // if we keep this file, it will cause repeated failures
                    throw new IOException("Failed to parse "+df+" into JSON",e);
                }
            return null;
        }

        /**
         * This is where the browser sends us the data. 
         */
        public void doPostBack(StaplerRequest req, StaplerResponse rsp) throws IOException {
            DownloadSettings.checkPostBackAccess();
            long dataTimestamp = System.currentTimeMillis();
            due = dataTimestamp+getInterval();  // success or fail, don't try too often

            String json = IOUtils.toString(req.getInputStream(),"UTF-8");
            FormValidation e = load(json, dataTimestamp);
            if (e.kind != Kind.OK) {
                LOGGER.severe(e.renderHtml());
                throw e;
            }
            rsp.setContentType("text/plain");  // So browser won't try to parse response
        }

        private FormValidation load(String json, long dataTimestamp) throws IOException {
            JSONObject o = JSONObject.fromObject(json);

            if (signatureCheck) {
                FormValidation e = new JSONSignatureValidator("downloadable '"+id+"'").verifySignature(o);
                if (e.kind!= Kind.OK) {
                    return e;
                }
            }

            TextFile df = getDataFile();
            df.write(json);
            df.file.setLastModified(dataTimestamp);
            LOGGER.info("Obtained the updated data file for "+id);
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation updateNow() throws IOException {
            return load(loadJSONHTML(new URL(getUrl() + ".html?id=" + URLEncoder.encode(getId(), "UTF-8") + "&version=" + URLEncoder.encode(Jenkins.VERSION, "UTF-8"))), System.currentTimeMillis());
        }

        /**
         * Returns all the registered {@link Downloadable}s.
         */
        public static ExtensionList<Downloadable> all() {
            return ExtensionList.lookup(Downloadable.class);
        }

        /**
         * Returns the {@link Downloadable} that has the given ID.
         */
        public static Downloadable get(String id) {
            for (Downloadable d : all()) {
                if(d.id.equals(id))
                    return d;
            }
            return null;
        }

        private static final Logger LOGGER = Logger.getLogger(Downloadable.class.getName());
        private static final long DEFAULT_INTERVAL =
                Long.getLong(Downloadable.class.getName()+".defaultInterval", DAYS.toMillis(1));
    }

    public static boolean neverUpdate = SystemProperties.getBoolean(DownloadService.class.getName()+".never");

    /**
     * May be used to temporarily disable signature checking on {@link DownloadService} and {@link UpdateCenter}.
     * Useful when upstream signatures are broken, such as due to expired certificates.
     * Should only be used when {@link DownloadSettings#isUseBrowser};
     * disabling signature checks for in-browser downloads is <em>very dangerous</em> as unprivileged users could submit spoofed metadata!
     */
    public static boolean signatureCheck = !SystemProperties.getBoolean(DownloadService.class.getName()+".noSignatureCheck");
}

