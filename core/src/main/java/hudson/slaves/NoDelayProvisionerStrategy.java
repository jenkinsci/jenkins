package hudson.slaves;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * Implementation of {@link NodeProvisioner.Strategy} which will provision a new node immediately as
 * a task enters the queue.
 *
 * This strategy provisions new nodes without delay when there is excess demand, making it suitable
 * for cloud environments where rapid scaling is desired and billing is fine-grained (e.g., per-minute).
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @since 2.530
 */
@Extension(ordinal = 200) @Symbol("no-delay")
public class NoDelayProvisionerStrategy extends NodeProvisioner.Strategy {

    private static final Logger LOGGER = Logger.getLogger(NoDelayProvisionerStrategy.class.getName());

    @NonNull
    @Override
    public NodeProvisioner.StrategyDecision apply(@NonNull NodeProvisioner.StrategyState strategyState) {
        final Label label = strategyState.getLabel();

        LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
        int availableCapacity = snapshot.getAvailableExecutors() // live executors
                + snapshot.getConnectingExecutors() // executors present but not yet connected
                + strategyState
                        .getPlannedCapacitySnapshot() // capacity added by previous strategies from previous rounds
                + strategyState.getAdditionalPlannedCapacity(); // capacity added by previous strategies _this round_
        int currentDemand = snapshot.getQueueLength();
        LOGGER.log(
                Level.FINE, "Available capacity={0}, currentDemand={1}", new Object[] {availableCapacity, currentDemand
                });
        if (availableCapacity < currentDemand) {
            Jenkins jenkinsInstance = Jenkins.get();
            for (Cloud cloud : jenkinsInstance.clouds) {
                Cloud.CloudState cloudState = new Cloud.CloudState(label, 0);
                if (!cloud.canProvision(cloudState)) {
                    continue;
                }

                // Check if this cloud supports no-delay provisioning
                if (!supportsNoDelayProvisioning(cloud)) {
                    continue;
                }

                Collection<NodeProvisioner.PlannedNode> plannedNodes =
                        cloud.provision(cloudState, currentDemand - availableCapacity);
                LOGGER.log(Level.FINE, "Planned {0} new nodes", plannedNodes.size());
                strategyState.recordPendingLaunches(plannedNodes);
                availableCapacity += plannedNodes.size();
                LOGGER.log(Level.FINE, "After provisioning, available capacity={0}, currentDemand={1}", new Object[] {
                    availableCapacity, currentDemand,
                });
                break;
            }
        }
        if (availableCapacity >= currentDemand) {
            LOGGER.log(Level.FINE, "Provisioning completed");
            return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
        } else {
            LOGGER.log(Level.FINE, "Provisioning not complete, consulting remaining strategies");
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }
    }

    /**
     * Determines whether a cloud supports no-delay provisioning.
     *
     * This default implementation returns true for all clouds, allowing any cloud to use
     * the no-delay strategy. Cloud implementations can override this behavior by implementing
     * a method to indicate their no-delay provisioning preference.
     *
     * @param cloud the cloud to check
     * @return true if the cloud supports no-delay provisioning, false otherwise
     */
    protected boolean supportsNoDelayProvisioning(Cloud cloud) {
        // Use reflection to check if the cloud has a method to indicate no-delay provisioning support
        try {
            java.lang.reflect.Method method = cloud.getClass().getMethod("isNoDelayProvisioning");
            if (method.getReturnType() == boolean.class) {
                return (Boolean) method.invoke(cloud);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Method doesn't exist or couldn't be invoked, fall back to default behavior
            LOGGER.log(Level.FINEST, "Cloud {0} does not have isNoDelayProvisioning method, defaulting to true", cloud.getClass().getName());
        }

        // Default to true - allow all clouds to use no-delay provisioning
        return true;
    }
}
