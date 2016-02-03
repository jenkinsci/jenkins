/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import hudson.ExtensionPoint;
import hudson.Util;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.*;
import hudson.util.DescriptorList;
import java.util.Collections;
import java.util.HashMap;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.concurrent.GuardedBy;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls when to take {@link Computer} offline, bring it back online, or even to destroy it.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
 */
public abstract class RetentionStrategy<T extends Computer> extends AbstractDescribableImpl<RetentionStrategy<?>> implements ExtensionPoint {

    /**
     * This method will be called periodically to allow this strategy to decide what to do with it's owning slave.
     *
     * @param c {@link Computer} for which this strategy is assigned. This computer may be online or offline.
     *          This object also exposes a bunch of properties that the callee can use to decide what action to take.
     * @return The number of minutes after which the strategy would like to be checked again. The strategy may be
     *         rechecked earlier or later that this!
     */
    @GuardedBy("hudson.model.Queue.lock")
    public abstract long check(T c);

    /**
     * This method is called to determine whether manual launching of the slave is allowed at this point in time.
     * @param c {@link Computer} for which this strategy is assigned. This computer may be online or offline.
     *          This object also exposes a bunch of properties that the callee can use to decide if manual launching is
     * allowed at this time.
     * @return {@code true} if manual launching of the slave is allowed at this point in time.
     */
    public boolean isManualLaunchAllowed(T c) {
        return true;
    }

    /**
     * Returns {@code true} if the computer is accepting tasks. Needed to allow retention strategies programmatic
     * suspension of task scheduling that in preparation for going offline. Called by
     * {@link hudson.model.Computer#isAcceptingTasks()}
     *
     * @param c the computer.
     * @return {@code true} if the computer is accepting tasks
     * @see hudson.model.Computer#isAcceptingTasks()
     * @since 1.586
     */
    public boolean isAcceptingTasks(T c) {
        return true;
    }

    /**
     * Called when a new {@link Computer} object is introduced (such as when Hudson started, or when
     * a new slave is added.)
     *
     * <p>
     * The default implementation of this method delegates to {@link #check(Computer)},
     * but this allows {@link RetentionStrategy} to distinguish the first time invocation from the rest.
     *
     * @since 1.275
     */
    public void start(final T c) {
        Queue.withLock(new Runnable() {
            @Override
            public void run() {
                check(c);
            }
        });
    }

    /**
     * Returns all the registered {@link RetentionStrategy} descriptors.
     */
    public static DescriptorExtensionList<RetentionStrategy<?>,Descriptor<RetentionStrategy<?>>> all() {
        return (DescriptorExtensionList) Jenkins.getInstance().getDescriptorList(RetentionStrategy.class);
    }

    /**
     * All registered {@link RetentionStrategy} implementations.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<RetentionStrategy<?>> LIST = new DescriptorList<RetentionStrategy<?>>((Class)RetentionStrategy.class);

    /**
     * Dummy instance that doesn't do any attempt to retention.
     */
    public static final RetentionStrategy<Computer> NOOP = new RetentionStrategy<Computer>() {
        @GuardedBy("hudson.model.Queue.lock")
        public long check(Computer c) {
            return 60;
        }

        @Override
        public void start(Computer c) {
            c.connect(false);
        }

        @Override
        public Descriptor<RetentionStrategy<?>> getDescriptor() {
            return DESCRIPTOR;
        }

        private final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {}
    };

    /**
     * Convenient singleton instance, since this {@link RetentionStrategy} is stateless.
     */
    public static final Always INSTANCE = new Always();

    /**
     * {@link RetentionStrategy} that tries to keep the node online all the time.
     */
    public static class Always extends RetentionStrategy<SlaveComputer> {
        /**
         * Constructs a new Always.
         */
        @DataBoundConstructor
        public Always() {
        }

        @GuardedBy("hudson.model.Queue.lock")
        public long check(SlaveComputer c) {
            if (c.isOffline() && !c.isConnecting() && c.isLaunchSupported())
                c.tryReconnect();
            return 1;
        }

        @Extension(ordinal=100)
        public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
            public String getDisplayName() {
                return Messages.RetentionStrategy_Always_displayName();
            }
        }
    }

    /**
     * {@link hudson.slaves.RetentionStrategy} that tries to keep the node offline when not in use.
     */
    public static class Demand extends RetentionStrategy<SlaveComputer> {

        private static final Logger logger = Logger.getLogger(Demand.class.getName());

        /**
         * The delay (in minutes) for which the slave must be in demand before tring to launch it.
         */
        private final long inDemandDelay;

        /**
         * The delay (in minutes) for which the slave must be idle before taking it offline.
         */
        private final long idleDelay;

        @DataBoundConstructor
        public Demand(long inDemandDelay, long idleDelay) {
            this.inDemandDelay = Math.max(0, inDemandDelay);
            this.idleDelay = Math.max(1, idleDelay);
        }

        /**
         * Getter for property 'inDemandDelay'.
         *
         * @return Value for property 'inDemandDelay'.
         */
        public long getInDemandDelay() {
            return inDemandDelay;
        }

        /**
         * Getter for property 'idleDelay'.
         *
         * @return Value for property 'idleDelay'.
         */
        public long getIdleDelay() {
            return idleDelay;
        }

        @Override
        @GuardedBy("hudson.model.Queue.lock")
        public long check(final SlaveComputer c) {
            if (c.isOffline() && c.isLaunchSupported()) {
                final HashMap<Computer, Integer> availableComputers = new HashMap<Computer, Integer>();
                for (Computer o : Jenkins.getInstance().getComputers()) {
                    if ((o.isOnline() || o.isConnecting()) && o.isPartiallyIdle() && o.isAcceptingTasks()) {
                        final int idleExecutors = o.countIdle();
                        if (idleExecutors>0)
                            availableComputers.put(o, idleExecutors);
                    }
                }

                boolean needComputer = false;
                long demandMilliseconds = 0;
                for (Queue.BuildableItem item : Queue.getInstance().getBuildableItems()) {
                    // can any of the currently idle executors take this task?
                    // assume the answer is no until we can find such an executor
                    boolean needExecutor = true;
                    for (Computer o : Collections.unmodifiableSet(availableComputers.keySet())) {
                        Node otherNode = o.getNode();
                        if (otherNode != null && otherNode.canTake(item) == null) {
                            needExecutor = false;
                            final int availableExecutors = availableComputers.remove(o);
                            if (availableExecutors > 1) {
                                availableComputers.put(o, availableExecutors - 1);
                            } else {
                                availableComputers.remove(o);
                            }
                            break;
                        }
                    }

                    // this 'item' cannot be built by any of the existing idle nodes, but it can be built by 'c'
                    Node checkedNode = c.getNode();
                    if (needExecutor && checkedNode != null && checkedNode.canTake(item) == null) {
                        demandMilliseconds = System.currentTimeMillis() - item.buildableStartMilliseconds;
                        needComputer = demandMilliseconds > inDemandDelay * 1000 * 60 /*MINS->MILLIS*/;
                        break;
                    }
                }

                if (needComputer) {
                    // we've been in demand for long enough
                    logger.log(Level.INFO, "Launching computer {0} as it has been in demand for {1}",
                            new Object[]{c.getName(), Util.getTimeSpanString(demandMilliseconds)});
                    c.connect(false);
                }
            } else if (c.isIdle()) {
                final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                if (idleMilliseconds > idleDelay * 1000 * 60 /*MINS->MILLIS*/) {
                    // we've been idle for long enough
                    logger.log(Level.INFO, "Disconnecting computer {0} as it has been idle for {1}",
                            new Object[]{c.getName(), Util.getTimeSpanString(idleMilliseconds)});
                    c.disconnect(OfflineCause.create(Messages._RetentionStrategy_Demand_OfflineIdle()));
                } else {
                    // no point revisiting until we can be confident we will be idle
                    return TimeUnit.MILLISECONDS.toMinutes(TimeUnit.MINUTES.toMillis(idleDelay) - idleMilliseconds);
                }
            }
            return 1;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
            @Override
            public String getDisplayName() {
                return Messages.RetentionStrategy_Demand_displayName();
            }
        }
    }
}
