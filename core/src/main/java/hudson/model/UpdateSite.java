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

import com.trilead.ssh2.crypto.Base64;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.lifecycle.Lifecycle;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.IOUtils;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.jvnet.hudson.crypto.CertificateUtil;
import org.jvnet.hudson.crypto.SignatureOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.util.TimeUnit2.*;


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
public class UpdateSite {
    /**
     * What's the time stamp of data file?
     */
    private transient long dataTimestamp = -1;

    /**
     * When was the last time we asked a browser to check the data for us?
     *
     * <p>
     * There's normally some delay between when we send HTML that includes the check code,
     * until we get the data back, so this variable is used to avoid asking too many browseres
     * all at once.
     */
    private transient volatile long lastAttempt = -1;

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
     * When read back from XML, initialize them back to -1.
     */
    private Object readResolve() {
        dataTimestamp = lastAttempt = -1;
        return this;
    }

    /**
     * Get ID string.
     */
    public String getId() {
        return id;
    }

    public long getDataTimestamp() {
        return dataTimestamp;
    }

    /**
     * This is the endpoint that receives the update center data file from the browser.
     */
    public FormValidation doPostBack(StaplerRequest req) throws IOException, GeneralSecurityException {
        dataTimestamp = System.currentTimeMillis();
        String json = IOUtils.toString(req.getInputStream(),"UTF-8");
        JSONObject o = JSONObject.fromObject(json);

        int v = o.getInt("updateCenterVersion");
        if(v !=1)
            throw new IllegalArgumentException("Unrecognized update center version: "+v);

        if (signatureCheck) {
            FormValidation e = verifySignature(o);
            if (e.kind!=Kind.OK) {
                LOGGER.severe(e.renderHtml());
                return e;
            }
        }

        LOGGER.info("Obtained the latest update center data file for UpdateSource " + id);
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
        try {
            FormValidation warning = null;

            JSONObject signature = o.getJSONObject("signature");
            if (signature.isNullObject()) {
                return FormValidation.error("No signature block found in update center '"+id+"'");
            }
            o.remove("signature");

            List<X509Certificate> certs = new ArrayList<X509Certificate>();
            {// load and verify certificates
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                for (Object cert : signature.getJSONArray("certificates")) {
                    X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Base64.decode(cert.toString().toCharArray())));
                    try {
                        c.checkValidity();
                    } catch (CertificateExpiredException e) { // even if the certificate isn't valid yet, we'll proceed it anyway
                        warning = FormValidation.warning(e,String.format("Certificate %s has expired in update center '%s'",cert.toString(),id));
                    } catch (CertificateNotYetValidException e) {
                        warning = FormValidation.warning(e,String.format("Certificate %s is not yet valid in update center '%s'",cert.toString(),id));
                    }
                    certs.add(c);
                }

                // all default root CAs in JVM are trusted, plus certs bundled in Jenkins
                Set<TrustAnchor> anchors = new HashSet<TrustAnchor>(); // CertificateUtil.getDefaultRootCAs();
                ServletContext context = Jenkins.getInstance().servletContext;
                for (String cert : (Set<String>) context.getResourcePaths("/WEB-INF/update-center-rootCAs")) {
                    if (cert.endsWith(".txt"))  continue;       // skip text files that are meant to be documentation
                    anchors.add(new TrustAnchor((X509Certificate)cf.generateCertificate(context.getResourceAsStream(cert)),null));
                }
                CertificateUtil.validatePath(certs,anchors);
            }

            // this is for computing a digest to check sanity
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            DigestOutputStream dos = new DigestOutputStream(new NullOutputStream(),sha1);

            // this is for computing a signature
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(certs.get(0));
            SignatureOutputStream sos = new SignatureOutputStream(sig);

            o.writeCanonical(new OutputStreamWriter(new TeeOutputStream(dos,sos),"UTF-8"));

            // did the digest match? this is not a part of the signature validation, but if we have a bug in the c14n
            // (which is more likely than someone tampering with update center), we can tell
            String computedDigest = new String(Base64.encode(sha1.digest()));
            String providedDigest = signature.getString("digest");
            if (!computedDigest.equalsIgnoreCase(providedDigest)) {
                return FormValidation.error("Digest mismatch: "+computedDigest+" vs "+providedDigest+" in update center '"+id+"'");
            }

            if (!sig.verify(Base64.decode(signature.getString("signature").toCharArray()))) {
                return FormValidation.error("Signature in the update center doesn't match with the certificate in update center '"+id+"'");
            }

            if (warning!=null)  return warning;
            return FormValidation.ok();
        } catch (GeneralSecurityException e) {
            return FormValidation.error(e,"Signature verification failed in the update center '"+id+"'");
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
     * Loads the update center data, if any.
     *
     * @return  null if no data is available.
     */
    public Data getData() {
        JSONObject o = getJSONObject();
        if (o!=null)    return new Data(o);
        return null;
    }

    /**
     * Gets the raw update center JSON data.
     */
    public JSONObject getJSONObject() {
        TextFile df = getDataFile();
        if(df.exists()) {
            try {
                return JSONObject.fromObject(df.read());
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

    /**
     * Returns an "always up" server for Internet connectivity testing, or null if we are going to skip the test.
     */
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
    public String getUrl() {
        return url;
    }

    /**
     * Where to actually download the update center?
     *
     * @deprecated
     *      Exposed only for UI.
     */
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
        return id.equals("default") && url.startsWith("http://hudson-ci.org/") || url.startsWith("http://updates.hudson-labs.org/");
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
            if (sourceId.equals("default")) {
                core = new Entry(sourceId, o.getJSONObject("core"));
            }
            else {
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

    public static class Entry {
        /**
         * {@link UpdateSite} ID.
         */
        public final String sourceId;

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

        public Entry(String sourceId, JSONObject o) {
            this.sourceId = sourceId;
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
        /**
         * Optional version # from which this plugin release is configuration-compatible.
         */
        public final String compatibleSinceVersion;
        /**
         * Version of Jenkins core this plugin was compiled against.
         */
        public final String requiredCore;
        /**
         * Categories for grouping plugins, taken from labels assigned to wiki page.
         * Can be null.
         */
        public final String[] categories;

        /**
         * Dependencies of this plugin.
         */
        public final Map<String,String> dependencies = new HashMap<String,String>();
        
        @DataBoundConstructor
        public Plugin(String sourceId, JSONObject o) {
            super(sourceId, o);
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
                    && !get(depObj,"name").equals("maven-plugin")
                    && get(depObj,"optional").equals("false")) {
                    dependencies.put(get(depObj,"name"), get(depObj,"version"));
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
            if(title!=null) return title;
            return name;
        }

        /**
         * If some version of this plugin is currently installed, return {@link PluginWrapper}.
         * Otherwise null.
         */
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
        public List<Plugin> getNeededDependencies() {
            List<Plugin> deps = new ArrayList<Plugin>();

            for(Map.Entry<String,String> e : dependencies.entrySet()) {
                Plugin depPlugin = Jenkins.getInstance().getUpdateCenter().getPlugin(e.getKey());
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

        /**
         * @deprecated as of 1.326
         *      Use {@link #deploy()}.
         */
        public void install() {
            deploy();
        }

        /**
         * Schedules the installation of this plugin.
         *
         * <p>
         * This is mainly intended to be called from the UI. The actual installation work happens
         * asynchronously in another thread.
         */
        public Future<UpdateCenterJob> deploy() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
            for (Plugin dep : getNeededDependencies()) {
                LOGGER.log(Level.WARNING, "Adding dependent install of " + dep.name + " for plugin " + name);
                dep.deploy();
            }
            return uc.addJob(uc.new InstallationJob(this, UpdateSite.this, Jenkins.getAuthentication()));
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
        public void doInstall(StaplerResponse rsp) throws IOException {
            deploy();
            rsp.sendRedirect2("../..");
        }

        /**
         * Performs the downgrade of the plugin.
         */
        public void doDowngrade(StaplerResponse rsp) throws IOException {
            deployBackup();
            rsp.sendRedirect2("../..");
        }
    }

    private static final long DAY = DAYS.toMillis(1);

    private static final Logger LOGGER = Logger.getLogger(UpdateSite.class.getName());

    // The name uses UpdateCenter for compatibility reason.
    public static boolean neverUpdate = Boolean.getBoolean(UpdateCenter.class.getName()+".never");

    /**
     * Off by default until we know this is reasonably working.
     */
    public static boolean signatureCheck = true; // Boolean.getBoolean(UpdateCenter.class.getName()+".signatureCheck");
}
