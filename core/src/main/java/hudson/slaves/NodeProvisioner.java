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

import hudson.model.*;
import jenkins.model.Jenkins;

import static hudson.model.LoadStatistics.DECAY;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.Extension;

import java.awt.Color;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

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
            if(displayName==null || future==null || numExecutors<1)  throw new IllegalArgumentException();
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
    private final Label label;

    private List<PlannedNode> pendingLaunches = new ArrayList<PlannedNode>();

    private transient volatile long lastSuggestedReview;

    /**
     * Exponential moving average of the "planned capacity" over time, which is the number of
     * additional executors being brought up.
     *
     * This is used to filter out high-frequency components from the planned capacity, so that
     * the comparison with other low-frequency only variables won't leave spikes.
     */
    private final MultiStageTimeSeries plannedCapacitiesEMA =
            new MultiStageTimeSeries(Messages._NodeProvisioner_EmptyString(),Color.WHITE,0,DECAY);

    public NodeProvisioner(Label label, LoadStatistics loadStatistics) {
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
    public synchronized List<PlannedNode> getPendingLaunches() {
        return new ArrayList<PlannedNode>(pendingLaunches);
    }

    /**
     * Give the {@link NodeProvisioner} a hint that now would be a good time to think about provisioning some nodes.
     * The hint will be ignored if subjected to excessive pestering by callers.
     *
     * @since 1.415
     */
    public void suggestReviewNow() {
        if (System.currentTimeMillis() > lastSuggestedReview + TimeUnit.SECONDS.toMillis(1)) {
            lastSuggestedReview = System.currentTimeMillis();
            Computer.threadPoolForRemoting.submit(new Runnable() {
                public void run() {
                    update();
                }
            });
        }
    }

    /**
     * Periodically invoked to keep track of the load.
     * Launches additional nodes if necessary.
     */
    private synchronized void update() {
        Jenkins hudson = Jenkins.getInstance();
        lastSuggestedReview = System.currentTimeMillis();

        // clean up the cancelled launch activity, then count the # of executors that we are about to bring up.
        int plannedCapacitySnapshot = 0;
        for (Iterator<PlannedNode> itr = pendingLaunches.iterator(); itr.hasNext();) {
            PlannedNode f = itr.next();
            if(f.future.isDone()) {
                try {
                    Node node = f.future.get();
                    for (CloudProvisioningListener cl : CloudProvisioningListener.all())
                        cl.onComplete(f,node);

                    hudson.addNode(node);
                    LOGGER.info(f.displayName+" provisioning successfully completed. We have now "+hudson.getComputers().length+" computer(s)");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Provisioned slave "+f.displayName+" failed to launch",e.getCause());
                    for (CloudProvisioningListener cl : CloudProvisioningListener.all())
                        cl.onFailure(f,e.getCause());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Provisioned slave "+f.displayName+" failed to launch",e);
                    for (CloudProvisioningListener cl : CloudProvisioningListener.all())
                        cl.onFailure(f,e);
                }

                f.spent();

                itr.remove();
            } else
                plannedCapacitySnapshot += f.numExecutors;
        }
        float plannedCapacity = plannedCapacitySnapshot;
        plannedCapacitiesEMA.update(plannedCapacity);

        /*
            Here we determine how many additional slaves we need to keep up with the load (if at all),
            which involves a simple math.

            Broadly speaking, first we check that all the executors are fully utilized before attempting
            to start any new slave (this also helps to ignore the temporary gap between different numbers,
            as changes in them are not necessarily synchronized --- for example, there's a time lag between
            when a slave launches (thus bringing the planned capacity down) and the time when its executors
            pick up builds (thus bringing the queue length down.)

            Once we confirm that, we compare the # of buildable items against the additional slaves
            that are being brought online. If we have more jobs than our executors can handle, we'll launch a new slave.

            So this computation involves three stats:

              1. # of idle executors
              2. # of jobs that are starving for executors
              3. # of additional slaves being provisioned (planned capacities.)

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

        int idleSnapshot = stat.computeIdleExecutors();
        int totalSnapshot = stat.computeTotalExecutors();
        boolean needSomeWhenNoneAtAll = (idleSnapshot==0) && (totalSnapshot + plannedCapacitySnapshot == 0) && (stat.computeQueueLength() > 0);
        float idle = Math.max(stat.getLatestIdleExecutors(TIME_SCALE), idleSnapshot);
        if(idle<MARGIN || needSomeWhenNoneAtAll) {
            // make sure the system is fully utilized before attempting any new launch.

            // this is the amount of work left to be done
            float qlen = Math.min(stat.queueLength.getLatest(TIME_SCALE), stat.computeQueueLength());

            // ... and this is the additional executors we've already provisioned.
            plannedCapacity = Math.max(plannedCapacitiesEMA.getLatest(TIME_SCALE),plannedCapacity);

            float excessWorkload = qlen - plannedCapacity;
            if (needSomeWhenNoneAtAll && excessWorkload < 1) {
                // in this specific exceptional case we should just provision right now
                // the exponential smoothing will delay the build unnecessarily
                excessWorkload = 1;
            }
            float m = calcThresholdMargin(totalSnapshot);
            if(excessWorkload>1-m) {// and there's more work to do...
                LOGGER.fine("Excess workload "+excessWorkload+" detected. (planned capacity="+plannedCapacity+",Qlen="+qlen+",idle="+idle+"&"+idleSnapshot+",total="+totalSnapshot+"m,="+m+")");

            CLOUD:
                for( Cloud c : hudson.clouds ) {
                    if(excessWorkload<0)    break;  // enough slaves allocated

                    // Make sure this cloud actually can provision for this label.
                    if (c.canProvision(label)) {
                        // provisioning a new node should be conservative --- for example if exceeWorkload is 1.4,
                        // we don't want to allocate two nodes but just one.
                        // OTOH, because of the exponential decay, even when we need one slave, excess workload is always
                        // something like 0.95, in which case we want to allocate one node.
                        // so the threshold here is 1-MARGIN, and hence floor(excessWorkload+MARGIN) is needed to handle this.

                        int workloadToProvision = (int) Math.round(Math.floor(excessWorkload + m));

                        for (CloudProvisioningListener cl : CloudProvisioningListener.all())
                            // consider displaying reasons in a future cloud ux
                            if (cl.canProvision(c,label,workloadToProvision) != null)
                                break CLOUD;

                        Collection<PlannedNode> additionalCapacities = c.provision(label, workloadToProvision);

                        for (CloudProvisioningListener cl : CloudProvisioningListener.all())
                            cl.onStarted(c, label, additionalCapacities);

                        for (PlannedNode ac : additionalCapacities) {
                            excessWorkload -= ac.numExecutors;
                            LOGGER.info("Started provisioning "+ac.displayName+" from "+c.name+" with "+ac.numExecutors+" executors. Remaining excess workload:"+excessWorkload);
                        }
                        pendingLaunches.addAll(additionalCapacities);
                    }
                }
            }
        }
    }

    /**
     * Computes the threshold for triggering an allocation.
     *
     * <p>
     * Because the excessive workload value is EMA, even when the snapshot value of the excessive
     * workload is 1, the value never really gets to 1. So we need to introduce a notion of the margin M,
     * where we provision a new node if the EMA of the excessive workload goes beyond 1-M (where M is a small value
     * in the (0,1) range.)
     *
     * <p>
     * M effectively controls how long Hudson waits until allocating a new node, in the face of workload.
     * This delay is justified for absorbing temporary ups and downs, and can be interpreted as Hudson
     * holding off provisioning in the hope that one of the existing nodes will become available.
     *
     * <p>
     * M can be a constant value, but there's a benefit in adjusting M based on the total current capacity,
     * based on the above justification; that is, if there's no existing capacity at all, holding off
     * an allocation doesn't make much sense, as there won't be any executors available no matter how long we wait.
     * On the other hand, if we have a large number of existing executors, chances are good that some
     * of them become available &mdash; the chance gets better and better as the number of current total
     * capacity increases.
     *
     * <p>
     * Therefore, we compute the threshold margin as follows:
     *
     * <pre>
     *   M(t) = M* + (M0 - M*) alpha ^ t
     * </pre>
     *
     * ... where:
     *
     * <ul>
     * <li>M* is the ultimate margin value that M(t) converges to with t->inf,
     * <li>M0 is the value of M(0), the initial value.
     * <li>alpha is the decay factor in (0,1). M(t) converges to M* faster if alpha is smaller.
     * </ul>
     */
    private float calcThresholdMargin(int totalSnapshot) {
        float f = (float) (MARGIN + (MARGIN0 - MARGIN) * Math.pow(MARGIN_DECAY, totalSnapshot));
        // defensively ensure that the threshold margin is in (0,1)
        f = Math.max(f,0);
        f = Math.min(f,1);
        return f;
    }

    /**
     * Periodically invoke NodeProvisioners
     */
    @Extension
    public static class NodeProvisionerInvoker extends PeriodicWork {
        /**
         * Give some initial warm up time so that statically connected slaves
         * can be brought online before we start allocating more.
         */
    	 public static int INITIALDELAY = Integer.getInteger(NodeProvisioner.class.getName()+".initialDelay",LoadStatistics.CLOCK*10);
    	 public static int RECURRENCEPERIOD = Integer.getInteger(NodeProvisioner.class.getName()+".recurrencePeriod",LoadStatistics.CLOCK);
    	 
        @Override
        public long getInitialDelay() {
            return INITIALDELAY;
        }

        public long getRecurrencePeriod() {
            return RECURRENCEPERIOD;
        }

        @Override
        protected void doRun() {
            Jenkins h = Jenkins.getInstance();
            h.unlabeledNodeProvisioner.update();
            for( Label l : h.getLabels() )
                l.nodeProvisioner.update();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(NodeProvisioner.class.getName());
    private static final float MARGIN = Integer.getInteger(NodeProvisioner.class.getName()+".MARGIN",10)/100f;
    private static final float MARGIN0 = Math.max(MARGIN, getFloatSystemProperty(NodeProvisioner.class.getName()+".MARGIN0",0.5f));
    private static final float MARGIN_DECAY = getFloatSystemProperty(NodeProvisioner.class.getName()+".MARGIN_DECAY",0.5f);

    // TODO: picker should be selectable
    private static final TimeScale TIME_SCALE = TimeScale.SEC10;

    private static float getFloatSystemProperty(String propName, float defaultValue) {
        String v = System.getProperty(propName);
        if (v!=null)
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                LOGGER.warning("Failed to parse a float value from system property "+propName+". value was "+v);
            }
        return defaultValue;
    }
}
