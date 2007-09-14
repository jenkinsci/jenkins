package hudson.maven;

/**
 * A part of {@link MavenBuildProxy} that's used internally
 * for aggregated build. Fired and consumed internally and
 * not exposed to plugins.
 *
 * @author Kohsuke Kawaguchi
 */
interface MavenBuildProxy2 extends MavenBuildProxy {
    /**
     * Notifies that the build has entered a module.
     */
    void start();

    /**
     * Notifies that the build has left a module.
     */
    void end();

    /**
     * Maven produces additional error message after the module build is done.
     * So to catch those messages, invoke this method on the last module that was built
     * after all the Maven processing is done, to append last messages to the console
     * output of the module.
     */
    void appendLastLog();
}
