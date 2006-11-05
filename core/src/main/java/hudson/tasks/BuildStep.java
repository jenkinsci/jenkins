package hudson.tasks;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.junit.JUnitResultArchiver;

import java.util.List;

/**
 * One step of the whole build process.
 *
 * @author Kohsuke Kawaguchi
 */
public interface BuildStep {

    /**
     * Runs before the build begins.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     */
    boolean prebuild( Build build, BuildListener listener );

    /**
     * Runs the step over the given build and reports the progress to the listener.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     */
    boolean perform(Build build, Launcher launcher, BuildListener listener);

    /**
     * Returns an action object if this {@link BuildStep} has an action
     * to contribute to a {@link Project}.
     *
     * @param project
     *      {@link Project} that owns this build step,
     *      since {@link BuildStep} object doesn't usually have this "parent" pointer.
     */
    Action getProjectAction(Project project);

    /**
     * List of all installed builders.
     *
     * Builders are invoked to perform the build itself.
     */
    public static final List<Descriptor<Builder>> BUILDERS = Descriptor.toList(
        Shell.DESCRIPTOR,
        BatchFile.DESCRIPTOR,
        Ant.DESCRIPTOR,
        Maven.DESCRIPTOR
    );

    /**
     * List of all installed publishers.
     *
     * Publishers are invoked after the build is completed, normally to perform
     * some post-actions on build results, such as sending notifications, collecting
     * results, etc.
     */
    public static final List<Descriptor<Publisher>> PUBLISHERS = Descriptor.toList(
        ArtifactArchiver.DESCRIPTOR,
        Fingerprinter.DESCRIPTOR,
        JavadocArchiver.DESCRIPTOR,
        JUnitResultArchiver.DESCRIPTOR,
        Mailer.DESCRIPTOR,
        BuildTrigger.DESCRIPTOR
    );
}
