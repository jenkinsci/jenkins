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

import hudson.maven.MavenAggregatedReport;
import hudson.maven.MavenBuild;
import hudson.maven.MavenEmbedder;
import hudson.maven.MavenEmbedderException;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Action;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Redeploy action for the entire {@link MavenModuleSetBuild}.
 * 
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
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

    /**
     * {@link MavenArtifactRecord}s of every module build contributed to {@link #parent}.
     */
    @Exported(inline=true)
    public List<MavenArtifactRecord> getModuleRecords() {
        List<MavenArtifactRecord> r = new ArrayList<MavenArtifactRecord>();
        for (MavenBuild build : parent.getModuleLastBuilds().values()) {
            MavenArtifactRecord mar = build.getAction(MavenArtifactRecord.class);
            if(mar!=null)   r.add(mar);
        }
        return r;
    }

    public void deploy(MavenEmbedder embedder, ArtifactRepository deploymentRepository, TaskListener listener) throws MavenEmbedderException, IOException, ComponentLookupException, ArtifactDeploymentException {
        if(debug)
            listener.getLogger().println("Redeploying artifacts of "+parent+" timestamp="+parent.getTimestamp());

        for (MavenArtifactRecord mar : getModuleRecords()) {
            if(debug)
                listener.getLogger().println("Deploying module: "+mar.parent+" timestamp="+mar.parent.getTimestamp());
            mar.deploy(embedder,deploymentRepository,listener);
        }
    }
}
