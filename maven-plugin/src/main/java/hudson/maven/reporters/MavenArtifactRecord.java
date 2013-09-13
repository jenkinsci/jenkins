/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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
package hudson.maven.reporters;

import hudson.maven.*;
import hudson.maven.RedeployPublisher.WrappedArtifactRepository;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.TaskListener;
import java.io.File;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link Action} that remembers {@link MavenArtifact artifact}s that are built.
 *
 * Defines the methods and UIs to do (delayed) deployment and installation. 
 *
 * @author Kohsuke Kawaguchi
 * @see MavenArtifactArchiver
 */
@ExportedBean
public class MavenArtifactRecord extends MavenAbstractArtifactRecord<MavenBuild> implements AggregatableAction {
    /**
     * The build to which this record belongs.
     */
    @Exported
    public final MavenBuild parent;

    /**
     * POM artifact.
     */
    @Exported(inline=true)
    public final MavenArtifact pomArtifact;

    /**
     * The main artifact (like jar or war, but could be anything.)
     *
     * If this is a POM module, the main artifact contains the same value as {@link #pomArtifact}.
     */
    @Exported(inline=true)
    public final MavenArtifact mainArtifact;

    /**
     * Attached artifacts. Can be empty but never null.
     */
    @Exported(inline=true)
    public final List<MavenArtifact> attachedArtifacts;

    /**
     * The repository identifier (matching maven settings) used for credentials to deploy artifacts
     */
    public final String repositoryId;

  /**
   * The repository URL used for credentials to deploy artifacts
   */
    public final String repositoryUrl;

    @Deprecated
    public MavenArtifactRecord(MavenBuild parent, MavenArtifact pomArtifact, MavenArtifact mainArtifact, List<MavenArtifact> attachedArtifacts) {
        this(parent, pomArtifact, mainArtifact, attachedArtifacts, null, null);
    }

    public MavenArtifactRecord(MavenBuild parent, MavenArtifact pomArtifact, MavenArtifact mainArtifact,
                               List<MavenArtifact> attachedArtifacts, String repositoryUrl, String repositoryId) {
        assert parent != null;
        assert pomArtifact != null;
        assert attachedArtifacts != null;
        if (mainArtifact == null) mainArtifact = pomArtifact;

        this.parent = parent;
        this.pomArtifact = pomArtifact;
        this.mainArtifact = mainArtifact;
        this.attachedArtifacts = attachedArtifacts;
        this.repositoryUrl = repositoryUrl;
        this.repositoryId = repositoryId;
    }

    public MavenBuild getBuild() {
        return parent;
    }

    /**
     * Returns the URL of this record relative to the context root of the application.
     *
     * @see AbstractItem#getUrl() for how to implement this.
     *
     * @return
     *      URL that ends with '/'.
     */
    public String getUrl() {
        return parent.getUrl()+"mavenArtifacts/";
    }

    /**
     * Obtains the absolute URL to this build.
     *
     * @deprecated
     *      This method shall <b>NEVER</b> be used during HTML page rendering, as it's too easy for
     *      misconfiguration to break this value, with network set up like Apache reverse proxy.
     *      This method is only intended for the remote API clients who cannot resolve relative references.
     */
    @Exported(visibility=2,name="url")
    public String getAbsoluteUrl() {
        return parent.getAbsoluteUrl()+"mavenArtifacts/";
    }

    public boolean isPOM() {
        return mainArtifact.isPOM();
    }

    public MavenAggregatedArtifactRecord createAggregatedAction(MavenModuleSetBuild build, Map<MavenModule, List<MavenBuild>> moduleBuilds) {
        return new MavenAggregatedArtifactRecord(build);
    }

    @Override
    public void deploy(MavenEmbedder embedder, ArtifactRepository deploymentRepository, TaskListener listener) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactDeploymentException {
        ArtifactHandlerManager handlerManager = embedder.lookup(ArtifactHandlerManager.class);

        ArtifactFactory artifactFactory = embedder.lookup(ArtifactFactory.class);
        PrintStream logger = listener.getLogger();
        boolean maven3orLater = MavenUtil.maven3orLater(parent.getModuleSetBuild().getMavenVersionUsed());
        boolean uniqueVersion = true;
        if (!deploymentRepository.isUniqueVersion()) {
            if (maven3orLater) {
                logger.println("[ERROR] uniqueVersion == false is not anymore supported in maven 3");
            } else {
                ((WrappedArtifactRepository) deploymentRepository).setUniqueVersion(false);
                uniqueVersion = false;
            }
        } else {
            ((WrappedArtifactRepository) deploymentRepository).setUniqueVersion(true);
        }
        Artifact main = mainArtifact.toArtifact(handlerManager, artifactFactory, parent);
        File pomFile = null;
        if (!isPOM()) {
            pomFile = pomArtifact.getFile(parent);
            main.addMetadata(new ProjectArtifactMetadata(main, pomFile));
        }
        if (main.getType().equals("maven-plugin")) {
            GroupRepositoryMetadata metadata = new GroupRepositoryMetadata(main.getGroupId());
            String goalPrefix = PluginDescriptor.getGoalPrefixFromArtifactId(main.getArtifactId());
            metadata.addPluginMapping(goalPrefix, main.getArtifactId(), null);
            main.addMetadata(metadata);
        }

        ArtifactDeployer deployer = embedder.lookup(ArtifactDeployer.class, uniqueVersion ? "default" : "maven2");
        logger.println(
                "[INFO] Deployment in " + deploymentRepository.getUrl() + " (id=" + deploymentRepository.getId() + ",uniqueVersion=" + deploymentRepository.isUniqueVersion()+")");

        // deploy the main artifact. This also deploys the POM
        logger.println(Messages.MavenArtifact_DeployingMainArtifact(main.getFile().getName()));
        deployer.deploy(main.getFile(), main, deploymentRepository, embedder.getLocalRepository());
        main.getFile().delete();
        if (pomFile != null) {
            pomFile.delete();
        }

        for (MavenArtifact aa : attachedArtifacts) {
            Artifact a = aa.toArtifact(handlerManager, artifactFactory, parent);
            logger.println(Messages.MavenArtifact_DeployingMainArtifact(a.getFile().getName()));
            deployer.deploy(a.getFile(), a, deploymentRepository, embedder.getLocalRepository());
            a.getFile().delete();
        }
    }

    public void recordFingerprints() throws IOException {
        // record fingerprints
        if(mainArtifact!=null)
            mainArtifact.recordFingerprint(parent);
        for (MavenArtifact a : attachedArtifacts)
            a.recordFingerprint(parent);
    }
}
