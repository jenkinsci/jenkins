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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.BulkChange;
import hudson.CopyOnWrite;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.Initializer;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.util.Iterators.reverse;

import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.Executables;
import hudson.model.queue.QueueListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.ScheduleResult;
import hudson.model.queue.ScheduleResult.Created;
import hudson.model.queue.SubTask;
import hudson.model.queue.FutureImpl;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.QueueSorter;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.Tasks;
import hudson.model.queue.WorkUnit;
import hudson.model.Node.Mode;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.FoldableAction;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsBusy;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsBusy;
import hudson.model.queue.WorkUnitContext;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.security.QueueItemAuthenticatorProvider;
import jenkins.util.Timer;
import hudson.triggers.SafeTimerTask;
import hudson.util.TimeUnit2;
import hudson.util.XStream2;
import hudson.util.ConsistentHash;
import hudson.util.ConsistentHash.Hash;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticator;
import jenkins.util.AtmostOneTaskExecutor;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.jenkinsci.bytecode.AdaptField;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnegative;
import jenkins.model.queue.AsynchronousExecution;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

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
 * <pre>
 * (enter) --> waitingList --+--> blockedProjects
 *                           |        ^
 *                           |        |
 *                           |        v
 *                           +--> buildables ---> pending ---> left
 *                                    ^              |
 *                                    |              |
 *                                    +---(rarely)---+
 * </pre>
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
    private final Set<WaitingItem> waitingList = new TreeSet<WaitingItem>();

    /**
     * {@link Task}s that can be built immediately
     * but blocked because another build is in progress,
     * required {@link Resource}s are not available,
     * blocked via {@link QueueTaskDispatcher#canRun(Item)},
     * or otherwise blocked by {@link Task#isBuildBlocked()}.
     */
    private final ItemList<BlockedItem> blockedProjects = new ItemList<BlockedItem>();

    /**
     * {@link Task}s that can be built immediately
     * that are waiting for available {@link Executor}.
     * This list is sorted in such a way that earlier items are built earlier.
     */
    private final ItemList<BuildableItem> buildables = new ItemList<BuildableItem>();

    /**
     * {@link Task}s that are being handed over to the executor, but execution
     * has not started yet.
     */
    private final ItemList<BuildableItem> pendings = new ItemList<BuildableItem>();

    private transient volatile Snapshot snapshot = new Snapshot(waitingList, blockedProjects, buildables, pendings);

    /**
     * Items that left queue would stay here for a while to enable tracking via {@link Item#getId()}.
     *
     * This map is forgetful, since we can't remember everything that executed in the past.
     */
    private final Cache<Long,LeftItem> leftItems = CacheBuilder.newBuilder().expireAfterWrite(5*60, TimeUnit.SECONDS).build();

    /**
     * Data structure created for each idle {@link Executor}.
     * This is a job offer from the queue to an executor.
     *
     * <p>
     * For each idle executor, this gets created to allow the scheduling logic
     * to assign a work. Once a work is assigned, the executor actually gets
     * started to carry out the task in question.
     */
    public class JobOffer extends MappingWorksheet.ExecutorSlot {
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
         * Verifies that the {@link Executor} represented by this object is capable of executing the given task.
         */
        public boolean canTake(BuildableItem item) {
            Node node = getNode();
            if (node==null)     return false;   // this executor is about to die

            if(node.canTake(item)!=null)
                return false;   // this node is not able to take the task

            for (QueueTaskDispatcher d : QueueTaskDispatcher.all())
                if (d.canTake(node,item)!=null)
                    return false;

            return isAvailable();
        }

        /**
         * Is this executor ready to accept some tasks?
         */
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
            return String.format("JobOffer[%s #%d]",executor.getOwner().getName(), executor.getNumber());
        }
    }

    private volatile transient LoadBalancer loadBalancer;

    private volatile transient QueueSorter sorter;

    private transient final AtmostOneTaskExecutor<Void> maintainerThread = new AtmostOneTaskExecutor<Void>(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
            maintain();
            return null;
        }
    });

    private transient final ReentrantLock lock = new ReentrantLock();

    private transient final Condition condition = lock.newCondition();

    public Queue(@Nonnull LoadBalancer loadBalancer) {
        this.loadBalancer =  loadBalancer.sanitize();
        // if all the executors are busy doing something, then the queue won't be maintained in
        // timely fashion, so use another thread to make sure it happens.
        new MaintainTask(this).periodic();
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(@Nonnull LoadBalancer loadBalancer) {
        if(loadBalancer==null)  throw new IllegalArgumentException();
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
    static class State {
        public long counter;
        public List<Item> items = new ArrayList<Item>();
    }

    /**
     * Loads the queue contents that was {@link #save() saved}.
     */
    public void load() {
        lock.lock();
        try { try {
            // first try the old format
            File queueFile = getQueueFile();
            if (queueFile.exists()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(queueFile)));
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        AbstractProject j = Jenkins.getInstance().getItemByFullName(line, AbstractProject.class);
                        if (j != null)
                            j.scheduleBuild();
                    }
                } finally {
                    in.close();
                }
                // discard the queue file now that we are done
                queueFile.delete();
            } else {
                queueFile = getXMLQueueFile();
                if (queueFile.exists()) {
                    Object unmarshaledObj = new XmlFile(XSTREAM, queueFile).read();
                    List items;

                    if (unmarshaledObj instanceof State) {
                        State state = (State) unmarshaledObj;
                        items = state.items;
                        WaitingItem.COUNTER.set(state.counter);
                    } else {
                        // backward compatibility - it's an old List queue.xml
                        items = (List) unmarshaledObj;
                        long maxId = 0;
                        for (Object o : items) {
                            if (o instanceof Item) {
                                maxId = Math.max(maxId, ((Item)o).id);
                            }
                        }
                        WaitingItem.COUNTER.set(maxId);
                    }

                    for (Object o : items) {
                        if (o instanceof Task) {
                            // backward compatibility
                            schedule((Task)o, 0);
                        } else if (o instanceof Item) {
                            Item item = (Item)o;

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
                    bk.delete();
                    queueFile.renameTo(bk);
                    queueFile.delete();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load the queue file " + getXMLQueueFile(), e);
        } finally { updateSnapshot(); } } finally {
            lock.unlock();
        }
    }

    /**
     * Persists the queue contents to the disk.
     */
    public void save() {
        if(BulkChange.contains(this))  return;

        XmlFile queueFile = new XmlFile(XSTREAM, getXMLQueueFile());
        lock.lock();
        try {
            // write out the queue state we want to save
            State state = new State();
            state.counter = WaitingItem.COUNTER.longValue();

            // write out the tasks on the queue
            for (Item item: getItems()) {
                if(item.task instanceof TransientTask)  continue;
                state.items.add(item);
            }

            try {
                queueFile.write(state);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to write out the queue file " + getXMLQueueFile(), e);
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
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        lock.lock();
        try { try {
            for (WaitingItem i : new ArrayList<WaitingItem>(
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

    private File getQueueFile() {
        return new File(Jenkins.getInstance().getRootDir(), "queue.txt");
    }

    /*package*/ File getXMLQueueFile() {
        return new File(Jenkins.getInstance().getRootDir(), "queue.xml");
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(AbstractProject)}
     */
    @Deprecated
    public boolean add(AbstractProject p) {
        return schedule(p)!=null;
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
        return schedule(p, quietPeriod)!=null;
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
     *      added to the {@link Run} object, and hence avaialable to everyone.
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
    public @Nonnull ScheduleResult schedule2(Task p, int quietPeriod, List<Action> actions) {
        // remove nulls
        actions = new ArrayList<Action>(actions);
        for (Iterator<Action> itr = actions.iterator(); itr.hasNext();) {
            Action a =  itr.next();
            if (a==null)    itr.remove();
        }

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
    private @Nonnull ScheduleResult scheduleInternal(Task p, int quietPeriod, List<Action> actions) {
        lock.lock();
        try { try {
            Calendar due = new GregorianCalendar();
            due.add(Calendar.SECOND, quietPeriod);

            // Do we already have this task in the queue? Because if so, we won't schedule a new one.
            List<Item> duplicatesInQueue = new ArrayList<Item>();
            for (Item item : liveGetItems(p)) {
                boolean shouldScheduleItem = false;
                for (QueueAction action : item.getActions(QueueAction.class)) {
                    shouldScheduleItem |= action.shouldSchedule(actions);
                }
                for (QueueAction action : Util.filter(actions, QueueAction.class)) {
                    shouldScheduleItem |= action.shouldSchedule((new ArrayList<Action>(item.getAllActions())));
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
    	return schedule(p, quietPeriod)!=null;
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
    	return schedule(p, quietPeriod, actions)!=null;
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
    public @Nonnull ScheduleResult schedule2(Task p, int quietPeriod, Action... actions) {
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
        snapshot = new Snapshot(waitingList, blockedProjects, buildables, pendings);
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
     * Called from {@code queue.jelly} and {@code entries.jelly}.
     */
    @RequirePOST
    public HttpResponse doCancelItem(@QueryParameter long id) throws IOException, ServletException {
        Item item = getItem(id);
        if (item != null) {
            cancel(item);
        } // else too late, ignore (JENKINS-14813)
        return HttpResponses.forwardToPreviousPage();
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
    @Exported(inline=true)
    public Item[] getItems() {
        Snapshot s = this.snapshot;
        List<Item> r = new ArrayList<Item>();

        for(WaitingItem p : s.waitingList) {
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BlockedItem p : s.blockedProjects){
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BuildableItem p : reverse(s.buildables)) {
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BuildableItem p : reverse(s.pendings)) {
            r= checkPermissionsAndAddToList(r, p);
        }
        Item[] items = new Item[r.size()];
        r.toArray(items);
        return items;
    }

    private List<Item> checkPermissionsAndAddToList(List<Item> r, Item t) {
        if (t.task instanceof hudson.security.AccessControlled) {
            if (((hudson.security.AccessControlled)t.task).hasPermission(hudson.model.Item.READ)
                    || ((hudson.security.AccessControlled) t.task).hasPermission(hudson.security.Permission.READ)) {
                r.add(t);
            }
        }
        return r;
    }

    /**
     * Returns an array of Item for which it is only visible the name of the task.
     *
     * Generally speaking the array is sorted such that the items that are most likely built sooner are
     * at the end.
     */
    @Restricted(NoExternalUse.class)
    @Exported(inline=true)
    public StubItem[] getDiscoverableItems() {
        Snapshot s = this.snapshot;
        List<StubItem> r = new ArrayList<StubItem>();

        for(WaitingItem p : s.waitingList) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BlockedItem p : s.blockedProjects){
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BuildableItem p : reverse(s.buildables)) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BuildableItem p : reverse(s.pendings)) {
            r= filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        StubItem[] items = new StubItem[r.size()];
        r.toArray(items);
        return items;
    }

    private List<StubItem> filterDiscoverableItemListBasedOnPermissions(List<StubItem> r, Item t) {
        if (t.task instanceof hudson.model.Item) {
            if (!((hudson.model.Item)t.task).hasPermission(hudson.model.Item.READ) && ((hudson.model.Item)t.task).hasPermission(hudson.model.Item.DISCOVER)) {
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
        List<BuildableItem> result = new ArrayList<BuildableItem>();
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
        ArrayList<BuildableItem> r = new ArrayList<BuildableItem>(snapshot.buildables);
        r.addAll(snapshot.pendings);
        return r;
    }

    /**
     * Gets the snapshot of all {@link BuildableItem}s.
     */
    public List<BuildableItem> getPendingItems() {
        return new ArrayList<BuildableItem>(snapshot.pendings);
    }

    /**
     * Gets the snapshot of all {@link BlockedItem}s.
     */
    protected List<BlockedItem> getBlockedItems() {
        return new ArrayList<BlockedItem>(snapshot.blockedProjects);
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
        List<Item> queuedNotBlocked = new ArrayList<Item>();
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
        Set<Task> unblockedTasks = new HashSet<Task>(items.size());
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
    public @Nonnegative int countBuildableItemsFor(@CheckForNull Label l) {
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
     * <p/>
     * The implementation is quite similar to {@link #countBuildableItemsFor(hudson.model.Label)},
     * but it has another behavior for null parameters.
     * @param l Label to be checked. If null, only jobs without assigned labels
     *      will be taken into the account.
     * @return Number of {@link BuildableItem}s for the specified label.
     * @since 1.615
     */
    public @Nonnegative int strictCountBuildableItemsFor(@CheckForNull Label l) {
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
            List<Item> result = new ArrayList<Item>();
            result.addAll(blockedProjects.getAll(t));
            result.addAll(buildables.getAll(t));
            result.addAll(pendings.getAll(t));
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
     * @return null if the project is not in the queue.
     */
    public List<Item> getItems(Task t) {
        Snapshot snapshot = this.snapshot;
        List<Item> result = new ArrayList<Item>();
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
        final Snapshot snapshot = this.snapshot;
        for (Item item : snapshot.blockedProjects) {
            if (item.task.equals(t))
                return true;
        }
        for (Item item : snapshot.buildables) {
            if (item.task.equals(t))
                return true;
        }
        for (Item item : snapshot.pendings) {
            if (item.task.equals(t))
                return true;
        }
        for (Item item : snapshot.waitingList) {
            if (item.task.equals(t)) {
                return true;
            }
        }
        return false;
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
     */
    private boolean isBuildBlocked(Item i) {
        if (i.task.isBuildBlocked() || !canRun(i.task.getResourceList()))
            return true;

        for (QueueTaskDispatcher d : QueueTaskDispatcher.all()) {
            if (d.canRun(i)!=null)
                return true;
        }

        return false;
    }

    /**
     * Make sure we don't queue two tasks of the same project to be built
     * unless that project allows concurrent builds.
     */
    private boolean allowNewBuildableTask(Task t) {
        try {
            if (t.isConcurrentBuild())
                return true;
        } catch (AbstractMethodError e) {
            // earlier versions don't have the "isConcurrentBuild" method, so fall back gracefully
        }
        return !buildables.containsKey(t) && !pendings.containsKey(t);
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
        return queue == null ? callable : new LockedJUCCallable<V>(callable);
    }

    @Override
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
        lock.lock();
        try { try {

            LOGGER.log(Level.FINE, "Queue maintenance started {0}", this);

            // The executors that are currently waiting for a job to run.
            Map<Executor, JobOffer> parked = new HashMap<Executor, JobOffer>();

            {// update parked (and identify any pending items whose executor has disappeared)
                List<BuildableItem> lostPendings = new ArrayList<BuildableItem>(pendings);
                for (Computer c : Jenkins.getInstance().getComputers()) {
                    for (Executor e : c.getExecutors()) {
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
                for (BuildableItem p: lostPendings) {
                    LOGGER.log(Level.INFO,
                            "BuildableItem {0}: pending -> buildable as the assigned executor disappeared",
                            p.task.getFullDisplayName());
                    p.isPending = false;
                    pendings.remove(p);
                    makeBuildable(p);
                }
            }

            final QueueSorter s = sorter;

            {// blocked -> buildable
                // copy as we'll mutate the list and we want to process in a potentially different order
                List<BlockedItem> blockedItems = new ArrayList<>(blockedProjects.values());
                // if facing a cycle of blocked tasks, ensure we process in the desired sort order
                if (s != null) {
                    s.sortBlockedItems(blockedItems);
                } else {
                    Collections.sort(blockedItems, QueueSorter.DEFAULT_BLOCKED_ITEM_COMPARATOR);
                }
                for (BlockedItem p : blockedItems) {
                    String taskDisplayName = p.task.getFullDisplayName();
                    LOGGER.log(Level.FINEST, "Current blocked item: {0}", taskDisplayName);
                    if (!isBuildBlocked(p) && allowNewBuildableTask(p.task)) {
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
                Task p = top.task;
                if (!isBuildBlocked(top) && allowNewBuildableTask(p)) {
                    // ready to be executed immediately
                    Runnable r = makeBuildable(new BuildableItem(top));
                    String topTaskDisplayName = top.task.getFullDisplayName();
                    if (r != null) {
                        LOGGER.log(Level.FINEST, "Executing runnable {0}", topTaskDisplayName);
                        r.run();
                    } else {
                        LOGGER.log(Level.FINEST, "Item {0} was unable to be made a buildable and is now a blocked item.", topTaskDisplayName);
                        new BlockedItem(top).enter(this);
                    }
                } else {
                    // this can't be built now because another build is in progress
                    // set this project aside.
                    new BlockedItem(top).enter(this);
                }
            }

            if (s != null)
                s.sortBuildableItems(buildables);
            
            // Ensure that identification of blocked tasks is using the live state: JENKINS-27708 & JENKINS-27871
            updateSnapshot();
            
            // allocate buildable jobs to executors
            for (BuildableItem p : new ArrayList<BuildableItem>(
                    buildables)) {// copy as we'll mutate the list in the loop
                // one last check to make sure this build is not blocked.
                if (isBuildBlocked(p)) {
                    p.leave(this);
                    new BlockedItem(p).enter(this);
                    LOGGER.log(Level.FINE, "Catching that {0} is blocked in the last minute", p);
                    // JENKINS-28926 we have moved an unblocked task into the blocked state, update snapshot
                    // so that other buildables which might have been blocked by this can see the state change
                    updateSnapshot();
                    continue;
                }

                String taskDisplayName = p.task.getFullDisplayName();

                if (p.task instanceof FlyweightTask) {
                    Runnable r = makeFlyWeightTaskBuildable(new BuildableItem(p));
                    if (r != null) {
                        p.leave(this);
                        LOGGER.log(Level.FINEST, "Executing flyweight task {0}", taskDisplayName);
                        r.run();
                        updateSnapshot();
                    }
                } else {

                    List<JobOffer> candidates = new ArrayList<JobOffer>(parked.size());
                    for (JobOffer j : parked.values()) {
                        if (j.canTake(p)) {
                            LOGGER.log(Level.FINEST,
                                    "{0} is a potential candidate for task {1}",
                                    new Object[]{j.executor.getDisplayName(), taskDisplayName});
                            candidates.add(j);
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
                    // of isBuildBlocked(p) will become a bottleneck before updateSnapshot() will. Additionally
                    // since the snapshot itself only ever has at most one reference originating outside of the stack
                    // it should remain in the eden space and thus be cheap to GC.
                    // See https://issues.jenkins-ci.org/browse/JENKINS-27708?focusedCommentId=225819&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-225819
                    // or https://issues.jenkins-ci.org/browse/JENKINS-27708?focusedCommentId=225906&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-225906
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
            String taskDisplayName = p.task.getFullDisplayName();
            if (!isBlockedByShutdown(p.task)) {

                Runnable runnable = makeFlyWeightTaskBuildable(p);
                LOGGER.log(Level.FINEST, "Converting flyweight task: {0} into a BuildableRunnable", taskDisplayName);
                if(runnable != null){
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
    private Runnable makeFlyWeightTaskBuildable(final BuildableItem p){
        //we double check if this is a flyweight task
        if (p.task instanceof FlyweightTask) {
            Jenkins h = Jenkins.getInstance();
            Map<Node, Integer> hashSource = new HashMap<Node, Integer>(h.getNodes().size());

            // Even if master is configured with zero executors, we may need to run a flyweight task like MatrixProject on it.
            hashSource.put(h, Math.max(h.getNumExecutors() * 100, 1));

            for (Node n : h.getNodes()) {
                hashSource.put(n, n.getNumExecutors() * 100);
            }

            ConsistentHash<Node> hash = new ConsistentHash<Node>(NODE_HASH);
            hash.addAll(hashSource);

            Label lbl = p.getAssignedLabel();
            for (Node n : hash.list(p.task.getFullDisplayName())) {
                final Computer c = n.toComputer();
                if (c == null || c.isOffline()) {
                    continue;
                }
                if (lbl!=null && !lbl.contains(n)) {
                    continue;
                }
                if (n.canTake(p) != null) {
                    continue;
                }

                LOGGER.log(Level.FINEST, "Creating flyweight task {0} for computer {1}", new Object[]{p.task.getFullDisplayName(), c.getName()});
                return new Runnable() {
                    @Override public void run() {
                        c.startFlyWeightTask(new WorkUnitContext(p).createWorkUnit(p.task));
                        makePending(p);
                    }
                };
            }
        }
        return null;
    }

    private static Hash<Node> NODE_HASH = new Hash<Node>() {
        public String hash(Node node) {
            return node.getNodeName();
        }
    };

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
        return Jenkins.getInstance().isQuietingDown() && !(task instanceof NonBlockingTask);
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
     * Plugins are encouraged to extend from {@link AbstractQueueTask}
     * instead of implementing this interface directly, to maintain
     * compatibility with future changes to this interface.
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
         *
         * <p>
         * Short-hand for {@code getCauseOfBlockage()!=null}.
         */
        boolean isBuildBlocked();

        /**
         * @deprecated as of 1.330
         *      Use {@link CauseOfBlockage#getShortDescription()} instead.
         */
        @Deprecated
        String getWhyBlocked();

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
         */
        CauseOfBlockage getCauseOfBlockage();

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
         * Checks the permission to see if the current user can abort this executable.
         * Returns normally from this method if it's OK.
         * <p>
         * NOTE: If you have implemented {@link AccessControlled} this should just be
         * {@code checkPermission(hudson.model.Item.CANCEL);}
         *
         * @throws AccessDeniedException if the permission is not granted.
         */
        void checkAbortPermission();

        /**
         * Works just like {@link #checkAbortPermission()} except it indicates the status by a return value,
         * instead of exception.
         * Also used by default for {@link hudson.model.Queue.Item#hasCancelPermission}.
         * <p>
         * NOTE: If you have implemented {@link AccessControlled} this should just be
         * {@code return hasPermission(hudson.model.Item.CANCEL);}
         *
         * @return false
         *      if the user doesn't have the permission.
         */
        boolean hasAbortPermission();

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
         *
         * @since 1.338
         */
        boolean isConcurrentBuild();

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
         * <p>
         * Since this is a newly added method, the invocation may results in {@link AbstractMethodError}.
         * Use {@link Tasks#getSubTasksOf(Queue.Task)} that avoids this.
         *
         * @since 1.377
         */
        Collection<? extends SubTask> getSubTasks();

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
         * @since 1.520
         * @see QueueItemAuthenticator
         * @see Tasks#getDefaultAuthenticationOf(Queue.Task)
         */
        @Nonnull Authentication getDefaultAuthentication();

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
         * @since 1.592
         * @see QueueItemAuthenticator
         * @see Tasks#getDefaultAuthenticationOf(Queue.Task, Queue.Item)
         */
        @Nonnull Authentication getDefaultAuthentication(Queue.Item item);
    }

    /**
     * Represents the real meat of the computation run by {@link Executor}.
     *
     * <h2>Views</h2>
     * <p>
     * Implementation must have <tt>executorCell.jelly</tt>, which is
     * used to render the HTML that indicates this executable is executing.
     */
    public interface Executable extends Runnable {
        /**
         * Task from which this executable was created.
         *
         * <p>
         * Since this method went through a signature change in 1.377, the invocation may results in
         * {@link AbstractMethodError}.
         * Use {@link Executables#getParentOf(Queue.Executable)} that avoids this.
         */
        @Nonnull SubTask getParent();

        /**
         * Called by {@link Executor} to perform the task.
         * @throws AsynchronousExecution if you would like to continue without consuming a thread
         */
        @Override void run() throws AsynchronousExecution;

        /**
         * Estimate of how long will it take to execute this executable.
         * Measured in milliseconds.
         *
         * Please, consider using {@link Executables#getEstimatedDurationFor(Queue.Executable)}
         * to protected against AbstractMethodErrors!
         *
         * @return -1 if it's impossible to estimate.
         * @since 1.383
         */
        long getEstimatedDuration();

        /**
         * Used to render the HTML. Should be a human readable text of what this executable is.
         */
        @Override String toString();
    }

    /**
     * Item in a queue.
     */
    @ExportedBean(defaultVisibility = 999)
    public static abstract class Item extends Actionable {

        private final long id;

        /**
         * Unique ID (per master) that tracks the {@link Task} as it moves through different stages
         * in the queue (each represented by different subtypes of {@link Item} and into any subsequent
         * {@link Run} instance (see {@link Run#getQueueId()}).
         * @return
         * @since 1.601
         */
        @Exported
        public long getId() {
            return id;
        }

        @AdaptField(was=int.class, name="id")
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
        public final Task task;

        private /*almost final*/ transient FutureImpl future;

        private final long inQueueSince;

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
        public Label getAssignedLabel() {
            for (LabelAssignmentAction laa : getActions(LabelAssignmentAction.class)) {
                Label l = laa.getAssignedLabel(task);
                if (l!=null)    return l;
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
        public @CheckForNull Label getAssignedLabelFor(@Nonnull SubTask st) {
            for (LabelAssignmentAction laa : getActions(LabelAssignmentAction.class)) {
                Label l = laa.getAssignedLabel(st);
                if (l!=null)    return l;
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
            if (ca!=null)
                return Collections.unmodifiableList(ca.getCauses());
            return Collections.emptyList();
        }

        @Restricted(DoNotUse.class) // used from Jelly
        public String getCausesDescription() {
            List<Cause> causes = getCauses();
            StringBuilder s = new StringBuilder();
            for (Cause c : causes) {
                s.append(c.getShortDescription()).append('\n');
            }
            return s.toString();
        }

        protected Item(Task task, List<Action> actions, long id, FutureImpl future) {
            this.task = task;
            this.id = id;
            this.future = future;
            this.inQueueSince = System.currentTimeMillis();
            for (Action action: actions) addAction(action);
        }

        protected Item(Task task, List<Action> actions, long id, FutureImpl future, long inQueueSince) {
            this.task = task;
            this.id = id;
            this.future = future;
            this.inQueueSince = inQueueSince;
            for (Action action: actions) addAction(action);
        }

        protected Item(Item item) {
        	this(item.task, new ArrayList<Action>(item.getAllActions()), item.id, item.future, item.inQueueSince);
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
            return "queue/item/"+id+'/';
        }

        /**
         * Gets a human-readable status message describing why it's in the queue.
         */
        @Exported
        public final String getWhy() {
            CauseOfBlockage cob = getCauseOfBlockage();
            return cob!=null ? cob.getShortDescription() : null;
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
        public String getParams() {
        	StringBuilder s = new StringBuilder();
        	for (ParametersAction pa : getActions(ParametersAction.class)) {
                for (ParameterValue p : pa.getParameters()) {
                    s.append('\n').append(p.getShortDescription());
                }
        	}
        	return s.toString();
        }

        /**
         * Checks whether a scheduled item may be canceled.
         * @return by default, the same as {@link hudson.model.Queue.Task#hasAbortPermission}
         */
        public boolean hasCancelPermission() {
            return task.hasAbortPermission();
        }

        public String getDisplayName() {
			// TODO Auto-generated method stub
			return null;
		}

		public String getSearchUrl() {
			// TODO Auto-generated method stub
			return null;
		}

        /** @deprecated Use {@link #doCancelItem} instead. */
        @Deprecated
        @RequirePOST
        public HttpResponse doCancelQueue() throws IOException, ServletException {
        	Jenkins.getInstance().getQueue().cancel(this);
            return HttpResponses.forwardToPreviousPage();
        }

        /**
         * Returns the identity that this task carries when it runs, for the purpose of access control.
         *
         * When the task execution touches other objects inside Jenkins, the access control is performed
         * based on whether this {@link Authentication} is allowed to use them. Implementers, if you are unsure,
         * return the identity of the user who queued the task, or {@link ACL#SYSTEM} to bypass the access control
         * and run as the super user.
         *
         * @since 1.520
         */
        @Nonnull
        public Authentication authenticate() {
            for (QueueItemAuthenticator auth : QueueItemAuthenticatorProvider.authenticators()) {
                Authentication a = auth.authenticate(this);
                if (a!=null)
                    return a;
            }
            return Tasks.getDefaultAuthenticationOf(task, this);
        }


        public Api getApi() {
            return new Api(this);
        }

        private Object readResolve() {
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

        private String name;

        public StubTask(@Nonnull Queue.Task base) {
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
    public class StubItem {

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
    public static abstract class QueueDecisionHandler implements ExtensionPoint {
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
	private static final AtomicLong COUNTER = new AtomicLong(0);

        /**
         * This item can be run after this time.
         */
        @Exported
        public Calendar timestamp;

        public WaitingItem(Calendar timestamp, Task project, List<Action> actions) {
            super(project, actions, COUNTER.incrementAndGet(), new FutureImpl(project));
            this.timestamp = timestamp;
        }

        static int getCurrentCounterValue() {
            return COUNTER.intValue();
        }

        public int compareTo(WaitingItem that) {
            int r = this.timestamp.getTime().compareTo(that.timestamp.getTime());
            if (r != 0) return r;

            if (this.getId() < that.getId()) {
                return -1;
            } else if (this.getId() == that.getId()) {
                return 0;
            } else {
                return 1;
            }
        }

        public CauseOfBlockage getCauseOfBlockage() {
            long diff = timestamp.getTimeInMillis() - System.currentTimeMillis();
            if (diff > 0)
                return CauseOfBlockage.fromMessage(Messages._Queue_InQuietPeriod(Util.getTimeSpanString(diff)));
            else
                return CauseOfBlockage.fromMessage(Messages._Queue_Unknown());
        }

        @Override
        /*package*/ void enter(Queue q) {
            if (q.waitingList.add(this)) {
                for (QueueListener ql : QueueListener.all()) {
                    try {
                        ql.onEnterWaiting(this);
                    } catch (Throwable e) {
                        // don't let this kill the queue
                        LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                    }
                }
            }
        }

        @Override
        /*package*/ boolean leave(Queue q) {
            boolean r = q.waitingList.remove(this);
            if (r) {
                for (QueueListener ql : QueueListener.all()) {
                    try {
                        ql.onLeaveWaiting(this);
                    } catch (Throwable e) {
                        // don't let this kill the queue
                        LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                    }
                }
            }
            return r;
        }


    }

    /**
     * Common part between {@link BlockedItem} and {@link BuildableItem}.
     */
    public static abstract class NotWaitingItem extends Item {
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
        public BlockedItem(WaitingItem wi) {
            super(wi);
        }

        public BlockedItem(NotWaitingItem ni) {
            super(ni);
        }

        public CauseOfBlockage getCauseOfBlockage() {
            ResourceActivity r = getBlockingActivity(task);
            if (r != null) {
                if (r == task) // blocked by itself, meaning another build is in progress
                    return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
                return CauseOfBlockage.fromMessage(Messages._Queue_BlockedBy(r.getDisplayName()));
            }

            for (QueueTaskDispatcher d : QueueTaskDispatcher.all()) {
                CauseOfBlockage cause = d.canRun(this);
                if (cause != null)
                    return cause;
            }

            return task.getCauseOfBlockage();
        }

        /*package*/ void enter(Queue q) {
            LOGGER.log(Level.FINE, "{0} is blocked", this);
            blockedProjects.add(this);
            for (QueueListener ql : QueueListener.all()) {
                try {
                    ql.onEnterBlocked(this);
                } catch (Throwable e) {
                    // don't let this kill the queue
                    LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                }
            }
        }

        /*package*/ boolean leave(Queue q) {
            boolean r = blockedProjects.remove(this);
            if (r) {
                LOGGER.log(Level.FINE, "{0} no longer blocked", this);
                for (QueueListener ql : QueueListener.all()) {
                    try {
                        ql.onLeaveBlocked(this);
                    } catch (Throwable e) {
                        // don't let this kill the queue
                        LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                    }
                }
            }
            return r;
        }
    }

    /**
     * {@link Item} in the {@link Queue#buildables} stage.
     */
    public final static class BuildableItem extends NotWaitingItem {
        /**
         * Set to true when this is added to the {@link Queue#pendings} list.
         */
        private boolean isPending;

        public BuildableItem(WaitingItem wi) {
            super(wi);
        }

        public BuildableItem(NotWaitingItem ni) {
            super(ni);
        }

        public CauseOfBlockage getCauseOfBlockage() {
            Jenkins jenkins = Jenkins.getInstance();
            if(isBlockedByShutdown(task))
                return CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown());

            Label label = getAssignedLabel();
            List<Node> allNodes = jenkins.getNodes();
            if (allNodes.isEmpty())
                label = null;    // no master/slave. pointless to talk about nodes

            if (label != null) {
                Set<Node> nodes = label.getNodes();
                if (label.isOffline()) {
                    if (nodes.size() != 1)      return new BecauseLabelIsOffline(label);
                    else                        return new BecauseNodeIsOffline(nodes.iterator().next());
                } else {
                    if (nodes.size() != 1)      return new BecauseLabelIsBusy(label);
                    else                        return new BecauseNodeIsBusy(nodes.iterator().next());
                }
            } else {
                return CauseOfBlockage.createNeedsMoreExecutor(Messages._Queue_WaitingForNextAvailableExecutor());
            }
        }

        @Override
        public boolean isStuck() {
            Label label = getAssignedLabel();
            if(label!=null && label.isOffline())
                // no executor online to process this job. definitely stuck.
                return true;

            long d = task.getEstimatedDuration();
            long elapsed = System.currentTimeMillis()-buildableStartMilliseconds;
            if(d>=0) {
                // if we were running elsewhere, we would have done this build ten times.
                return elapsed > Math.max(d,60000L)*10;
            } else {
                // more than a day in the queue
                return TimeUnit2.MILLISECONDS.toHours(elapsed)>24;
            }
        }

        @Exported
        public boolean isPending() {
            return isPending;
        }

        @Override
        /*package*/ void enter(Queue q) {
            q.buildables.add(this);
            for (QueueListener ql : QueueListener.all()) {
                try {
                    ql.onEnterBuildable(this);
                } catch (Throwable e) {
                    // don't let this kill the queue
                    LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                }
            }
        }

        @Override
        /*package*/ boolean leave(Queue q) {
            boolean r = q.buildables.remove(this);
            if (r) {
                LOGGER.log(Level.FINE, "{0} no longer blocked", this);
                for (QueueListener ql : QueueListener.all()) {
                    try {
                        ql.onLeaveBuildable(this);
                    } catch (Throwable e) {
                        // don't let this kill the queue
                        LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                    }
                }
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
    public final static class LeftItem extends Item {
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
            return outcome!=null ? outcome.getPrimaryWorkUnit().getExecutable() : null;
        }

        /**
         * Is this representing a cancelled item?
         */
        @Exported
        public boolean isCancelled() {
            return outcome==null;
        }

        @Override
        void enter(Queue q) {
            q.leftItems.put(getId(),this);
            for (QueueListener ql : QueueListener.all()) {
                try {
                    ql.onLeft(this);
                } catch (Throwable e) {
                    // don't let this kill the queue
                    LOGGER.log(Level.WARNING, "QueueListener failed while processing "+this,e);
                }
            }
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
			@SuppressWarnings("unchecked")
			public boolean canConvert(Class klazz) {
				return hudson.model.Item.class.isAssignableFrom(klazz);
			}

			@Override
			public Object fromString(String string) {
                Object item = Jenkins.getInstance().getItemByFullName(string);
                if(item==null)  throw new NoSuchElementException("No such job exists: "+string);
                return item;
			}

			@Override
			public String toString(Object item) {
				return ((hudson.model.Item) item).getFullName();
			}
        });
        XSTREAM.registerConverter(new AbstractSingleValueConverter() {

			@SuppressWarnings("unchecked")
			@Override
			public boolean canConvert(Class klazz) {
				return Run.class.isAssignableFrom(klazz);
			}

			@Override
			public Object fromString(String string) {
				String[] split = string.split("#");
				String projectName = split[0];
				int buildNumber = Integer.parseInt(split[1]);
				Job<?,?> job = (Job<?,?>) Jenkins.getInstance().getItemByFullName(projectName);
                if(job==null)  throw new NoSuchElementException("No such job exists: "+projectName);
				Run<?,?> run = job.getBuildByNumber(buildNumber);
                if(run==null)  throw new NoSuchElementException("No such build: "+string);
				return run;
			}

			@Override
			public String toString(Object object) {
				Run<?,?> run = (Run<?,?>) object;
				return run.getParent().getFullName() + "#" + run.getNumber();
			}
        });

        /**
         * Reconnect every reference to {@link Queue} by the singleton.
         */
        XSTREAM.registerConverter(new AbstractSingleValueConverter() {
			@Override
			public boolean canConvert(Class klazz) {
				return Queue.class.isAssignableFrom(klazz);
			}

			@Override
			public Object fromString(String string) {
                return Jenkins.getInstance().getQueue();
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
            this.queue = new WeakReference<Queue>(queue);
        }

        private void periodic() {
            long interval = 5000;
            Timer.get().scheduleWithFixedDelay(this, interval, interval, TimeUnit.MILLISECONDS);
        }

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
    		for (T item: this) {
    			if (item.task.equals(task)) {
    				return item;
    			}
    		}
    		return null;
    	}

    	public List<T> getAll(Task task) {
    		List<T> result = new ArrayList<T>();
    		for (T item: this) {
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
            if(x!=null) x.cancel(Queue.this);
            return x;
        }

        public void cancelAll() {
            for (T t : new ArrayList<T>(this))
                t.cancel(Queue.this);

            clear();    // just to be sure
        }
    }

    private static class Snapshot {
        private final Set<WaitingItem> waitingList;
        private final List<BlockedItem> blockedProjects;
        private final List<BuildableItem> buildables;
        private final List<BuildableItem> pendings;

        public Snapshot(Set<WaitingItem> waitingList, List<BlockedItem> blockedProjects, List<BuildableItem> buildables,
                        List<BuildableItem> pendings) {
            this.waitingList = new LinkedHashSet<WaitingItem>(waitingList);
            this.blockedProjects = new ArrayList<BlockedItem>(blockedProjects);
            this.buildables = new ArrayList<BuildableItem>(buildables);
            this.pendings = new ArrayList<BuildableItem>(pendings);
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

    private static class LockedHRCallable<V,T extends Throwable> implements hudson.remoting.Callable<V,T> {
        private static final long serialVersionUID = 1L;
        private final hudson.remoting.Callable<V,T> delegate;

        private LockedHRCallable(hudson.remoting.Callable<V,T> delegate) {
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
        return Jenkins.getInstance().getQueue();
    }

    /**
     * Restores the queue content during the start up.
     */
    @Initializer(after=JOB_LOADED)
    public static void init(Jenkins h) {
        h.getQueue().load();
    }
}
