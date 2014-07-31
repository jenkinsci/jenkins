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
import jenkins.model.Jenkins;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.DependencyGraph.Dependency;
import hudson.tasks.Maven.ProjectWithMaven;
import hudson.triggers.Trigger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Common part between {@link MavenModule} and {@link MavenModuleSet}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMavenProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends AbstractProject<P,R>
    implements ProjectWithMaven {

	protected static class MavenModuleDependency extends Dependency {

    	public MavenModuleDependency(AbstractMavenProject<?,?> upstream,
    			AbstractProject<?,?> downstream) {
    		super(upstream, downstream);
    	}

    	@Override
    	public boolean shouldTriggerBuild(AbstractBuild build,
    			TaskListener listener, List<Action> actions) {
    		/**
    		 * Schedules all the downstream builds.
    		 * Returns immediately if build result doesn't meet the required level
    		 * (as specified by {@link BuildTrigger}, or {@link Result#SUCCESS} if none).
    		 *
    		 * @param listener
    		 *      Where the progress reports go.
    		 */
    		if (build.getResult().isWorseThan(Result.SUCCESS)) return false;
    		// trigger dependency builds
    		AbstractProject<?,?> downstreamProject = getDownstreamProject();
    		if(AbstractMavenBuild.debug)
    			listener.getLogger().println("Considering whether to trigger "+downstreamProject+" or not");

    		// if the downstream module depends on multiple modules,
    		// only trigger them when all the upstream dependencies are updated.
    		boolean trigger = true;

    		// Check to see if any of its upstream dependencies are already building or in queue.
    		AbstractMavenProject<?,?> parent = (AbstractMavenProject<?,?>) getUpstreamProject();
    		if (areUpstreamsBuilding(downstreamProject, parent)) {
    			if(AbstractMavenBuild.debug)
    				listener.getLogger().println(" -> No, because downstream has dependencies already building or in queue");
    			trigger = false;
    		}
    		// Check to see if any of its upstream dependencies are in this list of downstream projects.
    		else if (inDownstreamProjects(downstreamProject)) {
    			if(AbstractMavenBuild.debug)
    				listener.getLogger().println(" -> No, because downstream has dependencies in the downstream projects list");
    			trigger = false;
    		}
    		else {
    			AbstractBuild<?,?> dlb = downstreamProject.getLastBuild(); // can be null.
    			for (AbstractMavenProject up : Util.filter(downstreamProject.getUpstreamProjects(),AbstractMavenProject.class)) {
    				Run ulb;
    				if(up==parent) {
    					// the current build itself is not registered as lastSuccessfulBuild
    					// at this point, so we have to take that into account. ugly.
    					if(build.getResult()==null || !build.getResult().isWorseThan(Result.UNSTABLE))
    						ulb = build;
    					else
    						ulb = up.getLastSuccessfulBuild();
    				} else
    					ulb = up.getLastSuccessfulBuild();
    				if(ulb==null) {
    					// if no usable build is available from the upstream,
    					// then we have to wait at least until this build is ready
    					if(AbstractMavenBuild.debug)
    						listener.getLogger().println(" -> No, because another upstream "+up+" for "+downstreamProject+" has no successful build");
    					trigger = false;
    					break;
    				}

    				// if no record of the relationship in the last build
    				// is available, we'll just have to assume that the condition
    				// for the new build is met, or else no build will be fired forever.
    				if(dlb==null)   continue;
    				int n = dlb.getUpstreamRelationship(up);
    				if(n==-1)   continue;

    				assert ulb.getNumber()>=n;
    			}
    		}			    
    		return trigger;
    	}

		/**
		 * Determines whether any of the upstream project are either
		 * building or in the queue.
		 *
		 * This means eventually there will be an automatic triggering of
		 * the given project (provided that all builds went smoothly.)
		 *
		 * @param downstreamProject
		 *      The AbstractProject we want to build.
		 * @param excludeProject
		 *      An AbstractProject to exclude - if we see this in the transitive
		 *      dependencies, we're not going to bother checking to see if it's
		 *      building. For example, pass the current parent project to be sure
		 *      that it will be ignored when looking for building dependencies.
		 * @return
		 *      True if any upstream projects are building or in queue, false otherwise.
		 */
		@SuppressWarnings("rawtypes")
        private boolean areUpstreamsBuilding(AbstractProject<?,?> downstreamProject,
				AbstractProject<?,?> excludeProject) {
			DependencyGraph graph = Jenkins.getInstance().getDependencyGraph();
			Set<AbstractProject> tups = graph.getTransitiveUpstream(downstreamProject);
			for (AbstractProject tup : tups) {
				if(tup!=excludeProject && (tup.isBuilding() || tup.isInQueue()))
					return true;
			}
			return false;
		}

		private boolean inDownstreamProjects(AbstractProject<?,?> downstreamProject) {
			DependencyGraph graph = Jenkins.getInstance().getDependencyGraph();
			Set<AbstractProject> tups = graph.getTransitiveUpstream(downstreamProject);
		
			for (AbstractProject tup : tups) {
				List<AbstractProject<?,?>> downstreamProjects = getUpstreamProject().getDownstreamProjects();
				for (AbstractProject<?,?> dp : downstreamProjects) {
					if(dp!=getUpstreamProject() && dp!=downstreamProject && dp==tup) 
						return true;
				}
			}
			return false;
		}
    }

    protected AbstractMavenProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        // if we just pick up the project actions from the last build,
        // and if the last build failed very early, then the reports that
        // kick in later (like test results) won't be displayed.
        // so pick up last successful build, too.
        Set<Class> added = new HashSet<Class>();
        addTransientActionsFromBuild(getLastBuild(),r,added);
        addTransientActionsFromBuild(getLastSuccessfulBuild(),r,added);

        for (Trigger<?> trigger : triggers())
            r.addAll(trigger.getProjectActions());

        return r;
    }

    /**
     * @param collection
     *      Add the transient actions to this collection.
     */
    protected abstract void addTransientActionsFromBuild(R lastBuild, List<Action> collection, Set<Class> added);
    
}
