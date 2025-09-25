package hudson.slaves;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Queue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
 * <p>This implementation is designed to work generically with any {@link Cloud} implementation.
 * Cloud-specific plugins can extend this class and override specific methods to customize behavior
 * for their cloud type while inheriting the core provisioning logic and BEE-60267 over-provisioning
 * protection.</p>
 *
 * <h3>Extension Points for Plugin Authors</h3>
 * <p>Plugin authors can extend this class and override:</p>
 * <ul>
 *   <li>{@link #shouldProcessCloud(Cloud, Label)} - to filter which clouds this strategy handles</li>
 *   <li>{@link #supportsNoDelayProvisioning(Cloud)} - to determine no-delay provisioning support</li>
 *   <li>{@link #getMaxProvisioningBatchSize(Cloud)} - to customize batch sizing per cloud</li>
 *   <li>{@link #extractTemplateIdFromPlannedNode(NodeProvisioner.PlannedNode, Cloud)} - to improve template identification</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 * @Extension(ordinal = 100)
 * public class EC2NoDelayProvisionerStrategy extends NoDelayProvisionerStrategy {
 *     @Override
 *     protected boolean shouldProcessCloud(Cloud cloud, Label label) {
 *         return cloud instanceof AmazonEC2Cloud;
 *     }
 *
 *     @Override
 *     protected boolean supportsNoDelayProvisioning(Cloud cloud) {
 *         return ((AmazonEC2Cloud) cloud).isNoDelayProvisioning();
 *     }
 * }
 * }</pre>
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
        String strategyName = this.getClass().getSimpleName();

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

        // Get intelligently ordered list of clouds for optimal provisioning
        List<Cloud> orderedClouds = CloudStateManager.getInstance().getOptimalCloudOrder(label, remainingDemand);

        // Fallback: if CloudStateManager returns empty list (e.g., in test environments),
        // use all available clouds from Jenkins instance
        if (orderedClouds.isEmpty()) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null && !jenkins.clouds.isEmpty()) {
                orderedClouds = new ArrayList<>(jenkins.clouds);
                LOGGER.log(Level.FINE, "CloudStateManager returned empty list, using fallback with {0} clouds",
                    orderedClouds.size());
            }
        }

        LOGGER.log(Level.FINE, "Processing {0} clouds for provisioning", orderedClouds.size());

        // Try provisioning across clouds in optimal order for better utilization and failover
        for (Cloud cloud : orderedClouds) {
            if (remainingDemand <= 0) {
                break; // Demand satisfied
            }

            cloudsAttempted++;

            // Check if this strategy should process this cloud
            if (!shouldProcessCloud(cloud, label)) {
                cloudsSkipped++;
                LOGGER.log(Level.FINEST, "Strategy {0} skipping cloud {1} for label {2}",
                    new Object[]{this.getClass().getSimpleName(), cloud.name, label});
                continue;
            }

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

            // Start metrics tracking for this provisioning attempt
            ProvisioningMetrics.ProvisioningAttemptContext metricsContext =
                ProvisioningMetrics.getInstance().startProvisioningAttempt(cloud, strategyName, label, requestedExecutors);

            // Attempt provisioning with error handling and metrics tracking
            Collection<NodeProvisioner.PlannedNode> plannedNodes =
                attemptProvisioningWithMetrics(cloud, cloudState, requestedExecutors, label, metricsContext);

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
     * distribution across multiple clouds. Cloud-specific strategies can override
     * this method to provide custom batch sizing logic.
     *
     * @param cloud the cloud to check
     * @return the maximum batch size for provisioning
     */
    protected int getMaxProvisioningBatchSize(@NonNull Cloud cloud) {
        // Calculate based on provisioning limits if available
        if (cloud.supportsProvisioningLimits()) {
            int globalCap = cloud.getGlobalProvisioningCap();
            if (globalCap != Integer.MAX_VALUE && globalCap > 0) {
                // Limit batch to 25% of global cap to allow multiple concurrent requests
                return Math.max(1, globalCap / 4);
            }
        }

        // Conservative default that works for any cloud type
        return 10;
    }

    /**
     * Attempts provisioning with proper error handling, recovery, and metrics tracking.
     *
     * This enhanced method wraps the cloud provisioning call with error handling to ensure
     * that failures in one cloud don't prevent trying other clouds, while also tracking
     * comprehensive metrics for monitoring and optimization.
     *
     * @param cloud the cloud to provision from
     * @param cloudState the cloud state
     * @param requestedExecutors the number of executors requested
     * @param label the label being provisioned for
     * @param metricsContext the metrics context for tracking this attempt
     * @return the planned nodes, or null if provisioning failed
     */
    protected Collection<NodeProvisioner.PlannedNode> attemptProvisioningWithMetrics(@NonNull Cloud cloud,
                                                                                      @NonNull Cloud.CloudState cloudState,
                                                                                      int requestedExecutors,
                                                                                      Label label,
                                                                                      @NonNull ProvisioningMetrics.ProvisioningAttemptContext metricsContext) {
        long startTime = System.currentTimeMillis();

        try {
            LOGGER.log(Level.FINE, "Attempting to provision {0} executors from cloud {1} for label {2}",
                new Object[]{requestedExecutors, cloud.name, label});

            Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(cloudState, requestedExecutors);
            long duration = System.currentTimeMillis() - startTime;

            if (plannedNodes == null || plannedNodes.isEmpty()) {
                String reason = "Cloud returned no planned nodes";
                LOGGER.log(Level.INFO, "Cloud {0} returned no planned nodes for {1} requested executors",
                    new Object[]{cloud.name, requestedExecutors});

                // Record failure in metrics and cloud state
                ProvisioningMetrics.getInstance().recordProvisioningFailure(metricsContext, reason);
                CloudStateManager.getInstance().recordProvisioningResult(cloud, false, duration, requestedExecutors, 0);
                return null;
            }

            // Validate the planned nodes
            int actualExecutors = plannedNodes.stream().mapToInt(node -> node.numExecutors).sum();
            if (actualExecutors <= 0) {
                String reason = "Planned nodes have zero total executors";
                LOGGER.log(Level.WARNING, "Cloud {0} returned planned nodes with zero total executors",
                    cloud.name);

                // Record failure in metrics and cloud state
                ProvisioningMetrics.getInstance().recordProvisioningFailure(metricsContext, reason);
                CloudStateManager.getInstance().recordProvisioningResult(cloud, false, duration, requestedExecutors, 0);
                return null;
            }

            LOGGER.log(Level.FINE, "Cloud {0} successfully planned {1} nodes with {2} total executors",
                new Object[]{cloud.name, plannedNodes.size(), actualExecutors});

            // Record successful provisioning in metrics and cloud state
            ProvisioningMetrics.getInstance().recordProvisioningSuccess(metricsContext, actualExecutors);
            CloudStateManager.getInstance().recordProvisioningResult(cloud, true, duration, requestedExecutors, actualExecutors);

            return plannedNodes;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String reason = "Exception during provisioning: " + e.getClass().getSimpleName();
            LOGGER.log(Level.WARNING, "Exception while provisioning from cloud " + cloud.name +
                " for " + requestedExecutors + " executors: " + e.getMessage(), e);

            // Record failure in metrics and cloud state
            ProvisioningMetrics.getInstance().recordProvisioningFailure(metricsContext, reason);
            CloudStateManager.getInstance().recordProvisioningResult(cloud, false, duration, requestedExecutors, 0);

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
     * Determines whether this strategy should process a specific cloud.
     *
     * This method provides a central extension point for cloud-specific strategies
     * to filter which clouds they handle. The default implementation processes
     * all clouds, but plugin-specific strategies can override this to target
     * specific cloud types.
     *
     * @param cloud the cloud to check
     * @param label the label being provisioned for (may be null)
     * @return true if this strategy should process the cloud, false to skip it
     */
    protected boolean shouldProcessCloud(@NonNull Cloud cloud, Label label) {
        // Default implementation processes all clouds
        // Plugin-specific strategies can override this for cloud-type filtering
        // Example: return cloud instanceof AmazonEC2Cloud;
        return true;
    }

    /**
     * Determines whether a cloud supports no-delay provisioning.
     *
     * This default implementation returns true for all clouds, allowing any cloud to use
     * the no-delay strategy. Cloud-specific strategy implementations can override this
     * method to provide custom logic for determining no-delay provisioning support.
     *
     * @param cloud the cloud to check
     * @return true if the cloud supports no-delay provisioning, false otherwise
     */
    protected boolean supportsNoDelayProvisioning(Cloud cloud) {
        // Default to true - allow all clouds to use no-delay provisioning
        // Plugin-specific strategies can override this method to provide custom logic
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
