package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TaskListener;

/**
 * Immutable object that represents revisions of the files in the repository,
 * used to represent the result of
 * {@linkplain SCM#poll(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState) a SCM polling}.
 *
 * <p>
 * This object is used so that the successive polling can compare the tip of the repository now vs
 * what it was when it was last polled. (Before 1.345, Hudson was only able to compare the tip
 * of the repository vs the state of the workspace, which resulted in a problem like HUDSON-2180.
 *
 * <p>
 * {@link SCMRevisionState} is persisted as an action to {@link AbstractBuild}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.345
 */
public abstract class SCMRevisionState implements Action {
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    /*
      I can't really make this comparable because comparing two revision states often requires
      non-trivial computation and conversations with the repository (mainly to figure out
      which changes are insignificant and which are not.)

      So instead, here we opt to a design where we tell SCM upfront about what we are comparing
      against (baseline), and have it give us the new state and degree of change in PollingResult.
     */

    public static SCMRevisionState NONE = new None();

    private static final class None extends SCMRevisionState {}
}
