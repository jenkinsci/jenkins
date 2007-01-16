package hudson.maven;

import hudson.model.Describable;
import hudson.model.BuildListener;
import hudson.model.Action;
import hudson.model.Project;
import hudson.ExtensionPoint;
import hudson.tasks.BuildStep;

import java.io.IOException;

import org.apache.maven.project.MavenProject;

/**
 * Listens to the build execution of {@link MavenBuild},
 * and normally records some information and exposes thoses
 * in {@link MavenBuild} later.
 *
 * <p>
 * TODO: talk about two nodes involved
 * Because builds may happen on a remote slave node, {@link MavenReporter}
 * implementation needs  ...
 *
 * <p>
 * This is the {@link MavenBuild} equivalent of {@link BuildStep}.
 *
 * @author Kohsuke Kawaguchi
 * @see MavenReporters
 */
public abstract class MavenReporter implements Describable<MavenReporter>, ExtensionPoint {
    /**
     * Called before the actual maven2 execution begins.
     *
     * @param pom
     *      Represents the POM to be executed.
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     * @throws InterruptedException
     *      If the build is interrupted by the user (in an attempt to abort the build.)
     *      Normally the {@link MavenReporter} implementations may simply forward the exception
     *      it got from its lower-level functions.
     * @throws IOException
     *      If the implementation wants to abort the processing when an {@link IOException}
     *      happens, it can simply propagate the exception to the caller. This will cause
     *      the build to fail, with the default error message.
     *      Implementations are encouraged to catch {@link IOException} on its own to
     *      provide a better error message, if it can do so, so that users have better
     *      understanding on why it failed.
     */
    public boolean preBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called when the build enters a next {@link MavenProject}.
     *
     * <p>
     * When the current build is a multi-module reactor build, every time the build
     * moves on to the next module, this method will be invoked.
     *
     * <p>
     * Note that as of Maven 2.0.4, Maven does not perform any smart optimization
     * on the order of goal executions. Therefore, the same module might be entered more than
     * once during the build.
     *
     * @return
     *      See {@link #preBuild}
     * @throws InterruptedException
     *      See {@link #preBuild}
     * @throws IOException
     *      See {@link #preBuild}
     */
    public boolean enterModule(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called when the build leaves the current {@link MavenProject}.
     *
     * @see #enterModule
     */
    public boolean leaveModule(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called before execution of a single mojo.
     * 
     * @return
     *      See {@link #preBuild}
     * @throws InterruptedException
     *      See {@link #preBuild}
     * @throws IOException
     *      See {@link #preBuild}
     */
    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called after execution of a single mojo.
     * <p>
     * See {@link #preExecute} for the contract.
     */
    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called after the actual maven2 execution completed.
     *
     * @return
     *      See {@link #preBuild}
     * @throws InterruptedException
     *      See {@link #preBuild}
     * @throws IOException
     *      See {@link #preBuild}
     */
    public boolean postBuild(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Equivalent of {@link BuildStep#getProjectAction(Project)}
     * for {@link MavenReporter}.
     */
    public Action getProjectAction(MavenJob project) {
        return null;
    }
}
