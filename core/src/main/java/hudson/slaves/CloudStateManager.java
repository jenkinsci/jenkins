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
import hudson.model.Label;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Enhanced cloud state management system that provides sophisticated cloud selection,
 * state tracking, and coordination for optimal provisioning decisions.
 *
 * This class addresses advanced provisioning scenarios by providing:
 * - Intelligent cloud ranking and selection
 * - Performance-based cloud prioritization
 * - Load balancing across multiple clouds
 * - Health monitoring and circuit breaker patterns
 * - Historical performance tracking for optimization
 *
 * The CloudStateManager works in conjunction with CloudProvisioningLimits and
 * ProvisioningMetrics to provide a comprehensive provisioning orchestration system.
 *
 * @author Mike Cirioli
 * @since 2.530
 */
public class CloudStateManager {

    private static final Logger LOGGER = Logger.getLogger(CloudStateManager.class.getName());

    private static final CloudStateManager INSTANCE = new CloudStateManager();

    /**
     * Cloud health status tracking.
     * Key: cloud.name
     */
    private final ConcurrentMap<String, CloudHealthStatus> cloudHealth = new ConcurrentHashMap<>();

    /**
     * Cloud performance metrics for selection optimization.
     * Key: cloud.name
     */
    private final ConcurrentMap<String, CloudPerformanceTracker> cloudPerformance = new ConcurrentHashMap<>();

    /**
     * Recent cloud selection history for load balancing.
     * Key: cloud.name, Value: last selection timestamp
     */
    private final ConcurrentMap<String, AtomicLong> lastSelectionTime = new ConcurrentHashMap<>();

    /**
     * Circuit breaker configuration
     */
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_RESET_TIME_MS = 300000; // 5 minutes
    private static final double MINIMUM_SUCCESS_RATE = 0.7; // 70%

    private CloudStateManager() {
        // Singleton
    }

    /**
     * Gets the singleton instance of CloudStateManager.
     *
     * @return the singleton instance
     */
    public static CloudStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * Gets an intelligently ordered list of clouds for provisioning a specific label.
     *
     * This method implements sophisticated cloud selection logic that considers:
     * - Cloud health status and circuit breaker states
     * - Historical performance and success rates
     * - Current load and capacity utilization
     * - Load balancing across multiple viable clouds
     *
     * @param label the label to provision for
     * @param requestedExecutors the number of executors needed
     * @return ordered list of clouds, best candidates first
     */
    public List<Cloud> getOptimalCloudOrder(@NonNull Label label, int requestedExecutors) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return Collections.emptyList();
        }

        List<CloudCandidate> candidates = new ArrayList<>();

        // Evaluate each cloud as a candidate
        for (Cloud cloud : jenkins.clouds) {
            CloudCandidate candidate = evaluateCloudCandidate(cloud, label, requestedExecutors);
            if (candidate != null && candidate.isViable()) {
                candidates.add(candidate);
            }
        }

        // Sort candidates by their computed scores (higher is better)
        candidates.sort(Comparator.comparingDouble(CloudCandidate::getScore).reversed());

        // Extract the ordered cloud list
        List<Cloud> orderedClouds = new ArrayList<>();
        for (CloudCandidate candidate : candidates) {
            orderedClouds.add(candidate.getCloud());
        }

        LOGGER.log(Level.FINE, "Optimal cloud order for label {0} ({1} executors): {2}",
            new Object[]{label, requestedExecutors,
                orderedClouds.stream().map(c -> c.name).toArray()});

        return orderedClouds;
    }

    /**
     * Records a provisioning attempt result for cloud health tracking.
     *
     * @param cloud the cloud that was used
     * @param success whether the provisioning was successful
     * @param duration the time taken for the provisioning attempt
     * @param executorsRequested the number of executors that were requested
     * @param executorsProvisioned the number of executors actually provisioned
     */
    public void recordProvisioningResult(@NonNull Cloud cloud, boolean success, long duration,
                                        int executorsRequested, int executorsProvisioned) {
        String cloudKey = cloud.name;

        // Update health status
        CloudHealthStatus health = getCloudHealth(cloudKey);
        health.recordAttempt(success);

        // Update performance tracking
        CloudPerformanceTracker performance = getCloudPerformance(cloudKey);
        performance.recordProvisioning(success, duration, executorsRequested, executorsProvisioned);

        // Update last selection time if successful
        if (success) {
            lastSelectionTime.computeIfAbsent(cloudKey, k -> new AtomicLong(0))
                .set(System.currentTimeMillis());
        }

        LOGGER.log(Level.FINE, "Recorded provisioning result for cloud {0}: success={1}, duration={2}ms",
            new Object[]{cloudKey, success, duration});
    }

    /**
     * Checks if a cloud is currently healthy and available for provisioning.
     *
     * @param cloud the cloud to check
     * @return true if the cloud is healthy and should be considered for provisioning
     */
    public boolean isCloudHealthy(@NonNull Cloud cloud) {
        CloudHealthStatus health = cloudHealth.get(cloud.name);
        if (health == null) {
            return true; // Unknown clouds are considered healthy initially
        }

        return health.isHealthy();
    }

    /**
     * Gets comprehensive state information for all clouds.
     *
     * @return a map containing detailed state information for monitoring
     */
    public java.util.Map<String, Object> getCloudStatesSummary() {
        java.util.Map<String, Object> summary = new java.util.HashMap<>();

        for (java.util.Map.Entry<String, CloudHealthStatus> entry : cloudHealth.entrySet()) {
            String cloudName = entry.getKey();
            CloudHealthStatus health = entry.getValue();
            CloudPerformanceTracker performance = cloudPerformance.get(cloudName);

            java.util.Map<String, Object> cloudInfo = new java.util.HashMap<>();
            cloudInfo.put("health", health.toMap());
            if (performance != null) {
                cloudInfo.put("performance", performance.toMap());
            }

            AtomicLong lastSelection = lastSelectionTime.get(cloudName);
            if (lastSelection != null) {
                cloudInfo.put("lastSelectionTime", lastSelection.get());
                cloudInfo.put("timeSinceLastSelection",
                    System.currentTimeMillis() - lastSelection.get());
            }

            summary.put(cloudName, cloudInfo);
        }

        return summary;
    }

    /**
     * Resets all cloud state tracking. Useful for testing or recovery scenarios.
     */
    public void resetCloudStates() {
        cloudHealth.clear();
        cloudPerformance.clear();
        lastSelectionTime.clear();
        LOGGER.log(Level.INFO, "Reset all cloud state tracking");
    }

    /**
     * Evaluates a cloud as a candidate for provisioning.
     */
    private CloudCandidate evaluateCloudCandidate(@NonNull Cloud cloud, @NonNull Label label, int requestedExecutors) {
        String cloudKey = cloud.name;

        // Check basic provisioning capability
        Cloud.CloudState cloudState = new Cloud.CloudState(label, 0);
        if (!cloud.canProvision(cloudState)) {
            return null;
        }

        // Check health status (circuit breaker)
        CloudHealthStatus health = getCloudHealth(cloudKey);
        if (!health.isHealthy()) {
            LOGGER.log(Level.FINE, "Cloud {0} is unhealthy, skipping", cloudKey);
            return null;
        }

        // Calculate composite score
        double score = calculateCloudScore(cloud, health, requestedExecutors);

        return new CloudCandidate(cloud, score);
    }

    /**
     * Calculates a composite score for cloud selection.
     */
    private double calculateCloudScore(@NonNull Cloud cloud, @NonNull CloudHealthStatus health, int requestedExecutors) {
        double score = 0.0;

        // Health component (40% of total score)
        double healthScore = health.getSuccessRate() * 0.4;
        score += healthScore;

        // Performance component (30% of total score)
        CloudPerformanceTracker performance = cloudPerformance.get(cloud.name);
        if (performance != null) {
            double performanceScore = performance.getPerformanceScore() * 0.3;
            score += performanceScore;
        } else {
            // New clouds get a neutral performance score
            score += 0.15; // 50% of 0.3
        }

        // Capacity component (20% of total score)
        double capacityScore = calculateCapacityScore(cloud, requestedExecutors) * 0.2;
        score += capacityScore;

        // Load balancing component (10% of total score)
        double loadBalanceScore = calculateLoadBalanceScore(cloud.name) * 0.1;
        score += loadBalanceScore;

        return score;
    }

    /**
     * Calculates capacity-based score considering limits and current usage.
     */
    private double calculateCapacityScore(@NonNull Cloud cloud, int requestedExecutors) {
        if (!cloud.supportsProvisioningLimits()) {
            return 1.0; // Full score for unlimited clouds
        }

        int globalCap = cloud.getGlobalProvisioningCap();
        if (globalCap == Integer.MAX_VALUE) {
            return 1.0;
        }

        // Get current usage
        int currentUsage = CloudProvisioningLimits.getInstance().getCloudExecutorCount(cloud.name);
        int availableCapacity = globalCap - currentUsage;

        if (availableCapacity < requestedExecutors) {
            return 0.0; // Cannot satisfy request
        }

        // Score based on remaining capacity ratio
        double utilizationRatio = (double) currentUsage / globalCap;
        return 1.0 - utilizationRatio; // Higher score for less utilized clouds
    }

    /**
     * Calculates load balancing score to distribute load across clouds.
     */
    private double calculateLoadBalanceScore(@NonNull String cloudName) {
        AtomicLong lastSelection = lastSelectionTime.get(cloudName);
        if (lastSelection == null) {
            return 1.0; // New clouds get full score
        }

        long timeSinceLastSelection = System.currentTimeMillis() - lastSelection.get();
        long fiveMinutes = 5 * 60 * 1000;

        // Score increases as time since last selection increases
        return Math.min(1.0, (double) timeSinceLastSelection / fiveMinutes);
    }

    /**
     * Gets or creates cloud health status tracking.
     */
    private CloudHealthStatus getCloudHealth(@NonNull String cloudKey) {
        return cloudHealth.computeIfAbsent(cloudKey, k -> new CloudHealthStatus());
    }

    /**
     * Gets or creates cloud performance tracking.
     */
    private CloudPerformanceTracker getCloudPerformance(@NonNull String cloudKey) {
        return cloudPerformance.computeIfAbsent(cloudKey, k -> new CloudPerformanceTracker());
    }

    /**
     * Represents a cloud candidate with its computed selection score.
     */
    private static class CloudCandidate {
        private final Cloud cloud;
        private final double score;

        CloudCandidate(@NonNull Cloud cloud, double score) {
            this.cloud = cloud;
            this.score = score;
        }

        Cloud getCloud() {
            return cloud;
        }

        double getScore() {
            return score;
        }

        boolean isViable() {
            return score > 0.0;
        }
    }

    /**
     * Tracks health status and implements circuit breaker pattern for clouds.
     */
    private static class CloudHealthStatus {
        private final AtomicInteger totalAttempts = new AtomicInteger(0);
        private final AtomicInteger successfulAttempts = new AtomicInteger(0);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);

        void recordAttempt(boolean success) {
            totalAttempts.incrementAndGet();
            if (success) {
                successfulAttempts.incrementAndGet();
                consecutiveFailures.set(0);
            } else {
                consecutiveFailures.incrementAndGet();
                lastFailureTime.set(System.currentTimeMillis());
            }
        }

        boolean isHealthy() {
            // Circuit breaker logic
            int failures = consecutiveFailures.get();
            if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
                long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
                if (timeSinceLastFailure < CIRCUIT_BREAKER_RESET_TIME_MS) {
                    return false; // Circuit breaker open
                } else {
                    // Reset circuit breaker for retry
                    consecutiveFailures.set(0);
                }
            }

            // Check minimum success rate
            int total = totalAttempts.get();
            if (total >= 10) { // Only apply after sufficient data
                double successRate = (double) successfulAttempts.get() / total;
                return successRate >= MINIMUM_SUCCESS_RATE;
            }

            return true; // Healthy until proven otherwise
        }

        double getSuccessRate() {
            int total = totalAttempts.get();
            if (total == 0) {
                return 1.0; // No data means perfect score
            }
            return (double) successfulAttempts.get() / total;
        }

        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("totalAttempts", totalAttempts.get());
            map.put("successfulAttempts", successfulAttempts.get());
            map.put("successRate", getSuccessRate());
            map.put("consecutiveFailures", consecutiveFailures.get());
            map.put("isHealthy", isHealthy());
            return map;
        }
    }

    /**
     * Tracks performance metrics for cloud selection optimization.
     */
    private static class CloudPerformanceTracker {
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicInteger provisioningCount = new AtomicInteger(0);
        private final AtomicLong totalRequestedExecutors = new AtomicLong(0);
        private final AtomicLong totalProvisionedExecutors = new AtomicLong(0);

        void recordProvisioning(boolean success, long duration, int requested, int provisioned) {
            if (success) {
                totalDuration.addAndGet(duration);
                provisioningCount.incrementAndGet();
                totalRequestedExecutors.addAndGet(requested);
                totalProvisionedExecutors.addAndGet(provisioned);
            }
        }

        double getPerformanceScore() {
            int count = provisioningCount.get();
            if (count == 0) {
                return 0.5; // Neutral score for no data
            }

            // Average duration score (lower is better, normalized)
            double avgDuration = (double) totalDuration.get() / count;
            double durationScore = Math.max(0.0, 1.0 - (avgDuration / 300000.0)); // 5 minutes baseline

            // Provisioning efficiency score
            long requested = totalRequestedExecutors.get();
            long provisioned = totalProvisionedExecutors.get();
            double efficiencyScore = requested > 0 ? (double) provisioned / requested : 1.0;

            // Combine scores (70% efficiency, 30% speed)
            return (efficiencyScore * 0.7) + (durationScore * 0.3);
        }

        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            int count = provisioningCount.get();
            map.put("provisioningCount", count);
            if (count > 0) {
                map.put("averageDuration", (double) totalDuration.get() / count);
                long requested = totalRequestedExecutors.get();
                if (requested > 0) {
                    map.put("provisioningEfficiency", (double) totalProvisionedExecutors.get() / requested);
                }
            }
            map.put("performanceScore", getPerformanceScore());
            return map;
        }
    }
}
