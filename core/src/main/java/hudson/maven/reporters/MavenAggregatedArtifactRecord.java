package hudson.maven.reporters;

import hudson.maven.MavenAggregatedReport;
import hudson.maven.MavenBuild;
import hudson.maven.MavenEmbedder;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Action;
import hudson.model.TaskListener;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.embedder.MavenEmbedderException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Redeploy action for the entire {@link MavenModuleSetBuild}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenAggregatedArtifactRecord extends MavenAbstractArtifactRecord<MavenModuleSetBuild> implements MavenAggregatedReport {
    public final MavenModuleSetBuild parent;

    public MavenAggregatedArtifactRecord(MavenModuleSetBuild build) {
        this.parent = build;
    }

    public MavenModuleSetBuild getBuild() {
        return parent;
    }

    public void update(Map<MavenModule,List<MavenBuild>> moduleBuilds, MavenBuild newBuild) {
    }

    public Class<MavenArtifactRecord> getIndividualActionType() {
        return MavenArtifactRecord.class;
    }

    public Action getProjectAction(MavenModuleSet moduleSet) {
        return null;
    }

    public void deploy(MavenEmbedder embedder, ArtifactRepository deploymentRepository, TaskListener listener) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactDeploymentException {
        if(debug)
            listener.getLogger().println("Redeploying artifacts of "+parent+" timestamp="+parent.getTimestamp());

        for (MavenBuild build : parent.getModuleLastBuilds().values()) {
            MavenArtifactRecord mar = build.getAction(MavenArtifactRecord.class);
            if(mar!=null) {
                if(debug)
                    listener.getLogger().println("Deploying module: "+build+" timestamp="+build.getTimestamp());
                mar.deploy(embedder,deploymentRepository,listener);
            }
        }
    }
}
