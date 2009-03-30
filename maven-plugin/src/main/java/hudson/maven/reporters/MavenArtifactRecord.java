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
package hudson.maven.reporters;

import hudson.maven.AggregatableAction;
import hudson.maven.MavenAggregatedReport;
import hudson.maven.MavenBuild;
import hudson.maven.MavenEmbedder;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Action;
import hudson.model.TaskListener;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * {@link Action} that remembers {@link MavenArtifact artifact}s that are built.
 *
 * Defines the methods and UIs to do (delayed) deployment and installation. 
 *
 * @author Kohsuke Kawaguchi
 * @see MavenArtifactArchiver
 */
public class MavenArtifactRecord extends MavenAbstractArtifactRecord<MavenBuild> implements AggregatableAction {
    /**
     * The build to which this record belongs.
     */
    public final MavenBuild parent;

    /**
     * POM artifact.
     */
    public final MavenArtifact pomArtifact;

    /**
     * The main artifact (like jar or war, but could be anything.)
     *
     * If this is a POM module, the main artifact contains the same value as {@link #pomArtifact}.
     */
    public final MavenArtifact mainArtifact;

    /**
     * Attached artifacts. Can be empty but never null.
     */
    public final List<MavenArtifact> attachedArtifacts;

    public MavenArtifactRecord(MavenBuild parent, MavenArtifact pomArtifact, MavenArtifact mainArtifact, List<MavenArtifact> attachedArtifacts) {
        assert parent!=null;
        assert pomArtifact!=null;
        assert attachedArtifacts!=null;
        if(mainArtifact==null)  mainArtifact=pomArtifact;

        this.parent = parent;
        this.pomArtifact = pomArtifact;
        this.mainArtifact = mainArtifact;
        this.attachedArtifacts = attachedArtifacts;
    }

    public MavenBuild getBuild() {
        return parent;
    }

    public boolean isPOM() {
        return mainArtifact.isPOM();
    }

    public MavenAggregatedReport createAggregatedAction(MavenModuleSetBuild build, Map<MavenModule, List<MavenBuild>> moduleBuilds) {
        return new MavenAggregatedArtifactRecord(build);
    }

    @Override
    public void deploy(MavenEmbedder embedder, ArtifactRepository deploymentRepository, TaskListener listener) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactDeploymentException {
        ArtifactHandlerManager handlerManager = (ArtifactHandlerManager) embedder.lookup(ArtifactHandlerManager.ROLE);
        ArtifactDeployer deployer = (ArtifactDeployer) embedder.lookup(ArtifactDeployer.ROLE);
        ArtifactFactory factory = (ArtifactFactory) embedder.lookup(ArtifactFactory.ROLE);
        PrintStream logger = listener.getLogger();

        Artifact main = mainArtifact.toArtifact(handlerManager,factory,parent);
        if(!isPOM())
            main.addMetadata(new ProjectArtifactMetadata(main,pomArtifact.getFile(parent)));

        // deploy the main artifact. This also deploys the POM
        logger.println(Messages.MavenArtifact_DeployingMainArtifact(main.getFile().getName()));
        deployer.deploy(main.getFile(),main,deploymentRepository,embedder.getLocalRepository());

        for (MavenArtifact aa : attachedArtifacts) {
            logger.println(Messages.MavenArtifact_DeployingAttachedArtifact(main.getFile().getName()));
            Artifact a = aa.toArtifact(handlerManager,factory, parent);
            deployer.deploy(a.getFile(),a,deploymentRepository,embedder.getLocalRepository());
        }
    }

    /**
     * Installs the artifact to the local Maven repository.
     */
    public void install(MavenEmbedder embedder) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactInstallationException {
        ArtifactHandlerManager handlerManager = (ArtifactHandlerManager) embedder.lookup(ArtifactHandlerManager.ROLE);
        ArtifactInstaller installer = (ArtifactInstaller) embedder.lookup(ArtifactInstaller.class.getName());
        ArtifactFactory factory = (ArtifactFactory) embedder.lookup(ArtifactFactory.class.getName());

        Artifact main = mainArtifact.toArtifact(handlerManager,factory,parent);
        if(!isPOM())
            main.addMetadata(new ProjectArtifactMetadata(main,pomArtifact.getFile(parent)));
        installer.install(mainArtifact.getFile(parent),main,embedder.getLocalRepository());

        for (MavenArtifact aa : attachedArtifacts)
            installer.install(aa.getFile(parent),aa.toArtifact(handlerManager,factory,parent),embedder.getLocalRepository());
    }

    public void recordFingerprints() throws IOException {
        // record fingerprints
        if(mainArtifact!=null)
            mainArtifact.recordFingerprint(parent);
        for (MavenArtifact a : attachedArtifacts)
            a.recordFingerprint(parent);
    }
}
