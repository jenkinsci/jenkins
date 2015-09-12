/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
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

package org.jvnet.hudson.test;

import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link PluginManager} to speed up unit tests.
 *
 * <p>
 * Instead of loading every plugin for every test case, this allows them to reuse a single plugin manager.
 *
 * <p>
 * TODO: {@link Plugin} start/stop/postInitialize invocation semantics gets different. Perhaps
 * 
 * @author Kohsuke Kawaguchi
 * @see HudsonTestCase#useLocalPluginManager
 */
public class TestPluginManager extends PluginManager {
    public static final PluginManager INSTANCE;

    private TestPluginManager() throws IOException {
        // TestPluginManager outlives a Jetty server, so can't pass in ServletContext.
        super(null, Util.createTempDir());
    }

    @Override
    protected Collection<String> loadBundledPlugins() throws Exception {
        Set<String> names = new HashSet<>();
        
        File warBundledPlugins = new File(WarExploder.getExplodedDir(), "WEB-INF/plugins");

        names.addAll(loadBundledPlugins(warBundledPlugins, this));
        names.addAll(loadTestBundledPlugins(this));

        return names;
    }
    
    public static Set<String> loadTestBundledPlugins(PluginManager pluginManager) throws IOException, URISyntaxException {
        File testBundledPlugins = new File(System.getProperty("buildDirectory"), "bundled-plugins"); // Copied by maven - see pom.xml
        return loadBundledPlugins(testBundledPlugins, pluginManager);
    }

    private static Set<String> loadBundledPlugins(File fromDir, PluginManager pluginManager) throws IOException, URISyntaxException {
        Set<String> names = new HashSet<String>();

        File[] children = fromDir.listFiles();
        if (children==null)
            throw new Error("Unable to find "+fromDir);
        for (File child : children) {
            try {
                names.add(child.getName());

                pluginManager.copyBundledPlugin(child.toURI().toURL(), child.getName());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to extract the bundled plugin "+child,e);
            }
        }
        // If running tests for a plugin, include the plugin being tested
        URL u = TestPluginManager.class.getClassLoader().getResource("the.jpl");
        if(u==null){
        	u = TestPluginManager.class.getClassLoader().getResource("the.hpl"); // keep backward compatible 
        }
        if (u!=null) try {
            names.add("the.jpl");
            pluginManager.copyBundledPlugin(u, "the.jpl");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy the.jpl",e);
        }

        // and pick up test dependency *.jpi that are placed by maven-hpi-plugin TestDependencyMojo.
        // and copy them into $JENKINS_HOME/plugins.
        URL index = TestPluginManager.class.getResource("/test-dependencies/index");
        if (index!=null) {// if built with maven-hpi-plugin < 1.52 this file won't exist.
            BufferedReader r = new BufferedReader(new InputStreamReader(index.openStream(),"UTF-8"));
            try {
                String line;
                while ((line=r.readLine())!=null) {
                	final URL url = new URL(index, line + ".jpi");
					File f = new File(url.toURI());
                	if(f.exists()){
                		pluginManager.copyBundledPlugin(url, line + ".jpi");
                	}else{
                		pluginManager.copyBundledPlugin(new URL(index, line + ".hpi"), line + ".jpi"); // fallback to hpi
                	}
                }
            } finally {
                r.close();
            }
        }

        return names;
    }

    // Overwrite PluginManager#stop, not to release plugins in each tests.
    // Releasing plugins result fail to access files in webapp directory in following tests.
    @Override
    public void stop() {
        for (PluginWrapper p : activePlugins)
            p.stop();
    }

    /**
     * As we don't actually shut down classloaders, we instead provide this method that does
     * what {@link #stop()} normally does.
     */
    private void reallyStop() {
        super.stop();
    }

    private static final Logger LOGGER = Logger.getLogger(TestPluginManager.class.getName());

    static {
        try {
            INSTANCE = new TestPluginManager();
            Runtime.getRuntime().addShutdownHook(new Thread("delete " + INSTANCE.rootDir) {
                @Override public void run() {
                    // Shutdown and release plugins as in PluginManager#stop
                    ((TestPluginManager)INSTANCE).reallyStop();

                    // allow JVM cleanup handles of jar files...
                    System.gc();

                    try {
                        Util.deleteRecursive(INSTANCE.rootDir);
                    } catch (IOException x) {
                        x.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}
