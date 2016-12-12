/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Brian Westrich, Jean-Baptiste Quenot
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
package hudson;

import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import hudson.security.ACL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.logging.Logger;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

/**
 * Runs a job on all projects in the order of dependencies
 */
public class DependencyRunner implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(DependencyRunner.class.getName());
	
    ProjectRunnable runnable;

    List<AbstractProject> polledProjects = new ArrayList<AbstractProject>();

    public DependencyRunner(ProjectRunnable runnable) {
        this.runnable = runnable;
    }

    public void run() {
        SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
        try {
            Set<AbstractProject> topLevelProjects = new HashSet<AbstractProject>();
            // Get all top-level projects
            LOGGER.fine("assembling top level projects");
            for (AbstractProject p : Jenkins.getInstance().getAllItems(AbstractProject.class))
                if (p.getUpstreamProjects().size() == 0) {
                    LOGGER.fine("adding top level project " + p.getName());
                    topLevelProjects.add(p);
                } else {
                    LOGGER.fine("skipping project since not a top level project: " + p.getName());
                }
            populate(topLevelProjects);
            for (AbstractProject p : polledProjects) {
                    LOGGER.fine("running project in correct dependency order: " + p.getName());
                runnable.run(p);
            }
        } finally {
            SecurityContextHolder.setContext(oldContext);
        }
    }

    private void populate(Collection<? extends AbstractProject> projectList) {
        for (AbstractProject<?,?> p : projectList) {
            if (polledProjects.contains(p)) {
                // Project will be readded at the queue, so that we always use
                // the longest path
            	LOGGER.fine("removing project " + p.getName() + " for re-add");
                polledProjects.remove(p);
            }

            LOGGER.fine("adding project " + p.getName());
            polledProjects.add(p);

            // Add all downstream dependencies
            populate(p.getDownstreamProjects());
        }
    }

    public interface ProjectRunnable {
        void run(AbstractProject p);
    }
}
