package hudson.tasks;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Project;

import java.util.Map;
import java.io.IOException;

/**
 * Pluggability point for performing pre/post actions for the build process.
 *
 * <p>
 * <b>STILL EXPERIMENTAL. SUBJECT TO CHANGE</b>
 *
 * <p>
 * This extension point enables a plugin to set up / tear down additional
 * services needed to perform a build, such as setting up local X display,
 * or launching and stopping application servers for testing.
 *
 * <p>
 * An instance of {@link BuildWrapper} is associated with a {@link Project}
 * with configuration information as its state. An instance is persisted
 * along with {@link Project}.
 *
 * <p>
 * The {@link #setUp(Build, BuildListener)} method is invoked for each build.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BuildWrapper implements ExtensionPoint, Describable<BuildWrapper> {
    /**
     * Represents the environment set up by {@link BuildWrapper#setUp(Build, BuildListener)}.
     *
     * <p>
     * It is expected that the subclasses of {@link BuildWrapper} extends this
     * class and implements its own semantics.
     */
    public class Environment {
        /**
         * Adds environmental variables for the builds to the given map.
         */
        public void buildEnvVars(Map<String,String> env) {
            // no-op by default
        }

        /**
         * Runs after the {@link Builder} completes, and performs a tear down.
         *
         * <p>
         * This method is invoked even when the build failed, so that the
         * clean up operation can be performed regardless of the build result
         * (for example, you'll want to stop application server even if a build
         * fails.)
         *
         * @return
         *      true if the build can continue, false if there was an error
         *      and the build needs to be aborted.
         */
        public boolean tearDown( Build build, BuildListener listener ) throws IOException {
            return true;
        }
    }

    /**
     * Runs before the {@link Builder} runs, and performs a set up.
     *
     * @return
     *      non-null if the build can continue, null if there was an error
     *      and the build needs to be aborted.
     */
    public Environment setUp( Build build, Launcher launcher, BuildListener listener ) throws IOException {
        return new Environment();
    }
}
