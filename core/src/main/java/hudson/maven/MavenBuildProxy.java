package hudson.maven;

import hudson.FilePath;
import hudson.model.Result;

import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;
import java.util.List;

/**
 * Remoting proxy interface for {@link MavenReporter}s to talk to {@link MavenBuild}
 * during the build.
 *
 * @author Kohsuke Kawaguchi
 */
public interface MavenBuildProxy {
    /**
     * Executes the given {@link BuildCallable} on the master, where one
     * has access to {@link MavenBuild} and all the other Hudson objects.
     *
     * <p>
     * The parameter, return value, and exception are all transfered by using
     * Java serialization.
     *
     * @return
     *      the value that {@link BuildCallable} returned.
     * @throws T
     *      if {@link BuildCallable} throws this exception.
     * @throws IOException
     *      if the remoting failed.
     * @throws InterruptedException
     *      if the remote execution is aborted.
     */
    <V,T extends Throwable> V execute( BuildCallable<V,T> program ) throws T, IOException, InterruptedException;

    /**
     * Root directory of the build.
     *
     * @see MavenBuild#getRootDir() 
     */
    FilePath getRootDir();

    /**
     * Root directory of the parent of this build.
     */
    FilePath getProjectRootDir();

    /**
     * @see MavenBuild#getArtifactsDir()
     */
    FilePath getArtifactsDir();

    /**
     * @see MavenBuild#setResult(Result)
     */
    void setResult(Result result);

    /**
     * @see MavenBuild#getTimestamp()
     */
    Calendar getTimestamp();

    /**
     * Nominates that the reporter will contribute a project action
     * for this build by using {@link MavenReporter#getProjectAction(MavenModule)}.
     *
     * <p>
     * The specified {@link MavenReporter} object will be transfered to the master
     * and will become a persisted part of the {@link MavenBuild}. 
     */
    void registerAsProjectAction(MavenReporter reporter);

    /**
     * Called at the end of the build to record what mojos are executed.
     */
    void setExecutedMojos(List<ExecutedMojo> executedMojos);

    public interface BuildCallable<V,T extends Throwable> extends Serializable {
        /**
         * Performs computation and returns the result,
         * or throws some exception.
         *
         * @throws InterruptedException
         *      if the processing is interrupted in the middle. Exception will be
         *      propagated to the caller.
         * @throws IOException
         *      if the program simply wishes to propage the exception, it may throw
         *      {@link IOException}.
         */
        V call(MavenBuild build) throws T, IOException, InterruptedException;
    }
}
