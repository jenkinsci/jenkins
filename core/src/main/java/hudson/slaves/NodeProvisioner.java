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

import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.model.Hudson;
import hudson.model.MultiStageTimeSeries;
import hudson.model.Label;
import hudson.model.PeriodicWork;
import static hudson.model.LoadStatistics.DECAY;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.Extension;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
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
    public static final class PlannedNode {
        public final String displayName;
        public final Future<Node> future;
        public final int numExecutors;

        public PlannedNode(String displayName, Future<Node> future, int numExecutors) {
            if(displayName==null || future==null || numExecutors<1)  throw new IllegalArgumentException();
            this.displayName = displayName;
            this.future = future;
            this.numExecutors = numExecutors;
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

    /**
     * Exponential moving average of the "planned capacity" over time, which is the number of
     * additional executors being brought up.
     *
     * This is used to filter out high-frequency components from the planned capacity, so that
     * the comparison with other low-frequency only variables won't leave spikes.
     */
    private final MultiStageTimeSeries plannedCapacitiesEMA = new MultiStageTimeSeries(0,DECAY);

    public NodeProvisioner(Label label, LoadStatistics loadStatistics) {
        this.label = label;
        this.stat = loadStatistics;
    }

    /**
     * Periodically invoked to keep track of the load.
     * Launches additional nodes if necessary.
     */
    private void update() {
        Hudson hudson = Hudson.getInstance();

        // clean up the cancelled launch activity, then count the # of executors that we are about to bring up.
        float plannedCapacity = 0;
        for (Iterator<PlannedNode> itr = pendingLaunches.iterator(); itr.hasNext();) {
            PlannedNode f = itr.next();
            if(f.future.isDone()) {
                try {
                    hudson.addNode(f.future.get());
                    LOGGER.info(f.displayName+" provisioning successfully completed. We have now "+hudson.getComputers().length+" computer(s)");
                } catch (InterruptedException e) {
                    throw new AssertionError(e); // since we confirmed that the future is already done
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Provisioned slave "+f.displayName+" failed to launch",e.getCause());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Provisioned slave "+f.displayName+" failed to launch",e);
                }
                itr.remove();
            } else
                plannedCapacity += f.numExecutors;
        }
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
        float idle = Math.max(stat.getLatestIdleExecutors(TIME_SCALE), idleSnapshot);
        if(idle<MARGIN) {
            // make sure the system is fully utilized before attempting any new launch.

            // this is the amount of work left to be done
            float qlen = Math.min(stat.queueLength.getLatest(TIME_SCALE), stat.computeQueueLength());

            // ... and this is the additional executors we've already provisioned.
            plannedCapacity = Math.max(plannedCapacitiesEMA.getLatest(TIME_SCALE),plannedCapacity);

            float excessWorkload = qlen - plannedCapacity;
            if(excessWorkload>1-MARGIN) {// and there's more work to do...
                LOGGER.fine("Excess workload "+excessWorkload+" detected. (planned capacity="+plannedCapacity+",Qlen="+qlen+",idle="+idle+"&"+idleSnapshot+",total="+totalSnapshot+")");
                for( Cloud c : hudson.clouds ) {
                    if(excessWorkload<0)    break;  // enough slaves allocated

                    // provisioning a new node should be conservative --- for example if exceeWorkload is 1.4,
                    // we don't want to allocate two nodes but just one.
                    // OTOH, because of the exponential decay, even when we need one slave, excess workload is always
                    // something like 0.95, in which case we want to allocate one node.
                    // so the threshold here is 1-MARGIN, and hence floor(excessWorkload+MARGIN) is needed to handle this.

                    Collection<PlannedNode> additionalCapacities = c.provision(label, (int)Math.round(Math.floor(excessWorkload+MARGIN)));
                    for (PlannedNode ac : additionalCapacities) {
                        excessWorkload -= ac.numExecutors;
                        LOGGER.info("Started provisioning "+ac.displayName+" from "+c.name+" with "+ac.numExecutors+" executors. Remaining excess workload:"+excessWorkload);
                    }
                    pendingLaunches.addAll(additionalCapacities);
                }
            }
        }
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
        @Override
        public long getInitialDelay() {
            return LoadStatistics.CLOCK*10;
        }

        public long getRecurrencePeriod() {
            return LoadStatistics.CLOCK;
        }

        @Override
        protected void doRun() {
            Hudson h = Hudson.getInstance();
            h.overallNodeProvisioner.update();
            for( Label l : h.getLabels() )
                l.nodeProvisioner.update();
        }
    }

    private static final float MARGIN = 0.1f;
    private static final Logger LOGGER = Logger.getLogger(NodeProvisioner.class.getName());

    // TODO: picker should be selectable
    private static final TimeScale TIME_SCALE = TimeScale.SEC10;
}
