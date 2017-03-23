package jenkins.model;

import hudson.model.Job;
import hudson.model.ParametersDefinitionProperty;

import java.io.IOException;

/**
 * Allows a {@link Job} to declare that it is disableable without extending AbstractProject.
 * Consumers who want to check or update the enabled/disabled state of jobs should use the
 * static methods provided here.
 *
 * @since TODO
 */
public final class DisableableJobMixIn {

    /**
     * Returns true if the given Job implements DisableableJobMixIn and is disabled.
     * Otherwise returns false.
     */
    public static boolean isDisabled(final Job<?,?> job) {
        if (!(job instanceof DisableableJob)) {
            return false;
        }
        return ((DisableableJob) job).isDisabled();
    }

    /**
     * Puts the Job into an enabled state. Has no effect if the job is already enabled
     * or does not implement DisableableJob (implying it is always enabled).
     */
    public static void enable(final Job<?,?> job) throws IOException {
        if (job instanceof DisableableJob) {
            ((DisableableJob) job).enable();
        }
    }

    /**
     * Puts the job into a disabled state if it supports it (implements DisableableJob).
     * Has no effect if the job is already disabled, or does not implement DisableableJob.
     */
    public static void disableIfSupported(final Job<?,?> job) throws IOException {
        if (job instanceof DisableableJob) {
            ((DisableableJob) job).disable();
        }
    }

    public interface DisableableJob {
        boolean isDisabled();

        /**
	 * Should put the job into an enabled state. If the job is already enabled this
	 * should be a no-op.
	 */
        void enable() throws IOException;

	/**
	 * Should put the job into a disabled state. If the job is already disabled this
	 * should be a no-op.
	 */
        void disable() throws IOException;
    }
}
