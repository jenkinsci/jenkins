/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe,
 * Stephen Connolly, Tom Huybrechts, Yahoo! Inc., Alan Harder, CloudBees, Inc.
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

package hudson.model;


import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerListener;
import hudson.slaves.RetentionStrategy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerProxy;

public abstract class AbstractCIBase extends Node implements ItemGroup<TopLevelItem>, StaplerProxy, StaplerFallback, ViewGroup, AccessControlled, DescriptorByNameOwner {

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean LOG_STARTUP_PERFORMANCE = SystemProperties.getBoolean(Jenkins.class.getName() + "." + "logStartupPerformance", false);

    private static final Logger LOGGER = Logger.getLogger(AbstractCIBase.class.getName());

    /**
     * If you are calling this on Hudson something is wrong.
     *
     * @deprecated
     *      Maybe you were trying to call {@link #getDisplayName()}.
     */
    @NonNull
    @Deprecated @Override
    public String getNodeName() {
        return "";
    }

   /**
     * @deprecated
     *      Why are you calling a method that always returns ""?
    *       You probably want to call {@link Jenkins#getRootUrl()}
     */
    @NonNull
    @Override
    @Deprecated
    public String getUrl() {
        return "";
    }

    /* =================================================================================================================
     * Support functions that can only be accessed through package-protected
     * ============================================================================================================== */
    protected void resetLabel(Label l) {
        l.reset();
    }

    protected void setViewOwner(View v) {
        v.owner = this;
    }

    protected void interruptReloadThread() {
        ViewJob.interruptReloadThread();
    }

    protected void killComputer(Computer c) {
        c.kill();
    }

    private final Set<String> disabledAdministrativeMonitors = new HashSet<>();

    /**
     * Get the disabled administrative monitors
     *
     * @since 2.230
     */
    public Set<String> getDisabledAdministrativeMonitors() {
        synchronized (this.disabledAdministrativeMonitors) {
            return new HashSet<>(disabledAdministrativeMonitors);
        }
    }

    /**
     * Set the disabled administrative monitors
     *
     * @since 2.230
     */
    public void setDisabledAdministrativeMonitors(Set<String> disabledAdministrativeMonitors) {
        synchronized (this.disabledAdministrativeMonitors) {
            this.disabledAdministrativeMonitors.clear();
            this.disabledAdministrativeMonitors.addAll(disabledAdministrativeMonitors);
        }
    }

    /* =================================================================================================================
     * Implementation provided
     * ============================================================================================================== */

     /**
     * Returns all {@link Node}s in the system, excluding {@link jenkins.model.Jenkins} instance itself which
     * represents the built-in node in this context.
     */
    public abstract List<Node> getNodes();

    public abstract Queue getQueue();

    protected abstract ConcurrentMap<Node, Computer> getComputerMap();

    /* =================================================================================================================
     * Computer API uses package protection heavily
     * ============================================================================================================== */

    private void updateComputer(Node n, Map<String, Computer> byNameMap, Set<Computer> used, boolean automaticAgentLaunch) {
        Computer c = byNameMap.get(n.getNodeName());
        if (c != null) {
            try {
                c.setNode(n); // reuse
                used.add(c);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Error updating node " + n.getNodeName() + ", continuing", e);
            }
        } else {
            c = createNewComputerForNode(n, automaticAgentLaunch);
            if (c != null) {
                used.add(c);
            }
        }
    }

    @CheckForNull
    private Computer createNewComputerForNode(Node n, boolean automaticAgentLaunch) {
        Computer c = null;
        ConcurrentMap<Node, Computer> computers = getComputerMap();
        // we always need Computer for the built-in node as a fallback in case there's no other Computer.
        if (n.getNumExecutors() > 0 || n == Jenkins.get()) {
            // The start/connect of a new computer is potentially costly, so we don't want to perform it inside
            // computeIfAbsent. Instead, we use this creationWasAttempted flag to signal if start/connect is needed or
            // not.
            final AtomicBoolean creationWasAttempted = new AtomicBoolean(false);
            try {
                c = computers.computeIfAbsent(n, node -> {
                    creationWasAttempted.set(true);
                    return node.createComputer();
                });
            } catch (RuntimeException ex) { // Just in case there is a bogus extension
                LOGGER.log(Level.WARNING, "Error retrieving computer for node " + n.getNodeName() + ", continuing", ex);
            }
            if (!creationWasAttempted.get()) {
                LOGGER.log(Level.FINE, "Node {0} is not a new node skipping", n.getNodeName());
                return null;
            }
            if (c == null) {
                LOGGER.log(Level.WARNING, "Cannot create computer for node {0}, the {1}#createComputer() method returned null. Skipping this node",
                        new Object[]{n.getNodeName(), n.getClass().getName()});
                return null;
            }

            if (!n.isHoldOffLaunchUntilSave() && automaticAgentLaunch) {
                RetentionStrategy retentionStrategy = c.getRetentionStrategy();
                if (retentionStrategy != null) {
                    // if there is a retention strategy, it is responsible for deciding to start the computer
                    retentionStrategy.start(c);
                } else {
                    // we should never get here, but just in case, we'll fall back to the legacy behaviour
                    c.connect(true);
                }
            }
            return c;
        } else {
            // TODO: Maybe it should be allowed, but we would just get NPE in the original logic before JENKINS-43496
            LOGGER.log(Level.WARNING, "Node {0} has no executors. Cannot update the Computer instance of it", n.getNodeName());
            return null;
        }
    }

    /*package*/ void removeComputer(final Computer computer) {
        ConcurrentMap<Node, Computer> computers = getComputerMap();
        Queue.withLock(() -> {
            if (computers.values().remove(computer)) {
                computer.onRemoved();
            }
        });
    }

    /*package*/ @CheckForNull Computer getComputer(Node n) {
        ConcurrentMap<Node, Computer> computers = getComputerMap();
        return computers.get(n);
    }

    protected void updateNewComputer(final Node n, boolean automaticAgentLaunch) {
        if (createNewComputerForNode(n, automaticAgentLaunch) == null) {
            return;
        }
        getQueue().scheduleMaintenance();
        Listeners.notify(ComputerListener.class, false, ComputerListener::onConfigurationChange);
    }

    /**
     * Updates Computers for the specified nodes.
     *
     * <p>
     * This method tries to reuse existing {@link Computer} objects
     * so that we won't upset {@link Executor}s running in it.
     *
     * @param automaticAgentLaunch whether to automatically launch agents.
     * @param nodes the nodes to update.
     */
    protected void updateComputerList(final boolean automaticAgentLaunch, @NonNull Collection<Node> nodes) {
        final Set<Computer> old = new HashSet<>(nodes.size());
        Queue.withLock(() -> {
            Map<String, Computer> byName = new HashMap<>();
            for (Computer c : getComputerMap().values()) {
                Node node = c.getNode();
                if (node == null) {
                    old.add(c);
                    // this computer is gone
                } else if (nodes.contains(node)) {
                    old.add(c);
                    byName.put(node.getNodeName(), c);
                }
            }
            Set<Computer> used = new HashSet<>(old.size());
            for (Node node : nodes) {
                long start = System.currentTimeMillis();
                updateComputer(node, byName, used, automaticAgentLaunch);
                if (LOG_STARTUP_PERFORMANCE && LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(String.format("Took %dms to update node %s",
                            System.currentTimeMillis() - start, node.getNodeName()));
                }
            }

            // find out what computers are removed, and kill off all executors.
            // when all executors exit, it will be removed from the computers map.
            // so don't remove too quickly
            old.removeAll(used);
            // we need to start the process of reducing the executors on all computers as distinct
            // from the killing action which should not excessively use the Queue lock.
            for (Computer c : old) {
                c.inflictMortalWound();
            }
        });
        for (Computer c : old) {
            // when we get to here, the number of executors should be zero so this call should not need the Queue.lock
            killComputer(c);
        }
        getQueue().scheduleMaintenance();
        Listeners.notify(ComputerListener.class, false, ComputerListener::onConfigurationChange);
    }

}
