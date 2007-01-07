package hudson.maven;

import hudson.model.Describable;
import hudson.model.BuildListener;
import hudson.model.Action;
import hudson.model.Project;
import hudson.ExtensionPoint;
import hudson.tasks.BuildStep;

import java.io.IOException;

/**
 * Listens to the build execution of {@link MavenBuild},
 * and normally records some information and exposes thoses
 * in {@link MavenBuild} later.
 *
 * <p>
 * This is the {@link MavenBuild} equivalent of {@link BuildStep}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MavenListener implements Describable<MavenListener>, ExtensionPoint {
    /**
     * Called before the actual maven2 execution begins.
     *
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     * @throws InterruptedException
     *      If the build is interrupted by the user (in an attempt to abort the build.)
     *      Normally the {@link MavenListener} implementations may simply forward the exception
     *      it got from its lower-level functions.
     * @throws IOException
     *      If the implementation wants to abort the processing when an {@link IOException}
     *      happens, it can simply propagate the exception to the caller. This will cause
     *      the build to fail, with the default error message.
     *      Implementations are encouraged to catch {@link IOException} on its own to
     *      provide a better error message, if it can do so, so that users have better
     *      understanding on why it failed.
     */
    public boolean prebuild(MavenBuild build, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called after the actual maven2 execution completed.
     *
     * @return
     *      See {@link #prebuild(MavenBuild, BuildListener)}
     * @throws InterruptedException
     *      See {@link #prebuild(MavenBuild, BuildListener)}
     * @throws IOException
     *      See {@link #prebuild(MavenBuild, BuildListener)}
     */
    public boolean postbuild(MavenBuild build, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Equivalent of {@link BuildStep#getProjectAction(Project)}
     * for {@link MavenListener}.
     */
    public Action getProjectAction(MavenJob project) {
        return null;
    }
}
