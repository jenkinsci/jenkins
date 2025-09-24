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
import hudson.model.Computer;
import hudson.model.Node;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Tracks and enforces provisioning limits for cloud agents to prevent over-provisioning.
 *
 * Based on the implementation pattern from the Kubernetes plugin's KubernetesProvisioningLimits class,
 * this class provides thread-safe tracking of executor counts per cloud and template to enforce
 * global and per-template provisioning caps.
 *
 * @author Mike Cirioli
 * @since 2.530
 */
public class CloudProvisioningLimits {

    private static final Logger LOGGER = Logger.getLogger(CloudProvisioningLimits.class.getName());

    private static final CloudProvisioningLimits INSTANCE = new CloudProvisioningLimits();

    /**
     * Tracks the number of executors currently provisioned per cloud.
     * Key format: cloud.name
     */
    private final ConcurrentMap<String, AtomicInteger> cloudExecutorCounts = new ConcurrentHashMap<>();

    /**
     * Tracks the number of executors currently provisioned per template within a cloud.
     * Key format: cloud.name:templateId
     */
    private final ConcurrentMap<String, AtomicInteger> templateExecutorCounts = new ConcurrentHashMap<>();

    private CloudProvisioningLimits() {
        // Singleton
    }

    /**
     * Gets the singleton instance of CloudProvisioningLimits.
     *
     * @return the singleton instance
     */
    public static CloudProvisioningLimits getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a provisioning request and checks if it would exceed limits.
     *
     * Based on KubernetesProvisioningLimits.register() method.
     *
     * @param cloud the cloud requesting provisioning
     * @param templateId the template identifier (can be null for clouds without templates)
     * @param executors the number of executors being requested
     * @return true if provisioning is allowed, false if it would exceed limits
     */
    public synchronized boolean register(@NonNull Cloud cloud, String templateId, int executors) {
        if (!cloud.supportsProvisioningLimits()) {
            // Skip limits for clouds that don't support them
            return true;
        }

        String cloudKey = cloud.name;
        String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

        // Check global cloud limit
        int globalCap = cloud.getGlobalProvisioningCap();
        if (globalCap != Integer.MAX_VALUE) {
            int currentCloudCount = cloudExecutorCounts.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).get();
            if (currentCloudCount + executors > globalCap) {
                LOGGER.log(Level.INFO, "Reached global provisioning cap for cloud {0}. Current: {1}, Requested: {2}, Cap: {3}",
                    new Object[]{cloudKey, currentCloudCount, executors, globalCap});
                return false;
            }
        }

        // Check template-specific limit
        if (templateKey != null) {
            int templateCap = cloud.getTemplateProvisioningCap(templateId);
            if (templateCap != Integer.MAX_VALUE) {
                int currentTemplateCount = templateExecutorCounts.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).get();
                if (currentTemplateCount + executors > templateCap) {
                    LOGGER.log(Level.INFO, "Reached template provisioning cap for {0}. Current: {1}, Requested: {2}, Cap: {3}",
                        new Object[]{templateKey, currentTemplateCount, executors, templateCap});
                    return false;
                }
            }
        }

        // Register the provisioning
        cloudExecutorCounts.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).addAndGet(executors);
        if (templateKey != null) {
            templateExecutorCounts.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).addAndGet(executors);
        }

        LOGGER.log(Level.FINE, "Registered {0} executors for cloud {1}, template {2}",
            new Object[]{executors, cloudKey, templateId});

        return true;
    }

    /**
     * Unregisters executors when nodes are terminated or provisioning fails.
     *
     * @param cloud the cloud that was provisioning
     * @param templateId the template identifier (can be null)
     * @param executors the number of executors to unregister
     */
    public synchronized void unregister(@NonNull Cloud cloud, String templateId, int executors) {
        if (!cloud.supportsProvisioningLimits()) {
            return;
        }

        String cloudKey = cloud.name;
        String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

        // Unregister from cloud count
        AtomicInteger cloudCount = cloudExecutorCounts.get(cloudKey);
        if (cloudCount != null) {
            int newValue = cloudCount.addAndGet(-executors);
            if (newValue <= 0) {
                cloudExecutorCounts.remove(cloudKey);
            }
        }

        // Unregister from template count
        if (templateKey != null) {
            AtomicInteger templateCount = templateExecutorCounts.get(templateKey);
            if (templateCount != null) {
                int newValue = templateCount.addAndGet(-executors);
                if (newValue <= 0) {
                    templateExecutorCounts.remove(templateKey);
                }
            }
        }

        LOGGER.log(Level.FINE, "Unregistered {0} executors for cloud {1}, template {2}",
            new Object[]{executors, cloudKey, templateId});
    }

    /**
     * Unregisters a node when it's deleted from Jenkins.
     *
     * @param node the node being deleted
     */
    public void unregisterNode(@NonNull Node node) {
        // Try to determine which cloud this node belongs to
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }

        Computer computer = node.toComputer();
        if (computer == null) {
            return;
        }

        // Find the cloud that created this node
        for (Cloud cloud : jenkins.clouds) {
            if (belongsToCloud(node, cloud)) {
                String templateId = extractTemplateId(node, cloud);
                int executors = node.getNumExecutors();
                unregister(cloud, templateId, executors);
                break;
            }
        }
    }

    /**
     * Initializes the provisioning limits by scanning existing nodes.
     * This should be called during Jenkins startup to account for existing nodes.
     */
    public void initInstance() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }

        LOGGER.log(Level.INFO, "Initializing cloud provisioning limits from existing nodes");

        // Clear existing counts
        cloudExecutorCounts.clear();
        templateExecutorCounts.clear();

        // Scan existing nodes
        for (Node node : jenkins.getNodes()) {
            for (Cloud cloud : jenkins.clouds) {
                if (belongsToCloud(node, cloud)) {
                    String templateId = extractTemplateId(node, cloud);
                    int executors = node.getNumExecutors();

                    // Register without limit checking since these nodes already exist
                    String cloudKey = cloud.name;
                    String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

                    cloudExecutorCounts.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).addAndGet(executors);
                    if (templateKey != null) {
                        templateExecutorCounts.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).addAndGet(executors);
                    }
                    break;
                }
            }
        }

        LOGGER.log(Level.INFO, "Initialized provisioning limits. Cloud counts: {0}, Template counts: {1}",
            new Object[]{cloudExecutorCounts.size(), templateExecutorCounts.size()});
    }

    /**
     * Gets the current executor count for a cloud.
     *
     * @param cloudName the name of the cloud
     * @return the current executor count
     */
    public int getCloudExecutorCount(@NonNull String cloudName) {
        AtomicInteger count = cloudExecutorCounts.get(cloudName);
        return count != null ? count.get() : 0;
    }

    /**
     * Gets the current executor count for a template.
     *
     * @param cloudName the name of the cloud
     * @param templateId the template identifier
     * @return the current executor count
     */
    public int getTemplateExecutorCount(@NonNull String cloudName, String templateId) {
        if (templateId == null) {
            return 0;
        }
        String templateKey = cloudName + ":" + templateId;
        AtomicInteger count = templateExecutorCounts.get(templateKey);
        return count != null ? count.get() : 0;
    }

    /**
     * Determines if a node belongs to a specific cloud.
     * This is a heuristic approach since there's no standard way to link nodes to clouds.
     *
     * Cloud plugins can improve this by:
     * 1. Adding cloud name/ID to node properties
     * 2. Using naming conventions in node names
     * 3. Storing cloud reference in node metadata
     *
     * @param node the node to check
     * @param cloud the cloud to check against
     * @return true if the node likely belongs to the cloud
     */
    private boolean belongsToCloud(Node node, Cloud cloud) {
        if (node == null || cloud == null) {
            return false;
        }

        String nodeName = node.getNodeName();
        String displayName = node.getDisplayName();
        String cloudName = cloud.name;

        // Check various naming patterns commonly used by cloud plugins
        if (nodeName != null && (
            nodeName.contains(cloudName) ||
            nodeName.startsWith(cloudName + "-") ||
            nodeName.endsWith("-" + cloudName))) {
            return true;
        }

        if (displayName != null && (
            displayName.contains(cloudName) ||
            displayName.startsWith(cloudName + "-") ||
            displayName.endsWith("-" + cloudName))) {
            return true;
        }

        // Check if node properties contain cloud information
        // This would be more reliable if cloud plugins stored cloud references
        try {
            // Use reflection to check for cloud-specific properties
            String className = node.getClass().getSimpleName().toLowerCase();
            String cloudClassName = cloud.getClass().getSimpleName().toLowerCase();

            // Match patterns like "EC2Slave" with "EC2Cloud"
            if (className.contains("slave") && cloudClassName.contains("cloud")) {
                String nodeType = className.replace("slave", "");
                String cloudType = cloudClassName.replace("cloud", "");
                if (nodeType.equals(cloudType)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
            LOGGER.log(Level.FINEST, "Error checking node class relationship", e);
        }

        return false;
    }

    /**
     * Extracts the template ID from a node for a given cloud.
     * This is a heuristic approach - cloud plugins should provide better mechanisms.
     *
     * Common patterns:
     * - Node name: "template-name-instance-id"
     * - Display name: "Template Name (instance-id)"
     * - Node properties containing template information
     *
     * @param node the node
     * @param cloud the cloud
     * @return the template ID or null
     */
    private String extractTemplateId(Node node, Cloud cloud) {
        if (node == null) {
            return null;
        }

        String nodeName = node.getNodeName();
        String displayName = node.getDisplayName();

        // Try to extract from node name first
        if (nodeName != null) {
            String templateId = extractTemplateFromName(nodeName, cloud.name);
            if (templateId != null) {
                return templateId;
            }
        }

        // Try to extract from display name
        if (displayName != null && !displayName.equals(nodeName)) {
            String templateId = extractTemplateFromName(displayName, cloud.name);
            if (templateId != null) {
                return templateId;
            }
        }

        // Could be extended to check node properties for template information
        // This would require cloud plugins to store template references

        return null;
    }

    /**
     * Helper method to extract template ID from a name string.
     *
     * @param name the name to parse
     * @param cloudName the cloud name to remove from the template name
     * @return the template ID or null
     */
    private String extractTemplateFromName(String name, String cloudName) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Remove cloud name prefix if present
        String cleanName = name;
        if (name.startsWith(cloudName + "-")) {
            cleanName = name.substring(cloudName.length() + 1);
        }

        // Look for common patterns:
        // "template-name-instance-id" -> "template-name"
        // "template_name_instance_id" -> "template_name"
        if (cleanName.contains("-")) {
            String[] parts = cleanName.split("-");
            if (parts.length >= 2) {
                // Check if last part looks like an instance ID (numbers, uuid, etc.)
                String lastPart = parts[parts.length - 1];
                if (isLikelyInstanceId(lastPart)) {
                    // Join all parts except the last one
                    return String.join("-", java.util.Arrays.copyOf(parts, parts.length - 1));
                }
            }
        }

        if (cleanName.contains("_")) {
            String[] parts = cleanName.split("_");
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1];
                if (isLikelyInstanceId(lastPart)) {
                    return String.join("_", java.util.Arrays.copyOf(parts, parts.length - 1));
                }
            }
        }

        return null;
    }

    /**
     * Heuristic to determine if a string looks like an instance ID.
     *
     * @param str the string to check
     * @return true if it looks like an instance ID
     */
    private boolean isLikelyInstanceId(String str) {
        if (str == null || str.length() < 3) {
            return false;
        }

        // Check for common instance ID patterns
        // Numbers only
        if (str.matches("\\d+")) {
            return true;
        }

        // UUID-like (contains hyphens and alphanumeric)
        if (str.matches("[0-9a-fA-F-]+") && str.contains("-")) {
            return true;
        }

        // AWS instance ID pattern (i-xxxxxxxxx)
        if (str.matches("i-[0-9a-fA-F]+")) {
            return true;
        }

        // Random alphanumeric (common for auto-generated IDs)
        if (str.matches("[a-zA-Z0-9]+") && str.length() > 6) {
            // Check if it's not a common word (simple heuristic)
            return !str.matches(".*[aeiou]{2,}.*"); // Avoid words with double vowels
        }

        return false;
    }
}
