/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import static hudson.model.LoadStatistics.DECAY;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.MultiStageTimeSeries;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import java.awt.Color;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import net.jcip.annotations.GuardedBy;
import org.jenkinsci.Symbol;

/**
 * Uses the {@link LoadStatistics} and determines when we need to allocate
 * new {@link Node}s through {@link Cloud}.
 *
 * @author Kohsuke Kawaguchi
 */
public class NodeProvisioner {
    /**
     * The node addition activity in progress.
     */
    public static class PlannedNode {
        /**
         * Used to display this planned node to UI. Should ideally include the identifier unique to the node
         * being provisioned (like the instance ID), but if such an identifier doesn't readily exist, this
         * can be just a name of the template being provisioned (like the machine image ID.)
         */
        public final String displayName;

        /**
         * Used to launch and return a {@link Node} object. {@link NodeProvisioner} will check
         * this {@link Future}'s isDone() method to determine when to finalize this object.
         */
        public final Future<Node> future;

        /**
         * The number of executors that will be provided by the {@link Node} launched by
         * this object. This is used for capacity planning in {@link NodeProvisioner#update}.
         */
        public final int numExecutors;

        /**
         * Construct a PlannedNode instance without {@link Cloud} callback for finalization.
         *
         * @param displayName Used to display this object in the UI.
         * @param future Used to launch a @{link Node} object.
         * @param numExecutors The number of executors that will be provided by the launched {@link Node}.
         */
        public PlannedNode(String displayName, Future<Node> future, int numExecutors) {
            if (displayName == null || future == null || numExecutors < 1)  throw new IllegalArgumentException();
            this.displayName = displayName;
            this.future = future;
            this.numExecutors = numExecutors;
        }

        /**
         * Indicate that this {@link PlannedNode} is being finalized.
         *
         * <p>
         * {@link NodeProvisioner} will call this method when it's done with {@link PlannedNode}.
         * This indicates that the {@link PlannedNode}'s work has been completed
         * (successfully or otherwise) and it is about to be removed from the list of pending
         * {@link Node}s to be launched.
         *
         * <p>
         * Create a subtype of this class and override this method to add any necessary behaviour.
         *
         * @since 1.503
         */
        public void spent() {
        }
    }

    /**
     * Load for the label.
     */
    private final LoadStatistics stat;

    /**
     * For which label are we working?
     * Null if this {@link NodeProvisioner} is working for the entire Hudson,
     * for jobs that are unassigned to any particular node.
     */
    @CheckForNull
    private final Label label;

    private final AtomicReference<List<PlannedNode>> pendingLaunches
            = new AtomicReference<>(new ArrayList<>());

    private final Lock provisioningLock = new ReentrantLock();

    @GuardedBy("provisioningLock")
    private StrategyState provisioningState = null;

    private transient volatile long lastSuggestedReview;
    private transient volatile boolean queuedReview;

    /**
     * Exponential moving average of the "planned capacity" over time, which is the number of
     * additional executors being brought up.
     *
     * This is used to filter out high-frequency components from the planned capacity, so that
     * the comparison with other low-frequency only variables won't leave spikes.
     */
    private final MultiStageTimeSeries plannedCapacitiesEMA =
            new MultiStageTimeSeries(Messages._NodeProvisioner_EmptyString(), Color.WHITE, 0, DECAY);

    public NodeProvisioner(@CheckForNull Label label, LoadStatistics loadStatistics) {
        this.label = label;
        this.stat = loadStatistics;
    }

    /**
     * Nodes that are being launched.
     *
     * @return
     *      Can be empty but never null
     * @since 1.401
     */
    public List<PlannedNode> getPendingLaunches() {
        return new ArrayList<>(pendingLaunches.get());
    }

    /**
     * Give the {@link NodeProvisioner} a hint that now would be a good time to think about provisioning some nodes.
     * Hints are throttled to one every second.
     *
     * @since 1.415
     */
    public void suggestReviewNow() {
        if (!queuedReview) {
            long delay = TimeUnit.SECONDS.toMillis(1) - (System.currentTimeMillis() - lastSuggestedReview);
            if (delay < 0) {
                lastSuggestedReview = System.currentTimeMillis();
                Computer.threadPoolForRemoting.submit(() -> {
                    LOGGER.fine(() -> "running suggested review for " + label);
                    update();
                });
            } else {
                queuedReview = true;
                LOGGER.fine(() -> "running suggested review in " + delay + " ms for " + label);
                Timer.get().schedule(() -> {
                    lastSuggestedReview = System.currentTimeMillis();
                    LOGGER.fine(() -> "running suggested review for " + label + " after " + delay + " ms");
                    update();
                }, delay, TimeUnit.MILLISECONDS);
            }
        } else {
            LOGGER.fine(() -> "ignoring suggested review for " + label);
        }
    }

    /**
     * Periodically invoked to keep track of the load.
     * Launches additional nodes if necessary.
     *
     * Note: This method will obtain a lock on {@link #provisioningLock} first (to ensure that one and only one
     * instance of this provisioner is running at a time) and then a lock on {@link Queue#lock}
     */
    private void update() {
        long start = LOGGER.isLoggable(Level.FINER) ? System.nanoTime() : 0;
        provisioningLock.lock();
        try {
            lastSuggestedReview = System.currentTimeMillis();
            queuedReview = false;
            Jenkins jenkins = Jenkins.get();
            // clean up the cancelled launch activity, then count the # of executors that we are about to
            // bring up.

            int plannedCapacitySnapshot = 0;

            List<PlannedNode> snapPendingLaunches = new ArrayList<>(pendingLaunches.get());
            for (PlannedNode f : snapPendingLaunches) {
                if (f.future.isDone()) {
                    try {
                        Node node = null;
                        try {
                            node = f.future.get();
                        } catch (InterruptedException e) {
                            throw new AssertionError("InterruptedException occurred", e); // since we confirmed that the future is already done
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause();
                            if (!(cause instanceof AbortException)) {
                                LOGGER.log(Level.WARNING,
                                        "Unexpected exception encountered while provisioning agent "
                                                + f.displayName,
                                        cause);
                            }
                            fireOnFailure(f, cause);
                        }

                        if (node != null) {
                            fireOnComplete(f, node);

                            try {
                                jenkins.addNode(node);
                                LOGGER.log(Level.INFO,
                                        "{0} provisioning successfully completed. "
                                                + "We have now {1,number,integer} computer(s)",
                                        new Object[]{f.displayName, jenkins.getComputers().length});
                                fireOnCommit(f, node);
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING,
                                        "Provisioned agent " + f.displayName + " failed to launch",
                                        e);
                                fireOnRollback(f, node, e);
                            }
                        }
                    } catch (Error e) {
                        // we are not supposed to try and recover from Errors
                        throw e;
                    } catch (Throwable e) {
                        // Just log it
                        LOGGER.log(Level.SEVERE,
                                "Unexpected uncaught exception encountered while processing agent "
                                        + f.displayName,
                                e);
                    } finally {
                        while (true) {
                            List<PlannedNode> orig = pendingLaunches.get();
                            List<PlannedNode> repl = new ArrayList<>(orig);
                            // the contract for List.remove(o) is that the first element i where
                            // (o==null ? get(i)==null : o.equals(get(i)))
                            // is true will be removed from the list
                            // since PlannedNode.equals(o) is not final and we cannot assume
                            // that subclasses do not override with an equals which does not
                            // assure object identity comparison, we need to manually
                            // do the removal based on instance identity not equality
                            boolean changed = false;
                            for (Iterator<PlannedNode> iterator = repl.iterator(); iterator.hasNext(); ) {
                                PlannedNode p = iterator.next();
                                if (p == f) {
                                    iterator.remove();
                                    changed = true;
                                    break;
                                }
                            }
                            if (!changed || pendingLaunches.compareAndSet(orig, repl)) {
                                break;
                            }
                        }
                        f.spent();
                    }
                } else {
                    plannedCapacitySnapshot += f.numExecutors;
                }
            }

            float plannedCapacity = plannedCapacitySnapshot;
            plannedCapacitiesEMA.update(plannedCapacity);

            final LoadStatistics.LoadStatisticsSnapshot snapshot = stat.computeSnapshot();

            int availableSnapshot = snapshot.getAvailableExecutors();
            int queueLengthSnapshot = snapshot.getQueueLength();

            if (queueLengthSnapshot <= availableSnapshot) {
                LOGGER.log(Level.FINER,
                        "Queue length {0} is less than the available capacity {1}. No provisioning strategy required",
                        new Object[]{queueLengthSnapshot, availableSnapshot});
                provisioningState = null;
            } else {
                provisioningState = new StrategyState(snapshot, label, plannedCapacitySnapshot);
            }

            if (provisioningState != null) {
                List<Strategy> strategies = Jenkins.get().getExtensionList(Strategy.class);
                for (Strategy strategy : strategies.isEmpty()
                        ? List.of(new StandardStrategyImpl())
                        : strategies) {
                    LOGGER.log(Level.FINER, "Consulting {0} provisioning strategy with state {1}",
                            new Object[]{strategy, provisioningState});
                    if (StrategyDecision.PROVISIONING_COMPLETED == strategy.apply(provisioningState)) {
                        LOGGER.log(Level.FINER, "Provisioning strategy {0} declared provisioning complete",
                                strategy);
                        break;
                    }
                }
            }
        } finally {
            provisioningLock.unlock();
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(() -> "ran update on " + label + " in " + (System.nanoTime() - start) / 1_000_000 + "ms");
        }
    }


    /**
     * Represents the decision taken by an individual {@link hudson.slaves.NodeProvisioner.Strategy}.
     * @since 1.588
     */
    public enum StrategyDecision {
        /**
         * This decision is the default decision and indicates that the {@link hudson.slaves.NodeProvisioner.Strategy}
         * either could not provision sufficient resources or did not take any action. Any remaining strategies
         * will be able to contribute to the ultimate decision.
         */
        CONSULT_REMAINING_STRATEGIES,
        /**
         * This decision indicates that the {@link hudson.slaves.NodeProvisioner.Strategy} has taken sufficient
         * action so as to ensure that the required resources are available, and therefore there is no requirement
         * to consult the remaining strategies. Only return this decision when you are certain that there is no
         * need for additional provisioning actions (i.e. you detected an excess workload and have provisioned enough
         * for that excess workload).
         */
        PROVISIONING_COMPLETED
    }

    /**
     * Extension point for node provisioning strategies.
     * @since 1.588
     */
    public abstract static class Strategy implements ExtensionPoint {

        /**
         * Called by {@link NodeProvisioner#update()} to apply this strategy against the specified state.
         * Any provisioning activities should be recorded by calling
         * {@link hudson.slaves.NodeProvisioner.StrategyState#recordPendingLaunches(java.util.Collection)}
         * This method will be called by a thread that is holding {@link hudson.slaves.NodeProvisioner#provisioningLock}
         * @param state the current state.
         * @return the decision.
         */
        @NonNull
        @GuardedBy("NodeProvisioner.this")
        public abstract StrategyDecision apply(@NonNull StrategyState state);

    }

    /**
     * Parameter object for {@link hudson.slaves.NodeProvisioner.Strategy}.
     * Intentionally non-static as we need to reference some fields in {@link hudson.slaves.NodeProvisioner}
     * @since 1.588
     */
    public final class StrategyState {
        /**
         * The label under consideration.
         */
        @CheckForNull
        private final Label label;
        /**
         * The planned capacity for this {@link #label}.
         */
        private final int plannedCapacitySnapshot;
        /**
         * The current statistics snapshot for this {@link #label}.
         */
        private final LoadStatistics.LoadStatisticsSnapshot snapshot;
        /**
         * The additional planned capacity for this {@link #label} and provisioned by previous strategies during the
         * current updating of the {@link NodeProvisioner}.
         */
        @GuardedBy("this")
        private int additionalPlannedCapacity;

        /**
         * Should only be instantiated by {@link NodeProvisioner#update()}
         * @param label the label.
         * @param plannedCapacitySnapshot the planned executor count.
         */
        private StrategyState(LoadStatistics.LoadStatisticsSnapshot snapshot, @CheckForNull Label label, int plannedCapacitySnapshot) {
            this.snapshot = snapshot;
            this.label = label;
            this.plannedCapacitySnapshot = plannedCapacitySnapshot;
        }

        /**
         * The label under consideration.
         */
        @CheckForNull
        public Label getLabel() {
            return label;
        }

        /**
         * The current snapshot of the load statistics for this {@link #getLabel()}.
         * @since 1.607
         */
        public LoadStatistics.LoadStatisticsSnapshot getSnapshot() {
            return snapshot;
        }

        /**
         * The number of items in the queue requiring this {@link #getLabel()}.
         * @deprecated use {@link #getSnapshot()}, {@link LoadStatistics.LoadStatisticsSnapshot#getQueueLength()}
         */
        @Deprecated
        public int getQueueLengthSnapshot() {
            return snapshot.getQueueLength();
        }

        /**
         * The planned capacity for this {@link #getLabel()}.
         */
        public int getPlannedCapacitySnapshot() {
            return plannedCapacitySnapshot;
        }

        /**
         * The number of idle executors for this {@link #getLabel()}
         * @deprecated use {@link #getSnapshot()}, {@link LoadStatistics.LoadStatisticsSnapshot#getAvailableExecutors()}
         */
        @Deprecated
        public int getIdleSnapshot() {
            return snapshot.getAvailableExecutors();
        }

        /**
         * The total number of executors for this {@link #getLabel()}
         * @deprecated use {@link #getSnapshot()}, {@link LoadStatistics.LoadStatisticsSnapshot#getOnlineExecutors()}
         */
        @Deprecated
        public int getTotalSnapshot() {
            return snapshot.getOnlineExecutors();
        }

        /**
         * The additional planned capacity for this {@link #getLabel()} and provisioned by previous strategies during
         * the current updating of the {@link NodeProvisioner}.
         */
        public synchronized int getAdditionalPlannedCapacity() {
            return additionalPlannedCapacity;
        }

        /**
         * The time series average number of items in the queue requiring this {@link #getLabel()}.
         */
        public float getQueueLengthLatest() {
            return stat.queueLength.getLatest(TIME_SCALE);
        }

        /**
         * The time series average planned capacity for this {@link #getLabel()}.
         */
        public float getPlannedCapacityLatest() {
            return plannedCapacitiesEMA.getLatest(TIME_SCALE);
        }

        /**
         * The time series average number of idle executors for this {@link #getLabel()}
         * @deprecated use {@link #getAvailableExecutorsLatest()}
         */
        @Deprecated
        public float getIdleLatest() {
            return getAvailableExecutorsLatest();
        }

        /**
         * The time series average total number of executors for this {@link #getLabel()}
         * @deprecated use {@link #getOnlineExecutorsLatest()}
         */
        @Deprecated
        public float getTotalLatest() {
            return getOnlineExecutorsLatest();
        }

        /**
         * The time series average number of defined executors for this {@link #getLabel()}
         * @since 1.607
         */
        public float getDefinedExecutorsLatest() {
            return stat.definedExecutors.getLatest(TIME_SCALE);
        }

        /**
         * The time series average number of online executors for this {@link #getLabel()}
         * @since 1.607
         */
        public float getOnlineExecutorsLatest() {
            return stat.onlineExecutors.getLatest(TIME_SCALE);
        }

        /**
         * The time series average number of connecting executors for this {@link #getLabel()}
         * @since 1.607
         */
        public float getConnectingExecutorsLatest() {
            return stat.connectingExecutors.getLatest(TIME_SCALE);
        }

        /**
         * The time series average number of busy executors for this {@link #getLabel()}
         * @since 1.607
         */
        public float getBusyExecutorsLatest() {
            return stat.busyExecutors.getLatest(TIME_SCALE);
        }

        /**
         * The time series average number of idle executors for this {@link #getLabel()}
         * @since 1.607
         */
        public float getIdleExecutorsLatest() {
            return stat.idleExecutors.getLatest(TIME_SCALE);
        }

        /**
         * The time series average number of available executors for this {@link #getLabel()}
         * @since 1.607
         */
        public float getAvailableExecutorsLatest() {
            return stat.availableExecutors.getLatest(TIME_SCALE);
        }

        /**
         * If a {@link hudson.slaves.NodeProvisioner.Strategy} takes some provisioning action, it should record
         * and {@link hudson.slaves.NodeProvisioner.PlannedNode} instances by calling this method.
         *
         * @param plannedNodes the {@link hudson.slaves.NodeProvisioner.PlannedNode} instances.
         */
        public void recordPendingLaunches(PlannedNode... plannedNodes) {
            recordPendingLaunches(Arrays.asList(plannedNodes));
        }

        /**
         * If a {@link hudson.slaves.NodeProvisioner.Strategy} takes some provisioning action, it should record
         * and {@link hudson.slaves.NodeProvisioner.PlannedNode} instances by calling this method.
         *
         * @param plannedNodes the {@link hudson.slaves.NodeProvisioner.PlannedNode} instances.
         */
        public void recordPendingLaunches(Collection<PlannedNode> plannedNodes) {
            int additionalPlannedCapacity = 0;
            for (PlannedNode f : plannedNodes) {
                if (f.future.isDone()) {
                    // if done we should use the actual delivered capacity
                    try {
                        Node node = f.future.get();
                        if (node != null) {
                            additionalPlannedCapacity += node.getNumExecutors();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        // InterruptedException: should never happen as we were told the future was done
                        // ExecutionException: ignore, this will be caught by others later
                    }
                } else {
                    additionalPlannedCapacity += f.numExecutors;
                }
            }
            while (!plannedNodes.isEmpty()) {
                List<PlannedNode> orig = pendingLaunches.get();
                List<PlannedNode> repl = new ArrayList<>(orig);
                repl.addAll(plannedNodes);
                if (pendingLaunches.compareAndSet(orig, repl)) {
                    if (additionalPlannedCapacity > 0) {
                        synchronized (this) {
                            this.additionalPlannedCapacity += additionalPlannedCapacity;
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public String toString() {
            String sb = "StrategyState{" + "label=" + label +
                    ", snapshot=" + snapshot +
                    ", plannedCapacitySnapshot=" + plannedCapacitySnapshot +
                    ", additionalPlannedCapacity=" + additionalPlannedCapacity +
                    '}';
            return sb;
        }
    }

    /**
     * The default strategy.
     *
     * @since 1.588
     */
    @Extension @Symbol("standard")
    public static class StandardStrategyImpl extends Strategy {

        @NonNull
        @Override
        public StrategyDecision apply(@NonNull StrategyState state) {
        /*
            Here we determine how many additional agents we need to keep up with the load (if at all),
            which involves a simple math.

            Broadly speaking, first we check that all the executors are fully utilized before attempting
            to start any new agent (this also helps to ignore the temporary gap between different numbers,
            as changes in them are not necessarily synchronized --- for example, there's a time lag between
            when an agent launches (thus bringing the planned capacity down) and the time when its executors
            pick up builds (thus bringing the queue length down.)

            Once we confirm that, we compare the # of buildable items against the additional agents
            that are being brought online. If we have more jobs than our executors can handle, we'll launch a new agent.

            So this computation involves three stats:

              1. # of idle executors
              2. # of jobs that are starving for executors
              3. # of additional agents being provisioned (planned capacities.)

            To ignore a temporary surge/drop, we make conservative estimates on each one of them. That is,
            we take the current snapshot value, and we take the current exponential moving average (EMA) value,
            and use the max/min.

            This is another measure to be robust against temporary surge/drop in those indicators, and helps
            us avoid over-reacting to stats.

            If we only use the snapshot value or EMA value, tests confirmed that the gap creates phantom
            excessive loads and Hudson ends up firing excessive capacities. In a static system, over the time
            EMA and the snapshot value becomes the same, so this makes sure that in a long run this conservative
            estimate won't create a starvation.
         */

            final LoadStatistics.LoadStatisticsSnapshot snapshot = state.getSnapshot();
            boolean needSomeWhenNoneAtAll = snapshot.getAvailableExecutors() + snapshot.getConnectingExecutors() == 0
                    && snapshot.getOnlineExecutors() + state.getPlannedCapacitySnapshot() + state.getAdditionalPlannedCapacity() == 0
                    && snapshot.getQueueLength() > 0;
            float available = Math.max(snapshot.getAvailableExecutors(), state.getAvailableExecutorsLatest());
            if (available < MARGIN || needSomeWhenNoneAtAll) {
                // make sure the system is fully utilized before attempting any new launch.

                // this is the amount of work left to be done
                float qlen = Math.min(state.getQueueLengthLatest(), snapshot.getQueueLength());

                float connectingCapacity = Math.min(state.getConnectingExecutorsLatest(), snapshot.getConnectingExecutors());

                // ... and this is the additional executors we've already provisioned.
                float plannedCapacity = Math.max(state.getPlannedCapacityLatest(), state.getPlannedCapacitySnapshot())
                        + state.getAdditionalPlannedCapacity();

                float excessWorkload = qlen - plannedCapacity - connectingCapacity;
                if (needSomeWhenNoneAtAll && excessWorkload < 1) {
                    // in this specific exceptional case we should just provision right now
                    // the exponential smoothing will delay the build unnecessarily
                    excessWorkload = 1;
                }
                float m = calcThresholdMargin(state.getTotalSnapshot());
                if (excessWorkload > 1 - m) { // and there's more work to do...
                    LOGGER.log(Level.FINE, "Excess workload {0,number,#.###} detected. "
                                    + "(planned capacity={1,number,#.###},connecting capacity={7,number,#.###},"
                                    + "Qlen={2,number,#.###},available={3,number,#.###}&{4,number,integer},"
                                    + "online={5,number,integer},m={6,number,#.###})",
                            new Object[]{
                                    excessWorkload,
                                    plannedCapacity,
                                    qlen,
                                    available,
                                    snapshot.getAvailableExecutors(),
                                    snapshot.getOnlineExecutors(),
                                    m,
                                    snapshot.getConnectingExecutors(),
                            });

                    CLOUD:
                    for (Cloud c : Jenkins.get().clouds) {
                        if (excessWorkload < 0) {
                            break;  // enough agents allocated
                        }
                        Cloud.CloudState cloudState = new Cloud.CloudState(state.getLabel(), state.getAdditionalPlannedCapacity());

                        // Make sure this cloud actually can provision for this label.
                        if (c.canProvision(cloudState)) {
                            // provisioning a new node should be conservative --- for example if excessWorkload is 1.4,
                            // we don't want to allocate two nodes but just one.
                            // OTOH, because of the exponential decay, even when we need one agent,
                            // excess workload is always
                            // something like 0.95, in which case we want to allocate one node.
                            // so the threshold here is 1-MARGIN, and hence floor(excessWorkload+MARGIN) is needed to
                            // handle this.

                            int workloadToProvision = (int) Math.round(Math.floor(excessWorkload + m));

                            for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
                                if (cl.canProvision(c, cloudState, workloadToProvision) != null) {
                                    // consider displaying reasons in a future cloud ux
                                    continue CLOUD;
                                }
                            }

                            Collection<PlannedNode> additionalCapacities = c.provision(cloudState, workloadToProvision);

                            fireOnStarted(c, state.getLabel(), additionalCapacities);

                            for (PlannedNode ac : additionalCapacities) {
                                excessWorkload -= ac.numExecutors;
                                LOGGER.log(Level.INFO, "Started provisioning {0} from {1} with {2,number,integer} "
                                                + "executors. Remaining excess workload: {3,number,#.###}",
                                        new Object[]{ac.displayName, c.name, ac.numExecutors, excessWorkload});
                            }
                            state.recordPendingLaunches(additionalCapacities);
                        }
                    }
                    // we took action, only pass on to other strategies if our action was insufficient
                    return excessWorkload > 1 - m ? StrategyDecision.CONSULT_REMAINING_STRATEGIES : StrategyDecision.PROVISIONING_COMPLETED;
                }
            }
            // if we reach here then the standard strategy obviously decided to do nothing, so let any other strategies
            // take their considerations.
            return StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }

        /**
         * Computes the threshold for triggering an allocation.
         * <p/>
         * <p/>
         * Because the excessive workload value is EMA, even when the snapshot value of the excessive
         * workload is 1, the value never really gets to 1. So we need to introduce a notion of the margin M,
         * where we provision a new node if the EMA of the excessive workload goes beyond 1-M (where M is a small value
         * in the (0,1) range.)
         * <p/>
         * <p/>
         * M effectively controls how long Hudson waits until allocating a new node, in the face of workload.
         * This delay is justified for absorbing temporary ups and downs, and can be interpreted as Hudson
         * holding off provisioning in the hope that one of the existing nodes will become available.
         * <p/>
         * <p/>
         * M can be a constant value, but there's a benefit in adjusting M based on the total current capacity,
         * based on the above justification; that is, if there's no existing capacity at all, holding off
         * an allocation doesn't make much sense, as there won't be any executors available no matter how long we wait.
         * On the other hand, if we have a large number of existing executors, chances are good that some
         * of them become available &mdash; the chance gets better and better as the number of current total
         * capacity increases.
         * <p/>
         * <p/>
         * Therefore, we compute the threshold margin as follows:
         * <p/>
         * <pre>
         *   M(t) = M* + (M0 - M*) alpha ^ t
         * </pre>
         * <p/>
         * ... where:
         * <p/>
         * <ul>
         * <li>M* is the ultimate margin value that M(t) converges to with t->inf,
         * <li>M0 is the value of M(0), the initial value.
         * <li>alpha is the decay factor in (0,1). M(t) converges to M* faster if alpha is smaller.
         * </ul>
         */
        private float calcThresholdMargin(int totalSnapshot) {
            float f = (float) (MARGIN + (MARGIN0 - MARGIN) * Math.pow(MARGIN_DECAY, totalSnapshot));
            // defensively ensure that the threshold margin is in (0,1)
            f = Math.max(f, 0);
            f = Math.min(f, 1);
            return f;
        }
    }

    /**
     * Periodically invoke NodeProvisioners
     */
    @Extension
    public static class NodeProvisionerInvoker extends PeriodicWork {
        /**
         * Give some initial warm up time so that statically connected agents
         * can be brought online before we start allocating more.
         */
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
        public static int INITIALDELAY = (int) SystemProperties.getDuration(NodeProvisioner.class.getName() + ".initialDelay", ChronoUnit.MILLIS, Duration.ofMillis((long) LoadStatistics.CLOCK * 10)).toMillis();
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
        public static int RECURRENCEPERIOD = (int) SystemProperties.getDuration(NodeProvisioner.class.getName() + ".recurrencePeriod", ChronoUnit.MILLIS, Duration.ofMillis(LoadStatistics.CLOCK)).toMillis();

        @Override
        public long getInitialDelay() {
            return INITIALDELAY;
        }

        @Override
        public long getRecurrencePeriod() {
            return RECURRENCEPERIOD;
        }

        @Override
        protected void doRun() {
            Jenkins j = Jenkins.get();
            j.unlabeledNodeProvisioner.update();
            for (Label l : j.getLabels())
                l.nodeProvisioner.update();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(NodeProvisioner.class.getName());
    private static final float MARGIN = SystemProperties.getInteger(NodeProvisioner.class.getName() + ".MARGIN", 10) / 100f;
    private static final float MARGIN0 = Math.max(MARGIN, getFloatSystemProperty(NodeProvisioner.class.getName() + ".MARGIN0", 0.5f));
    private static final float MARGIN_DECAY = getFloatSystemProperty(NodeProvisioner.class.getName() + ".MARGIN_DECAY", 0.5f);

    // TODO: picker should be selectable
    private static final TimeScale TIME_SCALE = TimeScale.SEC10;

    private static float getFloatSystemProperty(String propName, float defaultValue) {
        String v = SystemProperties.getString(propName);
        if (v != null)
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                LOGGER.warning("Failed to parse a float value from system property " + propName + ". value was " + v);
            }
        return defaultValue;
    }

    private static void fireOnFailure(final NodeProvisioner.PlannedNode plannedNode, final Throwable cause) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onFailure(plannedNode, cause);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onFailure() listener call in " + cl + " for agent "
                        + plannedNode.displayName, e);
            }
        }
    }

    private static void fireOnRollback(final NodeProvisioner.PlannedNode plannedNode, final Node newNode,
                                       final Throwable cause) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onRollback(plannedNode, newNode, cause);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onRollback() listener call in " + cl + " for agent "
                        + newNode.getDisplayName(), e);
            }
        }
    }

    private static void fireOnComplete(final NodeProvisioner.PlannedNode plannedNode, final Node newNode) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onComplete(plannedNode, newNode);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onComplete() listener call in " + cl + " for agent "
                        + plannedNode.displayName, e);
            }
        }
    }

    private static void fireOnCommit(final NodeProvisioner.PlannedNode plannedNode, final Node newNode) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onCommit(plannedNode, newNode);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onCommit() listener call in " + cl + " for agent "
                        + newNode.getDisplayName(), e);
            }
        }
    }

    private static void fireOnStarted(final Cloud cloud, final Label label,
                                      final Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onStarted(cloud, label, plannedNodes);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onStarted() listener call in " + cl + " for label "
                        + label.toString(), e);
            }
        }
    }
}
