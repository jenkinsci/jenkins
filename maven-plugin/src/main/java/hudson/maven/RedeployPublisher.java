/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman, Seiji Sogabe, Olivier Lamy
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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.reporters.MavenAbstractArtifactRecord;
import hudson.maven.reporters.MavenArtifactRecord;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import jenkins.model.Jenkins;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.cli.transfer.BatchModeMavenTransferListener;
import org.apache.maven.repository.Proxy;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link Publisher} for {@link MavenModuleSetBuild} to deploy artifacts
 * after a build is fully succeeded. 
 * <p>
 * Note that the - historical given - name of this class is quite misleading as this publisher usually does no REdeployment
 * of artifacts. If you're looking for the redeploy functionality in the Jenkins UI: that is implemented in
 * {@link MavenAbstractArtifactRecord#doRedeploy(String, String, boolean)}!
 *
 * @author Kohsuke Kawaguchi
 * @since 1.191
 */
public class RedeployPublisher extends Recorder {
    /**
     * Repository ID. This is matched up with <tt>~/.m2/settings.xml</tt> for authentication related information.
     */
    public final String id;
    /**
     * Repository URL to deploy artifacts to.
     */
    public final String url;
    public final boolean uniqueVersion;
    public final boolean evenIfUnstable;
    public final String releaseEnvVar;

    /**
     * For backward compatibility
     */
    @Deprecated
    public RedeployPublisher(String id, String url, boolean uniqueVersion) {
    	this(id, url, uniqueVersion, false, null);
    }
    
    /**
     * @since 1.347
     */
    @Deprecated
    public RedeployPublisher(String id, String url, boolean uniqueVersion, boolean evenIfUnstable) {
        this(id, url, uniqueVersion, evenIfUnstable, null);
    }
    
    @DataBoundConstructor
    public RedeployPublisher(String id, String url, boolean uniqueVersion, boolean evenIfUnstable, String releaseEnvVar) {
        this.id = id;
        this.url = Util.fixEmptyAndTrim(url);
        this.uniqueVersion = uniqueVersion;
        this.evenIfUnstable = evenIfUnstable;
        this.releaseEnvVar = Util.fixEmptyAndTrim(releaseEnvVar);
    }

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build.getResult().isWorseThan(getTreshold()))
            return true;    // build failed. Don't publish

        /**
         * Check if we should skip or not
         */
        if (releaseEnvVar != null) {
        	String envVarValue = build.getEnvironment(listener).get(releaseEnvVar);
        	if ("true".equals(envVarValue)) { // null or false are ignored
        		listener.getLogger().println("[INFO] Skipping deploying artifact as release build is in progress.");
        		return true; // skip the deploy
        	}
        }
        
        List<MavenAbstractArtifactRecord> mavenAbstractArtifactRecords = getActions(build, listener);
        if (mavenAbstractArtifactRecords == null || mavenAbstractArtifactRecords.isEmpty()) {
            listener.getLogger().println("[ERROR] No artifacts are recorded. Is this a Maven project?");
            build.setResult(Result.FAILURE);
            return true;
        }


        if(build instanceof MavenModuleSetBuild && ((MavenModuleSetBuild)build).getParent().isArchivingDisabled()){
          listener.getLogger().println("[ERROR] You cannot use the \"Deploy artifacts to Maven repository\" feature if you " +
                                           "disabled automatic artifact archiving");
          build.setResult(Result.FAILURE);
          return true;
        }

        long startupTime = System.currentTimeMillis();

        try {
            MavenEmbedder embedder = createEmbedder(listener, build);
            ArtifactRepositoryLayout layout =
                    (ArtifactRepositoryLayout) embedder.lookup(ArtifactRepositoryLayout.ROLE, "default");
            ArtifactRepositoryFactory factory =
                    (ArtifactRepositoryFactory) embedder.lookup(ArtifactRepositoryFactory.ROLE);
            ArtifactRepository artifactRepository = null;
            if (url != null) {
                // By default we try to get the repository definition from the job configuration
                artifactRepository = getDeploymentRepository(factory, layout, id, url);
            }
            for (MavenAbstractArtifactRecord mavenAbstractArtifactRecord : mavenAbstractArtifactRecords) {
                if (artifactRepository == null && mavenAbstractArtifactRecord instanceof MavenArtifactRecord) {
                    // If no repository definition is set on the job level we try to take it from the POM
                    MavenArtifactRecord mavenArtifactRecord = (MavenArtifactRecord) mavenAbstractArtifactRecord;
                    artifactRepository = getDeploymentRepository(factory, layout, mavenArtifactRecord.repositoryId, mavenArtifactRecord.repositoryUrl);
                }
                if (artifactRepository == null) {
                    listener.getLogger().println("[ERROR] No Repository settings defined in the job configuration or distributionManagement of the module.");
                    build.setResult(Result.FAILURE);
                    return true;
                }
                mavenAbstractArtifactRecord.deploy(embedder, artifactRepository, listener);
            }
            listener.getLogger().println("[INFO] Deployment done in " + Util.getTimeSpanString(System.currentTimeMillis() - startupTime));
            return true;
        } catch (MavenEmbedderException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (ComponentLookupException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (ArtifactDeploymentException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }
        // failed
        build.setResult(Result.FAILURE);
        listener.getLogger().println("[INFO] Deployment failed after " + Util.getTimeSpanString(System.currentTimeMillis() - startupTime));
        return true;
    }

    private ArtifactRepository getDeploymentRepository(ArtifactRepositoryFactory factory, ArtifactRepositoryLayout layout, String repositoryId, String repositoryUrl) throws ComponentLookupException {
        if (repositoryUrl == null) return null;
        final ArtifactRepository repository = factory.createDeploymentArtifactRepository(
                repositoryId, repositoryUrl, layout, uniqueVersion);
        return new WrappedArtifactRepository(repository, uniqueVersion);
    }

    /**
     * 
     * copy from MavenUtil but here we have to ignore localRepo path and setting as thoses paths comes
     * from the remote node and can not exist in master see http://issues.jenkins-ci.org/browse/JENKINS-8711
     * 
     */
    private MavenEmbedder createEmbedder(TaskListener listener, AbstractBuild<?,?> build) throws MavenEmbedderException, IOException, InterruptedException {
        MavenInstallation m=null;
        File settingsLoc = null, remoteGlobalSettingsFromConfig = null;
        String profiles = null;
        Properties systemProperties = null;
        String privateRepository = null;
        FilePath remoteSettingsFromConfig = null;
        
        File tmpSettings = File.createTempFile( "jenkins", "temp-settings.xml" );
        try {
            AbstractProject project = build.getProject();
            
            if (project instanceof MavenModuleSet) {
                MavenModuleSet mavenModuleSet = ((MavenModuleSet) project);
                profiles = mavenModuleSet.getProfiles();
                systemProperties = mavenModuleSet.getMavenProperties();
                
                // olamy see  
                // we have to take about the settings use for the project
                // order tru configuration 
                // TODO maybe in goals with -s,--settings last wins but not done in during pom parsing
                // or -Dmaven.repo.local
                // if not we must get ~/.m2/settings.xml then $M2_HOME/conf/settings.xml
                
                // TODO check if the remoteSettings has a localRepository configured and disabled it

                String altSettingsPath = SettingsProvider.getSettingsRemotePath(((MavenModuleSet) project).getSettings(), build, listener);
                String remoteGlobalSettingsPath = GlobalSettingsProvider.getSettingsRemotePath(((MavenModuleSet) project).getGlobalSettings(), build, listener);
                if(remoteGlobalSettingsPath != null){
                    remoteGlobalSettingsFromConfig = new File(remoteGlobalSettingsPath);
                }

                Node buildNode = build.getBuiltOn();
                
                if(buildNode == null) {
                    // assume that build was made on master
                    buildNode = Jenkins.getInstance();
                }

                if (StringUtils.isBlank( altSettingsPath ) ) {
                    // get userHome from the node where job has been executed
                    String remoteUserHome = build.getWorkspace().act( new GetUserHome() );
                    altSettingsPath = remoteUserHome + "/.m2/settings.xml";
                }
                
                // we copy this file in the master in a  temporary file 
                FilePath filePath = new FilePath( tmpSettings );
                FilePath remoteSettings = build.getWorkspace().child( altSettingsPath );
                if (!remoteSettings.exists()) {
                    // JENKINS-9084 we finally use $M2_HOME/conf/settings.xml as maven does
                    
                    String mavenHome = ((MavenModuleSet) project).getMaven().forNode(buildNode, listener ).getHome();
                    String settingsPath = mavenHome + "/conf/settings.xml";
                    remoteSettings = build.getWorkspace().child( settingsPath);
                }
                listener.getLogger().println( "Maven RedeployPublisher use remote " + (buildNode != null ? buildNode.getNodeName() : "local" )
                                              + " maven settings from : " + remoteSettings.getRemote() );
                remoteSettings.copyTo( filePath );
                settingsLoc = tmpSettings;
                
            }

            MavenEmbedderRequest mavenEmbedderRequest = new MavenEmbedderRequest(listener,
                                  m!=null?m.getHomeDir():null,
                                  profiles,
                                  systemProperties,
                                  privateRepository,
                                  settingsLoc );

            if (remoteGlobalSettingsFromConfig != null) {
                mavenEmbedderRequest.setGlobalSettings( remoteGlobalSettingsFromConfig );
            }

            mavenEmbedderRequest.setTransferListener(new BatchModeMavenTransferListener(listener.getLogger()));

            return MavenUtil.createEmbedder(mavenEmbedderRequest);
        } finally {
            if (tmpSettings != null) {
                tmpSettings.delete();
            }
        }
    }
    
    private static final class GetUserHome implements Callable<String,IOException> {
        private static final long serialVersionUID = -8755705771716056636L;

        public String call() throws IOException {
            return System.getProperty("user.home");
        }
    }
    
    
    /**
     * Obtains the {@link MavenModuleSetBuild} that we'll work on, or null.
     * <p>
     * This allows promoted-builds plugin to reuse the code for delayed deployment. 
     */
    protected MavenModuleSetBuild getMavenBuild(AbstractBuild<?, ?> build) {
        return (build instanceof MavenModuleSetBuild)
            ? (MavenModuleSetBuild) build
            : null;
    }
    
    protected List<MavenAbstractArtifactRecord> getActions(AbstractBuild<?, ?> build, BuildListener listener) {
        List<MavenAbstractArtifactRecord> actions = new ArrayList<MavenAbstractArtifactRecord>();
        MavenModuleSetBuild mavenBuild = getMavenBuild(build);
        if (mavenBuild == null) {
            return actions;
        }
        for (Entry<MavenModule, MavenBuild> e : mavenBuild.getModuleLastBuilds().entrySet()) {
            MavenAbstractArtifactRecord a = e.getValue().getAction( MavenAbstractArtifactRecord.class );
            if (a == null) {
                listener.getLogger().println("No artifacts are recorded for module" + e.getKey().getName() + ". Is this a Maven project?");
            } else {
                actions.add( a );    
            }
            
        }
        return actions;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    protected Result getTreshold() {
        if (evenIfUnstable) {
            return Result.UNSTABLE;
        } else {
            return Result.SUCCESS;
        }
    }
    
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
        }

        /**
         * @deprecated as of 1.290
         *      Use the default constructor.
         */
        protected DescriptorImpl(Class<? extends Publisher> clazz) {
            super(clazz);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return jobType==MavenModuleSet.class;
        }

        public RedeployPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(RedeployPublisher.class,formData);
        }

        public String getDisplayName() {
            return Messages.RedeployPublisher_getDisplayName();
        }

        public boolean showEvenIfUnstableOption() {
            // little hack to avoid showing this option on the redeploy action's screen
            return true;
        }

    }
    
    //---------------------------------------------
    
    @SuppressWarnings("deprecation") // as we're restricted to Maven 2.x API here, but compile against Maven 3.x we cannot avoid deprecations
    public static class WrappedArtifactRepository implements ArtifactRepository {
        private ArtifactRepository artifactRepository;
        private boolean uniqueVersion;
        public WrappedArtifactRepository (ArtifactRepository artifactRepository, boolean uniqueVersion)
        {
            this.artifactRepository = artifactRepository;
            this.uniqueVersion = uniqueVersion;
        }
        public String pathOf( Artifact artifact )
        {
            return artifactRepository.pathOf( artifact );
        }
        public String pathOfRemoteRepositoryMetadata( ArtifactMetadata artifactMetadata )
        {
            return artifactRepository.pathOfRemoteRepositoryMetadata( artifactMetadata );
        }
        public String pathOfLocalRepositoryMetadata( ArtifactMetadata metadata, ArtifactRepository repository )
        {
            return artifactRepository.pathOfLocalRepositoryMetadata( metadata, repository );
        }
        public String getUrl()
        {
            return artifactRepository.getUrl();
        }
        public void setUrl( String url )
        {
            artifactRepository.setUrl( url );
        }
        public String getBasedir()
        {
            return artifactRepository.getBasedir();
        }
        public String getProtocol()
        {
            return artifactRepository.getProtocol();
        }
        public String getId()
        {
            return artifactRepository.getId();
        }
        public void setId( String id )
        {
            artifactRepository.setId( id );
        }
        public ArtifactRepositoryPolicy getSnapshots()
        {
            return artifactRepository.getSnapshots();
        }
        public void setSnapshotUpdatePolicy( ArtifactRepositoryPolicy policy )
        {
            artifactRepository.setSnapshotUpdatePolicy( policy );
        }
        public ArtifactRepositoryPolicy getReleases()
        {
            return artifactRepository.getReleases();
        }
        public void setReleaseUpdatePolicy( ArtifactRepositoryPolicy policy )
        {
            artifactRepository.setReleaseUpdatePolicy( policy );
        }
        public ArtifactRepositoryLayout getLayout()
        {
            return artifactRepository.getLayout();
        }
        public void setLayout( ArtifactRepositoryLayout layout )
        {
            artifactRepository.setLayout( layout );
        }
        public String getKey()
        {
            return artifactRepository.getKey();
        }
        public boolean isUniqueVersion()
        {
            return this.uniqueVersion;
        }
        
        public void setUniqueVersion(boolean uniqueVersion) {
            this.uniqueVersion = uniqueVersion;
        }
        
        public boolean isBlacklisted()
        {
            return artifactRepository.isBlacklisted();
        }
        public void setBlacklisted( boolean blackListed )
        {
            artifactRepository.setBlacklisted( blackListed );
        }
        public Artifact find( Artifact artifact )
        {
            return artifactRepository.find( artifact );
        }
        public List<String> findVersions( Artifact artifact )
        {
            return artifactRepository.findVersions( artifact );
        }
        public boolean isProjectAware()
        {
            return artifactRepository.isProjectAware();
        }
        public void setAuthentication( Authentication authentication )
        {
            artifactRepository.setAuthentication( authentication );
        }
        public Authentication getAuthentication()
        {
            return artifactRepository.getAuthentication();
        }
        public void setProxy( Proxy proxy )
        {
            artifactRepository.setProxy( proxy );
        }
        public Proxy getProxy()
        {
            return artifactRepository.getProxy();
        }
        public List<ArtifactRepository> getMirroredRepositories()
        {
            return Collections.emptyList();
        }
        public void setMirroredRepositories( List<ArtifactRepository> arg0 )
        {
            // noop            
        }
    }    
    
}
