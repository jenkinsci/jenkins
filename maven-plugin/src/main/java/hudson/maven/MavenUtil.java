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

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Maven.ProjectWithMaven;
import jenkins.model.Jenkins;
import jenkins.mvn.SettingsProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.cli.logging.Slf4jLoggerManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.PlexusConstants;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static MavenEmbedder createEmbedder(TaskListener listener, AbstractProject<?,?> project, String profiles) throws MavenEmbedderException, IOException, InterruptedException {
        MavenInstallation m=null;
        if (project instanceof ProjectWithMaven)
            m = ((ProjectWithMaven) project).inferMavenInstallation().forNode(Jenkins.getInstance(),listener);

        return createEmbedder(listener,m!=null?m.getHomeDir():null,profiles);
    }

    /**
     * This version tries to infer mavenHome and other options by looking at a build.
     *
     * @see #createEmbedder(TaskListener, File, String)
     */
    public static MavenEmbedder createEmbedder(TaskListener listener, AbstractBuild<?,?> build) throws MavenEmbedderException, IOException, InterruptedException {
        MavenInstallation m=null;
        File settingsLoc = null;
        String profiles = null;
        Properties systemProperties = null;
        String privateRepository = null;
        
        AbstractProject<?,?> project = build.getProject();
        
        if (project instanceof ProjectWithMaven) {
            m = ((ProjectWithMaven) project).inferMavenInstallation().forNode(Jenkins.getInstance(),listener);
        }
        if (project instanceof MavenModuleSet) {
            String altSet = SettingsProvider.getSettingsRemotePath(((MavenModuleSet) project).getSettings(), build, listener);
            
            settingsLoc = (altSet == null) ? null 
                : new File(build.getWorkspace().child(altSet).getRemote());

            FilePath localRepo = ((MavenModuleSet) project).getLocalRepository().locate((MavenModuleSetBuild) build);
            if (localRepo!=null) {
                privateRepository = localRepo.getRemote();
            }

            profiles = ((MavenModuleSet) project).getProfiles();
            systemProperties = ((MavenModuleSet) project).getMavenProperties();
        }
        
        return createEmbedder(new MavenEmbedderRequest(listener,
                              m!=null?m.getHomeDir():null,
                              profiles,
                              systemProperties,
                              privateRepository,
                              settingsLoc ));
    }

    public static MavenEmbedder createEmbedder(TaskListener listener, File mavenHome, String profiles) throws MavenEmbedderException, IOException {
        return createEmbedder(listener,mavenHome,profiles,new Properties());
    }

    public static MavenEmbedder createEmbedder(TaskListener listener, File mavenHome, String profiles, Properties systemProperties) throws MavenEmbedderException, IOException {
        return createEmbedder(listener,mavenHome,profiles,systemProperties,null);
    }

    public static MavenEmbedder createEmbedder( TaskListener listener, File mavenHome, String profiles,
                                                Properties systemProperties, String privateRepository )
        throws MavenEmbedderException, IOException
    {
        return createEmbedder( new MavenEmbedderRequest( listener, mavenHome, profiles, systemProperties,
                                                         privateRepository, null ) );
    }

    /**
     * Creates a fresh {@link MavenEmbedder} instance.
     *
     */
    @SuppressWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public static MavenEmbedder createEmbedder(MavenEmbedderRequest mer) throws MavenEmbedderException, IOException {
        
        
        MavenRequest mavenRequest = new MavenRequest();
        
        // make sure ~/.m2 exists to avoid http://www.nabble.com/BUG-Report-tf3401736.html
        File m2Home = new File(MavenEmbedder.userHome, ".m2");
        m2Home.mkdirs();
        if(!m2Home.exists())
            throw new AbortException("Failed to create "+m2Home);

        if (mer.getPrivateRepository()!=null)
            mavenRequest.setLocalRepositoryPath( mer.getPrivateRepository() );

        if (mer.getProfiles() != null) {
            mavenRequest.setProfiles(Arrays.asList( StringUtils.split( mer.getProfiles(), "," ) ));
        }
        

        if ( mer.getAlternateSettings() != null ) {
            mavenRequest.setUserSettingsFile( mer.getAlternateSettings().getAbsolutePath() );
        } else {
            mavenRequest.setUserSettingsFile( new File( m2Home, "settings.xml" ).getAbsolutePath() );
        }

        if ( mer.getGlobalSettings() != null) {
            mavenRequest.setGlobalSettingsFile( mer.getGlobalSettings().getAbsolutePath() );
        } else {
            mavenRequest.setGlobalSettingsFile( new File( mer.getMavenHome(), "conf/settings.xml" ).getAbsolutePath() );
        }
        
        if (mer.getWorkspaceReader() != null ) {
            mavenRequest.setWorkspaceReader( mer.getWorkspaceReader() );
        }
        
        mavenRequest.setUpdateSnapshots(mer.isUpdateSnapshots());

        // TODO olamy check this sould be userProperties 
        mavenRequest.setSystemProperties(mer.getSystemProperties());

        if (mer.getTransferListener() != null) {
            if (debugMavenEmbedder) {
                mer.getListener().getLogger()
                    .println( "use transfertListener " + mer.getTransferListener().getClass().getName() );
            }
            mavenRequest.setTransferListener( mer.getTransferListener() );
        }

        mavenRequest.setMavenLoggerManager( new Slf4jLoggerManager() );

        //mavenRequest.setContainerClassPathScanning( PlexusConstants.SCANNING_OFF );

        //mavenRequest.setContainerComponentVisibility( PlexusConstants.GLOBAL_VISIBILITY );

        ClassLoader mavenEmbedderClassLoader = mer.getClassLoader();

        {// are we loading the right components.xml? (and not from Maven that's running Jetty, if we are running in "mvn hudson-dev:run" or "mvn hpi:run"?
            Enumeration<URL> e = mavenEmbedderClassLoader.getResources("META-INF/plexus/components.xml");
            while (e.hasMoreElements()) {
                URL url = e.nextElement();
                LOGGER.fine("components.xml from "+url);
            }
        }

        mavenRequest.setProcessPlugins( mer.isProcessPlugins() );
        mavenRequest.setResolveDependencies( mer.isResolveDependencies() );
        mavenRequest.setValidationLevel( mer.getValidationLevel() );
            
        // TODO check this MaskingClassLoader with maven 3 artifacts
        MavenEmbedder maven = new MavenEmbedder( mavenEmbedderClassLoader, mavenRequest );

        return maven;
    }


    /**
     * @deprecated MavenEmbedder has now a method to read all projects 
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
     * @throws MavenEmbedderException
     */
    public static void resolveModules( MavenEmbedder embedder, MavenProject project, String rel,
                                       Map<MavenProject, String> relativePathInfo, BuildListener listener,
                                       boolean nonRecursive )
        throws ProjectBuildingException, AbortException, MavenEmbedderException
    {

        File basedir = project.getFile().getParentFile();
        relativePathInfo.put( project, rel );

        List<MavenProject> modules = new ArrayList<MavenProject>();

        if ( !nonRecursive ) {
            for ( String modulePath : project.getModules()) {
                if ( Util.fixEmptyAndTrim( modulePath ) != null ) {
                    File moduleFile = new File( basedir, modulePath );
                    if ( moduleFile.exists() && moduleFile.isDirectory() ) {
                        moduleFile = new File( basedir, modulePath + "/pom.xml" );
                    }
                    if ( !moduleFile.exists() )
                        throw new AbortException( moduleFile + " is referenced from " + project.getFile()
                            + " but it doesn't exist" );

                    String relativePath = rel;
                    if ( relativePath.length() > 0 )
                        relativePath += '/';
                    relativePath += modulePath;

                    MavenProject child = embedder.readProject( moduleFile );
                    resolveModules( embedder, child, relativePath, relativePathInfo, listener, nonRecursive );
                    modules.add( child );
                }
            }
        }

        project.setCollectedProjects( modules );
    }

    public static boolean maven3orLater(String mavenVersion) {
        // null or empty so false !
        if (StringUtils.isBlank( mavenVersion )) {
            return false;
        }
        return new ComparableVersion(mavenVersion).compareTo( new ComparableVersion ("3.0") ) >= 0;
    }

    public static MavenVersion getMavenVersion(String mavenVersion){
        // we don't know so return maven 2
        if(StringUtils.isBlank( mavenVersion )){
            return MavenVersion.MAVEN_2;
        }

        ComparableVersion maven3_0 = new ComparableVersion("3.0");

        ComparableVersion maven2_0 = new ComparableVersion("2.0");

        ComparableVersion mavenCurrent = new ComparableVersion( mavenVersion );

        if (mavenCurrent.compareTo( maven2_0 ) >= 0 && mavenCurrent.compareTo( maven3_0 ) < 0){
            return MavenVersion.MAVEN_2;
        }

        ComparableVersion maven3_1_0 = new ComparableVersion("3.1.0");

        if (mavenCurrent.compareTo( maven3_0 ) >= 0 && mavenCurrent.compareTo( maven3_1_0 ) < 0){
            return MavenVersion.MAVEN_3_0_X;
        }

        return MavenVersion.MAVEN_3_1;

    }

    /**
     * support of {@link org.apache.maven.eventspy.EventSpy} only since 3.0.2
     * due to the current implementation will be supported only for maven 3.1.0
     * @param mavenVersion
     * @return
     */
    public static boolean supportEventSpy(String mavenVersion){
        // null or empty so false !
        if (StringUtils.isBlank( mavenVersion )) {
            return false;
        }
        return new ComparableVersion(mavenVersion).compareTo( new ComparableVersion ("3.1.0") ) >= 0;
    }

    public enum MavenVersion {
        MAVEN_2,MAVEN_3_0_X,MAVEN_3_1;
    }
    

    /**
     * If set to true, maximize the logging level of Maven embedder.
     */
    public static boolean debugMavenEmbedder = Boolean.getBoolean( "debugMavenEmbedder" );

    private static final Logger LOGGER = Logger.getLogger(MavenUtil.class.getName());
}
