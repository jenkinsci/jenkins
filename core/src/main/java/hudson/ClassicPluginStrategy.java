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

import hudson.PluginWrapper.Dependency;
import hudson.util.IOException2;
import hudson.model.Hudson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.types.FileSet;

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
	
	public ClassicPluginStrategy(PluginManager pluginManager) {
		this.pluginManager = pluginManager;
	}

	public PluginWrapper createPluginWrapper(File archive) throws IOException {
		LOGGER.info("Loading plugin: " + archive);

		Manifest manifest;
		URL baseResourceURL;

		boolean isLinked = archive.getName().endsWith(".hpl");

		File expandDir = null; 
		// if .hpi, this is the directory where war is expanded

		if (isLinked) {
			// resolve the .hpl file to the location of the manifest file
			String firstLine = new BufferedReader(new FileReader(archive))
					.readLine();
			if (firstLine.startsWith("Manifest-Version:")) {
				// this is the manifest already
			} else {
				// indirection
				archive = resolve(archive, firstLine);
			}
			// then parse manifest
			FileInputStream in = new FileInputStream(archive);
			try {
				manifest = new Manifest(in);
			} catch (IOException e) {
				throw new IOException2("Failed to load " + archive, e);
			} finally {
				in.close();
			}
		} else {
			expandDir = new File(archive.getParentFile(), PluginWrapper.getBaseName(archive));
			explode(archive, expandDir);

			File manifestFile = new File(expandDir, "META-INF/MANIFEST.MF");
			if (!manifestFile.exists()) {
				throw new IOException(
						"Plugin installation failed. No manifest at "
								+ manifestFile);
			}
			FileInputStream fin = new FileInputStream(manifestFile);
			try {
				manifest = new Manifest(fin);
			} finally {
				fin.close();
			}
		}

		// TODO: define a mechanism to hide classes
		// String export = manifest.getMainAttributes().getValue("Export");

		List<URL> paths = new ArrayList<URL>();
		if (isLinked) {
			parseClassPath(manifest, archive, paths, "Libraries", ",");
			parseClassPath(manifest, archive, paths, "Class-Path", " +"); // backward 
			// compatibility

			baseResourceURL = resolve(archive,
					manifest.getMainAttributes().getValue("Resource-Path"))
					.toURL();
		} else {
			File classes = new File(expandDir, "WEB-INF/classes");
			if (classes.exists())
				paths.add(classes.toURL());
			File lib = new File(expandDir, "WEB-INF/lib");
			File[] libs = lib.listFiles(JAR_FILTER);
			if (libs != null) {
				for (File jar : libs)
					paths.add(jar.toURL());
			}

			baseResourceURL = expandDir.toURL();
		}
		File disableFile = new File(archive.getPath() + ".disabled");
		if (disableFile.exists()) {
			LOGGER.info("Plugin is disabled");
		}

		// compute dependencies
		List<PluginWrapper.Dependency> dependencies = new ArrayList<PluginWrapper.Dependency>();
		List<PluginWrapper.Dependency> optionalDependencies = new ArrayList<PluginWrapper.Dependency>();
		String v = manifest.getMainAttributes().getValue("Plugin-Dependencies");
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

        // native m2 support moved to a plugin starting 1.296, so plugins built before that
        // needs to have an implicit dependency to the maven-plugin, or NoClassDefError will ensue.
        String hudsonVersion = manifest.getMainAttributes().getValue("Hudson-Version");
        String shortName = manifest.getMainAttributes().getValue("Short-Name");
        if (!"maven-plugin".equals(shortName) &&
                // some earlier versions of maven-hpi-plugin apparently puts "null" as a literal here. Watch out for those.
                (hudsonVersion == null || hudsonVersion.equals("null") || hudsonVersion.compareTo("1.296") <= 0)) {
            optionalDependencies.add(new PluginWrapper.Dependency("maven-plugin:" + Hudson.VERSION));
        }

        // subversion support was split off into a plugin in 1.311, so plugins built before that should automatically get
        // subversion plugin as a dependency
        if (!"subversion".equals(shortName) &&
                (hudsonVersion == null || hudsonVersion.equals("null") || hudsonVersion.compareTo("1.310") <= 0)) {
            optionalDependencies.add(new PluginWrapper.Dependency("subversion:1.0"));
        }

		ClassLoader dependencyLoader = new DependencyClassLoader(getClass()
				.getClassLoader(), Util.join(dependencies,optionalDependencies));
		ClassLoader classLoader = new URLClassLoader(paths.toArray(new URL[paths.size()]),
				dependencyLoader);

		return new PluginWrapper(archive, manifest, baseResourceURL,
				classLoader, disableFile, dependencies, optionalDependencies);
	}

	public void initializeComponents(PluginWrapper plugin) {
	}

	public void load(PluginWrapper wrapper) throws IOException {
		loadPluginDependencies(wrapper.getDependencies(),
				wrapper.getOptionalDependencies());

		if (!wrapper.isActive())
			return;

        // override the context classloader so that XStream activity in plugin.start()
        // will be able to resolve classes in this plugin
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(wrapper.classLoader);
        try {
            String className = wrapper.getPluginClass();
            if(className==null) {
                // use the default dummy instance
                wrapper.setPlugin(Plugin.NONE);
            } else {
                try {
                    Class clazz = wrapper.classLoader.loadClass(className);
                    Object o = clazz.newInstance();
                    if(!(o instanceof Plugin)) {
                        throw new IOException(className+" doesn't extend from hudson.Plugin");
                    }
                    wrapper.setPlugin((Plugin) o);
                } catch (LinkageError e) {
                    throw new IOException2("Unable to load " + className + " from " + wrapper.getShortName(),e);
                } catch (ClassNotFoundException e) {
                    throw new IOException2("Unable to load " + className + " from " + wrapper.getShortName(),e);
                } catch (IllegalAccessException e) {
                    throw new IOException2("Unable to create instance of " + className + " from " + wrapper.getShortName(),e);
                } catch (InstantiationException e) {
                    throw new IOException2("Unable to create instance of " + className + " from " + wrapper.getShortName(),e);
                }
            }

            // initialize plugin
            try {
            	Plugin plugin = wrapper.getPlugin();
                plugin.setServletContext(pluginManager.context);
                startPlugin(wrapper);
            } catch(Throwable t) {
                // gracefully handle any error in plugin.
                throw new IOException2("Failed to initialize",t);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
	}
	
	public void startPlugin(PluginWrapper plugin) throws Exception {
		plugin.getPlugin().start();
	}

    private static File resolve(File base, String relative) {
        File rel = new File(relative);
        if(rel.isAbsolute())
            return rel;
        else
            return new File(base.getParentFile(),relative);
    }

    private static void parseClassPath(Manifest manifest, File archive, List<URL> paths, String attributeName, String separator) throws IOException {
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

    /**
     * Explodes the plugin into a directory, if necessary.
     */
    private static void explode(File archive, File destDir) throws IOException {
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

	/**
	 * Loads the dependencies to other plugins.
	 * 
	 * @throws IOException
	 *             thrown if one or several mandatory dependencies doesnt
	 *             exists.
	 */
	private void loadPluginDependencies(List<Dependency> dependencies,
			List<Dependency> optionalDependencies) throws IOException {
		List<String> missingDependencies = new ArrayList<String>();
		// make sure dependencies exist
		for (Dependency d : dependencies) {
			if (pluginManager.getPlugin(d.shortName) == null)
				missingDependencies.add(d.toString());
		}
		if (!missingDependencies.isEmpty()) {
			StringBuilder builder = new StringBuilder();
			builder.append("Dependency ");
			builder.append(Util.join(missingDependencies, ", "));
			builder.append(" doesn't exist");
			throw new IOException(builder.toString());
		}

		// add the optional dependencies that exists
		for (Dependency d : optionalDependencies) {
			if (pluginManager.getPlugin(d.shortName) != null)
				dependencies.add(d);
		}
	}
    
    /**
     * Used to load classes from dependency plugins.
     */
    final class DependencyClassLoader extends ClassLoader {
		private List<Dependency> dependencies;

        public DependencyClassLoader(ClassLoader parent, List<Dependency> dependencies) {
            super(parent);
            this.dependencies = dependencies;
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (Dependency dep : dependencies) {
                PluginWrapper p = pluginManager.getPlugin(dep.shortName);
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
