/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Stephen Connolly, Tom Huybrechts, InfraDNA, Inc.
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

import static hudson.init.InitMilestone.JOB_CONFIG_ADAPTED;
import static hudson.model.Item.CANCEL;
import static hudson.util.Iterators.reverse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIResolver;
import hudson.init.Initializer;
import hudson.model.Node.Mode;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsBusy;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsBusy;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsOffline;
import hudson.model.queue.Executables;
import hudson.model.queue.FoldableAction;
import hudson.model.queue.FutureImpl;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.QueueListener;
import hudson.model.queue.QueueSorter;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.ScheduleResult;
import hudson.model.queue.ScheduleResult.Created;
import hudson.model.queue.SubTask;
import hudson.model.queue.Tasks;
import hudson.model.queue.WorkUnit;
import hudson.model.queue.WorkUnitContext;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.triggers.SafeTimerTask;
import hudson.util.ConsistentHash;
import hudson.util.Futures;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.console.WithConsoleUrl;
import jenkins.model.Jenkins;
import jenkins.model.queue.AsynchronousExecution;
import jenkins.model.queue.CompositeCauseOfBlockage;
import jenkins.model.queue.QueueIdStrategy;
import jenkins.model.queue.QueueItem;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorProvider;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.util.AtmostOneTaskExecutor;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import net.jcip.annotations.GuardedBy;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * Build queue.
 *
 * <p>
 * This class implements the core scheduling logic. {@link Task} represents the executable
 * task that are placed in the queue. While in the queue, it's wrapped into {@link Item}
 * so that we can keep track of additional data used for deciding what to execute when.
 *
 * <p>
 * Items in queue goes through several stages, as depicted below:
 * <pre>{@code
 * (enter) --> waitingList --+--> blockedProjects
 *                           |        ^
 *                           |        |
 *                           |        v
 *                           +--> buildables ---> pending ---> left
 *                                    ^              |
 *                                    |              |
 *                                    +---(rarely)---+
 * }</pre>
 *
 * <p>
 * Note: In the normal case of events pending items only move to left. However they can move back
 * if the node they are assigned to execute on disappears before their {@link Executor} thread
 * starts, where the node is removed before the {@link Executable} has been instantiated it
 * is safe to move the pending item back to buildable. Once the {@link Executable} has been
 * instantiated the only option is to let the {@link Executable} bomb out as soon as it starts
 * to try an execute on the node that no longer exists.
 *
 * <p>
 * In addition, at any stage, an item can be removed from the queue (for example, when the user
 * cancels a job in the queue.) See the corresponding field for their exact meanings.
 *
 * @author Kohsuke Kawaguchi
 * @see QueueListener
 * @see QueueTaskDispatcher
 */
@ExportedBean
public class Queue extends ResourceController implements Saveable {

    /**

     * Items that are waiting for its quiet period to pass.
     *
     * <p>
     * This consists of {@link Item}s that cannot be run yet
     * because its time has not yet come.
     */
    private final Set<WaitingItem> waitingList = new TreeSet<>();

    /**
     * {@link Task}s that can be built immediately
     * but blocked because another build is in progress,
     * required {@link Resource}s are not available,
     * blocked via {@link QueueTaskDispatcher#canRun(Item)},
     * or otherwise blocked by {@link Task#isBuildBlocked()}.
     */
    private final ItemList<BlockedItem> blockedProjects = new ItemList<>();

    /**
     * {@link Task}s that can be built immediately
     * that are waiting for available {@link Executor}.
     * This list is sorted in such a way that earlier items are built earlier.
     */
    private final ItemList<BuildableItem> buildables = new ItemList<>();

    /**
     * {@link Task}s that are being handed over to the executor, but execution
     * has not started yet.
     */
    private final ItemList<BuildableItem> pendings = new ItemList<>();

    private transient volatile Snapshot snapshot = new Snapshot(waitingList, blockedProjects, buildables, pendings);

    /**
     * Items that left queue would stay here for a while to enable tracking via {@link Item#getId()}.
     *
     * This map is forgetful, since we can't remember everything that executed in the past.
     */
    private final Cache<Long, LeftItem> leftItems = CacheBuilder.newBuilder().expireAfterWrite(5 * 60, TimeUnit.SECONDS).build();

    /**
     * Data structure created for each idle {@link Executor}.
     * This is a job offer from the queue to an executor.
     *
     * <p>
     * For each idle executor, this gets created to allow the scheduling logic
     * to assign a work. Once a work is assigned, the executor actually gets
     * started to carry out the task in question.
     */
    public static class JobOffer extends MappingWorksheet.ExecutorSlot {
        public final Executor executor;

        /**
         * The work unit that this {@link Executor} is going to handle.
         */
        private WorkUnit workUnit;

        private JobOffer(Executor executor) {
            this.executor = executor;
        }

        @Override
        protected void set(WorkUnit p) {
            assert this.workUnit == null;
            this.workUnit = p;
            assert executor.isParking();
            executor.start(workUnit);
            // LOGGER.info("Starting "+executor.getName());
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        /**
         * @deprecated discards information; prefer {@link #getCauseOfBlockage}
         */
        @Deprecated
        public boolean canTake(BuildableItem item) {
            return getCauseOfBlockage(item) == null;
        }

        /**
         * Checks whether the {@link Executor} represented by this object is capable of executing the given task.
         * @return a reason why it cannot, or null if it could
         * @since 2.37
         */
        public @CheckForNull CauseOfBlockage getCauseOfBlockage(BuildableItem item) {
            Node node = getNode();
            if (node == null) {
                return CauseOfBlockage.fromMessage(Messages._Queue_node_has_been_removed_from_configuration(executor.getOwner().getDisplayName()));
            }
            CauseOfBlockage reason = node.canTake(item);
            if (reason != null) {
                return reason;
            }
            for (QueueTaskDispatcher d : QueueTaskDispatcher.all()) {
                try {
                    reason = d.canTake(node, item);
                } catch (Throwable t) {
                    // We cannot guarantee the task can be taken by the node because something wrong happened
                    LOGGER.log(Level.WARNING, t, () -> String.format("Exception evaluating if the node '%s' can take the task '%s'", node.getNodeName(), item.task.getName()));
                    reason = CauseOfBlockage.fromMessage(Messages._Queue_ExceptionCanTake());
                }

                if (reason != null) {
                    return reason;
                }
            }
            // inlining isAvailable:
            if (workUnit != null) { // unlikely in practice (should not have even found this executor if so)
                return CauseOfBlockage.fromMessage(Messages._Queue_executor_slot_already_in_use());
            }
            if (executor.getOwner().isOffline()) {
                return new CauseOfBlockage.BecauseNodeIsOffline(node);
            }
            if (!executor.getOwner().isAcceptingTasks()) { // Node.canTake (above) does not consider RetentionStrategy.isAcceptingTasks
                return new CauseOfBlockage.BecauseNodeIsNotAcceptingTasks(node);
            }
            return null;
        }

        /**
         * Is this executor ready to accept some tasks?
         */
        @Override
        public boolean isAvailable() {
            return workUnit == null && !executor.getOwner().isOffline() && executor.getOwner().isAcceptingTasks();
        }

        @CheckForNull
        public Node getNode() {
            return executor.getOwner().getNode();
        }

        public boolean isNotExclusive() {
            return getNode().getMode() == Mode.NORMAL;
        }

        @Override
        public String toString() {
            return String.format("JobOffer[%s #%d]", executor.getOwner().getName(), executor.getNumber());
        }
    }

    private transient volatile LoadBalancer loadBalancer;

    private transient volatile QueueSorter sorter;

    private final transient AtmostOneTaskExecutor<Void> maintainerThread = new AtmostOneTaskExecutor<>(new Callable<>() {
        @Override
        public Void call() throws Exception {
            maintain();
            return null;
        }

        @Override
        public String toString() {
            return "Periodic Jenkins queue maintenance";
        }
    });

    private final transient ReentrantLock lock = new ReentrantLock();

    private final transient Condition condition = lock.newCondition();

    public Queue(@NonNull LoadBalancer loadBalancer) {
        this.loadBalancer =  loadBalancer.sanitize();
        // if all the executors are busy doing something, then the queue won't be maintained in
        // timely fashion, so use another thread to make sure it happens.
        new MaintainTask(this).periodic();
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(@NonNull LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer.sanitize();
    }

    public QueueSorter getSorter() {
        return sorter;
    }

    public void setSorter(QueueSorter sorter) {
        this.sorter = sorter;
    }

    /**
     * Simple queue state persistence object.
     */
    @Restricted(Beta.class)
    public static final class State {
        public /* almost final */ List<Item> items = new ArrayList<>();
        public /* almost final */ Map<String, Object> properties = new HashMap<>();

        private Object readResolve() {
            if (items == null) {
                items = new ArrayList<>();
            }
            if (properties == null) {
                properties = new HashMap<>();
            }
            return this;
        }
    }

    /**
     * Loads the queue contents that was {@link #save() saved}.
     */
    public void load() {
        lock.lock();
        try { try {
            // Clear items, for the benefit of reloading.
            waitingList.clear();
            blockedProjects.clear();
            buildables.clear();
            pendings.clear();

            File queueFile = getXMLQueueFile();
            if (Files.exists(queueFile.toPath())) {
                Object unmarshaledObj = new XmlFile(XSTREAM, queueFile).read();
                List items;

                State state;
                if (unmarshaledObj instanceof State) {
                    state = (State) unmarshaledObj;
                    items = state.items;
                } else {
                    // backward compatibility - it's an old List queue.xml
                    items = (List) unmarshaledObj;
                    state = new State();
                    state.items.addAll(items);
                }
                QueueIdStrategy.get().load(state);


                for (Object o : items) {
                    if (o instanceof Task) {
                        // backward compatibility
                        schedule((Task) o, 0);
                    } else if (o instanceof Item) {
                        Item item = (Item) o;

                        if (item.task == null) {
                            continue;   // botched persistence. throw this one away
                        }

                        if (item instanceof WaitingItem) {
                            item.enter(this);
                        } else if (item instanceof BlockedItem) {
                            item.enter(this);
                        } else if (item instanceof BuildableItem) {
                            item.enter(this);
                        } else {
                            throw new IllegalStateException("Unknown item type! " + item);
                        }
                    }
                }

                // I just had an incident where all the executors are dead at AbstractProject._getRuns()
                // because runs is null. Debugger revealed that this is caused by a MatrixConfiguration
                // object that doesn't appear to be de-serialized properly.
                // I don't know how this problem happened, but to diagnose this problem better
                // when it happens again, save the old queue file for introspection.
                File bk = new File(queueFile.getPath() + ".bak");
                Files.move(queueFile.toPath(), bk.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | InvalidPathException e) {
            LOGGER.log(Level.WARNING, "Failed to load the queue file " + getXMLQueueFile(), e);
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    /**
     * Persists the queue contents to the disk.
     */
    @Override
    public void save() {
        if (BulkChange.contains(this))  return;
        if (Jenkins.getInstanceOrNull() == null) {
            return;
        }

        XmlFile queueFile = new XmlFile(XSTREAM, getXMLQueueFile());
        lock.lock();
        try {
            // write out the queue state we want to save
            State state = new State();
            QueueIdStrategy.get().persist(state);
            // write out the tasks on the queue
            for (Item item : getItems()) {
                if (item.task instanceof TransientTask)  continue;
                state.items.add(item);
            }

            try {
                queueFile.write(state);
            } catch (IOException e) {
                LOGGER.log(e instanceof ClosedByInterruptException ? Level.FINE : Level.WARNING, "Failed to write out the queue file " + getXMLQueueFile(), e);
            }
        } finally {
            lock.unlock();
        }
        SaveableListener.fireOnChange(this, queueFile);
    }

    /**
     * Wipes out all the items currently in the queue, as if all of them are cancelled at once.
     */
    public void clear() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        lock.lock();
        try { try {
            for (WaitingItem i : new ArrayList<>(
                    waitingList))   // copy the list as we'll modify it in the loop
                i.cancel(this);
            blockedProjects.cancelAll();
            pendings.cancelAll();
            buildables.cancelAll();
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
        scheduleMaintenance();
    }

    /*package*/ File getXMLQueueFile() {
        String id = SystemProperties.getString(Queue.class.getName() + ".id");
        if (id != null) {
            return new File(Jenkins.get().getRootDir(), "queue/" + id + ".xml");
        }
        return new File(Jenkins.get().getRootDir(), "queue.xml");
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(AbstractProject)}
     */
    @Deprecated
    public boolean add(AbstractProject p) {
        return schedule(p) != null;
    }

    /**
     * Schedule a new build for this project.
     * @see #schedule(Task, int)
     */
    public @CheckForNull WaitingItem schedule(AbstractProject p) {
        return schedule(p, p.getQuietPeriod());
    }

    /**
     * Schedules a new build with a custom quiet period.
     *
     * <p>
     * Left for backward compatibility with &lt;1.114.
     *
     * @since 1.105
     * @deprecated as of 1.311
     *      Use {@link #schedule(Task, int)}
     */
    @Deprecated
    public boolean add(AbstractProject p, int quietPeriod) {
        return schedule(p, quietPeriod) != null;
    }

    /**
     * @deprecated as of 1.521
     *  Use {@link #schedule2(Task, int, List)}
     */
    @Deprecated
    public WaitingItem schedule(Task p, int quietPeriod, List<Action> actions) {
        return schedule2(p, quietPeriod, actions).getCreateItem();
    }

    /**
     * Schedules an execution of a task.
     *
     * @param actions
     *      These actions can be used for associating information scoped to a particular build, to
     *      the task being queued. Upon the start of the build, these {@link Action}s will be automatically
     *      added to the {@link Run} object, and hence available to everyone.
     *      For the convenience of the caller, this list can contain null, and those will be silently ignored.
     * @since 1.311
     * @return
     *      {@link hudson.model.queue.ScheduleResult.Refused} if Jenkins refused to add this task into the queue (for example because the system
     *      is about to shutdown.) Otherwise the task is either merged into existing items in the queue
     *      (in which case you get {@link hudson.model.queue.ScheduleResult.Existing} instance back), or a new item
     *      gets created in the queue (in which case you get {@link Created}.
     *
     *      Note the nature of the queue
     *      is that such {@link Item} only captures the state of the item at a particular moment,
     *      and by the time you inspect the object, some of its information can be already stale.
     *
     *      That said, one can still look at {@link Queue.Item#future}, {@link Queue.Item#getId()}, etc.
     */
    public @NonNull ScheduleResult schedule2(Task p, int quietPeriod, List<Action> actions) {
        // remove nulls
        actions = new ArrayList<>(actions);
        actions.removeIf(Objects::isNull);

        lock.lock();
        try { try {
            for (QueueDecisionHandler h : QueueDecisionHandler.all())
                if (!h.shouldSchedule(p, actions))
                    return ScheduleResult.refused();    // veto

            return scheduleInternal(p, quietPeriod, actions);
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    /**
     * Schedules an execution of a task.
     *
     * @since 1.311
     * @return
     *      {@link hudson.model.queue.ScheduleResult.Existing} if this task is already in the queue and
     *      therefore the add operation was no-op. Otherwise {@link hudson.model.queue.ScheduleResult.Created}
     *      indicates the {@link WaitingItem} object added, although the nature of the queue
     *      is that such {@link Item} only captures the state of the item at a particular moment,
     *      and by the time you inspect the object, some of its information can be already stale.
     *
     *      That said, one can still look at {@link WaitingItem#future}, {@link WaitingItem#getId()}, etc.
     */
    private @NonNull ScheduleResult scheduleInternal(Task p, int quietPeriod, List<Action> actions) {
        lock.lock();
        try { try {
            Calendar due = new GregorianCalendar();
            due.add(Calendar.SECOND, quietPeriod);

            // Do we already have this task in the queue? Because if so, we won't schedule a new one.
            List<Item> duplicatesInQueue = new ArrayList<>();
            for (Item item : liveGetItems(p)) {
                boolean shouldScheduleItem = false;
                for (QueueAction action : item.getActions(QueueAction.class)) {
                    shouldScheduleItem |= action.shouldSchedule(actions);
                }
                for (QueueAction action : Util.filter(actions, QueueAction.class)) {
                    shouldScheduleItem |= action.shouldSchedule(new ArrayList<>(item.getAllActions()));
                }
                if (!shouldScheduleItem) {
                    duplicatesInQueue.add(item);
                }
            }
            if (duplicatesInQueue.isEmpty()) {
                LOGGER.log(Level.FINE, "{0} added to queue", p);

                // put the item in the queue
                WaitingItem added = new WaitingItem(due, p, actions);
                added.enter(this);
                scheduleMaintenance();   // let an executor know that a new item is in the queue.
                return ScheduleResult.created(added);
            }

            LOGGER.log(Level.FINE, "{0} is already in the queue", p);

            // but let the actions affect the existing stuff.
            for (Item item : duplicatesInQueue) {
                for (FoldableAction a : Util.filter(actions, FoldableAction.class)) {
                    a.foldIntoExisting(item, p, actions);
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "after folding {0}, {1} includes {2}", new Object[] {a, item, item.getAllActions()});
                    }
                }
            }

            boolean queueUpdated = false;
            for (WaitingItem wi : Util.filter(duplicatesInQueue, WaitingItem.class)) {
                // make sure to always use the shorter of the available due times
                if (wi.timestamp.before(due))
                    continue;

                // waitingList is sorted, so when we change a timestamp we need to maintain order
                wi.leave(this);
                wi.timestamp = due;
                wi.enter(this);
                queueUpdated = true;
            }

            if (queueUpdated) scheduleMaintenance();

            // REVISIT: when there are multiple existing items in the queue that matches the incoming one,
            // whether the new one should affect all existing ones or not is debatable. I for myself
            // thought this would only affect one, so the code was bit of surprise, but I'm keeping the current
            // behaviour.
            return ScheduleResult.existing(duplicatesInQueue.get(0));
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }


    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(Task, int)}
     */
    @Deprecated
    public boolean add(Task p, int quietPeriod) {
        return schedule(p, quietPeriod) != null;
    }

    public @CheckForNull WaitingItem schedule(Task p, int quietPeriod) {
        return schedule(p, quietPeriod, new Action[0]);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(Task, int, Action...)}
     */
    @Deprecated
    public boolean add(Task p, int quietPeriod, Action... actions) {
        return schedule(p, quietPeriod, actions) != null;
    }

    /**
     * Convenience wrapper method around {@link #schedule(Task, int, List)}
     */
    public @CheckForNull WaitingItem schedule(Task p, int quietPeriod, Action... actions) {
        return schedule2(p, quietPeriod, actions).getCreateItem();
    }

    /**
     * Convenience wrapper method around {@link #schedule2(Task, int, List)}
     */
    public @NonNull ScheduleResult schedule2(Task p, int quietPeriod, Action... actions) {
        return schedule2(p, quietPeriod, Arrays.asList(actions));
    }

    /**
     * Cancels the item in the queue. If the item is scheduled more than once, cancels the first occurrence.
     *
     * @return true if the project was indeed in the queue and was removed.
     *         false if this was no-op.
     */
    public boolean cancel(Task p) {
        lock.lock();
        try { try {
            LOGGER.log(Level.FINE, "Cancelling {0}", p);
            for (WaitingItem item : waitingList) {
                if (item.task.equals(p)) {
                    return item.cancel(this);
                }
            }
            // use bitwise-OR to make sure that both branches get evaluated all the time
            return blockedProjects.cancel(p) != null | buildables.cancel(p) != null;
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    private void updateSnapshot() {
        Snapshot revised = new Snapshot(waitingList, blockedProjects, buildables, pendings);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0} → {1}; leftItems={2}", new Object[] {snapshot, revised, leftItems.asMap()});
        }
        snapshot = revised;
    }

    public boolean cancel(Item item) {
        LOGGER.log(Level.FINE, "Cancelling {0} item#{1}", new Object[] {item.task, item.id});
        lock.lock();
        try { try {
            return item.cancel(this);
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    /**
     * Called from {@code queue.jelly} and {@code queue-items.jelly}.
     */
    @RequirePOST
    public HttpResponse doCancelItem(@QueryParameter long id) throws IOException, ServletException {
        Item item = getItem(id);
        if (item != null && !hasReadPermission(item, true)) {
            item = null;
        }
        if (item != null) {
            if (item.hasCancelPermission()) {
                if (cancel(item)) {
                    return HttpResponses.status(HttpServletResponse.SC_NO_CONTENT);
                }
                return HttpResponses.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not cancel run for id " + id);
            }
            return HttpResponses.error(422, "Item for id (" + id + ") is not cancellable");
        } // else too late, ignore (JENKINS-14813)
        return HttpResponses.error(HttpServletResponse.SC_NOT_FOUND, "Provided id (" + id + ") not found");
    }

    public boolean isEmpty() {
        Snapshot snapshot = this.snapshot;
        return snapshot.waitingList.isEmpty() && snapshot.blockedProjects.isEmpty() && snapshot.buildables.isEmpty()
                && snapshot.pendings.isEmpty();
    }

    private WaitingItem peek() {
        return waitingList.iterator().next();
    }

    /**
     * Gets a snapshot of items in the queue.
     *
     * Generally speaking the array is sorted such that the items that are most likely built sooner are
     * at the end.
     */
    @Exported(inline = true)
    public Item[] getItems() {
        Snapshot s = this.snapshot;
        List<Item> r = new ArrayList<>();

        for (WaitingItem p : s.waitingList) {
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BlockedItem p : s.blockedProjects) {
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BuildableItem p : reverse(s.buildables)) {
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BuildableItem p : reverse(s.pendings)) {
            r = checkPermissionsAndAddToList(r, p);
        }
        Item[] items = new Item[r.size()];
        r.toArray(items);
        return items;
    }

    private List<Item> checkPermissionsAndAddToList(List<Item> r, Item t) {
        // TODO Changing the second arg to 'true' should reveal some tasks currently hidden for no obvious reason
        if (hasReadPermission(t.task, false)) {
            r.add(t);
        }
        return r;
    }

    private static boolean hasReadPermission(Item t, boolean valueIfNotAccessControlled) {
        return hasReadPermission(t.task, valueIfNotAccessControlled);
    }

    private static boolean hasReadPermission(Queue.Task t, boolean valueIfNotAccessControlled) {
        if (t instanceof AccessControlled) {
            AccessControlled taskAC = (AccessControlled) t;
            if (taskAC.hasPermission(hudson.model.Item.READ)
                    || taskAC.hasPermission(Permission.READ)) { // TODO should be unnecessary given the 'implies' relationship
                return true;
            }
            return false;
        }
        return valueIfNotAccessControlled;
    }

    /**
     * Returns an array of Item for which it is only visible the name of the task.
     *
     * Generally speaking the array is sorted such that the items that are most likely built sooner are
     * at the end.
     */
    @Restricted(NoExternalUse.class)
    @Exported(inline = true)
    public StubItem[] getDiscoverableItems() {
        Snapshot s = this.snapshot;
        List<StubItem> r = new ArrayList<>();

        for (WaitingItem p : s.waitingList) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BlockedItem p : s.blockedProjects) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BuildableItem p : reverse(s.buildables)) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BuildableItem p : reverse(s.pendings)) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        StubItem[] items = new StubItem[r.size()];
        r.toArray(items);
        return items;
    }

    private List<StubItem> filterDiscoverableItemListBasedOnPermissions(List<StubItem> r, Item t) {
        if (t.task instanceof hudson.model.Item) {
            hudson.model.Item taskAsItem = (hudson.model.Item) t.task;
            if (!taskAsItem.hasPermission(hudson.model.Item.READ)
                    && taskAsItem.hasPermission(hudson.model.Item.DISCOVER)) {
                r.add(new StubItem(new StubTask(t.task)));
            }
        }
        return r;
    }

    /**
     * Like {@link #getItems()}, but returns an approximation that might not be completely up-to-date.
     *
     * <p>
     * At the expense of accuracy, this method does not usually lock {@link Queue} and therefore is faster
     * in a highly concurrent situation.
     *
     * <p>
     * The list obtained is an accurate snapshot of the queue at some point in the past. The snapshot
     * is updated and normally no more than one second old, but this is a soft commitment that might
     * get violated when the lock on {@link Queue} is highly contended.
     *
     * <p>
     * This method is primarily added to make UI threads run faster.
     *
     * @since 1.483
     * @deprecated Use {@link #getItems()} directly. As of 1.607 the approximation is no longer needed.
     */
    @Deprecated
    public List<Item> getApproximateItemsQuickly() {
        return Arrays.asList(getItems());
    }

    public Item getItem(long id) {
        Snapshot snapshot = this.snapshot;
        for (Item item : snapshot.blockedProjects) {
            if (item.id == id)
                return item;
        }
        for (Item item : snapshot.buildables) {
            if (item.id == id)
                return item;
        }
        for (Item item : snapshot.pendings) {
            if (item.id == id)
                return item;
        }
        for (Item item : snapshot.waitingList) {
            if (item.id == id) {
                return item;
            }
        }
        return leftItems.getIfPresent(id);
    }

    /**
     * Gets all the {@link BuildableItem}s that are waiting for an executor in the given {@link Computer}.
     */
    public List<BuildableItem> getBuildableItems(Computer c) {
        Snapshot snapshot = this.snapshot;
        List<BuildableItem> result = new ArrayList<>();
        _getBuildableItems(c, snapshot.buildables, result);
        _getBuildableItems(c, snapshot.pendings, result);
        return result;
    }

    private void _getBuildableItems(Computer c, List<BuildableItem> col, List<BuildableItem> result) {
        Node node = c.getNode();
        if (node == null)   // Deleted computers cannot take build items...
            return;
        for (BuildableItem p : col) {
            if (node.canTake(p) == null)
                result.add(p);
        }
    }

    /**
     * Gets the snapshot of all {@link BuildableItem}s.
     */
    public List<BuildableItem> getBuildableItems() {
        Snapshot snapshot = this.snapshot;
        ArrayList<BuildableItem> r = new ArrayList<>(snapshot.buildables);
        r.addAll(snapshot.pendings);
        return r;
    }

    /**
     * Gets the snapshot of all {@link BuildableItem}s.
     */
    public List<BuildableItem> getPendingItems() {
        return new ArrayList<>(snapshot.pendings);
    }

    /**
     * Gets the snapshot of all {@link BlockedItem}s.
     */
    protected List<BlockedItem> getBlockedItems() {
        return new ArrayList<>(snapshot.blockedProjects);
    }

    /**
     * Returns the snapshot of all {@link LeftItem}s.
     *
     * @since 1.519
     */
    public Collection<LeftItem> getLeftItems() {
        return Collections.unmodifiableCollection(leftItems.asMap().values());
    }

    /**
     * Immediately clear the {@link #getLeftItems} cache.
     * Useful for tests which need to verify that no links to a build remain.
     * @since 1.519
     */
    public void clearLeftItems() {
        leftItems.invalidateAll();
    }

    /**
     * Gets all items that are in the queue but not blocked
     *
     * @since 1.402
     */
    public List<Item> getUnblockedItems() {
        Snapshot snapshot = this.snapshot;
        List<Item> queuedNotBlocked = new ArrayList<>();
        queuedNotBlocked.addAll(snapshot.waitingList);
        queuedNotBlocked.addAll(snapshot.buildables);
        queuedNotBlocked.addAll(snapshot.pendings);
        // but not 'blockedProjects'
        return queuedNotBlocked;
    }

    /**
     * Works just like {@link #getUnblockedItems()} but return tasks.
     *
     * @since 1.402
     */
    public Set<Task> getUnblockedTasks() {
        List<Item> items = getUnblockedItems();
        Set<Task> unblockedTasks = new HashSet<>(items.size());
        for (Queue.Item t : items)
            unblockedTasks.add(t.task);
        return unblockedTasks;
    }

    /**
     * Is the given task currently pending execution?
     */
    public boolean isPending(Task t) {
        Snapshot snapshot = this.snapshot;
        for (BuildableItem i : snapshot.pendings)
            if (i.task.equals(t))
                return true;
        return false;
    }

    /**
     * How many {@link BuildableItem}s are assigned for the given label?
     * @param l Label to be checked. If null, any label will be accepted.
     *    If you want to count {@link BuildableItem}s without assigned labels,
     *    use {@link #strictCountBuildableItemsFor(hudson.model.Label)}.
     * @return Number of {@link BuildableItem}s for the specified label.
     */
    public /* @java.annotation.Nonnegative */ int countBuildableItemsFor(@CheckForNull Label l) {
        Snapshot snapshot = this.snapshot;
        int r = 0;
        for (BuildableItem bi : snapshot.buildables)
            for (SubTask st : bi.task.getSubTasks())
                if (null == l || bi.getAssignedLabelFor(st) == l)
                    r++;
        for (BuildableItem bi : snapshot.pendings)
            for (SubTask st : bi.task.getSubTasks())
                if (null == l || bi.getAssignedLabelFor(st) == l)
                    r++;
        return r;
    }

    /**
     * How many {@link BuildableItem}s are assigned for the given label?
     * <p>
     * The implementation is quite similar to {@link #countBuildableItemsFor(hudson.model.Label)},
     * but it has another behavior for null parameters.
     * @param l Label to be checked. If null, only jobs without assigned labels
     *      will be taken into the account.
     * @return Number of {@link BuildableItem}s for the specified label.
     * @since 1.615
     */
    public /* @java.annotation.Nonnegative */ int strictCountBuildableItemsFor(@CheckForNull Label l) {
        Snapshot _snapshot = this.snapshot;
        int r = 0;
        for (BuildableItem bi : _snapshot.buildables)
            for (SubTask st : bi.task.getSubTasks())
                if (bi.getAssignedLabelFor(st) == l)
                    r++;
        for (BuildableItem bi : _snapshot.pendings)
            for (SubTask st : bi.task.getSubTasks())
                if (bi.getAssignedLabelFor(st) == l)
                    r++;
        return r;
    }

    /**
     * Counts all the {@link BuildableItem}s currently in the queue.
     */
    public int countBuildableItems() {
        return countBuildableItemsFor(null);
    }

    /**
     * Gets the information about the queue item for the given project.
     *
     * @return null if the project is not in the queue.
     */
    public Item getItem(Task t) {
        Snapshot snapshot = this.snapshot;
        for (Item item : snapshot.blockedProjects) {
            if (item.task.equals(t))
                return item;
        }
        for (Item item : snapshot.buildables) {
            if (item.task.equals(t))
                return item;
        }
        for (Item item : snapshot.pendings) {
            if (item.task.equals(t))
                return item;
        }
        for (Item item : snapshot.waitingList) {
            if (item.task.equals(t)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Gets the information about the queue item for the given project.
     *
     * @return null if the project is not in the queue.
     * @since 1.607
     */
    private List<Item> liveGetItems(Task t) {
        lock.lock();
        try {
            List<Item> result = new ArrayList<>();
            result.addAll(blockedProjects.getAll(t));
            result.addAll(buildables.getAll(t));
            // Do not include pendings—we have already finalized WorkUnitContext.actions.
            if (LOGGER.isLoggable(Level.FINE)) {
                List<BuildableItem> thePendings = pendings.getAll(t);
                if (!thePendings.isEmpty()) {
                    LOGGER.log(Level.FINE, "ignoring {0} during scheduleInternal", thePendings);
                }
            }
            for (Item item : waitingList) {
                if (item.task.equals(t)) {
                    result.add(item);
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the information about the queue item for the given project.
     *
     * @return empty if the project is not in the queue.
     */
    public List<Item> getItems(Task t) {
        Snapshot snapshot = this.snapshot;
        List<Item> result = new ArrayList<>();
        for (Item item : snapshot.blockedProjects) {
            if (item.task.equals(t)) {
                result.add(item);
            }
        }
        for (Item item : snapshot.buildables) {
            if (item.task.equals(t)) {
                result.add(item);
            }
        }
        for (Item item : snapshot.pendings) {
            if (item.task.equals(t)) {
                result.add(item);
            }
        }
        for (Item item : snapshot.waitingList) {
            if (item.task.equals(t)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Returns true if this queue contains the said project.
     */
    public boolean contains(Task t) {
        return getItem(t) != null;
    }

    /**
     * Called when the executor actually starts executing the assigned work unit.
     *
     * This moves the task from the pending state to the "left the queue" state.
     */
    /*package*/ void onStartExecuting(Executor exec) throws InterruptedException {
        lock.lock();
        try { try {
            final WorkUnit wu = exec.getCurrentWorkUnit();
            pendings.remove(wu.context.item);

            LeftItem li = new LeftItem(wu.context);
            li.enter(this);
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    /**
     * Checks the queue and runs anything that can be run.
     *
     * <p>
     * When conditions are changed, this method should be invoked.
     * <p>
     * This wakes up one {@link Executor} so that it will maintain a queue.
     */
    @WithBridgeMethods(void.class)
    public Future<?> scheduleMaintenance() {
        // LOGGER.info("Scheduling maintenance");
        return maintainerThread.submit();
    }

    /**
     * Checks if the given item should be prevented from entering into the {@link #buildables} state
     * and instead stay in the {@link #blockedProjects} state.
     *
     * @return the reason of blockage if it exists null otherwise.
     */
    @CheckForNull
    private CauseOfBlockage getCauseOfBlockageForItem(Item i) {
        CauseOfBlockage causeOfBlockage = getCauseOfBlockageForTask(i.task);
        if (causeOfBlockage != null) {
            return causeOfBlockage;
        }

        for (QueueTaskDispatcher d : QueueTaskDispatcher.all()) {
            try {
                causeOfBlockage = d.canRun(i);
            } catch (Throwable t) {
                // We cannot guarantee the task can be run because something wrong happened
                LOGGER.log(Level.WARNING, t, () -> String.format("Exception evaluating if the queue can run the task '%s'", i.task.getName()));
                causeOfBlockage = CauseOfBlockage.fromMessage(Messages._Queue_ExceptionCanRun());
            }
            if (causeOfBlockage != null)
                return causeOfBlockage;
        }

        if (!(i instanceof BuildableItem)) {
            // Make sure we don't queue two tasks of the same project to be built
            // unless that project allows concurrent builds. Once item is buildable it's ok.
            //
            // This check should never pass. And must be remove once we can completely rely on `getCauseOfBlockage`.
            // If `task.isConcurrentBuild` returns `false`,
            // it should also return non-null value for `task.getCauseOfBlockage` in case of on-going execution.
            // But both are public non-final methods, so, we need to keep backward compatibility here.
            // And check one more time across all `buildables` and `pendings` for O(N) each.
            if (!i.task.isConcurrentBuild() && (buildables.containsKey(i.task) || pendings.containsKey(i.task))) {
                return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
            }
        }

        return null;
    }

    /**
     *
     * Checks if the given task knows the reasons to be blocked or it needs some unavailable resources
     *
     * @param task the task.
     * @return the reason of blockage if it exists null otherwise.
     */
    @CheckForNull
    private CauseOfBlockage getCauseOfBlockageForTask(Task task) {
        CauseOfBlockage causeOfBlockage = task.getCauseOfBlockage();
        if (causeOfBlockage != null) {
            return causeOfBlockage;
        }

        if (!canRun(task.getResourceList())) {
            ResourceActivity r = getBlockingActivity(task);
            if (r != null) {
                if (r == task) // blocked by itself, meaning another build is in progress
                    return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
                return CauseOfBlockage.fromMessage(Messages._Queue_BlockedBy(r.getDisplayName()));
            }
        }

        return null;
    }

    /**
     * Some operations require to be performed with the {@link Queue} lock held. Use one of these methods rather
     * than locking directly on Queue in order to allow for future refactoring.
     * @param runnable the operation to perform.
     * @since 1.592
     */
    public static void withLock(Runnable runnable) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        if (queue == null) {
            runnable.run();
        } else {
            queue._withLock(runnable);
        }
    }

    /**
     * Some operations require the {@link Queue} lock held. Use one of these methods rather
     * than locking directly on Queue in order to allow for future refactoring.
     *
     * @param callable the operation to perform.
     * @param <V>      the type of return value
     * @param <T>      the type of exception.
     * @return the result of the callable.
     * @throws T the exception of the callable
     * @since 1.592
     */
    public static <V, T extends Throwable> V withLock(hudson.remoting.Callable<V, T> callable) throws T {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        if (queue == null) {
            return callable.call();
        } else {
            return queue._withLock(callable);
        }
    }

    /**
     * Some operations require to be performed with the {@link Queue} lock held. Use one of these methods rather
     * than locking directly on Queue in order to allow for future refactoring.
     *
     * @param callable the operation to perform.
     * @param <V>      the type of return value
     * @return the result of the callable.
     * @throws Exception if the callable throws an exception.
     * @since 1.592
     */
    public static <V> V withLock(java.util.concurrent.Callable<V> callable) throws Exception {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        if (queue == null) {
            return callable.call();
        } else {
            return queue._withLock(callable);
        }
    }

    /**
     * Invokes the supplied {@link Runnable} if the {@link Queue} lock was obtained without blocking.
     *
     * @param runnable the operation to perform.
     * @return {@code true} if the lock was available and the operation was performed.
     * @since 1.618
     */
    public static boolean tryWithLock(Runnable runnable) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        if (queue == null) {
            runnable.run();
            return true;
        } else {
            return queue._tryWithLock(runnable);
        }
    }
    /**
     * Wraps a {@link Runnable} with the  {@link Queue} lock held.
     *
     * @param runnable the operation to wrap.
     * @since 1.618
     */

    public static Runnable wrapWithLock(Runnable runnable) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        return queue == null ? runnable : new LockedRunnable(runnable);
    }

    /**
     * Wraps a {@link hudson.remoting.Callable} with the  {@link Queue} lock held.
     *
     * @param callable the operation to wrap.
     * @since 1.618
     */
    public static <V, T extends Throwable> hudson.remoting.Callable<V, T> wrapWithLock(hudson.remoting.Callable<V, T> callable) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        return queue == null ? callable : new LockedHRCallable<>(callable);
    }

    /**
     * Wraps a {@link java.util.concurrent.Callable} with the {@link Queue} lock held.
     *
     * @param callable the operation to wrap.
     * @since 1.618
     */
    public static <V> java.util.concurrent.Callable<V> wrapWithLock(java.util.concurrent.Callable<V> callable) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // TODO confirm safe to assume non-null and use getInstance()
        final Queue queue = jenkins == null ? null : jenkins.getQueue();
        return queue == null ? callable : new LockedJUCCallable<>(callable);
    }

    @Override
    @SuppressFBWarnings(value = "WA_AWAIT_NOT_IN_LOOP", justification = "the caller does indeed call this method in a loop")
    protected void _await() throws InterruptedException {
        condition.await();
    }

    @Override
    protected void _signalAll() {
        condition.signalAll();
    }

    /**
     * Some operations require to be performed with the {@link Queue} lock held. Use one of these methods rather
     * than locking directly on Queue in order to allow for future refactoring.
     * @param runnable the operation to perform.
     * @since 1.592
     */
    @Override
    protected void _withLock(Runnable runnable) {
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invokes the supplied {@link Runnable} if the {@link Queue} lock was obtained without blocking.
     *
     * @param runnable the operation to perform.
     * @return {@code true} if the lock was available and the operation was performed.
     * @since 1.618
     */
    protected boolean _tryWithLock(Runnable runnable) {
        if (lock.tryLock()) {
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Some operations require to be performed with the {@link Queue} lock held. Use one of these methods rather
     * than locking directly on Queue in order to allow for future refactoring.
     *
     * @param callable the operation to perform.
     * @param <V>      the type of return value
     * @param <T>      the type of exception.
     * @return the result of the callable.
     * @throws T the exception of the callable
     * @since 1.592
     */
    @Override
    protected <V, T extends Throwable> V _withLock(hudson.remoting.Callable<V, T> callable) throws T {
        lock.lock();
        try {
            return callable.call();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Some operations require to be performed with the {@link Queue} lock held. Use one of these methods rather
     * than locking directly on Queue in order to allow for future refactoring.
     *
     * @param callable the operation to perform.
     * @param <V>      the type of return value
     * @return the result of the callable.
     * @throws Exception if the callable throws an exception.
     * @since 1.592
     */
    @Override
    protected <V> V _withLock(java.util.concurrent.Callable<V> callable) throws Exception {
        lock.lock();
        try {
            return callable.call();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Queue maintenance.
     *
     * <p>
     * Move projects between {@link #waitingList}, {@link #blockedProjects}, {@link #buildables}, and {@link #pendings}
     * appropriately.
     *
     * <p>
     * Jenkins internally invokes this method by itself whenever there's a change that can affect
     * the scheduling (such as new node becoming online, # of executors change, a task completes execution, etc.),
     * and it also gets invoked periodically (see {@link Queue.MaintainTask}.)
     */
    public void maintain() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }
        lock.lock();
        try { try {

            LOGGER.log(Level.FINE, "Queue maintenance started on {0} with {1}", new Object[] {this, snapshot});

            // The executors that are currently waiting for a job to run.
            Map<Executor, JobOffer> parked = new HashMap<>();

            { // update parked (and identify any pending items whose executor has disappeared)
                List<BuildableItem> lostPendings = new ArrayList<>(pendings);
                for (Computer c : jenkins.getComputers()) {
                    for (Executor e : c.getAllExecutors()) {
                        if (e.isInterrupted()) {
                            // JENKINS-28840 we will deadlock if we try to touch this executor while interrupt flag set
                            // we need to clear lost pendings as we cannot know what work unit was on this executor
                            // while it is interrupted. (All this dancing is a result of Executor extending Thread)
                            lostPendings.clear(); // we'll get them next time around when the flag is cleared.
                            LOGGER.log(Level.FINEST,
                                    "Interrupt thread for executor {0} is set and we do not know what work unit was on the executor.",
                                    e.getDisplayName());
                            continue;
                        }
                        if (e.isParking()) {
                            LOGGER.log(Level.FINEST, "{0} is parking and is waiting for a job to execute.", e.getDisplayName());
                            parked.put(e, new JobOffer(e));
                        }
                        final WorkUnit workUnit = e.getCurrentWorkUnit();
                        if (workUnit != null) {
                            lostPendings.remove(workUnit.context.item);
                        }
                    }
                }
                // pending -> buildable
                for (BuildableItem p : lostPendings) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE,
                            "BuildableItem {0}: pending -> buildable as the assigned executor disappeared",
                            p.task.getFullDisplayName());
                    }
                    p.isPending = false;
                    pendings.remove(p);
                    makeBuildable(p); // TODO whatever this is for, the return value is being ignored, so this does nothing at all
                }
            }

            final QueueSorter s = sorter;

            { // blocked -> buildable
                // copy as we'll mutate the list and we want to process in a potentially different order
                List<BlockedItem> blockedItems = new ArrayList<>(blockedProjects.values());
                // if facing a cycle of blocked tasks, ensure we process in the desired sort order
                if (s != null) {
                    s.sortBlockedItems(blockedItems);
                } else {
                    blockedItems.sort(QueueSorter.DEFAULT_BLOCKED_ITEM_COMPARATOR);
                }
                for (BlockedItem p : blockedItems) {
                    String taskDisplayName = LOGGER.isLoggable(Level.FINEST) ? p.task.getFullDisplayName() : null;
                    LOGGER.log(Level.FINEST, "Current blocked item: {0}", taskDisplayName);
                    CauseOfBlockage causeOfBlockage = getCauseOfBlockageForItem(p);
                    if (causeOfBlockage == null) {
                        LOGGER.log(Level.FINEST,
                                "BlockedItem {0}: blocked -> buildable as the build is not blocked and new tasks are allowed",
                                taskDisplayName);

                        // ready to be executed
                        Runnable r = makeBuildable(new BuildableItem(p));
                        if (r != null) {
                            p.leave(this);
                            r.run();
                            // JENKINS-28926 we have removed a task from the blocked projects and added to building
                            // thus we should update the snapshot so that subsequent blocked projects can correctly
                            // determine if they are blocked by the lucky winner
                            updateSnapshot();
                        }
                    } else {
                        p.setCauseOfBlockage(causeOfBlockage);
                    }
                }
            }

            // waitingList -> buildable/blocked
            while (!waitingList.isEmpty()) {
                WaitingItem top = peek();

                if (top.timestamp.compareTo(new GregorianCalendar()) > 0) {
                    LOGGER.log(Level.FINEST, "Finished moving all ready items from queue.");
                    break; // finished moving all ready items from queue
                }

                top.leave(this);
                CauseOfBlockage causeOfBlockage = getCauseOfBlockageForItem(top);
                if (causeOfBlockage == null) {
                    // ready to be executed immediately
                    Runnable r = makeBuildable(new BuildableItem(top));
                    String topTaskDisplayName = LOGGER.isLoggable(Level.FINEST) ? top.task.getFullDisplayName() : null;
                    if (r != null) {
                        LOGGER.log(Level.FINEST, "Executing runnable {0}", topTaskDisplayName);
                        r.run();
                    } else {
                        LOGGER.log(Level.FINEST, "Item {0} was unable to be made a buildable and is now a blocked item.", topTaskDisplayName);
                        new BlockedItem(top, CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown())).enter(this);
                    }
                } else {
                    // this can't be built now because another build is in progress
                    // set this project aside.
                    new BlockedItem(top, causeOfBlockage).enter(this);
                }
            }

            if (s != null) {
                try {
                    s.sortBuildableItems(buildables);
                } catch (Throwable e) {
                    // We don't really care if the sort doesn't sort anything, we still should
                    // continue to do our job. We'll complain about it and continue.
                    LOGGER.log(Level.WARNING, "s.sortBuildableItems() threw Throwable: {0}", e);
                }
            }

            // Ensure that identification of blocked tasks is using the live state: JENKINS-27708 & JENKINS-27871
            updateSnapshot();

            // allocate buildable jobs to executors
            for (BuildableItem p : new ArrayList<>(
                    buildables)) { // copy as we'll mutate the list in the loop
                // one last check to make sure this build is not blocked.
                CauseOfBlockage causeOfBlockage = getCauseOfBlockageForItem(p);
                if (causeOfBlockage != null) {
                    p.leave(this);
                    new BlockedItem(p, causeOfBlockage).enter(this);
                    LOGGER.log(Level.FINE, "Catching that {0} is blocked in the last minute", p);
                    // JENKINS-28926 we have moved an unblocked task into the blocked state, update snapshot
                    // so that other buildables which might have been blocked by this can see the state change
                    updateSnapshot();
                    continue;
                }

                String taskDisplayName = LOGGER.isLoggable(Level.FINEST) ? p.task.getFullDisplayName() : null;

                if (p.task instanceof FlyweightTask) {
                    Runnable r = makeFlyWeightTaskBuildable(new BuildableItem(p));
                    if (r != null) {
                        p.leave(this);
                        LOGGER.log(Level.FINEST, "Executing flyweight task {0}", taskDisplayName);
                        r.run();
                        updateSnapshot();
                    }
                } else {

                    List<JobOffer> candidates = new ArrayList<>(parked.size());
                    Map<Node, CauseOfBlockage> reasonMap = new HashMap<>();
                    for (JobOffer j : parked.values()) {
                        Node offerNode = j.getNode();
                        CauseOfBlockage reason;
                        if (reasonMap.containsKey(offerNode)) {
                            reason = reasonMap.get(offerNode);
                        } else {
                            reason = j.getCauseOfBlockage(p);
                            reasonMap.put(offerNode, reason);
                        }
                        if (reason == null) {
                            LOGGER.log(Level.FINEST,
                                    "{0} is a potential candidate for task {1}",
                                    new Object[]{j, taskDisplayName});
                            candidates.add(j);
                        } else {
                            LOGGER.log(Level.FINEST, "{0} rejected {1}: {2}", new Object[] {j, taskDisplayName, reason});
                        }
                    }

                    MappingWorksheet ws = new MappingWorksheet(p, candidates);
                    Mapping m = loadBalancer.map(p.task, ws);
                    if (m == null) {
                        // if we couldn't find the executor that fits,
                        // just leave it in the buildables list and
                        // check if we can execute other projects
                        LOGGER.log(Level.FINER, "Failed to map {0} to executors. candidates={1} parked={2}",
                                new Object[]{p, candidates, parked.values()});
                        List<CauseOfBlockage> reasons = reasonMap.values().stream().filter(Objects::nonNull).collect(Collectors.toList());
                        p.transientCausesOfBlockage = reasons.isEmpty() ? null : reasons;
                        continue;
                    }

                    // found a matching executor. use it.
                    WorkUnitContext wuc = new WorkUnitContext(p);
                    LOGGER.log(Level.FINEST, "Found a matching executor for {0}. Using it.", taskDisplayName);
                    m.execute(wuc);

                    p.leave(this);
                    if (!wuc.getWorkUnits().isEmpty()) {
                        LOGGER.log(Level.FINEST, "BuildableItem {0} marked as pending.", taskDisplayName);
                        makePending(p);
                    }
                    else
                        LOGGER.log(Level.FINEST, "BuildableItem {0} with empty work units!?", p);

                    // Ensure that identification of blocked tasks is using the live state: JENKINS-27708 & JENKINS-27871
                    // The creation of a snapshot itself should be relatively cheap given the expected rate of
                    // job execution. You probably would need 100's of jobs starting execution every iteration
                    // of maintain() before this could even start to become an issue and likely the calculation
                    // of getCauseOfBlockageForItem(p) will become a bottleneck before updateSnapshot() will. Additionally
                    // since the snapshot itself only ever has at most one reference originating outside of the stack
                    // it should remain in the eden space and thus be cheap to GC.
                    // See https://issues.jenkins.io/browse/JENKINS-27708?focusedCommentId=225819&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-225819
                    // or https://issues.jenkins.io/browse/JENKINS-27708?focusedCommentId=225906&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-225906
                    // for alternative fixes of this issue.
                    updateSnapshot();
                }
            }
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    /**
     * Tries to make an item ready to build.
     * @param p a proposed buildable item
     * @return a thunk to actually prepare it (after leaving an earlier list), or null if it cannot be run now
     */
    private @CheckForNull Runnable makeBuildable(final BuildableItem p) {
        if (p.task instanceof FlyweightTask) {
            String taskDisplayName = LOGGER.isLoggable(Level.FINEST) ? p.task.getFullDisplayName() : null;
            if (!isBlockedByShutdown(p.task)) {

                Runnable runnable = makeFlyWeightTaskBuildable(p);
                LOGGER.log(Level.FINEST, "Converting flyweight task: {0} into a BuildableRunnable", taskDisplayName);
                if (runnable != null) {
                    return runnable;
                }

                //this is to solve JENKINS-30084: the task has to be buildable to force the provisioning of nodes.
                //if the execution gets here, it means the task could not be scheduled since the node
                //the task is supposed to run on is offline or not available.
                //Thus, the flyweighttask enters the buildables queue and will ask Jenkins to provision a node
                LOGGER.log(Level.FINEST, "Flyweight task {0} is entering as buildable to provision a node.", taskDisplayName);
                return new BuildableRunnable(p);
            }
            // if the execution gets here, it means the task is blocked by shutdown and null is returned.
            LOGGER.log(Level.FINEST, "Task {0} is blocked by shutdown.", taskDisplayName);
            return null;
        } else {
            // regular heavyweight task
            return new BuildableRunnable(p);
        }
    }

    /**
     * This method checks if the flyweight task can be run on any of the available executors
     * @param p - the flyweight task to be scheduled
     * @return a Runnable if there is an executor that can take the task, null otherwise
     */
    @CheckForNull
    private Runnable makeFlyWeightTaskBuildable(final BuildableItem p) {
        //we double check if this is a flyweight task
        if (p.task instanceof FlyweightTask) {
            Jenkins h = Jenkins.get();

            Label lbl = p.getAssignedLabel();

            Computer masterComputer = h.toComputer();
            if (lbl != null && lbl.equals(h.getSelfLabel()) && masterComputer != null) {
                // the flyweight task is bound to the master
                if (h.canTake(p) == null) {
                    return createFlyWeightTaskRunnable(p, masterComputer);
                } else {
                    return null;
                }
            }

            if (lbl == null && h.canTake(p) == null && masterComputer != null && masterComputer.isOnline() && masterComputer.isAcceptingTasks()) {
                // The flyweight task is not tied to a specific label, so execute on master if possible.
                // This will ensure that actual agent disconnects do not impact flyweight tasks randomly assigned to them.
                return createFlyWeightTaskRunnable(p, masterComputer);
            }

            Map<Node, Integer> hashSource = new HashMap<>(h.getNodes().size());

            for (Node n : h.getNodes()) {
                hashSource.put(n, n.getNumExecutors() * 100);
            }

            ConsistentHash<Node> hash = new ConsistentHash<>(NODE_HASH);
            hash.addAll(hashSource);

            String fullDisplayName = p.task.getFullDisplayName();
            for (Node n : hash.list(fullDisplayName)) {
                final Computer c = n.toComputer();
                if (c == null || c.isOffline()) {
                    continue;
                }
                if (lbl != null && !lbl.contains(n)) {
                    continue;
                }
                if (n.canTake(p) != null) {
                    continue;
                }

                return createFlyWeightTaskRunnable(p, c);
            }
        }
        return null;
    }

    private Runnable createFlyWeightTaskRunnable(final BuildableItem p, final @NonNull Computer c) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Creating flyweight task {0} for computer {1}",
                    new Object[]{p.task.getFullDisplayName(), c.getName()});
        }
        return () -> {
            c.startFlyWeightTask(new WorkUnitContext(p).createWorkUnit(p.task));
            makePending(p);
        };
    }

    private static final ConsistentHash.Hash<Node> NODE_HASH = Node::getNodeName;

    private boolean makePending(BuildableItem p) {
        // LOGGER.info("Making "+p.task+" pending"); // REMOVE
        p.isPending = true;
        return pendings.add(p);
    }

    /** @deprecated Use {@link #isBlockedByShutdown} instead. */
    @Deprecated
    public static boolean ifBlockedByHudsonShutdown(Task task) {
        return isBlockedByShutdown(task);
    }

    /**
     * Checks whether a task should not be scheduled because {@link Jenkins#isQuietingDown()}.
     * @param task some queue task
     * @return true if {@link Jenkins#isQuietingDown()} unless this is a {@link NonBlockingTask}
     * @since 1.598
     */
    public static boolean isBlockedByShutdown(Task task) {
        return Jenkins.get().isQuietingDown() && !(task instanceof NonBlockingTask);
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Marks {@link Task}s that are not persisted.
     * @since 1.311
     */
    public interface TransientTask extends Task {}

    /**
     * Marks {@link Task}s that do not consume {@link Executor}.
     * @see OneOffExecutor
     * @since 1.318
     */
    public interface FlyweightTask extends Task {}

    /**
     * Marks {@link Task}s that are not affected by the {@linkplain Jenkins#isQuietingDown()}  quieting down},
     * because these tasks keep other tasks executing.
     * @see #isBlockedByShutdown
     * @since 1.336
     */
    public interface NonBlockingTask extends Task {}

    /**
     * Task whose execution is controlled by the queue.
     *
     * <p>
     * {@link #equals(Object) Value equality} of {@link Task}s is used
     * to collapse two tasks into one. This is used to avoid infinite
     * queue backlog.
     *
     * <p>
     * Pending {@link Task}s are persisted when Hudson shuts down, so
     * it needs to be persistable via XStream. To create a non-persisted
     * transient Task, extend {@link TransientTask} marker interface.
     *
     * <p>
     * Plugins are encouraged to implement {@link AccessControlled} otherwise
     * the tasks will be hidden from display in the queue.
     *
     * <p>
     * For historical reasons, {@link Task} object by itself
     * also represents the "primary" sub-task (and as implied by this
     * design, a {@link Task} must have at least one sub-task.)
     * Most of the time, the primary subtask is the only sub task.
     */
    public interface Task extends ModelObject, SubTask {
        /**
         * Returns true if the execution should be blocked
         * for temporary reasons.
         * @deprecated Use {@link #getCauseOfBlockage} != null
         */
        @Deprecated
        default boolean isBuildBlocked() {
            return getCauseOfBlockage() != null;
        }

        /**
         * @deprecated as of 1.330
         *      Use {@link CauseOfBlockage#getShortDescription()} instead.
         */
        @Deprecated
        default String getWhyBlocked() {
            CauseOfBlockage cause = getCauseOfBlockage();
            return cause != null ? cause.getShortDescription() : null;
        }

        /**
         * If the execution of this task should be blocked for temporary reasons,
         * this method returns a non-null object explaining why.
         *
         * <p>
         * Otherwise this method returns null, indicating that the build can proceed right away.
         *
         * <p>
         * This can be used to define mutual exclusion that goes beyond
         * {@link #getResourceList()}.
         * @return by default, null
         */
        @CheckForNull
        default CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        /**
         * Unique name of this task.
         *
         * <p>
         * This method is no longer used, left here for compatibility. Just return {@link #getDisplayName()}.
         */
        String getName();

        /**
         * @see hudson.model.Item#getFullDisplayName()
         */
        String getFullDisplayName();

        /**
         * Returns task-specific key which is used by the {@link LoadBalancer} to choose one particular executor
         * amongst all the free executors on all possibly suitable nodes.
         * NOTE: To be able to re-use the same node during the next run this key should not change from one run to
         * another. You probably want to compute that key based on the job's name.
         *
         * @return by default: {@link #getFullDisplayName()}
         * @see hudson.model.LoadBalancer
         */
        default String getAffinityKey() { return getFullDisplayName(); }

        /**
         * Checks the permission to see if the current user can abort this executable.
         * Returns normally from this method if it's OK.
         * <p>
         * NOTE: If you have implemented {@link AccessControlled} this defaults to
         * {@code checkPermission(hudson.model.Item.CANCEL);}
         *
         * @throws AccessDeniedException if the permission is not granted.
         */
        default void checkAbortPermission() {
            if (this instanceof AccessControlled) {
                ((AccessControlled) this).checkPermission(CANCEL);
            }
        }

        /**
         * Works just like {@link #checkAbortPermission()} except it indicates the status by a return value,
         * instead of exception.
         * Also used by default for {@link hudson.model.Queue.Item#hasCancelPermission}.
         * <p>
         * NOTE: If you have implemented {@link AccessControlled} this returns by default
         * {@code return hasPermission(hudson.model.Item.CANCEL);}
         *
         * @return false
         *      if the user doesn't have the permission.
         */
        default boolean hasAbortPermission() {
            if (this instanceof AccessControlled) {
                return ((AccessControlled) this).hasPermission(CANCEL);
            }
            return true;
        }

        /**
         * Returns the URL of this task relative to the context root of the application.
         *
         * <p>
         * When the user clicks an item in the queue, this is the page where the user is taken to.
         * Hudson expects the current instance to be bound to the URL returned by this method.
         *
         * @return
         *      URL that ends with '/'.
         */
        String getUrl();

        /**
         * True if the task allows concurrent builds, where the same {@link Task} is executed
         * by multiple executors concurrently on the same or different nodes.
         * @return by default, false
         * @since 1.338
         */
        default boolean isConcurrentBuild() {
            return false;
        }

        /**
         * Obtains the {@link SubTask}s that constitute this task.
         *
         * <p>
         * The collection returned by this method must also contain the primary {@link SubTask}
         * represented by this {@link Task} object itself as the first element.
         * The returned value is read-only.
         *
         * <p>
         * At least size 1.
         *
         * @return by default, {@code this}
         * @since 1.377
         */
        default Collection<? extends SubTask> getSubTasks() {
            return Set.of(this);
        }

        /**
         * This method allows the task to provide the default fallback authentication object to be used
         * when {@link QueueItemAuthenticator} fails to authenticate the build.
         *
         * <p>
         * When the task execution touches other objects inside Jenkins, the access control is performed
         * based on whether this {@link Authentication} is allowed to use them.
         *
         * @return by default, {@link ACL#SYSTEM2}
         * @since 2.266
         * @see QueueItemAuthenticator
         * @see Tasks#getDefaultAuthenticationOf(Queue.Task)
         */
        default @NonNull Authentication getDefaultAuthentication2() {
            if (Util.isOverridden(Queue.Task.class, getClass(), "getDefaultAuthentication")) {
                return getDefaultAuthentication().toSpring();
            } else {
                return ACL.SYSTEM2;
            }
        }

        /**
         * @deprecated use {@link #getDefaultAuthentication2()}
         * @since 1.520
         */
        @Deprecated
        default @NonNull org.acegisecurity.Authentication getDefaultAuthentication() {
            return org.acegisecurity.Authentication.fromSpring(getDefaultAuthentication2());
        }

        /**
         * This method allows the task to provide the default fallback authentication object to be used
         * when {@link QueueItemAuthenticator} fails to authenticate the build.
         *
         * <p>
         * When the task execution touches other objects inside Jenkins, the access control is performed
         * based on whether this {@link Authentication} is allowed to use them.
         *
         * <p>
         * This method was added to an interface after it was created, so plugins built against
         * older versions of Jenkins may not have this method implemented. Called private method _getDefaultAuthenticationOf(Task) on {@link Tasks}
         * to avoid {@link AbstractMethodError}.
         *
         * @since 2.266
         * @see QueueItemAuthenticator
         * @see Tasks#getDefaultAuthenticationOf(Queue.Task, Queue.Item)
         */
        default @NonNull Authentication getDefaultAuthentication2(Queue.Item item) {
            if (Util.isOverridden(Queue.Task.class, getClass(), "getDefaultAuthentication", Queue.Item.class)) {
                return getDefaultAuthentication(item).toSpring();
            } else {
                return getDefaultAuthentication2();
            }
        }

        /**
         * @deprecated use {@link #getDefaultAuthentication2(Queue.Item)}
         * @since 1.592
         */
        @Deprecated
        default @NonNull org.acegisecurity.Authentication getDefaultAuthentication(Queue.Item item) {
            return org.acegisecurity.Authentication.fromSpring(getDefaultAuthentication2(item));
        }

    }

    /**
     * Represents the real meat of the computation run by {@link Executor}.
     *
     * <h2>Views</h2>
     * <p>
     * Implementation must have {@code executorCell.jelly}, which is
     * used to render the HTML that indicates this executable is executing.
     */
    @StaplerAccessibleType
    public interface Executable extends Runnable, WithConsoleUrl {
        /**
         * Task from which this executable was created.
         *
         * <p>
         * Since this method went through a signature change in 1.377, the invocation may results in
         * {@link AbstractMethodError}.
         * Use {@link Executables#getParentOf(Queue.Executable)} that avoids this.
         */
        @NonNull SubTask getParent();

        /**
         * An umbrella executable (such as a {@link Run}) of which this is one part.
         * Some invariants:
         * <ul>
         * <li>{@code getParent().getOwnerTask() == getParent() || getParentExecutable().getParent() == getParent().getOwnerTask()}
         * <li>{@code getParent().getOwnerExecutable() == null || getParentExecutable() == getParent().getOwnerExecutable()}
         * </ul>
         * @return a <em>distinct</em> executable (never {@code this}, unlike the default of {@link SubTask#getOwnerTask}!); or null if this executable was already at top level
         * @since 2.313, but implementations can already implement this with a lower core dependency.
         * @see SubTask#getOwnerExecutable
         */
        default @CheckForNull Executable getParentExecutable() {
            return null;
        }

        /**
         * Called by {@link Executor} to perform the task.
         * @throws AsynchronousExecution if you would like to continue without consuming a thread
         */
        @Override void run() throws AsynchronousExecution;

        /**
         * Estimate of how long will it take to execute this executable.
         * Measured in milliseconds.
         *
         * @return -1 if it's impossible to estimate; default, {@link SubTask#getEstimatedDuration}
         * @since 1.383
         */
        default long getEstimatedDuration() {
            return Executables.getParentOf(this).getEstimatedDuration();
        }

        /**
         * Handles cases such as {@code PlaceholderExecutable} for Pipeline node steps.
         * @return by default, that of {@link #getParentExecutable} if defined
         */
        @Override
        default String getConsoleUrl() {
            Executable parent = getParentExecutable();
            return parent != null ? parent.getConsoleUrl() : null;
        }

        /**
         * Used to render the HTML. Should be a human readable text of what this executable is.
         */
        @Override String toString();
    }

    /**
     * Item in a queue.
     */
    @ExportedBean(defaultVisibility = 999)
    public abstract static class Item extends Actionable implements QueueItem {

        private final long id;

        /**
         * Unique ID (per master) that tracks the {@link Task} as it moves through different stages
         * in the queue (each represented by different subtypes of {@link Item} and into any subsequent
         * {@link Run} instance (see {@link Run#getQueueId()}).
         * @since 1.601
         */
        @Exported
        @Override
        public long getId() {
            return id;
        }

        @Deprecated
        public int getIdLegacy() {
            if (id > Integer.MAX_VALUE) {
                throw new IllegalStateException("Sorry, you need to update any Plugins attempting to " +
                        "assign 'Queue.Item.id' to an int value. 'Queue.Item.id' is now a long value and " +
                        "has incremented to a value greater than Integer.MAX_VALUE (2^31 - 1).");
            }
            return (int) id;
        }


        /**
         * Project to be built.
         */
        @Exported
        @NonNull
        public final Task task;

        private /*almost final*/ transient FutureImpl future;

        private final long inQueueSince;

        @Override
        @NonNull
        public Task getTask() {
            return task;
        }

        /**
         * Build is blocked because another build is in progress,
         * required {@link Resource}s are not available, or otherwise blocked
         * by {@link Task#isBuildBlocked()}.
         */
        @Exported
        public boolean isBlocked() { return this instanceof BlockedItem; }

        /**
         * Build is waiting the executor to become available.
         * This flag is only used in {@link Queue#getItems()} for
         * 'pseudo' items that are actually not really in the queue.
         */
        @Exported
        public boolean isBuildable() { return this instanceof BuildableItem; }

        /**
         * True if the item is starving for an executor for too long.
         */
        @Exported
        @Override
        public boolean isStuck() { return false; }

        /**
         * Since when is this item in the queue.
         * @return Unix timestamp
         */
        @Exported
        public long getInQueueSince() {
            return this.inQueueSince;
        }

        /**
         * Returns a human readable presentation of how long this item is already in the queue.
         * E.g. something like '3 minutes 40 seconds'
         */
        @Override
        public String getInQueueForString() {
            long duration = System.currentTimeMillis() - this.inQueueSince;
            return Util.getTimeSpanString(duration);
        }

        /**
         * Can be used to wait for the completion (either normal, abnormal, or cancellation) of the {@link Task}.
         * <p>
         * Just like {@link #getId()}, the same object tracks various stages of the queue.
         */
        @WithBridgeMethods(Future.class)
        public QueueTaskFuture<Executable> getFuture() { return future; }

        /**
         * If this task needs to be run on a node with a particular label,
         * return that {@link Label}. Otherwise null, indicating
         * it can run on anywhere.
         *
         * <p>
         * This code takes {@link LabelAssignmentAction} into account, then fall back to {@link SubTask#getAssignedLabel()}
         */
        @CheckForNull
        public Label getAssignedLabel() {
            for (LabelAssignmentAction laa : getActions(LabelAssignmentAction.class)) {
                Label l = laa.getAssignedLabel(task);
                if (l != null)    return l;
            }
            return task.getAssignedLabel();
        }

        /**
         * Test if the specified {@link SubTask} needs to be run on a node with a particular label.
         * <p>
         * This method takes {@link LabelAssignmentAction} into account, the first
         * non-null assignment will be returned.
         * Otherwise falls back to {@link SubTask#getAssignedLabel()}
         * @param st {@link SubTask} to be checked.
         * @return Required {@link Label}. Otherwise null, indicating it can run on anywhere.

         */
        public @CheckForNull Label getAssignedLabelFor(@NonNull SubTask st) {
            for (LabelAssignmentAction laa : getActions(LabelAssignmentAction.class)) {
                Label l = laa.getAssignedLabel(st);
                if (l != null)    return l;
            }
            return st.getAssignedLabel();
        }

        /**
         * Convenience method that returns a read only view of the {@link Cause}s associated with this item in the queue.
         *
         * @return can be empty but never null
         * @since 1.343
         */
        public final List<Cause> getCauses() {
            CauseAction ca = getAction(CauseAction.class);
            if (ca != null)
                return Collections.unmodifiableList(ca.getCauses());
            return Collections.emptyList();
        }

        @Restricted(DoNotUse.class) // used from Jelly
        @Override
        public String getCausesDescription() {
            List<Cause> causes = getCauses();
            StringBuilder s = new StringBuilder();
            for (Cause c : causes) {
                s.append(c.getShortDescription()).append('\n');
            }
            return s.toString();
        }

        protected Item(@NonNull Task task, @NonNull List<Action> actions, long id, FutureImpl future) {
            this(task, actions, id, future, System.currentTimeMillis());
        }

        protected Item(@NonNull Task task, @NonNull List<Action> actions, long id, FutureImpl future, long inQueueSince) {
            this.task = task;
            this.id = id;
            this.future = future;
            this.inQueueSince = inQueueSince;
            for (Action action : actions) addAction(action);
        }

        @SuppressWarnings("deprecation") // JENKINS-51584
        protected Item(Item item) {
            // do not use item.getAllActions() here as this will persist actions from a TransientActionFactory
            this(item.task, new ArrayList<>(item.getActions()), item.id, item.future, item.inQueueSince);
        }

        /**
         * Returns the URL of this {@link Item} relative to the context path of Jenkins
         *
         * @return
         *      URL that ends with '/'.
         * @since 1.519
         */
        @Exported
        public String getUrl() {
            return "queue/item/" + id + '/';
        }

        /**
         * Gets a human-readable status message describing why it's in the queue.
         */
        @Exported
        @Override
        public final String getWhy() {
            CauseOfBlockage cob = getCauseOfBlockage();
            return cob != null ? cob.getShortDescription() : null;
        }

        /**
         * Gets an object that describes why this item is in the queue.
         */
        public abstract CauseOfBlockage getCauseOfBlockage();

        /**
         * Gets a human-readable message about the parameters of this item
         * @return String
         */
        @Exported
        @Override
        public String getParams() {
            StringBuilder s = new StringBuilder();
            for (ParametersAction pa : getActions(ParametersAction.class)) {
                for (ParameterValue p : pa.getParameters()) {
                    s.append('\n').append(p.getShortDescription());
                }
            }
            return s.toString();
        }

        @Override
        public String getSearchUrl() {
            return null;
        }

        /** @deprecated Use {@link #doCancelItem} instead. */
        @Deprecated
        @RequirePOST
        public HttpResponse doCancelQueue() {
            if (hasCancelPermission()) {
                Jenkins.get().getQueue().cancel(this);
            }
            return HttpResponses.status(HttpServletResponse.SC_NO_CONTENT);
        }

        /**
         * Returns the identity that this task carries when it runs, for the purpose of access control.
         *
         * When the task execution touches other objects inside Jenkins, the access control is performed
         * based on whether this {@link Authentication} is allowed to use them. Implementers, if you are unsure,
         * return the identity of the user who queued the task, or {@link ACL#SYSTEM2} to bypass the access control
         * and run as the super user.
         *
         * @since 2.266
         */
        @NonNull
        public Authentication authenticate2() {
            for (QueueItemAuthenticator auth : QueueItemAuthenticatorProvider.authenticators()) {
                Authentication a = auth.authenticate2(this);
                if (a != null)
                    return a;
            }
            return task.getDefaultAuthentication2(this);
        }

        /**
         * @deprecated use {@link #authenticate2}
         * @since 1.520
         */
        @Deprecated
        public org.acegisecurity.Authentication authenticate() {
            return org.acegisecurity.Authentication.fromSpring(authenticate2());
        }

        @Restricted(DoNotUse.class) // only for Stapler export
        public Api getApi() throws AccessDeniedException {
            if (task instanceof AccessControlled) {
                AccessControlled ac = (AccessControlled) task;
                if (!ac.hasPermission(hudson.model.Item.DISCOVER)) {
                    return null; // same as getItem(long) returning null (details are printed only in case of -Dstapler.trace=true)
                } else if (!ac.hasPermission(hudson.model.Item.READ)) {
                    throw new AccessDeniedException("Please log in to access " + task.getUrl()); // like Jenkins.getItem
                } else { // have READ
                    return new Api(this);
                }
            } else { // err on the safe side
                return null;
            }
        }

        public HttpResponse doIndex(StaplerRequest2 req) {
            return HttpResponses.text("Queue item exists. For details check, for example, " + req.getRequestURI() + "api/json?tree=cancelled,executable[url]");
        }

        protected Object readResolve() {
            this.future = new FutureImpl(task);
            return this;
        }

        @Override
        public String toString() {
            return getClass().getName() + ':' + task + ':' + id;
        }

        /**
         * Enters the appropriate queue for this type of item.
         */
        /*package*/ abstract void enter(Queue q);

        /**
         * Leaves the appropriate queue for this type of item.
         */
        /*package*/ abstract boolean leave(Queue q);

        /**
         * Cancels this item, which updates {@link #future} to notify the listener, and
         * also leaves the queue.
         *
         * @return true
         *      if the item was successfully cancelled.
         */
        /*package*/ boolean cancel(Queue q) {
            boolean r = leave(q);
            if (r) {
                future.setAsCancelled();
                LeftItem li = new LeftItem(this);
                li.enter(q);
            }
            return r;
        }

    }

    /**
     * A Stub class for {@link Task} which exposes only the name of the Task to be displayed when the user
     * has DISCOVERY permissions only.
     */
    @Restricted(NoExternalUse.class)
    @ExportedBean(defaultVisibility = 999)
    public static class StubTask {

        private final String name;

        public StubTask(@NonNull Queue.Task base) {
            this.name = base.getName();
        }

        @Exported
        public String getName() {
            return name;
        }
    }

    /**
     * A Stub class for {@link Item} which exposes only the name of the Task to be displayed when the user
     * has DISCOVERY permissions only.
     */
    @Restricted(NoExternalUse.class)
    @ExportedBean(defaultVisibility = 999)
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
    public static class StubItem {

        @Exported public StubTask task;

        public StubItem(StubTask task) {
            this.task = task;
        }

    }

    /**
     * An optional interface for actions on Queue.Item.
     * Lets the action cooperate in queue management.
     *
     * @since 1.300-ish.
     */
    public interface QueueAction extends Action {
        /**
         * Returns whether the new item should be scheduled.
         * An action should return true if the associated task is 'different enough' to warrant a separate execution.
         */
        boolean shouldSchedule(List<Action> actions);
    }

    /**
     * Extension point for deciding if particular job should be scheduled or not.
     *
     * <p>
     * This handler is consulted every time someone tries to submit a task to the queue.
     * If any of the registered handlers returns false, the task will not be added
     * to the queue, and the task will never get executed.
     *
     * <p>
     * The other use case is to add additional {@link Action}s to the task
     * (for example {@link LabelAssignmentAction}) to tasks that are submitted to the queue.
     *
     * @since 1.316
     */
    public abstract static class QueueDecisionHandler implements ExtensionPoint {
        /**
         * Returns whether the new item should be scheduled.
         *
         * @param actions
         *      List of actions that are to be made available as {@link AbstractBuild#getActions()}
         *      upon the start of the build. This list is live, and can be mutated.
         */
        public abstract boolean shouldSchedule(Task p, List<Action> actions);

        /**
         * All registered {@link QueueDecisionHandler}s
         */
        public static ExtensionList<QueueDecisionHandler> all() {
            return ExtensionList.lookup(QueueDecisionHandler.class);
        }
    }

    /**
     * {@link Item} in the {@link Queue#waitingList} stage.
     */
    public static final class WaitingItem extends Item implements Comparable<WaitingItem> {
        /**
         * This item can be run after this time.
         */
        @Exported
        public Calendar timestamp;

        public WaitingItem(Calendar timestamp, Task project, List<Action> actions) {
            super(project, actions, QueueIdStrategy.get().generateIdFor(project, actions), new FutureImpl(project));
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(WaitingItem that) {
            int r = this.timestamp.getTime().compareTo(that.timestamp.getTime());
            if (r != 0) return r;

            return Long.compare(this.getId(), that.getId());
        }

        @Override
        public CauseOfBlockage getCauseOfBlockage() {
            long diff = timestamp.getTimeInMillis() - System.currentTimeMillis();
            if (diff >= 0)
                return CauseOfBlockage.fromMessage(Messages._Queue_InQuietPeriod(Util.getTimeSpanString(diff)));
            else
                return CauseOfBlockage.fromMessage(Messages._Queue_FinishedWaiting());
        }

        @Override
        /*package*/ void enter(Queue q) {
            if (q.waitingList.add(this)) {
                Listeners.notify(QueueListener.class, true, l -> l.onEnterWaiting(this));
            }
        }

        @Override
        /*package*/ boolean leave(Queue q) {
            boolean r = q.waitingList.remove(this);
            if (r) {
                Listeners.notify(QueueListener.class, true, l -> l.onLeaveWaiting(this));
            }
            return r;
        }


    }

    /**
     * Common part between {@link BlockedItem} and {@link BuildableItem}.
     */
    public abstract static class NotWaitingItem extends Item {
        /**
         * When did this job exit the {@link Queue#waitingList} phase?
         */
        @Exported
        public final long buildableStartMilliseconds;

        protected NotWaitingItem(WaitingItem wi) {
            super(wi);
            buildableStartMilliseconds = System.currentTimeMillis();
        }

        protected NotWaitingItem(NotWaitingItem ni) {
            super(ni);
            buildableStartMilliseconds = ni.buildableStartMilliseconds;
        }
    }

    /**
     * {@link Item} in the {@link Queue#blockedProjects} stage.
     */
    public final class BlockedItem extends NotWaitingItem {
        private transient CauseOfBlockage causeOfBlockage = null;

        public BlockedItem(WaitingItem wi) {
            this(wi, null);
        }

        public BlockedItem(NotWaitingItem ni) {
            this(ni, null);
        }

        BlockedItem(WaitingItem wi, CauseOfBlockage causeOfBlockage) {
            super(wi);
            this.causeOfBlockage = causeOfBlockage;
        }

        BlockedItem(NotWaitingItem ni, CauseOfBlockage causeOfBlockage) {
            super(ni);
            this.causeOfBlockage = causeOfBlockage;
        }

        void setCauseOfBlockage(CauseOfBlockage causeOfBlockage) {
            this.causeOfBlockage = causeOfBlockage;
        }

        @Restricted(NoExternalUse.class)
        public boolean isCauseOfBlockageNull() {
            if (causeOfBlockage == null) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public CauseOfBlockage getCauseOfBlockage() {
            if (causeOfBlockage != null) {
                return causeOfBlockage;
            }

            // fallback for backward compatibility
            return getCauseOfBlockageForItem(this);
        }

                    @Override
        /*package*/ void enter(Queue q) {
            LOGGER.log(Level.FINE, "{0} is blocked", this);
            blockedProjects.add(this);
            Listeners.notify(QueueListener.class, true, l -> l.onEnterBlocked(this));
        }

                    @Override
        /*package*/ boolean leave(Queue q) {
            boolean r = blockedProjects.remove(this);
            if (r) {
                LOGGER.log(Level.FINE, "{0} no longer blocked", this);
                Listeners.notify(QueueListener.class, true, l -> l.onLeaveBlocked(this));
            }
            return r;
        }
    }

    /**
     * {@link Item} in the {@link Queue#buildables} stage.
     */
    public static final class BuildableItem extends NotWaitingItem {
        /**
         * Set to true when this is added to the {@link Queue#pendings} list.
         */
        private boolean isPending;

        /**
         * Reasons why the last call to {@link #maintain} left this buildable (but not blocked or executing).
         * May be null but not empty.
         */
        private transient volatile @CheckForNull List<CauseOfBlockage> transientCausesOfBlockage;

        public BuildableItem(WaitingItem wi) {
            super(wi);
        }

        public BuildableItem(NotWaitingItem ni) {
            super(ni);
        }

        @Override
        public CauseOfBlockage getCauseOfBlockage() {
            Jenkins jenkins = Jenkins.get();
            if (isBlockedByShutdown(task))
                return CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown());

            List<CauseOfBlockage> causesOfBlockage = transientCausesOfBlockage;

            Label label = getAssignedLabel();
            List<Node> allNodes = jenkins.getNodes();
            if (allNodes.isEmpty())
                label = null;    // no master/agent. pointless to talk about nodes

            if (label != null) {
                Set<Node> nodes = label.getNodes();
                if (label.isOffline()) {
                    if (nodes.size() != 1)      return new BecauseLabelIsOffline(label);
                    else                        return new BecauseNodeIsOffline(nodes.iterator().next());
                } else {
                    if (causesOfBlockage != null && label.getIdleExecutors() > 0) {
                        return new CompositeCauseOfBlockage(causesOfBlockage);
                    }
                    if (nodes.size() != 1)      return new BecauseLabelIsBusy(label);
                    else                        return new BecauseNodeIsBusy(nodes.iterator().next());
                }
            } else if (causesOfBlockage != null && new ComputerSet().getIdleExecutors() > 0) {
                return new CompositeCauseOfBlockage(causesOfBlockage);
            } else {
                return CauseOfBlockage.createNeedsMoreExecutor(Messages._Queue_WaitingForNextAvailableExecutor());
            }
        }

        @Override
        public boolean isStuck() {
            Label label = getAssignedLabel();
            if (label != null && label.isOffline())
                // no executor online to process this job. definitely stuck.
                return true;

            long d = task.getEstimatedDuration();
            long elapsed = System.currentTimeMillis() - buildableStartMilliseconds;
            if (d >= 0) {
                // if we were running elsewhere, we would have done this build ten times.
                return elapsed > Math.max(d, 60000L) * 10;
            } else {
                // more than a day in the queue
                return TimeUnit.MILLISECONDS.toHours(elapsed) > 24;
            }
        }

        @Exported
        public boolean isPending() {
            return isPending;
        }

        @Override
        /*package*/ void enter(Queue q) {
            q.buildables.add(this);
            Listeners.notify(QueueListener.class, true, l -> l.onEnterBuildable(this));
        }

        @Override
        /*package*/ boolean leave(Queue q) {
            boolean r = q.buildables.remove(this);
            if (r) {
                LOGGER.log(Level.FINE, "{0} no longer blocked", this);
                Listeners.notify(QueueListener.class, true, l -> l.onLeaveBuildable(this));
            }
            return r;
        }
    }

    /**
     * {@link Item} in the {@link Queue#leftItems} stage. These are items that had left the queue
     * by either began executing or by getting cancelled.
     *
     * @since 1.519
     */
    public static final class LeftItem extends Item {
        public final WorkUnitContext outcome;

        /**
         * When item has left the queue and begin executing.
         */
        public LeftItem(WorkUnitContext wuc) {
            super(wuc.item);
            this.outcome = wuc;
        }

        /**
         * When item is cancelled.
         */
        public LeftItem(Item cancelled) {
            super(cancelled);
            this.outcome = null;
        }

        @Override
        public CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        /**
         * If this is representing an item that started executing, this property returns
         * the primary executable (such as {@link AbstractBuild}) that created out of it.
         */
        @Exported
        public @CheckForNull Executable getExecutable() {
            return outcome != null ? outcome.getPrimaryWorkUnit().getExecutable() : null;
        }

        /**
         * Is this representing a cancelled item?
         */
        @Exported
        public boolean isCancelled() {
            return outcome == null;
        }

        @Override
        void enter(Queue q) {
            q.leftItems.put(getId(), this);
            Listeners.notify(QueueListener.class, true, l -> l.onLeft(this));
        }

        @Override
        boolean leave(Queue q) {
            // there's no leave operation
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Queue.class.getName());

    /**
     * This {@link XStream} instance is used to persist {@link Task}s.
     */
    public static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.registerConverter(new AbstractSingleValueConverter() {

            @Override
            public boolean canConvert(Class klazz) {
                return hudson.model.Item.class.isAssignableFrom(klazz);
            }

            @Override
            public Object fromString(String string) {
                Object item = Jenkins.get().getItemByFullName(string);
                if (item == null)  throw new NoSuchElementException("No such job exists: " + string);
                return item;
            }

            @Override
            public String toString(Object item) {
                return ((hudson.model.Item) item).getFullName();
            }
        });
        XSTREAM.registerConverter(new AbstractSingleValueConverter() {

            @Override
            public boolean canConvert(Class klazz) {
                return Run.class.isAssignableFrom(klazz);
            }

            @Override
            public Object fromString(String string) {
                String[] split = string.split("#");
                String projectName = split[0];
                int buildNumber = Integer.parseInt(split[1]);
                Job<?, ?> job = (Job<?, ?>) Jenkins.get().getItemByFullName(projectName);
                if (job == null)  throw new NoSuchElementException("No such job exists: " + projectName);
                Run<?, ?> run = job.getBuildByNumber(buildNumber);
                if (run == null)  throw new NoSuchElementException("No such build: " + string);
                return run;
            }

            @Override
            public String toString(Object object) {
                Run<?, ?> run = (Run<?, ?>) object;
                return run.getParent().getFullName() + "#" + run.getNumber();
            }
        });

        /*
         * Reconnect every reference to Queue by the singleton.
         */
        XSTREAM.registerConverter(new AbstractSingleValueConverter() {
            @Override
            public boolean canConvert(Class klazz) {
                return Queue.class.isAssignableFrom(klazz);
            }

            @Override
            public Object fromString(String string) {
                return Jenkins.get().getQueue();
            }

            @Override
            public String toString(Object item) {
                return "queue";
            }
        });
    }

    /**
     * Regularly invokes {@link Queue#maintain()} and clean itself up when
     * {@link Queue} gets GC-ed.
     */
    private static class MaintainTask extends SafeTimerTask {
        private final WeakReference<Queue> queue;

        MaintainTask(Queue queue) {
            this.queue = new WeakReference<>(queue);
        }

        private void periodic() {
            long interval = 5000;
            Timer.get().scheduleWithFixedDelay(this, interval, interval, TimeUnit.MILLISECONDS);
        }

        @Override
        protected void doRun() {
            Queue q = queue.get();
            if (q != null)
                q.maintain();
            else
                cancel();
        }
    }

    /**
     * {@link ArrayList} of {@link Item} with more convenience methods.
     */
    private class ItemList<T extends Item> extends ArrayList<T> {
        public T get(Task task) {
            for (T item : this) {
                if (item.task.equals(task)) {
                    return item;
                }
            }
            return null;
        }

        public List<T> getAll(Task task) {
            List<T> result = new ArrayList<>();
            for (T item : this) {
                if (item.task.equals(task)) {
                    result.add(item);
                }
            }
            return result;
        }

        public boolean containsKey(Task task) {
            return get(task) != null;
        }

        public T remove(Task task) {
            Iterator<T> it = iterator();
            while (it.hasNext()) {
                T t = it.next();
                if (t.task.equals(task)) {
                    it.remove();
                    return t;
                }
            }
            return null;
        }

        public void put(Task task, T item) {
            assert item.task.equals(task);
            add(item);
        }

        public ItemList<T> values() {
            return this;
        }

        /**
         * Works like {@link #remove(Task)} but also marks the {@link Item} as cancelled.
         */
        public T cancel(Task p) {
            T x = get(p);
            if (x != null) x.cancel(Queue.this);
            return x;
        }

        @SuppressFBWarnings(value = "IA_AMBIGUOUS_INVOCATION_OF_INHERITED_OR_OUTER_METHOD",
                justification = "It will invoke the inherited clear() method according to Java semantics. "
                              + "FindBugs recommends suppressing warnings in such case")
        public void cancelAll() {
            for (T t : new ArrayList<>(this))
                t.cancel(Queue.this);
            clear();
        }
    }

    private static class Snapshot {
        private final Set<WaitingItem> waitingList;
        private final List<BlockedItem> blockedProjects;
        private final List<BuildableItem> buildables;
        private final List<BuildableItem> pendings;

        Snapshot(Set<WaitingItem> waitingList, List<BlockedItem> blockedProjects, List<BuildableItem> buildables,
                        List<BuildableItem> pendings) {
            this.waitingList = new LinkedHashSet<>(waitingList);
            this.blockedProjects = new ArrayList<>(blockedProjects);
            this.buildables = new ArrayList<>(buildables);
            this.pendings = new ArrayList<>(pendings);
        }

        @Override
        public String toString() {
            return "Queue.Snapshot{waitingList=" + waitingList + ";blockedProjects=" + blockedProjects + ";buildables=" + buildables + ";pendings=" + pendings + "}";
        }
    }

    private static class LockedRunnable implements Runnable  {
        private final Runnable delegate;

        private LockedRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            withLock(delegate);
        }
    }

    private class BuildableRunnable implements Runnable  {
        private final BuildableItem buildableItem;

        private BuildableRunnable(BuildableItem p) {
            this.buildableItem = p;
        }

        @Override
        public void run() {
            //the flyweighttask enters the buildables queue and will ask Jenkins to provision a node
            buildableItem.enter(Queue.this);
        }
    }

    private static class LockedJUCCallable<V> implements java.util.concurrent.Callable<V> {
        private final java.util.concurrent.Callable<V> delegate;

        private LockedJUCCallable(java.util.concurrent.Callable<V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public V call() throws Exception {
            return withLock(delegate);
        }
    }

    private static class LockedHRCallable<V, T extends Throwable> implements hudson.remoting.Callable<V, T> {
        private static final long serialVersionUID = 1L;
        private final hudson.remoting.Callable<V, T> delegate;

        private LockedHRCallable(hudson.remoting.Callable<V, T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public V call() throws T {
            return withLock(delegate);
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            delegate.checkRoles(checker);
        }
    }

    @CLIResolver
    public static Queue getInstance() {
        return Jenkins.get().getQueue();
    }

    /**
     * Restores the queue content during the start up.
     */
    @Initializer(after = JOB_CONFIG_ADAPTED)
    public static void init(Jenkins h) {
        Queue queue = h.getQueue();
        Item[] items = queue.getItems();
        if (items.length > 0) {
            LOGGER.warning(() -> "Loading queue will discard previously scheduled items: " + Arrays.toString(items));
        }
        queue.load();
    }

    /**
     * Schedule {@link Queue#save()} call for near future once items change. Ignore all changes until the time the save
     * takes place.
     *
     * Once queue is restored after a crash, items stages might not be accurate until the next #maintain() - this is not
     * a problem as the items will be reshuffled first and then scheduled during the next maintainance cycle.
     *
     * Implementation note: Queue.load() calls QueueListener hooks for every item deserialized that can hammer the persistance
     * on load. The problem is avoided by delaying the actual save for the time long enough for queue to load so the save
     * operations will collapse into one. Also, items are persisted as buildable or blocked in vast majority of cases and
     * those stages does not trigger the save here.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static final class Saver extends QueueListener implements Runnable {

        /**
         * All negative values will disable periodic saving.
         */
        @VisibleForTesting
        /*package*/ static /*final*/ int DELAY_SECONDS = SystemProperties.getInteger("hudson.model.Queue.Saver.DELAY_SECONDS", 60);

        private final Object lock = new Object();
        @GuardedBy("lock")
        private Future<?> nextSave;

        @Override
        public void onEnterWaiting(WaitingItem wi) {
            push();
        }

        @Override
        public void onLeft(Queue.LeftItem li) {
            push();
        }

        private void push() {
            if (DELAY_SECONDS < 0) return;

            synchronized (lock) {
                // Can be done or canceled in case of a bug or external intervention - do not allow it to hang there forever
                if (nextSave != null && !(nextSave.isDone() || nextSave.isCancelled())) return;
                nextSave = Timer.get().schedule(this, DELAY_SECONDS, TimeUnit.SECONDS);
            }
        }

        @Override
        public void run() {
            try {
                Jenkins j = Jenkins.getInstanceOrNull();
                if (j != null) {
                    j.getQueue().save();
                }
            } finally {
                synchronized (lock) {
                    nextSave = null;
                }
            }
        }

        @VisibleForTesting @Restricted(NoExternalUse.class)
        /*package*/ @NonNull Future<?> getNextSave() {
            synchronized (lock) {
                return nextSave == null
                        ? Futures.precomputed(null)
                        : nextSave
                ;
            }
        }
    }
}
