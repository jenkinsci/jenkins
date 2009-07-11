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

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Publisher;
import hudson.tasks.Maven.ProjectWithMaven;
import hudson.triggers.Trigger;
import hudson.tasks.Maven.ProjectWithMaven;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Common part between {@link MavenModule} and {@link MavenModuleSet}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMavenProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends AbstractProject<P,R>
    implements ProjectWithMaven {
    protected AbstractMavenProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected void updateTransientActions() {
        synchronized(transientActions) {
            super.updateTransientActions();

            // if we just pick up the project actions from the last build,
            // and if the last build failed very early, then the reports that
            // kick in later (like test results) won't be displayed.
            // so pick up last successful build, too.
            Set<Class> added = new HashSet<Class>();
            addTransientActionsFromBuild(getLastBuild(),added);
            addTransientActionsFromBuild(getLastSuccessfulBuild(),added);

            for (Trigger trigger : triggers) {
                Action a = trigger.getProjectAction();
                if(a!=null)
                    transientActions.add(a);
            }
        }
    }
    
    private boolean blockBuildWhenUpstreamBuilding = true;
    
    protected abstract void addTransientActionsFromBuild(R lastBuild, Set<Class> added);
    
    @Override
    public boolean isBuildBlocked() {
        boolean blocked = super.isBuildBlocked();
        if (!blocked && blockBuildWhenUpstreamBuilding) {
            DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
            AbstractProject bup = getBuildingUpstream();
            if(bup!=null) {
                return true;
            }
        }
        return blocked;
    }
    
    public String getWhyBlocked() {
    	if (super.isBuildBlocked()) {
            return super.getWhyBlocked();
    	} else {
            AbstractProject bup = getBuildingUpstream();
            String projectName = "";
            if(bup!=null) {
                projectName = bup.getName();
            }
            return "Upstream project is building: " + projectName;
    	}
    }
    
    /**
     * Returns the project if any of the upstream project (or itself) is either
     * building or is in the queue.
     * <p>
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     */
    private AbstractProject getBuildingUpstream() {
    	DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
        Set<AbstractProject> tups = graph.getTransitiveUpstream(this);
        tups.add(this);
        for (AbstractProject tup : tups) {
            if(tup!=this && (tup.isBuilding() || tup.isInQueue()))
                return tup;
        }
        return null;
    }
    
    public boolean blockBuildWhenUpstreamBuilding() {
    	return blockBuildWhenUpstreamBuilding;
    }
    
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);
        blockBuildWhenUpstreamBuilding = req.hasParameter("maven.blockBuildWhenUpstreamBuilding");
    }
}
