package hudson.maven;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ItemGroup;
import hudson.triggers.Trigger;

import java.util.HashSet;
import java.util.Set;

/**
 * Common part between {@link MavenModule} and {@link MavenModuleSet}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMavenProject<P extends AbstractMavenProject<P,R>,R extends AbstractBuild<P,R>> extends AbstractProject<P,R>  {
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

    protected abstract void addTransientActionsFromBuild(R lastBuild, Set<Class> added);
}
