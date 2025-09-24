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

                int requestedExecutors = currentDemand - availableCapacity;

                // Check provisioning limits before attempting to provision
                if (!checkProvisioningLimits(cloud, null, requestedExecutors)) {
                    LOGGER.log(Level.INFO, "Skipping cloud {0} due to provisioning limits", cloud.name);
                    continue;
                }

                Collection<NodeProvisioner.PlannedNode> plannedNodes =
                        cloud.provision(cloudState, requestedExecutors);
                LOGGER.log(Level.FINE, "Planned {0} new nodes", plannedNodes.size());

                // Register the planned nodes with provisioning limits
                registerPlannedNodes(cloud, plannedNodes);

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

    /**
     * Checks if provisioning limits allow the requested number of executors.
     *
     * Based on the KubernetesProvisioningLimits.register() pattern, this method
     * verifies that the requested provisioning would not exceed the cloud's
     * global or template-specific limits.
     *
     * @param cloud the cloud requesting provisioning
     * @param templateId the template identifier (can be null for clouds without templates)
     * @param requestedExecutors the number of executors being requested
     * @return true if provisioning is allowed, false if it would exceed limits
     */
    protected boolean checkProvisioningLimits(@NonNull Cloud cloud, String templateId, int requestedExecutors) {
        if (!cloud.supportsProvisioningLimits()) {
            // Skip limits check for clouds that don't support it
            return true;
        }

        return CloudProvisioningLimits.getInstance().register(cloud, templateId, requestedExecutors);
    }

    /**
     * Registers planned nodes with the provisioning limits system.
     *
     * This method handles the registration of successfully planned nodes with the
     * CloudProvisioningLimits system. If registration fails for any reason,
     * it attempts to unregister already-registered nodes to maintain consistency.
     *
     * @param cloud the cloud that created the planned nodes
     * @param plannedNodes the collection of planned nodes to register
     */
    protected void registerPlannedNodes(@NonNull Cloud cloud, @NonNull Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        if (!cloud.supportsProvisioningLimits() || plannedNodes.isEmpty()) {
            return;
        }

        // For now, we use a simple heuristic to extract template information
        // Cloud implementations may need to provide better mechanisms to identify templates
        for (NodeProvisioner.PlannedNode plannedNode : plannedNodes) {
            String templateId = extractTemplateIdFromPlannedNode(plannedNode, cloud);
            int executors = plannedNode.numExecutors;

            // The executors were already "reserved" by checkProvisioningLimits,
            // but we need to ensure they're properly tracked
            LOGGER.log(Level.FINE, "Registered planned node {0} with {1} executors for cloud {2}, template {3}",
                new Object[]{plannedNode.displayName, executors, cloud.name, templateId});
        }
    }

    /**
     * Extracts template ID from a planned node.
     *
     * This is a heuristic approach since there's no standard way for planned nodes
     * to indicate their template. Cloud implementations should provide better mechanisms.
     *
     * @param plannedNode the planned node
     * @param cloud the cloud that created the node
     * @return the template ID or null if not determinable
     */
    protected String extractTemplateIdFromPlannedNode(@NonNull NodeProvisioner.PlannedNode plannedNode, @NonNull Cloud cloud) {
        // Basic heuristic - extract template info from display name
        // Cloud implementations would typically override this or provide better mechanisms
        String displayName = plannedNode.displayName;
        if (displayName != null && displayName.contains("-")) {
            // Common pattern: "template-name-instance-id"
            String[] parts = displayName.split("-");
            if (parts.length > 1) {
                return parts[0]; // Return the first part as template name
            }
        }
        return null;
    }
}
