package hudson;

import hudson.util.IOException2;
import hudson.util.VersionNumber;
import hudson.model.Hudson;
import hudson.model.UpdateCenter;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.types.FileSet;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.logging.Logger;

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

    private final File archive;

    private final List<Dependency> dependencies = new ArrayList<Dependency>();
    private final List<Dependency> optionalDependencies = new ArrayList<Dependency>();

    private static final class Dependency {
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
     *
     * @throws IOException
     *      if an installation of this plugin failed. The caller should
     *      proceed to work with other plugins.
     */
    public PluginWrapper(PluginManager owner, File archive) throws IOException {
        LOGGER.info("Loading plugin: "+archive);
        this.archive = archive;

        boolean isLinked = archive.getName().endsWith(".hpl");

        File expandDir = null;  // if .hpi, this is the directory where war is expanded

        if(isLinked) {
            // resolve the .hpl file to the location of the manifest file
            BufferedReader archiveReader = new BufferedReader(new FileReader(archive));
            try {
                String firstLine = archiveReader.readLine();
                if(firstLine.startsWith("Manifest-Version:")) {
                    // this is the manifest already
                } else {
                    // indirection
                    archive = resolve(archive, firstLine);
                }
            } finally {
                archiveReader.close();
            }
            // then parse manifest
            FileInputStream in = new FileInputStream(archive);
            try {
                manifest = new Manifest(in);
            } catch(IOException e) {
                throw new IOException2("Failed to load "+archive,e);
            } finally {
                in.close();
            }
        } else {
            expandDir = new File(archive.getParentFile(), getBaseName(archive));
            explode(archive,expandDir);

            File manifestFile = new File(expandDir,"META-INF/MANIFEST.MF");
            if(!manifestFile.exists()) {
                throw new IOException("Plugin installation failed. No manifest at "+manifestFile);
            }
            FileInputStream fin = new FileInputStream(manifestFile);
            try {
                manifest = new Manifest(fin);
            } finally {
                fin.close();
            }
        }

        this.shortName = computeShortName(manifest,archive);

        // TODO: define a mechanism to hide classes
        // String export = manifest.getMainAttributes().getValue("Export");

        List<URL> paths = new ArrayList<URL>();
        if(isLinked) {
            parseClassPath(archive, paths, "Libraries", ",");
            parseClassPath(archive, paths, "Class-Path", " +"); // backward compatibility

            this.baseResourceURL = resolve(archive,
                manifest.getMainAttributes().getValue("Resource-Path")).toURL();
        } else {
            File classes = new File(expandDir,"WEB-INF/classes");
            if(classes.exists())
                paths.add(classes.toURL());
            File lib = new File(expandDir,"WEB-INF/lib");
            File[] libs = lib.listFiles(JAR_FILTER);
            if(libs!=null) {
                for (File jar : libs)
                    paths.add(jar.toURL());
            }

            this.baseResourceURL = expandDir.toURL();
        }
        ClassLoader dependencyLoader = new DependencyClassLoader(getClass().getClassLoader(),owner);
        this.classLoader = new URLClassLoader(paths.toArray(new URL[0]), dependencyLoader);

        disableFile = new File(archive.getPath()+".disabled");
        if(disableFile.exists()) {
            LOGGER.info("Plugin is disabled");
            this.active = false;
        } else {
            this.active = true;
        }

        // compute dependencies
        String v = manifest.getMainAttributes().getValue("Plugin-Dependencies");
        if(v!=null) {
            for(String s : v.split(",")) {
                Dependency d = new Dependency(s);
                if (d.optional) {
                    optionalDependencies.add(d);
                } else {
                    dependencies.add(d);
                }
            }
        }
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
     * Loads the plugin and starts it.
     *
     * <p>
     * This should be done after all the classloaders are constructed for
     * all the plugins, so that dependencies can be properly loaded by plugins.
     */
    /*package*/ void load(PluginManager owner) throws IOException {
        String className = manifest.getMainAttributes().getValue("Plugin-Class");
        if(className ==null) {
            throw new IOException("Plugin installation failed. No 'Plugin-Class' entry in the manifest of "+archive);
        }

        loadPluginDependencies(owner);

        if(!active)
            return;

        // override the context classloader so that XStream activity in plugin.start()
        // will be able to resolve classes in this plugin
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            try {
                Class clazz = classLoader.loadClass(className);
                Object plugin = clazz.newInstance();
                if(!(plugin instanceof Plugin)) {
                    throw new IOException(className+" doesn't extend from hudson.Plugin");
                }
                this.plugin = (Plugin)plugin;
                this.plugin.wrapper = this;
            } catch (ClassNotFoundException e) {
                throw new IOException2("Unable to load " + className + " from " + archive,e);
            } catch (IllegalAccessException e) {
                throw new IOException2("Unable to create instance of " + className + " from " + archive,e);
            } catch (InstantiationException e) {
                throw new IOException2("Unable to create instance of " + className + " from " + archive,e);
            }

            // initialize plugin
            try {
                plugin.setServletContext(owner.context);
                plugin.start();
            } catch(Throwable t) {
                // gracefully handle any error in plugin.
                throw new IOException2("Failed to initialize",t);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    /**
     * Loads the dependencies to other plugins.
     * @param owner plugin manager to determine if the dependency is installed or not.
     * @throws IOException thrown if one or several mandatory dependencies doesnt exists.
     */
    private void loadPluginDependencies(PluginManager owner) throws IOException {
        List<String> missingDependencies = new ArrayList<String>();
        // make sure dependencies exist
        for (Dependency d : dependencies) {
            if(owner.getPlugin(d.shortName)==null)
                missingDependencies.add(d.toString());
        }
        if (! missingDependencies.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Dependency ");
            builder.append(Util.join(missingDependencies, ", "));
            builder.append(" doesn't exist");
            throw new IOException(builder.toString());
        }
        
        // add the optional dependencies that exists
        for (Dependency d : optionalDependencies) {
            if(owner.getPlugin(d.shortName)!=null)
                dependencies.add(d);
        }
    }

    private void parseClassPath(File archive, List<URL> paths, String attributeName, String separator) throws IOException {
        String classPath = manifest.getMainAttributes().getValue(attributeName);
        if(classPath==null) return; // attribute not found
        for (String s : classPath.split(separator)) {
            File file = resolve(archive, s);
            if(file.getName().contains("*")) {
                // handle wildcard
                FileSet fs = new FileSet();
                File dir = file.getParentFile();
                fs.setDir(dir);
                fs.setIncludes(file.getName());
                for( String included : fs.getDirectoryScanner(new Project()).getIncludedFiles() ) {
                    paths.add(new File(dir,included).toURL());
                }
            } else {
                if(!file.exists())
                    throw new IOException("No such file: "+file);
                paths.add(file.toURL());
            }
        }
    }

    private static File resolve(File base, String relative) {
        File rel = new File(relative);
        if(rel.isAbsolute())
            return rel;
        else
            return new File(base.getParentFile(),relative);
    }

    /**
     * Returns the URL of the index page jelly script.
     */
    public URL getIndexPage() {
        return classLoader.getResource("index.jelly");
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

    @Override
    public String toString() {
        return "Plugin:" + getShortName();
    }

    /**
     * Returns a one-line descriptive name of this plugin.
     */
    public String getLongName() {
        String name = manifest.getMainAttributes().getValue("Long-PluginName");
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
     * Gets the "abc" portion from "abc.ext".
     */
    private static String getBaseName(File archive) {
        String n = archive.getName();
        int idx = n.lastIndexOf('.');
        if(idx>=0)
            n = n.substring(0,idx);
        return n;
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
        if(p==null)     return null;

        try {
            if(new VersionNumber(getVersion()).compareTo(new VersionNumber(p.version)) < 0)
                return p;
            return null;
        } catch (IllegalArgumentException e) {
            // couldn't parse it as the version number. ignore. 
            return null;
        }
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

    /**
     * Explodes the plugin into a directory, if necessary.
     */
    private void explode(File archive, File destDir) throws IOException {
        if(!destDir.exists())
            destDir.mkdirs();

        // timestamp check
        File explodeTime = new File(destDir,".timestamp");
        if(explodeTime.exists() && explodeTime.lastModified()>archive.lastModified())
            return; // no need to expand

        LOGGER.info("Extracting "+archive);

        // delete the contents so that old files won't interfere with new files
        Util.deleteContentsRecursive(destDir);

        try {
            Expand e = new Expand();
            e.setProject(new Project());
            e.setTaskType("unzip");
            e.setSrc(archive);
            e.setDest(destDir);
            e.execute();
        } catch (BuildException x) {
            IOException ioe = new IOException("Failed to expand " + archive);
            ioe.initCause(x);
            throw ioe;
        }

        Util.touch(explodeTime);
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

    /**
     * Filter for jar files.
     */
    private static final FilenameFilter JAR_FILTER = new FilenameFilter() {
        public boolean accept(File dir,String name) {
            return name.endsWith(".jar");
        }
    };

    /**
     * Used to load classes from dependency plugins.
     */
    final class DependencyClassLoader extends ClassLoader {
        private final PluginManager manager;

        public DependencyClassLoader(ClassLoader parent, PluginManager manager) {
            super(parent);
            this.manager = manager;
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (Dependency dep : dependencies) {
                PluginWrapper p = manager.getPlugin(dep.shortName);
                if(p!=null)
                    try {
                        return p.classLoader.loadClass(name);
                    } catch (ClassNotFoundException _) {
                        // try next
                    }
            }

            throw new ClassNotFoundException(name);
        }

        // TODO: delegate resources? watch out for diamond dependencies
    }
}
