package hudson.maven;

import hudson.FilePath;

import java.io.Serializable;
import java.io.IOException;

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
     * @see MavenBuild#getArtifactsDir()
     */
    FilePath getArtifactsDir();

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
