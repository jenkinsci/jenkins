/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Erik Ramfelt, Tom Huybrechts
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

import hudson.model.Hudson;
import hudson.model.UpdateCenter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Represents a Hudson plug-in and associated control information
 * for Hudson to control {@link Plugin}.
 *
 * <p>
 * A plug-in is packaged into a jar file whose extension is <tt>".hpi"</tt>,
 * A plugin needs to have a special manifest entry to identify what it is.
 *
 * <p>
 * At the runtime, a plugin has two distinct state axis.
 * <ol>
 *  <li>Enabled/Disabled. If enabled, Hudson is going to use it
 *      next time Hudson runs. Otherwise the next run will ignore it.
 *  <li>Activated/Deactivated. If activated, that means Hudson is using
 *      the plugin in this session. Otherwise it's not.
 * </ol>
 * <p>
 * For example, an activated but disabled plugin is still running but the next
 * time it won't.
 *
 * @author Kohsuke Kawaguchi
 */
public final class PluginWrapper {
    /**
     * Plugin manifest.
     * Contains description of the plugin.
     */
    private final Manifest manifest;

    /**
     * Loaded plugin instance.
     * Null if disabled.
     */
    private Plugin plugin;

    /**
     * {@link ClassLoader} for loading classes from this plugin.
     * Null if disabled.
     */
    public final ClassLoader classLoader;

    /**
     * Base URL for loading static resources from this plugin.
     * Null if disabled. The static resources are mapped under
     * <tt>hudson/plugin/SHORTNAME/</tt>.
     */
    public final URL baseResourceURL;

    /**
     * Used to control enable/disable setting of the plugin.
     * If this file exists, plugin will be disabled.
     */
    private final File disableFile;

    /**
     * Short name of the plugin. The artifact Id of the plugin.
     * This is also used in the URL within Hudson, so it needs
     * to remain stable even when the *.hpi file name is changed
     * (like Maven does.)
     */
    private final String shortName;

	/**
     * True if this plugin is activated for this session.
     * The snapshot of <tt>disableFile.exists()</tt> as of the start up.
     */
    private final boolean active;

    private final List<Dependency> dependencies;
	private final List<Dependency> optionalDependencies;

    static final class Dependency {
        public final String shortName;
        public final String version;
        public final boolean optional;

        public Dependency(String s) {
            int idx = s.indexOf(':');
            if(idx==-1)
                throw new IllegalArgumentException("Illegal dependency specifier "+s);
            this.shortName = s.substring(0,idx);
            this.version = s.substring(idx+1);
            
            boolean isOptional = false;
            String[] osgiProperties = s.split(";");
            for (int i = 1; i < osgiProperties.length; i++) {
                String osgiProperty = osgiProperties[i].trim();
                if (osgiProperty.equalsIgnoreCase("resolution:=optional")) {
                    isOptional = true;
                }
            }
            this.optional = isOptional;
        }

        @Override
        public String toString() {
            return shortName + " (" + version + ")";
        }        
    }

    /**
     * @param archive
     *      A .hpi archive file jar file, or a .hpl linked plugin.
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
	public PluginWrapper(File archive, Manifest manifest, URL baseResourceURL, 
			ClassLoader classLoader, File disableFile, 
			List<Dependency> dependencies, List<Dependency> optionalDependencies) {
		this.manifest = manifest;
		this.shortName = computeShortName(manifest, archive);
		this.baseResourceURL = baseResourceURL;
		this.classLoader = classLoader;
		this.disableFile = disableFile;
		this.active = !disableFile.exists();
		this.dependencies = dependencies;
		this.optionalDependencies = optionalDependencies;
	}

    /**
     * Returns the URL of the index page jelly script.
     */
    public URL getIndexPage() {
        return classLoader.getResource("index.jelly");
    }

    private String computeShortName(Manifest manifest, File archive) {
        // use the name captured in the manifest, as often plugins
        // depend on the specific short name in its URLs.
        String n = manifest.getMainAttributes().getValue("Short-Name");
        if(n!=null)     return n;

        // maven seems to put this automatically, so good fallback to check.
        n = manifest.getMainAttributes().getValue("Extension-Name");
        if(n!=null)     return n;

        // otherwise infer from the file name, since older plugins don't have
        // this entry.
        return getBaseName(archive);
    }


    /**
     * Gets the "abc" portion from "abc.ext".
     */
    static String getBaseName(File archive) {
        String n = archive.getName();
        int idx = n.lastIndexOf('.');
        if(idx>=0)
            n = n.substring(0,idx);
        return n;
    }

    public List<Dependency> getDependencies() {
		return dependencies;
	}

	public List<Dependency> getOptionalDependencies() {
		return optionalDependencies;
	}


    /**
     * Returns the short name suitable for URL.
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * Gets the instance of {@link Plugin} contributed by this plugin.
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the URL that shows more information about this plugin.
     * @return
     *      null if this information is unavailable.
     * @since 1.283
     */
    public String getUrl() {
        // first look for the manifest entry. This is new in maven-hpi-plugin 1.30
        String url = manifest.getMainAttributes().getValue("Url");
        if(url!=null)      return url;

        // fallback to update center metadata
        UpdateCenter.Plugin ui = getInfo();
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
    public String getLongName() {
        String name = manifest.getMainAttributes().getValue("Long-Name");
        if(name!=null)      return name;
        return shortName;
    }

    /**
     * Returns the version number of this plugin
     */
    public String getVersion() {
        String v = manifest.getMainAttributes().getValue("Plugin-Version");
        if(v!=null)      return v;

        // plugins generated before maven-hpi-plugin 1.3 should still have this attribute
        v = manifest.getMainAttributes().getValue("Implementation-Version");
        if(v!=null)      return v;

        return "???";
    }

    /**
     * Terminates the plugin.
     */
    void stop() {
        LOGGER.info("Stopping "+shortName);
        try {
            plugin.stop();
        } catch(Throwable t) {
            System.err.println("Failed to shut down "+shortName);
            System.err.println(t);
        }
        // Work around a bug in commons-logging.
        // See http://www.szegedi.org/articles/memleak.html
        LogFactory.release(classLoader);
    }

    /**
     * Enables this plugin next time Hudson runs.
     */
    public void enable() throws IOException {
        if(!disableFile.delete())
            throw new IOException("Failed to delete "+disableFile);
    }

    /**
     * Disables this plugin next time Hudson runs.
     */
    public void disable() throws IOException {
        // creates an empty file
        OutputStream os = new FileOutputStream(disableFile);
        os.close();
    }

    /**
     * Returns true if this plugin is enabled for this session.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * If true, the plugin is going to be activated next time
     * Hudson runs.
     */
    public boolean isEnabled() {
        return !disableFile.exists();
    }

    public Manifest getManifest() {
		return manifest;
	}

	public void setPlugin(Plugin plugin) {
		this.plugin = plugin;
		plugin.wrapper = this;
	}
	
	public String getPluginClass() {
		return manifest.getMainAttributes().getValue("Plugin-Class");		
	}

    /**
     * If the plugin has {@link #getUpdateInfo() an update},
     * returns the {@link UpdateCenter.Plugin} object.
     *
     * @return
     *      This method may return null &mdash; for example,
     *      the user may have installed a plugin locally developed.
     */
    public UpdateCenter.Plugin getUpdateInfo() {
        UpdateCenter uc = Hudson.getInstance().getUpdateCenter();
        UpdateCenter.Plugin p = uc.getPlugin(getShortName());
        if(p!=null && p.isNewerThan(getVersion())) return p;
        return null;
    }
    
    /**
     * returns the {@link UpdateCenter.Plugin} object, or null.
     */
    public UpdateCenter.Plugin getInfo() {
        UpdateCenter uc = Hudson.getInstance().getUpdateCenter();
        return uc.getPlugin(getShortName());
    }

    /**
     * Returns true if this plugin has update in the update center.
     *
     * <p>
     * This method is conservative in the sense that if the version number is incomprehensible,
     * it always returns false.
     */
    public boolean hasUpdate() {
        return getUpdateInfo()!=null;
    }

//
//
// Action methods
//
//
    public void doMakeEnabled(StaplerRequest req, StaplerResponse rsp) throws IOException {
    Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        enable();
        rsp.setStatus(200);
    }
    public void doMakeDisabled(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        disable();
        rsp.setStatus(200);
    }


    private static final Logger LOGGER = Logger.getLogger(PluginWrapper.class.getName());

}
