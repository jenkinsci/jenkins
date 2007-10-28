package hudson.tasks;

import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Action;
import hudson.model.Project;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.Launcher;

import java.io.IOException;

/**
 * Provides compatibility with {@link BuildStep} before 1.150
 * so that old plugin binaries can continue to function with new Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.150
 */
public abstract class BuildStepCompatibilityLayer implements BuildStep {
//
// new definitions >= 1.150
//
    public boolean prebuild(AbstractBuild<?,?> build, BuildListener listener) {
        if (build instanceof Build)
            return prebuild((Build)build,listener);
        else
            return true;
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build instanceof Build)
            return perform((Build)build,launcher,listener);
        else
            return true;
    }

    public Action getProjectAction(AbstractProject<?, ?> project) {
        if (project instanceof Project)
            return getProjectAction((Project) project);
        else
            return null;
    }
//
// old definitions < 1.150
//
    /**
     * @deprecated
     *      Use {@link #prebuild(AbstractBuild, BuildListener)} instead.
     */
    public boolean prebuild(Build<?,?> build, BuildListener listener) {
        return true;
    }

    /**
     * @deprecated
     *      Use {@link #perform(AbstractBuild, Launcher, BuildListener)} instead.
     */
    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      Use {@link #getProjectAction(AbstractProject)} instead.
     */
    public Action getProjectAction(Project<?,?> project) {
        return null;
    }
}
