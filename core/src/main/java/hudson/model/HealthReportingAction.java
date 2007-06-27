package hudson.model;

/**
 * An {@link Action} that can return information about the health of the Job.
 * <p>
 * The health of a {@link Job} is the lowest health returned by the HealthReportingAction's
 * that contribute to the Job.
 * <p>
 * When a particular Action "wins", it's gets to determine the icon and associated description.
 * <p>
 * This provides a mechanism for plugins to present important summary information
 * on the {@link View} main pages without eating up significant screen real estate.
 *
 * @author Stephen Connolly
 * @since 1.115
 */
public interface HealthReportingAction extends Action {
    /**
     * Get this {@link Action}'s {@link HealthReport}.
     *
     * @return
     *     The health report for this instance of the Action or 
     *     <code>null</code> if the Action does not want to 
     *     contribute a HealthReport.
     */
    HealthReport getBuildHealth();
}
