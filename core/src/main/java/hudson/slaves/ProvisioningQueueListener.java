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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * QueueListener that integrates with the QueueItemTracker to maintain causal
 * linkage between queue items and provisioned agents.
 *
 * This listener addresses the core over-provisioning issue in BEE-60267 by
 * ensuring that when queue items are cancelled or start executing, any
 * associated provisioning can be cleaned up or cancelled appropriately.
 *
 * The key insight is that without this linkage, agents would continue to be
 * provisioned even after the demand (queue items) was removed, leading to
 * over-provisioning.
 *
 * @author Mike Cirioli
 * @since 2.530
 */
@Extension
public class ProvisioningQueueListener extends QueueListener {

    private static final Logger LOGGER = Logger.getLogger(ProvisioningQueueListener.class.getName());

    /**
     * Called when an item enters the buildable phase.
     *
     * This is typically when provisioning strategies will be triggered to
     * create agents for the buildable items. We log this for debugging
     * purposes but don't take action here since the provisioning tracking
     * happens in the strategy itself.
     *
     * @param bi the buildable item
     */
    @Override
    public void onEnterBuildable(@NonNull Queue.BuildableItem bi) {
        LOGGER.log(Level.FINEST, "Queue item {0} entered buildable phase: {1}",
            new Object[]{bi.getId(), bi.task.getDisplayName()});
    }

    /**
     * Called when an item leaves the buildable phase.
     *
     * Items leave buildable phase either because:
     * 1. They get allocated to an executor and start running
     * 2. They get cancelled
     * 3. They get blocked again
     *
     * We don't take action here since the definitive cleanup happens in onLeft().
     *
     * @param bi the buildable item
     */
    @Override
    public void onLeaveBuildable(@NonNull Queue.BuildableItem bi) {
        LOGGER.log(Level.FINEST, "Queue item {0} left buildable phase: {1}",
            new Object[]{bi.getId(), bi.task.getDisplayName()});
    }

    /**
     * Called when an item has left the queue.
     *
     * This is the critical method that addresses the over-provisioning issue.
     * When queue items leave the queue (either cancelled or started executing),
     * we need to clean up the tracking and potentially cancel unnecessary provisioning.
     *
     * This creates the causal linkage that was missing in BEE-60267: when demand
     * disappears (queue item removed), we can stop supply (cancel provisioning).
     *
     * @param li the left item
     */
    @Override
    public void onLeft(@NonNull Queue.LeftItem li) {
        LOGGER.log(Level.FINE, "Queue item {0} left queue: {1} (cancelled: {2})",
            new Object[]{li.getId(), li.task.getDisplayName(), li.isCancelled()});

        try {
            // Get the planned nodes that were linked to this queue item
            Collection<PlannedNode> linkedNodes = QueueItemTracker.getInstance().unlinkQueueItem(li);

            if (linkedNodes != null && !linkedNodes.isEmpty()) {
                LOGGER.log(Level.INFO, "Queue item {0} was linked to {1} planned nodes. " +
                    "Cleaning up tracking. Cancelled: {2}",
                    new Object[]{li.getId(), linkedNodes.size(), li.isCancelled()});

                // If the queue item was cancelled, we could potentially cancel
                // the associated provisioning, but this is complex because:
                // 1. Multiple queue items might share the same planned nodes
                // 2. The nodes might already be starting up
                // 3. Other queue items might still need these nodes

                // For now, we focus on cleaning up the tracking.
                // Future enhancements could implement more sophisticated
                // provisioning cancellation logic here.

                handleProvisioningCleanup(li, linkedNodes);
            }

        } catch (Exception e) {
            // Don't let exceptions in queue item tracking break queue processing
            LOGGER.log(Level.WARNING, "Error cleaning up queue item tracking for item " +
                li.getId() + ": " + li.task.getDisplayName(), e);
        }
    }

    /**
     * Handles cleanup when a queue item with linked provisioning is removed.
     *
     * This method provides a hook for future enhancements that might implement
     * more sophisticated provisioning cancellation logic.
     *
     * @param leftItem the queue item that was removed
     * @param linkedNodes the planned nodes that were provisioned for this item
     */
    private void handleProvisioningCleanup(@NonNull Queue.LeftItem leftItem,
                                           @NonNull Collection<PlannedNode> linkedNodes) {
        // Current implementation focuses on logging for observability
        // Future enhancements could implement:
        // 1. Cancellation of provisioning that hasn't started yet
        // 2. Early termination of nodes that are no longer needed
        // 3. Coordination with CloudProvisioningLimits to free up capacity

        if (leftItem.isCancelled()) {
            LOGGER.log(Level.INFO, "Queue item {0} was cancelled. {1} linked planned nodes " +
                "may no longer be needed: {2}",
                new Object[]{leftItem.getId(), linkedNodes.size(),
                    linkedNodes.stream().map(n -> n.displayName).toArray()});

            // TODO: Implement cancellation logic
            // - Check if planned nodes are still needed by other queue items
            // - Cancel or mark for early termination if not needed
            // - Update CloudProvisioningLimits to reflect cancelled provisioning
        } else {
            LOGGER.log(Level.FINE, "Queue item {0} started executing. {1} linked planned nodes " +
                "should continue as planned.",
                new Object[]{leftItem.getId(), linkedNodes.size()});
        }
    }

    /**
     * Called when an item enters the waiting phase.
     *
     * We log this for debugging but don't track items until they become buildable
     * since provisioning typically doesn't happen during the waiting phase.
     *
     * @param wi the waiting item
     */
    @Override
    public void onEnterWaiting(@NonNull Queue.WaitingItem wi) {
        LOGGER.log(Level.FINEST, "Queue item {0} entered waiting phase: {1}",
            new Object[]{wi.getId(), wi.task.getDisplayName()});
    }

    /**
     * Called when an item enters the blocked phase.
     *
     * Blocked items don't typically trigger provisioning, so we just log for debugging.
     *
     * @param bi the blocked item
     */
    @Override
    public void onEnterBlocked(@NonNull Queue.BlockedItem bi) {
        LOGGER.log(Level.FINEST, "Queue item {0} entered blocked phase: {1}",
            new Object[]{bi.getId(), bi.task.getDisplayName()});
    }
}
