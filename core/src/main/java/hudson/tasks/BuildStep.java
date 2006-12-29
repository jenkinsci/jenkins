package hudson.tasks;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.junit.JUnitResultArchiver;

import java.util.List;
import java.io.IOException;

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
     *
     * @throws InterruptedException
     *      If the build is interrupted by the user (in an attempt to abort the build.)
     *      Normally the {@link BuildStep} implementations may simply forward the exception
     *      it got from its lower-level functions.
     * @throws IOException
     *      If the implementation wants to abort the processing when an {@link IOException}
     *      happens, it can simply propagate the exception to the caller. This will cause
     *      the build to fail, with the default error message.
     *      Implementations are encouraged to catch {@link IOException} on its own to
     *      provide a better error message, if it can do so, so that users have better
     *      understanding on why it failed.
     */
    boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException;

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
        JUnitResultArchiver.DescriptorImpl.DESCRIPTOR,
        BuildTrigger.DESCRIPTOR,
        Mailer.DESCRIPTOR
    );
}
