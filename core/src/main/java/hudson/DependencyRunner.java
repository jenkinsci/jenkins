package hudson;

import hudson.model.AbstractProject;
import hudson.model.Hudson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a job on all projects in the order of dependencies
 */
public class DependencyRunner implements Runnable {
    AbstractProject currentProject;

    ProjectRunnable runnable;

    List<AbstractProject> polledProjects = new ArrayList<AbstractProject>();

    @SuppressWarnings("unchecked")
        public DependencyRunner(ProjectRunnable runnable) {
            this.runnable = runnable;
        }

    public void run() {
        Set<AbstractProject> topLevelProjects = new HashSet<AbstractProject>();
        // Get all top-level projects
        for (AbstractProject p : Hudson.getInstance().getAllItems(
                    AbstractProject.class))
            if (p.getUpstreamProjects().size() == 0)
                topLevelProjects.add(p);
        populate(topLevelProjects);
        for (AbstractProject p : polledProjects)
            runnable.run(p);
    }

    private void populate(Set<AbstractProject> projectList) {
        for (AbstractProject p : projectList) {
            if (polledProjects.contains(p))
                // Project will be readded at the queue, so that we always use
                // the longest path
                polledProjects.remove(p);

            polledProjects.add(p);

            // Add all downstream dependencies
            populate(new HashSet<AbstractProject>(p.getDownstreamProjects()));
        }
    }

    public interface ProjectRunnable {
        void run(AbstractProject p);
    }
}
