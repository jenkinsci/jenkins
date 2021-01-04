/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Tom Huybrechts
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

import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Plugin.DummyImpl;
import hudson.PluginWrapper.Dependency;
import hudson.model.Hudson;
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.IOUtils;
import hudson.util.MaskingClassLoader;
import jenkins.ClassLoaderReflectionToolkit;
import jenkins.ExtensionFilter;
import jenkins.plugins.DetachedPluginsUtil;
import jenkins.util.AntClassLoader;
import jenkins.util.AntWithFindResourceClassLoader;
import jenkins.util.SystemProperties;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ZipFileSet;
import org.apache.tools.ant.types.resources.MappedResourceCollection;
import org.apache.tools.ant.util.GlobPatternMapper;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipExtraField;
import org.apache.tools.zip.ZipOutputStream;
import org.jenkinsci.bytecode.Transformer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.io.FilenameUtils.getBaseName;

public class ClassicPluginStrategy implements PluginStrategy {

    private static final Logger LOGGER = Logger.getLogger(ClassicPluginStrategy.class.getName());

    /**
     * Filter for jar files.
     */
    private static final FilenameFilter JAR_FILTER = new FilenameFilter() {
        public boolean accept(File dir,String name) {
            return name.endsWith(".jar");
        }
    };

    private PluginManager pluginManager;

    /**
     * All the plugins eventually delegate this classloader to load core, servlet APIs, and SE runtime.
     */
    private final MaskingClassLoader coreClassLoader = new MaskingClassLoader(getClass().getClassLoader());

    public ClassicPluginStrategy(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override public String getShortName(File archive) throws IOException {
        Manifest manifest;
        if (!archive.exists()) {
            throw new FileNotFoundException("Failed to load " + archive + ". The file does not exist");
        } else if (!archive.isFile()) {
            throw new FileNotFoundException("Failed to load " + archive + ". It is not a file");
        }

        if (isLinked(archive)) {
            manifest = loadLinkedManifest(archive);
        } else {
            try (JarFile jf = new JarFile(archive, false)) {
                manifest = jf.getManifest();
            } catch (IOException ex) {
                // Mention file name in the exception
                throw new IOException("Failed to load " + archive, ex);
            }
        }
        return PluginWrapper.computeShortName(manifest, archive.getName());
    }

    private static boolean isLinked(File archive) {
        return archive.getName().endsWith(".hpl") || archive.getName().endsWith(".jpl");
    }

    private static Manifest loadLinkedManifest(File archive) throws IOException {
            // resolve the .hpl file to the location of the manifest file        
            try {
                // Locate the manifest
                String firstLine;
                try (InputStream manifestHeaderInput = Files.newInputStream(archive.toPath())) {
                    firstLine = IOUtils.readFirstLine(manifestHeaderInput, "UTF-8");
                } catch (InvalidPathException e) {
                    throw new IOException(e);
                }
                //noinspection StatementWithEmptyBody
                if (firstLine.startsWith("Manifest-Version:")) {
                    // this is the manifest already
                } else {
                    // indirection
                    archive = resolve(archive, firstLine);
                }
                
                // Read the manifest
                try (InputStream manifestInput = Files.newInputStream(archive.toPath())) {
                    return new Manifest(manifestInput);
                } catch (InvalidPathException e) {
                    throw new IOException(e);
                }
            } catch (IOException e) {
                throw new IOException("Failed to load " + archive, e);
            }
    }

    @Override public PluginWrapper createPluginWrapper(File archive) throws IOException {
        final Manifest manifest;

        URL baseResourceURL;
        File expandDir = null;
        // if .hpi, this is the directory where war is expanded

        boolean isLinked = isLinked(archive);
        if (isLinked) {
            manifest = loadLinkedManifest(archive);
        } else {
            if (archive.isDirectory()) {// already expanded
                expandDir = archive;
            } else {
                File f = pluginManager.getWorkDir();
                expandDir =  new File(f == null ? archive.getParentFile() : f, getBaseName(archive.getName()));
                explode(archive, expandDir);
            }

            File manifestFile = new File(expandDir, PluginWrapper.MANIFEST_FILENAME);
            if (!manifestFile.exists()) {
                throw new IOException(
                        "Plugin installation failed. No manifest at "
                                + manifestFile);
            }
            try (InputStream fin = Files.newInputStream(manifestFile.toPath())) {
                manifest = new Manifest(fin);
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }
            String canonicalName = manifest.getMainAttributes().getValue("Short-Name") + ".jpi";
            if (!archive.getName().equals(canonicalName)) {
                LOGGER.warning(() -> "encountered " + archive + " under a nonstandard name; expected " + canonicalName);
            }
        }

        final Attributes atts = manifest.getMainAttributes();

        // TODO: define a mechanism to hide classes
        // String export = manifest.getMainAttributes().getValue("Export");

        List<File> paths = new ArrayList<>();
        if (isLinked) {
            parseClassPath(manifest, archive, paths, "Libraries", ",");
            parseClassPath(manifest, archive, paths, "Class-Path", " +"); // backward compatibility

            baseResourceURL = resolve(archive,atts.getValue("Resource-Path")).toURI().toURL();
        } else {
            File classes = new File(expandDir, "WEB-INF/classes");
            if (classes.exists()) { // should not normally happen, due to createClassJarFromWebInfClasses
                LOGGER.log(Level.WARNING, "Deprecated unpacked classes directory found in {0}", classes);
                paths.add(classes);
            }
            File lib = new File(expandDir, "WEB-INF/lib");
            File[] libs = lib.listFiles(JAR_FILTER);
            if (libs != null)
                paths.addAll(Arrays.asList(libs));

            baseResourceURL = expandDir.toPath().toUri().toURL();

        }
        File disableFile = new File(archive.getPath() + ".disabled");
        if (disableFile.exists()) {
            LOGGER.info("Plugin " + archive.getName() + " is disabled");
        }

        // compute dependencies
        List<PluginWrapper.Dependency> dependencies = new ArrayList<>();
        List<PluginWrapper.Dependency> optionalDependencies = new ArrayList<>();
        String v = atts.getValue("Plugin-Dependencies");
        if (v != null) {
            for (String s : v.split(",")) {
                PluginWrapper.Dependency d = new PluginWrapper.Dependency(s);
                if (d.optional) {
                    optionalDependencies.add(d);
                } else {
                    dependencies.add(d);
                }
            }
        }
        
        fix(atts,optionalDependencies);

        // Register global classpath mask. This is useful for hiding JavaEE APIs that you might see from the container,
        // such as database plugin for JPA support. The Mask-Classes attribute is insufficient because those classes
        // also need to be masked by all the other plugins that depend on the database plugin.
        String masked = atts.getValue("Global-Mask-Classes");
        if(masked!=null) {
            for (String pkg : masked.trim().split("[ \t\r\n]+"))
                coreClassLoader.add(pkg);
        }

        ClassLoader dependencyLoader = new DependencyClassLoader(coreClassLoader, archive, Util.join(dependencies,optionalDependencies));
        dependencyLoader = getBaseClassLoader(atts, dependencyLoader);

        return new PluginWrapper(pluginManager, archive, manifest, baseResourceURL,
                createClassLoader(paths, dependencyLoader, atts), disableFile, dependencies, optionalDependencies);
    }

    private void fix(Attributes atts, List<PluginWrapper.Dependency> optionalDependencies) {
        String pluginName = atts.getValue("Short-Name");
        
        String jenkinsVersion = atts.getValue("Jenkins-Version");
        if (jenkinsVersion==null)
            jenkinsVersion = atts.getValue("Hudson-Version");

        for (Dependency d : DetachedPluginsUtil.getImpliedDependencies(pluginName, jenkinsVersion)) {
            LOGGER.fine(() -> "implied dep " + pluginName + " → " + d.shortName);
            pluginManager.considerDetachedPlugin(d.shortName);
            optionalDependencies.add(d);
        }
    }

    /**
     * @see DetachedPluginsUtil#getImpliedDependencies(String, String)
     *
     * @deprecated since 2.163
     */
    @Deprecated
    @NonNull
    public static List<PluginWrapper.Dependency> getImpliedDependencies(String pluginName, String jenkinsVersion) {
        return DetachedPluginsUtil.getImpliedDependencies(pluginName, jenkinsVersion);
    }

    @Deprecated
    protected ClassLoader createClassLoader(List<File> paths, ClassLoader parent) throws IOException {
        return createClassLoader( paths, parent, null );
    }

    /**
     * Creates the classloader that can load all the specified jar files and delegate to the given parent.
     */
    protected ClassLoader createClassLoader(List<File> paths, ClassLoader parent, Attributes atts) throws IOException {
        if (atts != null) {
            String usePluginFirstClassLoader = atts.getValue( "PluginFirstClassLoader" );
            if (Boolean.parseBoolean( usePluginFirstClassLoader )) {
                PluginFirstClassLoader classLoader = new PluginFirstClassLoader();
                classLoader.setParentFirst( false );
                classLoader.setParent( parent );
                classLoader.addPathFiles( paths );
                return classLoader;
            }
        }

        AntClassLoader2 classLoader = new AntClassLoader2(parent);
        classLoader.addPathFiles(paths);
        return classLoader;
    }

    /**
     * Computes the classloader that takes the class masking into account.
     *
     * <p>
     * This mechanism allows plugins to have their own versions for libraries that core bundles.
     */
    private ClassLoader getBaseClassLoader(Attributes atts, ClassLoader base) {
        String masked = atts.getValue("Mask-Classes");
        if(masked!=null)
            base = new MaskingClassLoader(base, masked.trim().split("[ \t\r\n]+"));
        return base;
    }

    public void initializeComponents(PluginWrapper plugin) {
    }

    public <T> List<ExtensionComponent<T>> findComponents(Class<T> type, Hudson hudson) {

        List<ExtensionFinder> finders;
        if (type==ExtensionFinder.class) {
            // Avoid infinite recursion of using ExtensionFinders to find ExtensionFinders
            finders = Collections.singletonList(new ExtensionFinder.Sezpoz());
        } else {
            finders = hudson.getExtensionList(ExtensionFinder.class);
        }

        /*
         * See ExtensionFinder#scout(Class, Hudson) for the dead lock issue and what this does.
         */
        if (LOGGER.isLoggable(Level.FINER))
            LOGGER.log(Level.FINER, "Scout-loading ExtensionList: "+type, new Throwable());
        for (ExtensionFinder finder : finders) {
            finder.scout(type, hudson);
        }

        List<ExtensionComponent<T>> r = Lists.newArrayList();
        for (ExtensionFinder finder : finders) {
            try {
                r.addAll(finder.find(type, hudson));
            } catch (AbstractMethodError e) {
                // backward compatibility
                for (T t : finder.findExtensions(type, hudson))
                    r.add(new ExtensionComponent<>(t));
            }
        }

        List<ExtensionComponent<T>> filtered = Lists.newArrayList();
        for (ExtensionComponent<T> e : r) {
            if (ExtensionFilter.isAllowed(type,e))
                filtered.add(e);
        }

        return filtered;
    }

    public void load(PluginWrapper wrapper) throws IOException {
        // override the context classloader. This no longer makes sense,
        // but it is left for the backward compatibility
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(wrapper.classLoader);
        try {
            String className = wrapper.getPluginClass();
            if(className==null) {
                // use the default dummy instance
                wrapper.setPlugin(new DummyImpl());
            } else {
                try {
                    Class<?> clazz = wrapper.classLoader.loadClass(className);
                    Object o = clazz.newInstance();
                    if(!(o instanceof Plugin)) {
                        throw new IOException(className+" doesn't extend from hudson.Plugin");
                    }
                    wrapper.setPlugin((Plugin) o);
                } catch (LinkageError | ClassNotFoundException e) {
                    throw new IOException("Unable to load " + className + " from " + wrapper.getShortName(),e);
                } catch (IllegalAccessException | InstantiationException e) {
                    throw new IOException("Unable to create instance of " + className + " from " + wrapper.getShortName(),e);
                }
            }

            // initialize plugin
            try {
                Plugin plugin = wrapper.getPlugin();
                plugin.setServletContext(pluginManager.context);
                startPlugin(wrapper);
            } catch(Throwable t) {
                // gracefully handle any error in plugin.
                throw new IOException("Failed to initialize",t);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    public void startPlugin(PluginWrapper plugin) throws Exception {
        plugin.getPlugin().start();
    }

    @Override
    public void updateDependency(PluginWrapper depender, PluginWrapper dependee) {
        DependencyClassLoader classLoader = findAncestorDependencyClassLoader(depender.classLoader);
        if (classLoader != null) {
            classLoader.updateTransientDependencies();
            LOGGER.log(Level.INFO, "Updated dependency of {0}", depender.getShortName());
        }
    }

    private DependencyClassLoader findAncestorDependencyClassLoader(ClassLoader classLoader)
    {
        for (; classLoader != null; classLoader = classLoader.getParent()) {
            if (classLoader instanceof DependencyClassLoader) {
                return (DependencyClassLoader)classLoader;
            }
            
            if (classLoader instanceof AntClassLoader) {
                // AntClassLoaders hold parents not only as AntClassLoader#getParent()
                // but also as AntClassLoader#getConfiguredParent()
                DependencyClassLoader ret = findAncestorDependencyClassLoader(
                        ((AntClassLoader)classLoader).getConfiguredParent()
                );
                if (ret != null) {
                    return ret;
                }
            }
        }
        return null;
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Administrator action installing a plugin, which could do far worse.")
    private static File resolve(File base, String relative) {
        File rel = new File(relative);
        if(rel.isAbsolute())
            return rel;
        else
            return new File(base.getParentFile(),relative);
    }

    private static void parseClassPath(Manifest manifest, File archive, List<File> paths, String attributeName, String separator) throws IOException {
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
                    paths.add(new File(dir,included));
                }
            } else {
                if(!file.exists())
                    throw new IOException("No such file: "+file);
                paths.add(file);
            }
        }
    }

    /**
     * Explodes the plugin into a directory, if necessary.
     */
    private static void explode(File archive, File destDir) throws IOException {
        destDir.mkdirs();

        // timestamp check
        File explodeTime = new File(destDir,".timestamp2");
        if(explodeTime.exists() && explodeTime.lastModified()==archive.lastModified())
            return; // no need to expand

        // delete the contents so that old files won't interfere with new files
        Util.deleteRecursive(destDir);

        try {
            Project prj = new Project();
            unzipExceptClasses(archive, destDir, prj);
            createClassJarFromWebInfClasses(archive, destDir, prj);
        } catch (BuildException x) {
            throw new IOException("Failed to expand " + archive,x);
        }

        try {
            new FilePath(explodeTime).touch(archive.lastModified());
        } catch (InterruptedException e) {
            throw new AssertionError(e); // impossible
        }
    }

    /**
     * Repackage classes directory into a jar file to make it remoting friendly.
     * The remoting layer can cache jar files but not class files.
     */
    private static void createClassJarFromWebInfClasses(File archive, File destDir, Project prj) throws IOException {
        File classesJar = new File(destDir, "WEB-INF/lib/classes.jar");

        ZipFileSet zfs = new ZipFileSet();
        zfs.setProject(prj);
        zfs.setSrc(archive);
        zfs.setIncludes("WEB-INF/classes/");

        MappedResourceCollection mapper = new MappedResourceCollection();
        mapper.add(zfs);

        GlobPatternMapper gm = new GlobPatternMapper();
        gm.setFrom("WEB-INF/classes/*");
        gm.setTo("*");
        mapper.add(gm);

        final long dirTime = archive.lastModified();
        // this ZipOutputStream is reused and not created for each directory
        try (ZipOutputStream wrappedZOut = new ZipOutputStream(NullOutputStream.NULL_OUTPUT_STREAM) {
            @Override
            public void putNextEntry(ZipEntry ze) throws IOException {
                ze.setTime(dirTime+1999);   // roundup
                super.putNextEntry(ze);
            }
        }) {
            Zip z = new Zip() {
                /**
                 * Forces the fixed timestamp for directories to make sure
                 * classes.jar always get a consistent checksum.
                 */
                protected void zipDir(Resource dir, ZipOutputStream zOut, String vPath,
                                      int mode, ZipExtraField[] extra)
                    throws IOException {
                    // use wrappedZOut instead of zOut
                    super.zipDir(dir,wrappedZOut,vPath,mode,extra);
                }
            };
            z.setProject(prj);
            z.setTaskType("zip");
            classesJar.getParentFile().mkdirs();
            z.setDestFile(classesJar);
            z.add(mapper);
            z.execute();
        }
        if (classesJar.isFile()) {
            LOGGER.log(Level.WARNING, "Created {0}; update plugin to a version created with a newer harness", classesJar);
        }
    }

    private static void unzipExceptClasses(File archive, File destDir, Project prj) {
        Expand e = new Expand();
        e.setProject(prj);
        e.setTaskType("unzip");
        e.setSrc(archive);
        e.setDest(destDir);
        PatternSet p = new PatternSet();
        p.setExcludes("WEB-INF/classes/");
        e.addPatternset(p);
        e.execute();
    }

    /**
     * Used to load classes from dependency plugins.
     */
    final class DependencyClassLoader extends ClassLoader {
        /**
         * This classloader is created for this plugin. Useful during debugging.
         */
        private final File _for;

        private List<Dependency> dependencies;

        /**
         * Topologically sorted list of transient dependencies.
         */
        private volatile List<PluginWrapper> transientDependencies;

        public DependencyClassLoader(ClassLoader parent, File archive, List<Dependency> dependencies) {
            super(parent);
            this._for = archive;
            this.dependencies = dependencies;
        }

        private void updateTransientDependencies() {
            // This will be recalculated at the next time.
            transientDependencies = null;
        }

        private List<PluginWrapper> getTransitiveDependencies() {
            if (transientDependencies==null) {
                CyclicGraphDetector<PluginWrapper> cgd = new CyclicGraphDetector<PluginWrapper>() {
                    @Override
                    protected List<PluginWrapper> getEdges(PluginWrapper pw) {
                        List<PluginWrapper> dep = new ArrayList<>();
                        for (Dependency d : pw.getDependencies()) {
                            PluginWrapper p = pluginManager.getPlugin(d.shortName);
                            if (p!=null && p.isActive())
                                dep.add(p);
                        }
                        return dep;
                    }
                };

                try {
                    for (Dependency d : dependencies) {
                        PluginWrapper p = pluginManager.getPlugin(d.shortName);
                        if (p!=null && p.isActive())
                            cgd.run(Collections.singleton(p));
                    }
                } catch (CycleDetectedException e) {
                    throw new AssertionError(e);    // such error should have been reported earlier
                }

                transientDependencies = cgd.getSorted();
            }
            return transientDependencies;
        }

//        public List<PluginWrapper> getDependencyPluginWrappers() {
//            List<PluginWrapper> r = new ArrayList<PluginWrapper>();
//            for (Dependency d : dependencies) {
//                PluginWrapper w = pluginManager.getPlugin(d.shortName);
//                if (w!=null)    r.add(w);
//            }
//            return r;
//        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (PluginManager.FAST_LOOKUP) {
                for (PluginWrapper pw : getTransitiveDependencies()) {
                    try {
                        Class<?> c = ClassLoaderReflectionToolkit._findLoadedClass(pw.classLoader, name);
                        if (c!=null)    return c;
                        return ClassLoaderReflectionToolkit._findClass(pw.classLoader, name);
                    } catch (ClassNotFoundException ignored) {
                        //not found. try next
                    }
                }
            } else {
                for (Dependency dep : dependencies) {
                    PluginWrapper p = pluginManager.getPlugin(dep.shortName);
                    if(p!=null) {
                        try {
                            return p.classLoader.loadClass(name);
                        } catch (ClassNotFoundException ignored) {
                            // OK, try next
                        }
                    }
                }
            }

            throw new ClassNotFoundException(name);
        }

        @Override
        @SuppressFBWarnings(value = "DMI_COLLECTION_OF_URLS",
                            justification = "Should not produce network overheads since the URL is local. JENKINS-53793 is a follow-up")
        protected Enumeration<URL> findResources(String name) throws IOException {
            HashSet<URL> result = new HashSet<>();

            if (PluginManager.FAST_LOOKUP) {
                    for (PluginWrapper pw : getTransitiveDependencies()) {
                        Enumeration<URL> urls = ClassLoaderReflectionToolkit._findResources(pw.classLoader, name);
                        while (urls != null && urls.hasMoreElements())
                            result.add(urls.nextElement());
                    }
            } else {
                for (Dependency dep : dependencies) {
                    PluginWrapper p = pluginManager.getPlugin(dep.shortName);
                    if (p!=null) {
                        Enumeration<URL> urls = p.classLoader.getResources(name);
                        while (urls != null && urls.hasMoreElements())
                            result.add(urls.nextElement());
                    }
                }
            }

            return Collections.enumeration(result);
        }

        @Override
        protected URL findResource(String name) {
            if (PluginManager.FAST_LOOKUP) {
                    for (PluginWrapper pw : getTransitiveDependencies()) {
                        URL url = ClassLoaderReflectionToolkit._findResource(pw.classLoader, name);
                        if (url!=null)    return url;
                    }
            } else {
                for (Dependency dep : dependencies) {
                    PluginWrapper p = pluginManager.getPlugin(dep.shortName);
                    if(p!=null) {
                        URL url = p.classLoader.getResource(name);
                        if (url!=null)
                            return url;
                    }
                }
            }

            return null;
        }
    }

    /**
     * {@link AntClassLoader} with a few methods exposed, {@link Closeable} support, and {@link Transformer} support.
     */
    private final class AntClassLoader2 extends AntWithFindResourceClassLoader implements Closeable {
        private AntClassLoader2(ClassLoader parent) {
            super(parent, true);
        }
        
        @Override
        protected Class defineClassFromData(File container, byte[] classData, String classname) throws IOException {
            if (!DISABLE_TRANSFORMER)
                classData = pluginManager.getCompatibilityTransformer().transform(classname, classData, this);
            return super.defineClassFromData(container, classData, classname);
        }
    }

    /* Unused since 1.527, see https://github.com/jenkinsci/jenkins/commit/47de54d070f67af95b4fefb6d006a72bb31a5cb8 */
    @Deprecated
    public static boolean useAntClassLoader = SystemProperties.getBoolean(ClassicPluginStrategy.class.getName()+".useAntClassLoader");
    public static boolean DISABLE_TRANSFORMER = SystemProperties.getBoolean(ClassicPluginStrategy.class.getName()+".noBytecodeTransformer");
}
