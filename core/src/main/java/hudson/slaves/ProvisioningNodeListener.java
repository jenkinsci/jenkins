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
import hudson.model.Node;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.NodeListener;

/**
 * NodeListener that integrates with CloudProvisioningLimits to maintain accurate
 * provisioning counts when nodes are created, updated, or deleted.
 *
 * Based on the pattern from the Kubernetes plugin's NodeListenerImpl, this listener
 * ensures that the CloudProvisioningLimits system stays synchronized with the actual
 * node state in Jenkins.
 *
 * @author Mike Cirioli
 * @since 2.530
 */
@Extension
public class ProvisioningNodeListener extends NodeListener {

    private static final Logger LOGGER = Logger.getLogger(ProvisioningNodeListener.class.getName());

    /**
     * Called when a node is created in Jenkins.
     *
     * This method ensures that newly created nodes are properly accounted for
     * in the provisioning limits system. This is particularly important during
     * Jenkins startup when existing nodes need to be registered.
     *
     * @param node the node being created
     */
    @Override
    protected void onCreated(@NonNull Node node) {
        LOGGER.log(Level.FINE, "Node created: {0}", node.getDisplayName());

        // During startup, nodes may be loaded that were previously provisioned
        // We don't need to register them again as initInstance() handles this
        // But we log for debugging purposes
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Node {0} created with {1} executors",
                new Object[]{node.getDisplayName(), node.getNumExecutors()});
        }
    }

    /**
     * Called when a node is updated in Jenkins.
     *
     * This method handles cases where node configuration changes might affect
     * executor counts, which impacts provisioning limits.
     *
     * @param oldNode the previous node configuration
     * @param newNode the new node configuration
     */
    @Override
    protected void onUpdated(@NonNull Node oldNode, @NonNull Node newNode) {
        LOGGER.log(Level.FINE, "Node updated: {0}", newNode.getDisplayName());

        // Check if executor count changed
        int oldExecutors = oldNode.getNumExecutors();
        int newExecutors = newNode.getNumExecutors();

        if (oldExecutors != newExecutors) {
            LOGGER.log(Level.INFO, "Node {0} executor count changed from {1} to {2}",
                new Object[]{newNode.getDisplayName(), oldExecutors, newExecutors});

            // For now, we log this change but don't automatically adjust limits
            // Cloud plugins may need to handle this case specifically
            // TODO: Consider adding support for automatic adjustment
        }
    }

    /**
     * Called when a node is deleted from Jenkins.
     *
     * This is the critical method that ensures proper cleanup of provisioning limits
     * when nodes are removed. Based on the Kubernetes plugin's NodeListenerImpl.onDeleted().
     *
     * @param node the node being deleted
     */
    @Override
    protected void onDeleted(@NonNull Node node) {
        LOGGER.log(Level.INFO, "Node deleted: {0} with {1} executors",
            new Object[]{node.getDisplayName(), node.getNumExecutors()});

        try {
            // Delegate to CloudProvisioningLimits to handle the cleanup
            CloudProvisioningLimits.getInstance().unregisterNode(node);

            LOGGER.log(Level.FINE, "Successfully unregistered node {0} from provisioning limits",
                node.getDisplayName());

        } catch (Exception e) {
            // Log error but don't fail node deletion
            LOGGER.log(Level.WARNING, "Failed to unregister node " + node.getDisplayName() +
                " from provisioning limits", e);
        }
    }
}
