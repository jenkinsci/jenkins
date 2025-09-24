package hudson.slaves;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Queue;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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
        long startTime = System.currentTimeMillis();

        LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
        int availableCapacity = calculateAvailableCapacity(snapshot, strategyState);
        int currentDemand = snapshot.getQueueLength();

        LOGGER.log(Level.FINE, "NoDelay strategy for label {0}: Available capacity={1}, currentDemand={2}",
                new Object[] {label, availableCapacity, currentDemand});

        if (availableCapacity >= currentDemand) {
            LOGGER.log(Level.FINE, "Demand already satisfied, no provisioning needed");
            return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
        }

        int remainingDemand = currentDemand - availableCapacity;
        int totalProvisioned = 0;
        int cloudsAttempted = 0;
        int cloudsSkipped = 0;

        Jenkins jenkinsInstance = Jenkins.get();

        // Try provisioning across multiple clouds for better utilization and failover
        for (Cloud cloud : jenkinsInstance.clouds) {
            if (remainingDemand <= 0) {
                break; // Demand satisfied
            }

            cloudsAttempted++;

            Cloud.CloudState cloudState = new Cloud.CloudState(label, 0);
            if (!cloud.canProvision(cloudState)) {
                cloudsSkipped++;
                LOGGER.log(Level.FINEST, "Cloud {0} cannot provision for label {1}",
                    new Object[]{cloud.name, label});
                continue;
            }

            // Check if this cloud supports no-delay provisioning
            if (!supportsNoDelayProvisioning(cloud)) {
                cloudsSkipped++;
                LOGGER.log(Level.FINEST, "Cloud {0} does not support no-delay provisioning", cloud.name);
                continue;
            }

            int requestedExecutors = Math.min(remainingDemand, getMaxProvisioningBatchSize(cloud));

            // Check provisioning limits before attempting to provision
            if (!checkProvisioningLimits(cloud, null, requestedExecutors)) {
                cloudsSkipped++;
                LOGGER.log(Level.INFO, "Skipping cloud {0} due to provisioning limits (requested: {1})",
                    new Object[]{cloud.name, requestedExecutors});
                continue;
            }

            // Attempt provisioning with error handling
            Collection<NodeProvisioner.PlannedNode> plannedNodes =
                attemptProvisioning(cloud, cloudState, requestedExecutors, label);

            if (plannedNodes != null && !plannedNodes.isEmpty()) {
                int provisionedCount = plannedNodes.size();
                totalProvisioned += provisionedCount;
                remainingDemand -= provisionedCount;

                // Register the planned nodes with provisioning limits
                registerPlannedNodes(cloud, plannedNodes);

                // Link queue items to planned nodes for tracking causal relationship
                linkQueueItemsToPlannedNodes(strategyState, plannedNodes, cloud);

                strategyState.recordPendingLaunches(plannedNodes);

                LOGGER.log(Level.INFO, "Successfully provisioned {0} executors from cloud {1} for label {2}",
                    new Object[]{provisionedCount, cloud.name, label});

                // Update available capacity
                availableCapacity += provisionedCount;
            } else {
                cloudsSkipped++;
                LOGGER.log(Level.INFO, "Cloud {0} failed to provision {1} executors for label {2}",
                    new Object[]{cloud.name, requestedExecutors, label});
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Enhanced logging with strategy execution metrics
        LOGGER.log(Level.FINE, "NoDelay strategy completed for label {0}: " +
            "Total provisioned={1}, Remaining demand={2}, Duration={3}ms, " +
            "Clouds attempted={4}, Clouds skipped={5}",
            new Object[]{label, totalProvisioned, remainingDemand, duration,
                cloudsAttempted, cloudsSkipped});

        if (remainingDemand <= 0) {
            LOGGER.log(Level.FINE, "Provisioning completed, demand fully satisfied");
            return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
        } else {
            LOGGER.log(Level.FINE, "Provisioning not complete, {0} demand remaining, consulting other strategies",
                remainingDemand);
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }
    }

    /**
     * Calculates the total available capacity including pending reservations.
     *
     * This enhanced calculation considers pending provisioning reservations to avoid
     * over-provisioning due to concurrent strategy executions.
     *
     * @param snapshot the load statistics snapshot
     * @param strategyState the current strategy state
     * @return the total available capacity
     */
    protected int calculateAvailableCapacity(@NonNull LoadStatistics.LoadStatisticsSnapshot snapshot,
                                           @NonNull NodeProvisioner.StrategyState strategyState) {
        return snapshot.getAvailableExecutors() // live executors
                + snapshot.getConnectingExecutors() // executors present but not yet connected
                + strategyState.getPlannedCapacitySnapshot() // capacity added by previous strategies from previous rounds
                + strategyState.getAdditionalPlannedCapacity(); // capacity added by previous strategies _this round_
    }

    /**
     * Gets the maximum provisioning batch size for a cloud.
     *
     * This prevents excessive provisioning requests and allows for better resource
     * distribution across multiple clouds.
     *
     * @param cloud the cloud to check
     * @return the maximum batch size for provisioning
     */
    protected int getMaxProvisioningBatchSize(@NonNull Cloud cloud) {
        // Use reflection to check if cloud has a preferred batch size
        try {
            java.lang.reflect.Method method = cloud.getClass().getMethod("getMaxBatchSize");
            if (method.getReturnType() == int.class) {
                int batchSize = (Integer) method.invoke(cloud);
                return Math.max(1, batchSize); // Ensure at least 1
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, use default
            LOGGER.log(Level.FINEST, "Cloud {0} does not have getMaxBatchSize method, using default", cloud.name);
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            // Method couldn't be invoked, use default
            LOGGER.log(Level.FINEST, "Cloud {0} getMaxBatchSize method could not be invoked, using default: {1}",
                new Object[]{cloud.name, e.getMessage()});
        }

        // Default batch size - reasonable for most cloud providers
        if (cloud.supportsProvisioningLimits()) {
            int globalCap = cloud.getGlobalProvisioningCap();
            if (globalCap != Integer.MAX_VALUE) {
                // Limit batch to 25% of global cap to allow multiple concurrent requests
                return Math.max(1, globalCap / 4);
            }
        }

        // Conservative default
        return 10;
    }

    /**
     * Attempts provisioning with proper error handling and recovery.
     *
     * This method wraps the cloud provisioning call with error handling to ensure
     * that failures in one cloud don't prevent trying other clouds.
     *
     * @param cloud the cloud to provision from
     * @param cloudState the cloud state
     * @param requestedExecutors the number of executors requested
     * @param label the label being provisioned for
     * @return the planned nodes, or null if provisioning failed
     */
    protected Collection<NodeProvisioner.PlannedNode> attemptProvisioning(@NonNull Cloud cloud,
                                                                           @NonNull Cloud.CloudState cloudState,
                                                                           int requestedExecutors,
                                                                           Label label) {
        try {
            LOGGER.log(Level.FINE, "Attempting to provision {0} executors from cloud {1} for label {2}",
                new Object[]{requestedExecutors, cloud.name, label});

            Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(cloudState, requestedExecutors);

            if (plannedNodes == null || plannedNodes.isEmpty()) {
                LOGGER.log(Level.INFO, "Cloud {0} returned no planned nodes for {1} requested executors",
                    new Object[]{cloud.name, requestedExecutors});
                return null;
            }

            // Validate the planned nodes
            int actualExecutors = plannedNodes.stream().mapToInt(node -> node.numExecutors).sum();
            if (actualExecutors <= 0) {
                LOGGER.log(Level.WARNING, "Cloud {0} returned planned nodes with zero total executors",
                    cloud.name);
                return null;
            }

            LOGGER.log(Level.FINE, "Cloud {0} successfully planned {1} nodes with {2} total executors",
                new Object[]{cloud.name, plannedNodes.size(), actualExecutors});

            return plannedNodes;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception while provisioning from cloud " + cloud.name +
                " for " + requestedExecutors + " executors: " + e.getMessage(), e);

            // Cancel any pending reservations that may have been made
            if (cloud.supportsProvisioningLimits()) {
                try {
                    CloudProvisioningLimits.getInstance().cancelPendingProvisioning(cloud, null, requestedExecutors);
                } catch (Exception cleanupException) {
                    LOGGER.log(Level.WARNING, "Failed to cleanup pending reservations for cloud " + cloud.name +
                        ": " + cleanupException.getMessage(), cleanupException);
                }
            }

            return null;
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
     * This enhanced method confirms provisioning by moving reservations from pending to active.
     * This is part of the concurrent provisioning tracking improvements in Phase 2.2.
     *
     * @param cloud the cloud that created the planned nodes
     * @param plannedNodes the collection of planned nodes to register
     */
    protected void registerPlannedNodes(@NonNull Cloud cloud, @NonNull Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        if (!cloud.supportsProvisioningLimits() || plannedNodes.isEmpty()) {
            return;
        }

        // Confirm provisioning for each planned node, moving from pending to active
        for (NodeProvisioner.PlannedNode plannedNode : plannedNodes) {
            String templateId = extractTemplateIdFromPlannedNode(plannedNode, cloud);
            int executors = plannedNode.numExecutors;

            // Confirm the provisioning - this moves the reservation from pending to active
            CloudProvisioningLimits.getInstance().confirmProvisioning(cloud, templateId, executors);

            LOGGER.log(Level.FINE, "Confirmed provisioning of planned node {0} with {1} executors for cloud {2}, template {3}",
                new Object[]{plannedNode.displayName, executors, cloud.name, templateId});
        }
    }

    /**
     * Links queue items to planned nodes for tracking causal relationship.
     *
     * This method addresses the core issue identified in BEE-60267: the lack of
     * causal linkage between Queue.Item and provisioned cloud agents. By establishing
     * this linkage, we can later cancel provisioning when queue items are removed
     * and prevent over-provisioning.
     *
     * @param strategyState the current provisioning strategy state
     * @param plannedNodes the planned nodes that were created
     * @param cloud the cloud that created the planned nodes
     */
    protected void linkQueueItemsToPlannedNodes(@NonNull NodeProvisioner.StrategyState strategyState,
                                                @NonNull Collection<NodeProvisioner.PlannedNode> plannedNodes,
                                                @NonNull Cloud cloud) {
        if (plannedNodes.isEmpty()) {
            return;
        }

        // Get the queue items that are driving the demand for this label
        Collection<Queue.Item> queueItems = getQueueItemsForLabel(strategyState.getLabel());

        if (queueItems.isEmpty()) {
            LOGGER.log(Level.FINE, "No queue items found for label {0}, cannot link to planned nodes",
                strategyState.getLabel());
            return;
        }

        LOGGER.log(Level.FINE, "Linking {0} queue items to {1} planned nodes for label {2}",
            new Object[]{queueItems.size(), plannedNodes.size(), strategyState.getLabel()});

        // Link all queue items to all planned nodes
        // This is a simplified approach - in practice, we might want more sophisticated
        // mapping logic based on specific requirements or queue item priorities
        QueueItemTracker.getInstance().linkQueueItemsToNodes(queueItems, plannedNodes, cloud);
    }

    /**
     * Gets the queue items that are buildable for the specified label.
     *
     * This method retrieves the queue items that are currently waiting for
     * executors on the specified label. These are the items that drive
     * provisioning demand.
     *
     * @param label the label to find queue items for
     * @return collection of queue items for the label
     */
    protected Collection<Queue.Item> getQueueItemsForLabel(Label label) {
        Jenkins jenkins = Jenkins.get();
        // Get all buildable items and filter by label
        return jenkins.getQueue().getBuildableItems().stream()
            .filter(item -> item.getAssignedLabel() == label ||
                           (label == null && item.getAssignedLabel() == null))
            .collect(Collectors.toList());
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
