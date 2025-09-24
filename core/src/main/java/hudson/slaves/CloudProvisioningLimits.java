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
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

    /**
     * Tracks pending provisioning requests that have been approved but not yet completed.
     * This prevents race conditions where multiple threads see the same available capacity.
     */
    private final ConcurrentMap<String, AtomicInteger> pendingCloudExecutors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> pendingTemplateExecutors = new ConcurrentHashMap<>();

    /**
     * Read-write lock for coordinating batch operations and ensuring consistency
     * during concurrent access to multiple data structures.
     */
    private final ReentrantReadWriteLock batchLock = new ReentrantReadWriteLock();

    /**
     * Tracks concurrent access statistics for monitoring provisioning patterns.
     */
    private final AtomicInteger concurrentRegistrations = new AtomicInteger(0);
    private final AtomicInteger totalRegistrationAttempts = new AtomicInteger(0);
    private final AtomicInteger rejectedRegistrations = new AtomicInteger(0);

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
     * This enhanced version provides better concurrent access handling by using
     * read-write locks and pending reservations to prevent race conditions.
     *
     * Based on KubernetesProvisioningLimits.register() method.
     *
     * @param cloud the cloud requesting provisioning
     * @param templateId the template identifier (can be null for clouds without templates)
     * @param executors the number of executors being requested
     * @return true if provisioning is allowed, false if it would exceed limits
     */
    public boolean register(@NonNull Cloud cloud, String templateId, int executors) {
        totalRegistrationAttempts.incrementAndGet();

        if (!cloud.supportsProvisioningLimits()) {
            // Skip limits for clouds that don't support them
            return true;
        }

        // Use read lock for the limit checking phase
        batchLock.readLock().lock();
        try {
            concurrentRegistrations.incrementAndGet();

            String cloudKey = cloud.name;
            String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

            // Check limits including pending reservations to prevent race conditions
            if (!checkLimitsWithPending(cloud, cloudKey, templateKey, executors)) {
                rejectedRegistrations.incrementAndGet();
                return false;
            }

            // Reserve the executors in pending counts
            reservePendingExecutors(cloudKey, templateKey, executors);

            LOGGER.log(Level.FINE, "Reserved {0} executors for cloud {1}, template {2}",
                new Object[]{executors, cloudKey, templateId});

            return true;
        } finally {
            try {
                concurrentRegistrations.decrementAndGet();
            } finally {
                batchLock.readLock().unlock();
            }
        }
    }

    /**
     * Checks provisioning limits including pending reservations.
     *
     * @param cloud the cloud requesting provisioning
     * @param cloudKey the cloud key
     * @param templateKey the template key (can be null)
     * @param executors the number of executors being requested
     * @return true if within limits, false otherwise
     */
    private boolean checkLimitsWithPending(@NonNull Cloud cloud, String cloudKey, String templateKey, int executors) {
        // Check global cloud limit including pending
        int globalCap = cloud.getGlobalProvisioningCap();
        if (globalCap != Integer.MAX_VALUE) {
            int currentCloudCount = cloudExecutorCounts.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).get();
            int pendingCloudCount = pendingCloudExecutors.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).get();
            int totalCount = currentCloudCount + pendingCloudCount + executors;

            if (totalCount > globalCap) {
                LOGGER.log(Level.INFO, "Would exceed global provisioning cap for cloud {0}. " +
                    "Current: {1}, Pending: {2}, Requested: {3}, Cap: {4}",
                    new Object[]{cloudKey, currentCloudCount, pendingCloudCount, executors, globalCap});
                return false;
            }
        }

        // Check template-specific limit including pending
        if (templateKey != null) {
            int templateCap = cloud.getTemplateProvisioningCap(extractTemplateIdFromKey(templateKey));
            if (templateCap != Integer.MAX_VALUE) {
                int currentTemplateCount = templateExecutorCounts.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).get();
                int pendingTemplateCount = pendingTemplateExecutors.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).get();
                int totalCount = currentTemplateCount + pendingTemplateCount + executors;

                if (totalCount > templateCap) {
                    LOGGER.log(Level.INFO, "Would exceed template provisioning cap for {0}. " +
                        "Current: {1}, Pending: {2}, Requested: {3}, Cap: {4}",
                        new Object[]{templateKey, currentTemplateCount, pendingTemplateCount, executors, templateCap});
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Reserves executors in pending counts.
     *
     * @param cloudKey the cloud key
     * @param templateKey the template key (can be null)
     * @param executors the number of executors to reserve
     */
    private void reservePendingExecutors(String cloudKey, String templateKey, int executors) {
        pendingCloudExecutors.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).addAndGet(executors);
        if (templateKey != null) {
            pendingTemplateExecutors.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).addAndGet(executors);
        }
    }

    /**
     * Extracts template ID from a template key.
     *
     * @param templateKey the template key in format "cloudname:templateid"
     * @return the template ID portion
     */
    private String extractTemplateIdFromKey(String templateKey) {
        int colonIndex = templateKey.indexOf(':');
        return colonIndex >= 0 ? templateKey.substring(colonIndex + 1) : templateKey;
    }

    /**
     * Confirms provisioning after successful node creation, moving from pending to active counts.
     *
     * This method should be called when planned nodes successfully become active nodes.
     *
     * @param cloud the cloud that completed provisioning
     * @param templateId the template identifier (can be null)
     * @param executors the number of executors that were successfully provisioned
     */
    public void confirmProvisioning(@NonNull Cloud cloud, String templateId, int executors) {
        if (!cloud.supportsProvisioningLimits()) {
            return;
        }

        batchLock.writeLock().lock();
        try {
            String cloudKey = cloud.name;
            String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

            // Move from pending to active counts
            AtomicInteger pendingCloud = pendingCloudExecutors.get(cloudKey);
            if (pendingCloud != null) {
                int newPending = pendingCloud.addAndGet(-executors);
                if (newPending <= 0) {
                    pendingCloudExecutors.remove(cloudKey);
                }
            }

            if (templateKey != null) {
                AtomicInteger pendingTemplate = pendingTemplateExecutors.get(templateKey);
                if (pendingTemplate != null) {
                    int newPending = pendingTemplate.addAndGet(-executors);
                    if (newPending <= 0) {
                        pendingTemplateExecutors.remove(templateKey);
                    }
                }
            }

            // Add to active counts
            cloudExecutorCounts.computeIfAbsent(cloudKey, k -> new AtomicInteger(0)).addAndGet(executors);
            if (templateKey != null) {
                templateExecutorCounts.computeIfAbsent(templateKey, k -> new AtomicInteger(0)).addAndGet(executors);
            }

            LOGGER.log(Level.FINE, "Confirmed provisioning of {0} executors for cloud {1}, template {2}",
                new Object[]{executors, cloudKey, templateId});

        } finally {
            batchLock.writeLock().unlock();
        }
    }

    /**
     * Unregisters executors when nodes are terminated or provisioning fails.
     *
     * This enhanced version handles both active and pending executor counts.
     *
     * @param cloud the cloud that was provisioning
     * @param templateId the template identifier (can be null)
     * @param executors the number of executors to unregister
     */
    public void unregister(@NonNull Cloud cloud, String templateId, int executors) {
        if (!cloud.supportsProvisioningLimits()) {
            return;
        }

        batchLock.writeLock().lock();
        try {
            String cloudKey = cloud.name;
            String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

            // Unregister from active cloud count
            AtomicInteger cloudCount = cloudExecutorCounts.get(cloudKey);
            if (cloudCount != null) {
                int newValue = cloudCount.addAndGet(-executors);
                if (newValue <= 0) {
                    cloudExecutorCounts.remove(cloudKey);
                }
            }

            // Unregister from active template count
            if (templateKey != null) {
                AtomicInteger templateCount = templateExecutorCounts.get(templateKey);
                if (templateCount != null) {
                    int newValue = templateCount.addAndGet(-executors);
                    if (newValue <= 0) {
                        templateExecutorCounts.remove(templateKey);
                    }
                }
            }

            LOGGER.log(Level.FINE, "Unregistered {0} active executors for cloud {1}, template {2}",
                new Object[]{executors, cloudKey, templateId});

        } finally {
            batchLock.writeLock().unlock();
        }
    }

    /**
     * Cancels pending provisioning, removing reservations that were made but never fulfilled.
     *
     * This method should be called when provisioning fails or is cancelled before nodes are created.
     *
     * @param cloud the cloud that was provisioning
     * @param templateId the template identifier (can be null)
     * @param executors the number of executors to cancel
     */
    public void cancelPendingProvisioning(@NonNull Cloud cloud, String templateId, int executors) {
        if (!cloud.supportsProvisioningLimits()) {
            return;
        }

        batchLock.writeLock().lock();
        try {
            String cloudKey = cloud.name;
            String templateKey = templateId != null ? cloudKey + ":" + templateId : null;

            // Remove from pending cloud count
            AtomicInteger pendingCloud = pendingCloudExecutors.get(cloudKey);
            if (pendingCloud != null) {
                int newPending = pendingCloud.addAndGet(-executors);
                if (newPending <= 0) {
                    pendingCloudExecutors.remove(cloudKey);
                }
            }

            // Remove from pending template count
            if (templateKey != null) {
                AtomicInteger pendingTemplate = pendingTemplateExecutors.get(templateKey);
                if (pendingTemplate != null) {
                    int newPending = pendingTemplate.addAndGet(-executors);
                    if (newPending <= 0) {
                        pendingTemplateExecutors.remove(templateKey);
                    }
                }
            }

            LOGGER.log(Level.FINE, "Cancelled pending provisioning of {0} executors for cloud {1}, template {2}",
                new Object[]{executors, cloudKey, templateId});

        } finally {
            batchLock.writeLock().unlock();
        }
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

    /**
     * Gets current concurrent access statistics for monitoring provisioning patterns.
     *
     * @return a map containing concurrent access statistics
     */
    public java.util.Map<String, Integer> getConcurrentAccessStatistics() {
        java.util.Map<String, Integer> stats = new java.util.HashMap<>();
        stats.put("currentConcurrentRegistrations", concurrentRegistrations.get());
        stats.put("totalRegistrationAttempts", totalRegistrationAttempts.get());
        stats.put("rejectedRegistrations", rejectedRegistrations.get());
        stats.put("successfulRegistrations", totalRegistrationAttempts.get() - rejectedRegistrations.get());

        // Calculate success rate
        int total = totalRegistrationAttempts.get();
        if (total > 0) {
            int successRate = ((total - rejectedRegistrations.get()) * 100) / total;
            stats.put("successRatePercentage", successRate);
        } else {
            stats.put("successRatePercentage", 100);
        }

        return stats;
    }

    /**
     * Gets pending executor counts for monitoring pending provisioning.
     *
     * @return a map containing pending counts by cloud and template
     */
    public java.util.Map<String, Integer> getPendingExecutorCounts() {
        java.util.Map<String, Integer> pending = new java.util.HashMap<>();

        batchLock.readLock().lock();
        try {
            // Add cloud pending counts
            for (java.util.Map.Entry<String, AtomicInteger> entry : pendingCloudExecutors.entrySet()) {
                pending.put("cloud:" + entry.getKey(), entry.getValue().get());
            }

            // Add template pending counts
            for (java.util.Map.Entry<String, AtomicInteger> entry : pendingTemplateExecutors.entrySet()) {
                pending.put("template:" + entry.getKey(), entry.getValue().get());
            }
        } finally {
            batchLock.readLock().unlock();
        }

        return pending;
    }

    /**
     * Resets concurrent access statistics.
     * This method can be useful for monitoring over specific time periods.
     */
    public void resetStatistics() {
        totalRegistrationAttempts.set(0);
        rejectedRegistrations.set(0);
        LOGGER.log(Level.INFO, "Reset CloudProvisioningLimits concurrent access statistics");
    }

    /**
     * Gets a summary of current provisioning state for debugging and monitoring.
     *
     * @return a formatted string containing current state information
     */
    public String getProvisioningStateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("CloudProvisioningLimits State Summary:\n");

        batchLock.readLock().lock();
        try {
            // Active counts
            summary.append("Active Cloud Executors: ").append(cloudExecutorCounts.size()).append(" clouds\n");
            for (java.util.Map.Entry<String, AtomicInteger> entry : cloudExecutorCounts.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get()).append("\n");
            }

            summary.append("Active Template Executors: ").append(templateExecutorCounts.size()).append(" templates\n");
            for (java.util.Map.Entry<String, AtomicInteger> entry : templateExecutorCounts.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get()).append("\n");
            }

            // Pending counts
            summary.append("Pending Cloud Executors: ").append(pendingCloudExecutors.size()).append(" clouds\n");
            for (java.util.Map.Entry<String, AtomicInteger> entry : pendingCloudExecutors.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get()).append("\n");
            }

            summary.append("Pending Template Executors: ").append(pendingTemplateExecutors.size()).append(" templates\n");
            for (java.util.Map.Entry<String, AtomicInteger> entry : pendingTemplateExecutors.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get()).append("\n");
            }

            // Statistics
            java.util.Map<String, Integer> stats = getConcurrentAccessStatistics();
            summary.append("Concurrent Access Statistics:\n");
            for (java.util.Map.Entry<String, Integer> entry : stats.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

        } finally {
            batchLock.readLock().unlock();
        }

        return summary.toString();
    }
}
