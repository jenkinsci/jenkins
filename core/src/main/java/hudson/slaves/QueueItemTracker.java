/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.slaves;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks the causal linkage between Queue.Item and provisioned cloud agents.
 *
 * This class addresses the core issue identified in BEE-60267 where there was
 * "not a causal linkage between a Queue.Item and a provisioned cloud agent",
 * leading to over-provisioning problems.
 *
 * By maintaining a direct mapping between queue items and the planned nodes
 * provisioned to satisfy them, we can:
 * - Cancel provisioning when queue items are removed
 * - Prevent duplicate provisioning for the same demand
 * - Ensure 1:1 relationship between demand and supply
 *
 * @author Mike Cirioli
 * @since 2.530
 */
public class QueueItemTracker {

    private static final Logger LOGGER = Logger.getLogger(QueueItemTracker.class.getName());

    private static final QueueItemTracker INSTANCE = new QueueItemTracker();

    /**
     * Maps queue item IDs to the planned nodes provisioned for them.
     * Key: Queue.Item.getId()
     * Value: Collection of PlannedNode instances provisioned for this item
     */
    private final ConcurrentMap<Long, Collection<PlannedNode>> queueItemToNodes = new ConcurrentHashMap<>();

    /**
     * Reverse mapping: Maps planned node display names to queue item IDs.
     * This allows efficient lookup when nodes complete or fail.
     * Key: PlannedNode.displayName
     * Value: Queue.Item.getId()
     */
    private final ConcurrentMap<String, Long> nodeToQueueItem = new ConcurrentHashMap<>();

    private QueueItemTracker() {
        // Singleton
    }

    /**
     * Gets the singleton instance of QueueItemTracker.
     *
     * @return the singleton instance
     */
    public static QueueItemTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Links queue items to the planned nodes provisioned for them.
     *
     * This method establishes the causal linkage that was missing in the original
     * over-provisioning issue. When provisioning strategies create planned nodes,
     * they should call this method to track which queue items triggered the provisioning.
     *
     * @param items the queue items that triggered provisioning
     * @param plannedNodes the planned nodes created to satisfy the demand
     * @param cloud the cloud that created the planned nodes
     */
    public void linkQueueItemsToNodes(@NonNull Collection<Queue.Item> items,
                                      @NonNull Collection<PlannedNode> plannedNodes,
                                      @NonNull Cloud cloud) {
        if (items.isEmpty() || plannedNodes.isEmpty()) {
            return;
        }

        LOGGER.log(Level.FINE, "Linking {0} queue items to {1} planned nodes from cloud {2}",
            new Object[]{items.size(), plannedNodes.size(), cloud.name});

        // For simplicity, we distribute planned nodes across all queue items that triggered provisioning
        // In practice, most provisioning is triggered by a single queue item, but this handles bulk scenarios
        for (Queue.Item item : items) {
            long itemId = item.getId();

            // Link this item to all planned nodes
            queueItemToNodes.put(itemId, plannedNodes);

            // Create reverse mapping for efficient cleanup
            for (PlannedNode plannedNode : plannedNodes) {
                String nodeKey = cloud.name + ":" + plannedNode.displayName;
                nodeToQueueItem.put(nodeKey, itemId);

                LOGGER.log(Level.FINEST, "Linked queue item {0} to planned node {1}",
                    new Object[]{itemId, plannedNode.displayName});
            }
        }
    }

    /**
     * Links a single queue item to planned nodes.
     *
     * Convenience method for the common case of single-item provisioning.
     *
     * @param item the queue item that triggered provisioning
     * @param plannedNodes the planned nodes created to satisfy the demand
     * @param cloud the cloud that created the planned nodes
     */
    public void linkQueueItemToNodes(@NonNull Queue.Item item,
                                     @NonNull Collection<PlannedNode> plannedNodes,
                                     @NonNull Cloud cloud) {
        if (plannedNodes.isEmpty()) {
            return;
        }

        long itemId = item.getId();

        LOGGER.log(Level.FINE, "Linking queue item {0} to {1} planned nodes from cloud {2}",
            new Object[]{itemId, plannedNodes.size(), cloud.name});

        queueItemToNodes.put(itemId, plannedNodes);

        for (PlannedNode plannedNode : plannedNodes) {
            String nodeKey = cloud.name + ":" + plannedNode.displayName;
            nodeToQueueItem.put(nodeKey, itemId);
        }
    }

    /**
     * Unlinks a queue item when it leaves the queue.
     *
     * This method should be called when queue items are cancelled or when they
     * start executing. It cleans up the tracking information and can potentially
     * cancel provisioning that's no longer needed.
     *
     * @param item the queue item being removed
     * @return the planned nodes that were linked to this item, or null if none
     */
    public Collection<PlannedNode> unlinkQueueItem(@NonNull Queue.Item item) {
        long itemId = item.getId();
        Collection<PlannedNode> linkedNodes = queueItemToNodes.remove(itemId);

        if (linkedNodes != null) {
            LOGGER.log(Level.FINE, "Unlinking queue item {0} from {1} planned nodes",
                new Object[]{itemId, linkedNodes.size()});

            // Clean up reverse mapping
            for (PlannedNode plannedNode : linkedNodes) {
                // We need to find the node key - iterate through all entries
                nodeToQueueItem.entrySet().removeIf(entry -> entry.getValue().equals(itemId));
            }
        }

        return linkedNodes;
    }

    /**
     * Unlinks a planned node when it completes or fails.
     *
     * This method should be called when planned nodes complete (successfully or not)
     * to clean up the tracking information.
     *
     * @param plannedNode the planned node being removed
     * @param cloud the cloud that created the planned node
     * @return the queue item ID that was linked to this node, or null if none
     */
    public Long unlinkPlannedNode(@NonNull PlannedNode plannedNode, @NonNull Cloud cloud) {
        String nodeKey = cloud.name + ":" + plannedNode.displayName;
        Long queueItemId = nodeToQueueItem.remove(nodeKey);

        if (queueItemId != null) {
            LOGGER.log(Level.FINE, "Unlinking planned node {0} from queue item {1}",
                new Object[]{plannedNode.displayName, queueItemId});

            // Remove this node from the queue item's collection
            Collection<PlannedNode> nodes = queueItemToNodes.get(queueItemId);
            if (nodes != null) {
                nodes.remove(plannedNode);
                // If no more nodes for this queue item, remove the entry
                if (nodes.isEmpty()) {
                    queueItemToNodes.remove(queueItemId);
                }
            }
        }

        return queueItemId;
    }

    /**
     * Checks if a queue item is still pending (in the queue).
     *
     * This method can be used to determine if provisioning should continue
     * for a particular queue item.
     *
     * @param item the queue item to check
     * @return true if the item is still tracked (presumably still in queue)
     */
    public boolean isQueueItemStillPending(@NonNull Queue.Item item) {
        return queueItemToNodes.containsKey(item.getId());
    }

    /**
     * Gets the planned nodes linked to a queue item.
     *
     * @param item the queue item
     * @return the planned nodes linked to this item, or null if none
     */
    public Collection<PlannedNode> getLinkedNodes(@NonNull Queue.Item item) {
        return queueItemToNodes.get(item.getId());
    }

    /**
     * Gets the queue item ID linked to a planned node.
     *
     * @param plannedNode the planned node
     * @param cloud the cloud that created the planned node
     * @return the queue item ID linked to this node, or null if none
     */
    public Long getLinkedQueueItem(@NonNull PlannedNode plannedNode, @NonNull Cloud cloud) {
        String nodeKey = cloud.name + ":" + plannedNode.displayName;
        return nodeToQueueItem.get(nodeKey);
    }

    /**
     * Gets the current number of tracked queue items.
     *
     * @return the number of queue items being tracked
     */
    public int getTrackedQueueItemCount() {
        return queueItemToNodes.size();
    }

    /**
     * Gets the current number of tracked planned nodes.
     *
     * @return the number of planned nodes being tracked
     */
    public int getTrackedPlannedNodeCount() {
        return nodeToQueueItem.size();
    }

    /**
     * Clears all tracking information.
     * This method is primarily for testing purposes.
     */
    @VisibleForTesting
    void clear() {
        queueItemToNodes.clear();
        nodeToQueueItem.clear();
        LOGGER.log(Level.FINE, "Cleared all queue item tracking information");
    }
}
