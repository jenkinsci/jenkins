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
package hudson.maven;

import hudson.AbortException;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Maven.ProjectWithMaven;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.MavenEmbedderLogger;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenUtil {
    /**
     * @deprecated
     *      Use {@link #createEmbedder(TaskListener, File, String, Properties)}  
     *      or other overloaded versions that infers maven home.
     */
    public static MavenEmbedder createEmbedder(TaskListener listener, String profiles) throws MavenEmbedderException, IOException {
        return createEmbedder(listener,(File)null,profiles);
    }

    /**
     * This version tries to infer mavenHome by looking at a project.
     *
     * @see #createEmbedder(TaskListener, File, String)
     */
    public static MavenEmbedder createEmbedder(TaskListener listener, AbstractProject<?,?> project, String profiles) throws MavenEmbedderException, IOException {
        MavenInstallation m=null;
        if (project instanceof ProjectWithMaven)
            m = ((ProjectWithMaven) project).inferMavenInstallation();

        return createEmbedder(listener,m!=null?m.getHomeDir():null,profiles);
    }

    public static MavenEmbedder createEmbedder(TaskListener listener, File mavenHome, String profiles) throws MavenEmbedderException, IOException {
        return createEmbedder(listener,mavenHome,profiles,new Properties());
    }

    /**
     * Creates a fresh {@link MavenEmbedder} instance.
     *
     * @param listener
     *      This is where the log messages from Maven will be recorded.
     * @param mavenHome
     *      Directory of the Maven installation. We read {@code conf/settings.xml}
     *      from here. Can be null.
     * @param profiles
     *      Profiles to activate/deactivate. Can be null.
     * @param systemProperties
     *      The system properties that the embedded Maven sees. See {@link MavenEmbedder#setSystemProperties(Properties)}.
     */
    public static MavenEmbedder createEmbedder(TaskListener listener, File mavenHome, String profiles, Properties systemProperties) throws MavenEmbedderException, IOException {
        MavenEmbedder maven = new MavenEmbedder(mavenHome);

        ClassLoader cl = MavenUtil.class.getClassLoader();
        maven.setClassLoader(new MaskingClassLoader(cl));
        EmbedderLoggerImpl logger = new EmbedderLoggerImpl(listener);
        if(debugMavenEmbedder)  logger.setThreshold(MavenEmbedderLogger.LEVEL_DEBUG);
        maven.setLogger(logger);

        {
            Enumeration<URL> e = cl.getResources("META-INF/plexus/components.xml");
            while (e.hasMoreElements()) {
                URL url = e.nextElement();
                LOGGER.fine("components.xml from "+url);
            }
        }
        // make sure ~/.m2 exists to avoid http://www.nabble.com/BUG-Report-tf3401736.html
        File m2Home = new File(MavenEmbedder.userHome, ".m2");
        m2Home.mkdirs();
        if(!m2Home.exists())
            throw new AbortException("Failed to create "+m2Home+
                "\nSee https://hudson.dev.java.net/cannot-create-.m2.html");

        maven.setProfiles(profiles);
        maven.setSystemProperties(systemProperties);
        maven.start();

        return maven;
    }


    /**
     * Recursively resolves module POMs that are referenced from
     * the given {@link MavenProject} and parses them into
     * {@link MavenProject}s.
     *
     * @param rel
     *      Used to compute the relative path. Pass in "" to begin.
     * @param relativePathInfo
     *      Upon the completion of this method, this variable stores the relative path
     *      from the root directory of the given {@link MavenProject} to the root directory
     *      of each of the newly parsed {@link MavenProject}.
     *
     * @throws AbortException
     *      errors will be reported to the listener and the exception thrown.
     */
    public static void resolveModules(MavenEmbedder embedder, MavenProject project, String rel, Map<MavenProject,String> relativePathInfo, BuildListener listener) throws ProjectBuildingException, AbortException {

        File basedir = project.getFile().getParentFile();
        relativePathInfo.put(project,rel);

        List<MavenProject> modules = new ArrayList<MavenProject>();

        for (String modulePath : (List<String>) project.getModules()) {
            File moduleFile = new File(basedir, modulePath);
            if (moduleFile.exists() && moduleFile.isDirectory()) {
                moduleFile = new File(basedir, modulePath + "/pom.xml");
            }
            if(!moduleFile.exists())
                throw new AbortException(moduleFile+" is referenced from "+project.getFile()+" but it doesn't exist");

            String relativePath = rel;
            if(relativePath.length()>0) relativePath+='/';
            relativePath+=modulePath;

            MavenProject child = embedder.readProject(moduleFile);
            resolveModules(embedder,child,relativePath,relativePathInfo,listener);
            modules.add(child);
        }

        project.setCollectedProjects(modules);
    }

    /**
     * When we run in Jetty during development, embedded Maven will end up
     * seeing some of the Maven class visible through Jetty, and this confuses it.
     *
     * <p>
     * Specifically, embedded Maven will find all the component descriptors
     * visible through Jetty, yet when it comes to loading classes, classworlds
     * still load classes from local realms created inside embedder.
     *
     * <p>
     * This classloader prevents this issue by hiding the component descriptor
     * visible through Jetty.
     */
    private static final class MaskingClassLoader extends ClassLoader {

        public MaskingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Enumeration<URL> getResources(String name) throws IOException {
            final Enumeration<URL> e = super.getResources(name);
            return new Enumeration<URL>() {
                URL next;

                public boolean hasMoreElements() {
                    fetch();
                    return next!=null;
                }

                public URL nextElement() {
                    fetch();
                    URL r = next;
                    next = null;
                    return r;
                }

                private void fetch() {
                    while(next==null && e.hasMoreElements()) {
                        next = e.nextElement();
                        if(shouldBeIgnored(next))
                            next = null;
                    }
                }

                private boolean shouldBeIgnored(URL url) {
                    String s = url.toExternalForm();
                    if(s.contains("maven-plugin-tools-api"))
                        return true;
                    // because RemoteClassLoader mangles the path, we can't check for plexus/components.xml,
                    // which would have otherwise made the test cheaper.
                    if(s.endsWith("components.xml")) {
                        BufferedReader r=null;
                        try {
                            // is this designated for interception purpose? If so, don't load them in the MavenEmbedder
                            // earlier I tried to use a marker file in the same directory, but that won't work
                            r = new BufferedReader(new InputStreamReader(url.openStream()));
                            for (int i=0; i<2; i++) {
                                String l = r.readLine();
                                if(l!=null && l.contains("MAVEN-INTERCEPTION-TO-BE-MASKED"))
                                    return true;
                            }
                        } catch (IOException _) {
                            // let whoever requesting this resource re-discover an error and report it
                        } finally {
                            IOUtils.closeQuietly(r);
                        }
                    }
                    return false;
                }
            };
        }
    }

    /**
     * If set to true, maximize the logging level of Maven embedder.
     */
    public static boolean debugMavenEmbedder = false;

    private static final Logger LOGGER = Logger.getLogger(MavenUtil.class.getName());
}
