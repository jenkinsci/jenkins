/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Yahoo! Inc., Erik Ramfelt, Tom Huybrechts
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
package hudson;

import com.google.common.collect.ImmutableSet;
import hudson.PluginManager.PluginInstanceStore;
import hudson.model.AdministrativeMonitor;
import hudson.model.Api;
import hudson.model.ModelObject;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.util.VersionNumber;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Closeable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import static java.util.logging.Level.WARNING;
import static org.apache.commons.io.FilenameUtils.getBaseName;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.logging.Level;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Represents a Jenkins plug-in and associated control information
 * for Jenkins to control {@link Plugin}.
 *
 * <p>
 * A plug-in is packaged into a jar file whose extension is <tt>".jpi"</tt> (or <tt>".hpi"</tt> for backward compatibility),
 * A plugin needs to have a special manifest entry to identify what it is.
 *
 * <p>
 * At the runtime, a plugin has two distinct state axis.
 * <ol>
 *  <li>Enabled/Disabled. If enabled, Jenkins is going to use it
 *      next time Jenkins runs. Otherwise the next run will ignore it.
 *  <li>Activated/Deactivated. If activated, that means Jenkins is using
 *      the plugin in this session. Otherwise it's not.
 * </ol>
 * <p>
 * For example, an activated but disabled plugin is still running but the next
 * time it won't.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class PluginWrapper implements Comparable<PluginWrapper>, ModelObject {
    /**
     * A plugin won't be loaded unless his declared dependencies are present and match the required minimal version.
     * This can be set to false to disable the version check (legacy behaviour)
     */
    private static final boolean ENABLE_PLUGIN_DEPENDENCIES_VERSION_CHECK = Boolean.parseBoolean(System.getProperty(PluginWrapper.class.getName()+"." + "dependenciesVersionCheck.enabled", "true"));

    /**
     * {@link PluginManager} to which this belongs to.
     */
    public final PluginManager parent;

    /**
     * Plugin manifest.
     * Contains description of the plugin.
     */
    private final Manifest manifest;

    /**
     * {@link ClassLoader} for loading classes from this plugin.
     * Null if disabled.
     */
    public final ClassLoader classLoader;

    /**
     * Base URL for loading static resources from this plugin.
     * Null if disabled. The static resources are mapped under
     * <tt>CONTEXTPATH/plugin/SHORTNAME/</tt>.
     */
    public final URL baseResourceURL;

    /**
     * Used to control enable/disable setting of the plugin.
     * If this file exists, plugin will be disabled.
     */
    private final File disableFile;

    /**
     * A .jpi file, an exploded plugin directory, or a .jpl file.
     */
    private final File archive;

    /**
     * Short name of the plugin. The artifact Id of the plugin.
     * This is also used in the URL within Jenkins, so it needs
     * to remain stable even when the *.jpi file name is changed
     * (like Maven does.)
     */
    private final String shortName;

    /**
     * True if this plugin is activated for this session.
     * The snapshot of <tt>disableFile.exists()</tt> as of the start up.
     */
    private final boolean active;
    
    private boolean hasCycleDependency = false;

    private final List<Dependency> dependencies;
    private final List<Dependency> optionalDependencies;

    /**
     * Is this plugin bundled in jenkins.war?
     */
    /*package*/ boolean isBundled;

    /**
     * List of plugins that depend on this plugin.
     */
    private Set<String> dependants = Collections.emptySet();

    /**
     * The core can depend on a plugin if it is bundled. Sometimes it's the only thing that
     * depends on the plugin e.g. UI support library bundle plugin.
     */
    private static Set<String> CORE_ONLY_DEPENDANT = ImmutableSet.copyOf(Arrays.asList("jenkins-core"));

    /**
     * Set the list of components that depend on this plugin.
     * @param dependants The list of components that depend on this plugin.
     */
    public void setDependants(@Nonnull Set<String> dependants) {
        this.dependants = dependants;
    }

    /**
     * Get the list of components that depend on this plugin.
     * @return The list of components that depend on this plugin.
     */
    public @Nonnull Set<String> getDependants() {
        if (isBundled && dependants.isEmpty()) {
            return CORE_ONLY_DEPENDANT;
        } else {
            return dependants;
        }
    }

    /**
     * Does this plugin have anything that depends on it.
     * @return {@code true} if something (Jenkins core, or another plugin) depends on this
     * plugin, otherwise {@code false}.
     */
    public boolean hasDependants() {
        return (isBundled || !dependants.isEmpty());
    }
    
    /**
     * Does this plugin depend on any other plugins.
     * @return {@code true} if this plugin depends on other plugins, otherwise {@code false}.
     */
    public boolean hasDependencies() {
        return (dependencies != null && !dependencies.isEmpty());
    }

    @ExportedBean
    public static final class Dependency {
        @Exported
        public final String shortName;
        @Exported
        public final String version;
        @Exported
        public final boolean optional;

        public Dependency(String s) {
            int idx = s.indexOf(':');
            if(idx==-1)
                throw new IllegalArgumentException("Illegal dependency specifier "+s);
            this.shortName = s.substring(0,idx);
            String version = s.substring(idx+1);

            boolean isOptional = false;
            String[] osgiProperties = version.split("[;]");
            for (int i = 1; i < osgiProperties.length; i++) {
                String osgiProperty = osgiProperties[i].trim();
                if (osgiProperty.equalsIgnoreCase("resolution:=optional")) {
                    isOptional = true;
                }
            }
            this.optional = isOptional;
            if (isOptional) {
                this.version = osgiProperties[0];
            } else {
                this.version = version;
            }
        }

        @Override
        public String toString() {
            return shortName + " (" + version + ") " + (optional ? "optional" : "");
        }        
    }

    /**
     * @param archive
     *      A .jpi archive file jar file, or a .jpl linked plugin.
     *  @param manifest
     *  	The manifest for the plugin
     *  @param baseResourceURL
     *  	A URL pointing to the resources for this plugin
     *  @param classLoader
     *  	a classloader that loads classes from this plugin and its dependencies
     *  @param disableFile
     *  	if this file exists on startup, the plugin will not be activated
     *  @param dependencies a list of mandatory dependencies
     *  @param optionalDependencies a list of optional dependencies
     */
    public PluginWrapper(PluginManager parent, File archive, Manifest manifest, URL baseResourceURL, 
			ClassLoader classLoader, File disableFile, 
			List<Dependency> dependencies, List<Dependency> optionalDependencies) {
        this.parent = parent;
		this.manifest = manifest;
		this.shortName = computeShortName(manifest, archive.getName());
		this.baseResourceURL = baseResourceURL;
		this.classLoader = classLoader;
		this.disableFile = disableFile;
		this.active = !disableFile.exists();
		this.dependencies = dependencies;
		this.optionalDependencies = optionalDependencies;
        this.archive = archive;
    }

    public String getDisplayName() {
        return StringUtils.removeStart(getLongName(), "Jenkins ");
    }

    public Api getApi() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return new Api(this);
    }

    /**
     * Returns the URL of the index page jelly script.
     */
    public URL getIndexPage() {
        // In the current impl dependencies are checked first, so the plugin itself
        // will add the last entry in the getResources result.
        URL idx = null;
        try {
            Enumeration<URL> en = classLoader.getResources("index.jelly");
            while (en.hasMoreElements())
                idx = en.nextElement();
        } catch (IOException ignore) { }
        // In case plugin has dependencies but is missing its own index.jelly,
        // check that result has this plugin's artifactId in it:
        return idx != null && idx.toString().contains(shortName) ? idx : null;
    }

    static String computeShortName(Manifest manifest, String fileName) {
        // use the name captured in the manifest, as often plugins
        // depend on the specific short name in its URLs.
        String n = manifest.getMainAttributes().getValue("Short-Name");
        if(n!=null)     return n;

        // maven seems to put this automatically, so good fallback to check.
        n = manifest.getMainAttributes().getValue("Extension-Name");
        if(n!=null)     return n;

        // otherwise infer from the file name, since older plugins don't have
        // this entry.
        return getBaseName(fileName);
    }

    @Exported
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public List<Dependency> getOptionalDependencies() {
        return optionalDependencies;
    }


    /**
     * Returns the short name suitable for URL.
     */
    @Exported
    public String getShortName() {
        return shortName;
    }

    /**
     * Gets the instance of {@link Plugin} contributed by this plugin.
     */
    public @CheckForNull Plugin getPlugin() {
        PluginInstanceStore pis = Jenkins.lookup(PluginInstanceStore.class);
        return pis != null ? pis.store.get(this) : null;
    }

    /**
     * Gets the URL that shows more information about this plugin.
     * @return
     *      null if this information is unavailable.
     * @since 1.283
     */
    @Exported
    public String getUrl() {
        // first look for the manifest entry. This is new in maven-hpi-plugin 1.30
        String url = manifest.getMainAttributes().getValue("Url");
        if(url!=null)      return url;

        // fallback to update center metadata
        UpdateSite.Plugin ui = getInfo();
        if(ui!=null)    return ui.wiki;

        return null;
    }
    
    

    @Override
    public String toString() {
        return "Plugin:" + getShortName();
    }

    /**
     * Returns a one-line descriptive name of this plugin.
     */
    @Exported
    public String getLongName() {
        String name = manifest.getMainAttributes().getValue("Long-Name");
        if(name!=null)      return name;
        return shortName;
    }

    /**
     * Does this plugin supports dynamic loading?
     */
    @Exported
    public YesNoMaybe supportsDynamicLoad() {
        String v = manifest.getMainAttributes().getValue("Support-Dynamic-Loading");
        if (v==null) return YesNoMaybe.MAYBE;
        return Boolean.parseBoolean(v) ? YesNoMaybe.YES : YesNoMaybe.NO;
    }

    /**
     * Returns the version number of this plugin
     */
    @Exported
    public String getVersion() {
        return getVersionOf(manifest);
    }

    private String getVersionOf(Manifest manifest) {
        String v = manifest.getMainAttributes().getValue("Plugin-Version");
        if(v!=null)      return v;

        // plugins generated before maven-hpi-plugin 1.3 should still have this attribute
        v = manifest.getMainAttributes().getValue("Implementation-Version");
        if(v!=null)      return v;

        return "???";
    }

    /**
     * Returns the required Jenkins core version of this plugin.
     * @return the required Jenkins core version of this plugin.
     * @since XXX
     */
    @Exported
    public @CheckForNull String getRequiredCoreVersion() {
        String v = manifest.getMainAttributes().getValue("Jenkins-Version");
        if (v!= null) return v;

        v = manifest.getMainAttributes().getValue("Hudson-Version");
        if (v!= null) return v;
        return null;
    }

    /**
     * Returns the version number of this plugin
     */
    public VersionNumber getVersionNumber() {
        return new VersionNumber(getVersion());
    }

    /**
     * Returns true if the version of this plugin is older than the given version.
     */
    public boolean isOlderThan(VersionNumber v) {
        try {
            return getVersionNumber().compareTo(v) < 0;
        } catch (IllegalArgumentException e) {
            // if we can't figure out our current version, it probably means it's very old,
            // since the version information is missing only from the very old plugins 
            return true;
        }
    }

    /**
     * Terminates the plugin.
     */
    public void stop() {
        Plugin plugin = getPlugin();
        if (plugin != null) {
            try {
                LOGGER.log(Level.FINE, "Stopping {0}", shortName);
                plugin.stop();
            } catch (Throwable t) {
                LOGGER.log(WARNING, "Failed to shut down " + shortName, t);
            }
        } else {
            LOGGER.log(Level.FINE, "Could not find Plugin instance to stop for {0}", shortName);
        }
        // Work around a bug in commons-logging.
        // See http://www.szegedi.org/articles/memleak.html
        LogFactory.release(classLoader);
    }

    public void releaseClassLoader() {
        if (classLoader instanceof Closeable)
            try {
                ((Closeable) classLoader).close();
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to shut down classloader",e);
            }
    }

    /**
     * Enables this plugin next time Jenkins runs.
     */
    public void enable() throws IOException {
        if (!disableFile.exists()) {
            LOGGER.log(Level.FINEST, "Plugin {0} has been already enabled. Skipping the enable() operation", getShortName());
            return;
        }
        if(!disableFile.delete())
            throw new IOException("Failed to delete "+disableFile);
    }

    /**
     * Disables this plugin next time Jenkins runs.
     */
    public void disable() throws IOException {
        // creates an empty file
        OutputStream os = new FileOutputStream(disableFile);
        os.close();
    }

    /**
     * Returns true if this plugin is enabled for this session.
     */
    @Exported
    public boolean isActive() {
        return active && !hasCycleDependency();
    }
    
    public boolean hasCycleDependency(){
        return hasCycleDependency;
    }

    public void setHasCycleDependency(boolean hasCycle){
        hasCycleDependency = hasCycle;
    }
    
    @Exported
    public boolean isBundled() {
        return isBundled;
    }

    /**
     * If true, the plugin is going to be activated next time
     * Jenkins runs.
     */
    @Exported
    public boolean isEnabled() {
        return !disableFile.exists();
    }

    public Manifest getManifest() {
        return manifest;
    }

    public void setPlugin(Plugin plugin) {
        Jenkins.lookup(PluginInstanceStore.class).store.put(this,plugin);
        plugin.wrapper = this;
    }

    public String getPluginClass() {
        return manifest.getMainAttributes().getValue("Plugin-Class");
    }

    public boolean hasLicensesXml() {
        try {
            new URL(baseResourceURL,"WEB-INF/licenses.xml").openStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Makes sure that all the dependencies exist, and then accept optional dependencies
     * as real dependencies.
     *
     * @throws IOException
     *             thrown if one or several mandatory dependencies doesn't exists.
     */
    /*package*/ void resolvePluginDependencies() throws IOException {
        if (ENABLE_PLUGIN_DEPENDENCIES_VERSION_CHECK) {
            String requiredCoreVersion = getRequiredCoreVersion();
            if (requiredCoreVersion == null) {
                LOGGER.warning(shortName + " doesn't declare required core version.");
            } else {
                checkRequiredCoreVersion(requiredCoreVersion);
            }
        }
        List<Dependency> missingDependencies = new ArrayList<>();
        List<Dependency> obsoleteDependencies = new ArrayList<>();
        List<Dependency> disabledDependencies = new ArrayList<>();
        // make sure dependencies exist
        for (Dependency d : dependencies) {
            PluginWrapper dependency = parent.getPlugin(d.shortName);
            if (dependency == null) {
                missingDependencies.add(d);
                NOTICE.addErrorMessage(Messages.PluginWrapper_admonitor_MissingDependency(getLongName(), d.shortName));
            } else {
                if (dependency.isActive()) {
                    if (isDependencyObsolete(d, dependency)) {
                        obsoleteDependencies.add(d);
                        NOTICE.addErrorMessage(Messages.PluginWrapper_admonitor_ObsoleteDependency(getLongName(), dependency.getLongName(), d.version));
                    }
                } else {
                    disabledDependencies.add(d);
                    NOTICE.addErrorMessage(Messages.PluginWrapper_admonitor_DisabledDependency(getLongName(), dependency.getLongName()));
                }

            }
        }
        // add the optional dependencies that exists
        for (Dependency d : optionalDependencies) {
            PluginWrapper dependency = parent.getPlugin(d.shortName);
            if (dependency != null && dependency.isActive()) {
                if (isDependencyObsolete(d, dependency)) {
                    obsoleteDependencies.add(d);
                    NOTICE.addErrorMessage(Messages.PluginWrapper_admonitor_ObsoleteDependency(getLongName(), dependency.getLongName(), d.version));
                } else {
                    dependencies.add(d);
                }
            }
        }
        StringBuilder messageBuilder = new StringBuilder();
        if (!missingDependencies.isEmpty()) {
            boolean plural = missingDependencies.size() > 1;
            messageBuilder.append(plural ? "Dependencies " : "Dependency ")
                    .append(Util.join(missingDependencies, ", "))
                    .append(" ").append(plural ? "don't" : "doesn't")
                    .append(" exist. ");
        }
        if (!disabledDependencies.isEmpty()) {
            boolean plural = disabledDependencies.size() > 1;
            messageBuilder.append(plural ? "Dependencies " : "Dependency ")
                    .append(Util.join(missingDependencies, ", "))
                    .append(" ").append(plural ? "are" : "is")
                    .append(" disabled. ");
        }
        if (!obsoleteDependencies.isEmpty()) {
            boolean plural = obsoleteDependencies.size() > 1;
            messageBuilder.append(plural ? "Dependencies " : "Dependency ")
                    .append(Util.join(obsoleteDependencies, ", "))
                    .append(" ").append(plural ? "are" : "is")
                    .append(" older than required.");
        }
        String message = messageBuilder.toString();
        if (!message.isEmpty()) {
            throw new IOException(message);
        }
    }

    private void checkRequiredCoreVersion(String requiredCoreVersion) throws IOException {
        if (Jenkins.getVersion().isOlderThan(new VersionNumber(requiredCoreVersion))) {
            NOTICE.addErrorMessage(Messages.PluginWrapper_admonitor_OutdatedCoreVersion(getLongName(), requiredCoreVersion));
            throw new IOException(shortName + " requires a more recent core version (" + requiredCoreVersion + ") than the current (" + Jenkins.getVersion() + ").");
        }
    }

    private boolean isDependencyObsolete(Dependency d, PluginWrapper dependency) {
        return ENABLE_PLUGIN_DEPENDENCIES_VERSION_CHECK && dependency.getVersionNumber().isOlderThan(new VersionNumber(d.version));
    }

    /**
     * If the plugin has {@link #getUpdateInfo() an update},
     * returns the {@link hudson.model.UpdateSite.Plugin} object.
     *
     * @return
     *      This method may return null &mdash; for example,
     *      the user may have installed a plugin locally developed.
     */
    public UpdateSite.Plugin getUpdateInfo() {
        UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
        UpdateSite.Plugin p = uc.getPlugin(getShortName());
        if(p!=null && p.isNewerThan(getVersion())) return p;
        return null;
    }
    
    /**
     * returns the {@link hudson.model.UpdateSite.Plugin} object, or null.
     */
    public UpdateSite.Plugin getInfo() {
        UpdateCenter uc = Jenkins.getInstance().getUpdateCenter();
        return uc.getPlugin(getShortName());
    }

    /**
     * Returns true if this plugin has update in the update center.
     *
     * <p>
     * This method is conservative in the sense that if the version number is incomprehensible,
     * it always returns false.
     */
    @Exported
    public boolean hasUpdate() {
        return getUpdateInfo()!=null;
    }
    
    @Exported
    @Deprecated // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
    public boolean isPinned() {
        return false;
    }

    /**
     * Returns true if this plugin is deleted.
     *
     * The plugin continues to function in this session, but in the next session it'll disappear.
     */
    @Exported
    public boolean isDeleted() {
        return !archive.exists();
    }

    /**
     * Sort by short name.
     */
    public int compareTo(PluginWrapper pw) {
        return shortName.compareToIgnoreCase(pw.shortName);
    }

    /**
     * returns true if backup of previous version of plugin exists
     */
    @Exported
    public boolean isDowngradable() {
        return getBackupFile().exists();
    }

    /**
     * Where is the backup file?
     */
    public File getBackupFile() {
        return new File(Jenkins.getInstance().getRootDir(),"plugins/"+getShortName() + ".bak");
    }

    /**
     * returns the version of the backed up plugin,
     * or null if there's no back up.
     */
    @Exported
    public String getBackupVersion() {
        File backup = getBackupFile();
        if (backup.exists()) {
            try {
                JarFile backupPlugin = new JarFile(backup);
                try {
                    return backupPlugin.getManifest().getMainAttributes().getValue("Plugin-Version");
                } finally {
                    backupPlugin.close();
                }
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to get backup version from " + backup, e);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Checks if this plugin is pinned and that's forcing us to use an older version than the bundled one.
     */
    @Deprecated // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
    public boolean isPinningForcingOldVersion() {
        return false;
    }

    @Extension
    public final static PluginWrapperAdministrativeMonitor NOTICE = new PluginWrapperAdministrativeMonitor();

    /**
     * Administrative Monitor for failed plugins
     */
    public static final class PluginWrapperAdministrativeMonitor extends AdministrativeMonitor {
        public final List<String> pluginError = new ArrayList<>();

        void addErrorMessage(String error) {
            pluginError.add(error);
        }

        public boolean isActivated() {
            return !pluginError.isEmpty();
        }

        /**
         * Depending on whether the user said "dismiss" or "correct", send him to the right place.
         */
        public void doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
            if(req.hasParameter("correct")) {
                rsp.sendRedirect(req.getContextPath()+"/pluginManager");

            }
        }

        public static PluginWrapperAdministrativeMonitor get() {
            return AdministrativeMonitor.all().get(PluginWrapperAdministrativeMonitor.class);
        }
    }

//
//
// Action methods
//
//
    @RequirePOST
    public HttpResponse doMakeEnabled() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        enable();
        return HttpResponses.ok();
    }

    @RequirePOST
    public HttpResponse doMakeDisabled() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        disable();
        return HttpResponses.ok();
    }

    @RequirePOST
    @Deprecated
    public HttpResponse doPin() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
        LOGGER.log(WARNING, "Call to pin plugin has been ignored. Plugin name: " + shortName);
        return HttpResponses.ok();
    }

    @RequirePOST
    @Deprecated
    public HttpResponse doUnpin() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        // See https://groups.google.com/d/msg/jenkinsci-dev/kRobm-cxFw8/6V66uhibAwAJ
        LOGGER.log(WARNING, "Call to unpin plugin has been ignored. Plugin name: " + shortName);
        return HttpResponses.ok();
    }

    @RequirePOST
    public HttpResponse doDoUninstall() throws IOException {
        Jenkins jenkins = Jenkins.getActiveInstance();
        
        jenkins.checkPermission(Jenkins.ADMINISTER);
        archive.delete();

        // Redo who depends on who.
        jenkins.getPluginManager().resolveDependantPlugins();

        return HttpResponses.redirectViaContextPath("/pluginManager/installed");   // send back to plugin manager
    }


    private static final Logger LOGGER = Logger.getLogger(PluginWrapper.class.getName());

    /**
     * Name of the plugin manifest file (to help find where we parse them.)
     */
    public static final String MANIFEST_FILENAME = "META-INF/MANIFEST.MF";
}
