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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks comprehensive metrics for cloud provisioning operations to provide
 * insights into performance, efficiency, and patterns.
 *
 * This class provides detailed metrics for monitoring and optimizing the
 * Jenkins cloud provisioning system, helping administrators understand
 * provisioning behavior and identify bottlenecks.
 *
 * Key metrics tracked:
 * - Provisioning success/failure rates by cloud and strategy
 * - Timing metrics for provisioning operations
 * - Capacity utilization and demand patterns
 * - Resource efficiency metrics
 * - Error patterns and recovery statistics
 *
 * @author Mike Cirioli
 * @since 2.530
 */
public class ProvisioningMetrics {

    private static final Logger LOGGER = Logger.getLogger(ProvisioningMetrics.class.getName());

    private static final ProvisioningMetrics INSTANCE = new ProvisioningMetrics();

    /**
     * Metrics for provisioning attempts by cloud.
     * Key format: cloud.name
     */
    private final ConcurrentMap<String, CloudMetrics> cloudMetrics = new ConcurrentHashMap<>();

    /**
     * Metrics for provisioning attempts by strategy.
     * Key format: strategy class simple name
     */
    private final ConcurrentMap<String, StrategyMetrics> strategyMetrics = new ConcurrentHashMap<>();

    /**
     * Metrics for provisioning attempts by label.
     * Key format: label.name (or "unlabeled" for null labels)
     */
    private final ConcurrentMap<String, LabelMetrics> labelMetrics = new ConcurrentHashMap<>();

    /**
     * Global provisioning metrics across all clouds and strategies.
     */
    private final GlobalMetrics globalMetrics = new GlobalMetrics();

    private ProvisioningMetrics() {
        // Singleton
    }

    /**
     * Gets the singleton instance of ProvisioningMetrics.
     *
     * @return the singleton instance
     */
    public static ProvisioningMetrics getInstance() {
        return INSTANCE;
    }

    /**
     * Records a provisioning attempt start.
     *
     * @param cloud the cloud being used
     * @param strategy the strategy being used
     * @param label the label being provisioned for
     * @param requestedExecutors the number of executors requested
     * @return a context object for tracking this provisioning attempt
     */
    public ProvisioningAttemptContext startProvisioningAttempt(@NonNull Cloud cloud,
                                                               @NonNull String strategy,
                                                               Label label,
                                                               int requestedExecutors) {
        String cloudKey = cloud.name;
        String labelKey = label != null ? label.getName() : "unlabeled";

        // Increment attempt counters
        getCloudMetrics(cloudKey).attempts.incrementAndGet();
        getStrategyMetrics(strategy).attempts.incrementAndGet();
        getLabelMetrics(labelKey).attempts.incrementAndGet();
        globalMetrics.attempts.incrementAndGet();

        // Track requested executors
        getCloudMetrics(cloudKey).totalRequestedExecutors.addAndGet(requestedExecutors);
        getStrategyMetrics(strategy).totalRequestedExecutors.addAndGet(requestedExecutors);
        getLabelMetrics(labelKey).totalRequestedExecutors.addAndGet(requestedExecutors);
        globalMetrics.totalRequestedExecutors.addAndGet(requestedExecutors);

        LOGGER.log(Level.FINEST, "Started provisioning attempt: cloud={0}, strategy={1}, label={2}, requested={3}",
            new Object[]{cloudKey, strategy, labelKey, requestedExecutors});

        return new ProvisioningAttemptContext(cloudKey, strategy, labelKey, requestedExecutors, System.currentTimeMillis());
    }

    /**
     * Records a successful provisioning completion.
     *
     * @param context the provisioning attempt context
     * @param actualExecutors the number of executors actually provisioned
     */
    public void recordProvisioningSuccess(@NonNull ProvisioningAttemptContext context, int actualExecutors) {
        long duration = System.currentTimeMillis() - context.startTime;

        // Record success metrics
        getCloudMetrics(context.cloudKey).successes.incrementAndGet();
        getStrategyMetrics(context.strategyKey).successes.incrementAndGet();
        getLabelMetrics(context.labelKey).successes.incrementAndGet();
        globalMetrics.successes.incrementAndGet();

        // Record provisioned executors
        getCloudMetrics(context.cloudKey).totalProvisionedExecutors.addAndGet(actualExecutors);
        getStrategyMetrics(context.strategyKey).totalProvisionedExecutors.addAndGet(actualExecutors);
        getLabelMetrics(context.labelKey).totalProvisionedExecutors.addAndGet(actualExecutors);
        globalMetrics.totalProvisionedExecutors.addAndGet(actualExecutors);

        // Record timing metrics
        getCloudMetrics(context.cloudKey).updateTiming(duration);
        getStrategyMetrics(context.strategyKey).updateTiming(duration);
        getLabelMetrics(context.labelKey).updateTiming(duration);
        globalMetrics.updateTiming(duration);

        LOGGER.log(Level.FINE, "Recorded provisioning success: cloud={0}, strategy={1}, label={2}, " +
            "requested={3}, actual={4}, duration={5}ms",
            new Object[]{context.cloudKey, context.strategyKey, context.labelKey,
                context.requestedExecutors, actualExecutors, duration});
    }

    /**
     * Records a provisioning failure.
     *
     * @param context the provisioning attempt context
     * @param reason the reason for failure
     */
    public void recordProvisioningFailure(@NonNull ProvisioningAttemptContext context, @NonNull String reason) {
        long duration = System.currentTimeMillis() - context.startTime;

        // Record failure metrics
        getCloudMetrics(context.cloudKey).failures.incrementAndGet();
        getStrategyMetrics(context.strategyKey).failures.incrementAndGet();
        getLabelMetrics(context.labelKey).failures.incrementAndGet();
        globalMetrics.failures.incrementAndGet();

        // Record failure reasons
        getCloudMetrics(context.cloudKey).recordFailureReason(reason);
        getStrategyMetrics(context.strategyKey).recordFailureReason(reason);
        getLabelMetrics(context.labelKey).recordFailureReason(reason);

        // Record timing for failures too (useful for timeout analysis)
        getCloudMetrics(context.cloudKey).updateTiming(duration);
        getStrategyMetrics(context.strategyKey).updateTiming(duration);
        getLabelMetrics(context.labelKey).updateTiming(duration);
        globalMetrics.updateTiming(duration);

        LOGGER.log(Level.INFO, "Recorded provisioning failure: cloud={0}, strategy={1}, label={2}, " +
            "requested={3}, reason={4}, duration={5}ms",
            new Object[]{context.cloudKey, context.strategyKey, context.labelKey,
                context.requestedExecutors, reason, duration});
    }

    /**
     * Records when provisioned nodes become available.
     *
     * @param cloudKey the cloud key
     * @param executors the number of executors that became available
     * @param provisioningToAvailableDuration time from provisioning start to availability
     */
    public void recordNodeAvailability(@NonNull String cloudKey, int executors, long provisioningToAvailableDuration) {
        getCloudMetrics(cloudKey).availableExecutors.addAndGet(executors);
        getCloudMetrics(cloudKey).updateAvailabilityTiming(provisioningToAvailableDuration);
        globalMetrics.totalAvailableExecutors.addAndGet(executors);
        globalMetrics.updateAvailabilityTiming(provisioningToAvailableDuration);

        LOGGER.log(Level.FINE, "Recorded node availability: cloud={0}, executors={1}, duration={2}ms",
            new Object[]{cloudKey, executors, provisioningToAvailableDuration});
    }

    /**
     * Gets cloud-specific metrics.
     */
    private CloudMetrics getCloudMetrics(String cloudKey) {
        return cloudMetrics.computeIfAbsent(cloudKey, k -> new CloudMetrics());
    }

    /**
     * Gets strategy-specific metrics.
     */
    private StrategyMetrics getStrategyMetrics(String strategyKey) {
        return strategyMetrics.computeIfAbsent(strategyKey, k -> new StrategyMetrics());
    }

    /**
     * Gets label-specific metrics.
     */
    private LabelMetrics getLabelMetrics(String labelKey) {
        return labelMetrics.computeIfAbsent(labelKey, k -> new LabelMetrics());
    }

    /**
     * Gets comprehensive metrics summary for monitoring and reporting.
     *
     * @return a map containing all metrics
     */
    public java.util.Map<String, Object> getMetricsSummary() {
        java.util.Map<String, Object> summary = new java.util.HashMap<>();

        // Global metrics
        summary.put("global", globalMetrics.toMap());

        // Cloud metrics
        java.util.Map<String, Object> cloudSummary = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, CloudMetrics> entry : cloudMetrics.entrySet()) {
            cloudSummary.put(entry.getKey(), entry.getValue().toMap());
        }
        summary.put("clouds", cloudSummary);

        // Strategy metrics
        java.util.Map<String, Object> strategySummary = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, StrategyMetrics> entry : strategyMetrics.entrySet()) {
            strategySummary.put(entry.getKey(), entry.getValue().toMap());
        }
        summary.put("strategies", strategySummary);

        // Label metrics
        java.util.Map<String, Object> labelSummary = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, LabelMetrics> entry : labelMetrics.entrySet()) {
            labelSummary.put(entry.getKey(), entry.getValue().toMap());
        }
        summary.put("labels", labelSummary);

        return summary;
    }

    /**
     * Resets all metrics. Useful for testing or periodic metric resets.
     */
    public void resetMetrics() {
        cloudMetrics.clear();
        strategyMetrics.clear();
        labelMetrics.clear();
        globalMetrics.reset();
        LOGGER.log(Level.INFO, "Reset all provisioning metrics");
    }

    /**
     * Context object for tracking individual provisioning attempts.
     */
    public static class ProvisioningAttemptContext {
        final String cloudKey;
        final String strategyKey;
        final String labelKey;
        final int requestedExecutors;
        final long startTime;

        ProvisioningAttemptContext(String cloudKey, String strategyKey, String labelKey, int requestedExecutors, long startTime) {
            this.cloudKey = cloudKey;
            this.strategyKey = strategyKey;
            this.labelKey = labelKey;
            this.requestedExecutors = requestedExecutors;
            this.startTime = startTime;
        }
    }

    /**
     * Base class for metrics tracking common provisioning statistics.
     */
    private abstract static class BaseMetrics {
        final AtomicInteger attempts = new AtomicInteger(0);
        final AtomicInteger successes = new AtomicInteger(0);
        final AtomicInteger failures = new AtomicInteger(0);
        final AtomicLong totalRequestedExecutors = new AtomicLong(0);
        final AtomicLong totalProvisionedExecutors = new AtomicLong(0);

        // Timing metrics
        final AtomicLong totalDuration = new AtomicLong(0);
        final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxDuration = new AtomicLong(0);

        // Failure reasons
        final ConcurrentMap<String, AtomicInteger> failureReasons = new ConcurrentHashMap<>();

        void updateTiming(long duration) {
            totalDuration.addAndGet(duration);
            updateMin(minDuration, duration);
            updateMax(maxDuration, duration);
        }

        void recordFailureReason(String reason) {
            failureReasons.computeIfAbsent(reason, k -> new AtomicInteger(0)).incrementAndGet();
        }

        private void updateMin(AtomicLong minValue, long newValue) {
            long currentMin;
            do {
                currentMin = minValue.get();
                if (newValue >= currentMin) break;
            } while (!minValue.compareAndSet(currentMin, newValue));
        }

        private void updateMax(AtomicLong maxValue, long newValue) {
            long currentMax;
            do {
                currentMax = maxValue.get();
                if (newValue <= currentMax) break;
            } while (!maxValue.compareAndSet(currentMax, newValue));
        }

        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();

            int attemptsCount = attempts.get();
            int successesCount = successes.get();
            int failuresCount = failures.get();

            map.put("attempts", attemptsCount);
            map.put("successes", successesCount);
            map.put("failures", failuresCount);
            map.put("successRate", attemptsCount > 0 ? (double) successesCount / attemptsCount : 0.0);
            map.put("failureRate", attemptsCount > 0 ? (double) failuresCount / attemptsCount : 0.0);

            map.put("totalRequestedExecutors", totalRequestedExecutors.get());
            map.put("totalProvisionedExecutors", totalProvisionedExecutors.get());

            long totalDur = totalDuration.get();
            map.put("totalDuration", totalDur);
            map.put("averageDuration", attemptsCount > 0 ? (double) totalDur / attemptsCount : 0.0);
            map.put("minDuration", minDuration.get() == Long.MAX_VALUE ? 0 : minDuration.get());
            map.put("maxDuration", maxDuration.get());

            // Failure reasons
            java.util.Map<String, Integer> reasons = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, AtomicInteger> entry : failureReasons.entrySet()) {
                reasons.put(entry.getKey(), entry.getValue().get());
            }
            map.put("failureReasons", reasons);

            return map;
        }
    }

    /**
     * Metrics specific to cloud providers.
     */
    private static class CloudMetrics extends BaseMetrics {
        final AtomicLong availableExecutors = new AtomicLong(0);
        final AtomicLong totalAvailabilityDuration = new AtomicLong(0);
        final AtomicInteger availabilityEvents = new AtomicInteger(0);

        void updateAvailabilityTiming(long duration) {
            totalAvailabilityDuration.addAndGet(duration);
            availabilityEvents.incrementAndGet();
        }

        @Override
        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = super.toMap();
            map.put("availableExecutors", availableExecutors.get());

            int events = availabilityEvents.get();
            long totalAvailDuration = totalAvailabilityDuration.get();
            map.put("averageAvailabilityDuration", events > 0 ? (double) totalAvailDuration / events : 0.0);

            return map;
        }
    }

    /**
     * Metrics specific to provisioning strategies.
     */
    private static class StrategyMetrics extends BaseMetrics {
        // Strategy metrics extend base metrics with no additional fields currently
        // This structure allows for future strategy-specific metrics
    }

    /**
     * Metrics specific to labels.
     */
    private static class LabelMetrics extends BaseMetrics {
        // Label metrics extend base metrics with no additional fields currently
        // This structure allows for future label-specific metrics
    }

    /**
     * Global metrics across all provisioning operations.
     */
    private static class GlobalMetrics extends BaseMetrics {
        final AtomicLong totalAvailableExecutors = new AtomicLong(0);
        final AtomicLong totalAvailabilityDuration = new AtomicLong(0);
        final AtomicInteger availabilityEvents = new AtomicInteger(0);

        void updateAvailabilityTiming(long duration) {
            totalAvailabilityDuration.addAndGet(duration);
            availabilityEvents.incrementAndGet();
        }

        void reset() {
            attempts.set(0);
            successes.set(0);
            failures.set(0);
            totalRequestedExecutors.set(0);
            totalProvisionedExecutors.set(0);
            totalDuration.set(0);
            minDuration.set(Long.MAX_VALUE);
            maxDuration.set(0);
            totalAvailableExecutors.set(0);
            totalAvailabilityDuration.set(0);
            availabilityEvents.set(0);
            failureReasons.clear();
        }

        @Override
        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = super.toMap();
            map.put("totalAvailableExecutors", totalAvailableExecutors.get());

            int events = availabilityEvents.get();
            long totalAvailDuration = totalAvailabilityDuration.get();
            map.put("averageAvailabilityDuration", events > 0 ? (double) totalAvailDuration / events : 0.0);

            return map;
        }
    }
}
