package hudson.model;

import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.util.Map;

/**
 * Represents the environment set up by
 * {@link NodeProperty#setUp(Build,Launcher,BuildListener)}.
 * 
 * <p>
 * It is expected that the subclasses of {@link NodeProperty} extends this class
 * and implements its own semantics.
 */
public abstract class Environment {
	/**
	 * Adds environmental variables for the builds to the given map.
	 * 
	 * <p>
	 * If the {@link Environment} object wants to pass in information to the
	 * build that runs, it can do so by exporting additional environment
	 * variables to the map.
	 * 
	 * <p>
	 * When this method is invoked, the map already contains the current
	 * "planned export" list.
	 * 
	 * @param env
	 *            never null.
	 */
	public void buildEnvVars(Map<String, String> env) {
		// no-op by default
	}

	/**
	 * Runs after the {@link Builder} completes, and performs a tear down.
	 * 
	 * <p>
	 * This method is invoked even when the build failed, so that the clean up
	 * operation can be performed regardless of the build result (for example,
	 * you'll want to stop application server even if a build fails.)
	 * 
	 * @param build
	 *            The same {@link Build} object given to the set up method.
	 * @param listener
	 *            The same {@link BuildListener} object given to the set up
	 *            method.
	 * @return true if the build can continue, false if there was an error and
	 *         the build needs to be aborted.
	 * @throws IOException
	 *             terminates the build abnormally. Hudson will handle the
	 *             exception and reports a nice error message.
	 */
	public boolean tearDown(AbstractBuild build, BuildListener listener)
			throws IOException, InterruptedException {
		return true;
	}
	
	public static Environment create(final Map<String,String> envVars) {
		return new Environment() {
			@Override
			public void buildEnvVars(Map<String, String> env) {
				env.putAll(envVars);
			}
		};
	}
	
}