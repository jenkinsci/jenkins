/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, Tom Huybrechts
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

import hudson.BulkChange;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.Initializer;
import static hudson.init.InitMilestone.JOB_LOADED;
import static hudson.util.Iterators.reverse;

import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.QueueSorter;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.remoting.AsyncFutureImpl;
import hudson.model.Node.Mode;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.FoldableAction;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsBusy;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseLabelIsOffline;
import hudson.model.queue.CauseOfBlockage.BecauseNodeIsBusy;
import hudson.triggers.SafeTimerTask;
import hudson.triggers.Trigger;
import hudson.util.OneShotEvent;
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
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.timer.Timer;
import javax.servlet.ServletException;

import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;

/**
 * Build queue.
 *
 * <p>
 * This class implements the core scheduling logic. {@link Task} represents the executable
 * task that are placed in the queue. While in the queue, it's wrapped into {@link Item}
 * so that we can keep track of additional data used for deciding what to exeucte when.
 *
 * <p>
 * Items in queue goes through several stages, as depicted below:
 * <pre>
 * (enter) --> waitingList --+--> blockedProjects
 *                           |        ^
 *                           |        |
 *                           |        v
 *                           +--> buildables ---> (executed)
 * </pre>
 *
 * <p>
 * In addition, at any stage, an item can be removed from the queue (for example, when the user
 * cancels a job in the queue.) See the corresponding field for their exact meanings.
 *
 * @author Kohsuke Kawaguchi
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
     * required {@link Resource}s are not available, or otherwise blocked
     * by {@link Task#isBuildBlocked()}.
     */
    private final ItemList<BlockedItem> blockedProjects = new ItemList<BlockedItem>();

    /**
     * Popped items are the keys, the value is when the item was placed in the Map.
     * When an item is popped it is placed here.  The Executor that popped it should
     * remove it via {@link #completePop()} once it creates the {@link Executable}.
     */
    private final Map<Item,Long> popped = new HashMap<Item,Long> ();
    
    /**
     * How long we wait for an {@link Executor} to call {@link #completePop()} before
     * we assume that something has gone wrong and remove an {@link Item} from
     * {@link #popped}.
     */
    private final long poppedWaitTime = 1000*60*1; // One minute
    
    /**
     * {@link Task}s that can be built immediately
     * that are waiting for available {@link Executor}.
     * This list is sorted in such a way that earlier items are built earlier.
     */
    private final ItemList<BuildableItem> buildables = new ItemList<BuildableItem>();

    /**
     * Data structure created for each idle {@link Executor}.
     * This is a job offer from the queue to an executor.
     *
     * <p>
     * An idle executor (that calls {@link Queue#pop()} creates
     * a new {@link JobOffer} and gets itself {@linkplain Queue#parked parked},
     * and we'll eventually hand out an {@link #item} to build.
     */
    public static class JobOffer {
        public final Executor executor;

        /**
         * Used to wake up an executor, when it has an offered
         * {@link Project} to build.
         */
        private final OneShotEvent event = new OneShotEvent();

        /**
         * The project that this {@link Executor} is going to build.
         * (Or null, in which case event is used to trigger a queue maintenance.)
         */
        private BuildableItem item;

        private JobOffer(Executor executor) {
            this.executor = executor;
        }

        public void set(BuildableItem p) {
            assert this.item == null;
            this.item = p;
            event.signal();
        }

        /**
         * Verifies that the {@link Executor} represented by this object is capable of executing the given task.
         */
        public boolean canTake(Task task) {
            Node node = getNode();
            if (node==null)     return false;   // this executor is about to die

            if(node.canTake(task)!=null)
                return false;   // this node is not able to take the task

            for (QueueTaskDispatcher d : QueueTaskDispatcher.all())
                if (d.canTake(node,task)!=null)
                    return false;

            return isAvailable();
        }

        /**
         * Is this executor ready to accept some tasks?
         */
        public boolean isAvailable() {
            return item == null && !executor.getOwner().isOffline() && executor.getOwner().isAcceptingTasks();
        }

        public Node getNode() {
            return executor.getOwner().getNode();
        }

        public boolean isNotExclusive() {
            return getNode().getMode() == Mode.NORMAL;
        }
    }

    /**
     * The executors that are currently waiting for a job to run.
     */
    private final Map<Executor,JobOffer> parked = new HashMap<Executor,JobOffer>();

    private volatile transient LoadBalancer loadBalancer;

    private volatile transient QueueSorter sorter;
    
    public Queue(LoadBalancer loadBalancer) {
        this.loadBalancer =  loadBalancer.sanitize();
        // if all the executors are busy doing something, then the queue won't be maintained in
        // timely fashion, so use another thread to make sure it happens.
        new MaintainTask(this);
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(LoadBalancer loadBalancer) {
        if(loadBalancer==null)  throw new IllegalArgumentException();
        this.loadBalancer = loadBalancer;
    }

    public QueueSorter getSorter() {
        return sorter;
    }

    public void setSorter(QueueSorter sorter) {
        this.sorter = sorter;
    }

    /**
     * Loads the queue contents that was {@link #save() saved}.
     */
    public synchronized void load() {
        try {
            // first try the old format
            File queueFile = getQueueFile();
            if (queueFile.exists()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(queueFile)));
                String line;
                while ((line = in.readLine()) != null) {
                    AbstractProject j = Hudson.getInstance().getItemByFullName(line, AbstractProject.class);
                    if (j != null)
                        j.scheduleBuild();
                }
                in.close();
                // discard the queue file now that we are done
                queueFile.delete();
            } else {
                queueFile = getXMLQueueFile();
                if (queueFile.exists()) {
                    List list = (List) new XmlFile(XSTREAM, queueFile).read();
                    int maxId = 0;
                    for (Object o : list) {
                        if (o instanceof Task) {
                            // backward compatibility
                            schedule((Task)o, 0);
                        } else if (o instanceof Item) {
                            Item item = (Item)o;
                            if(item.task==null)
                                continue;   // botched persistence. throw this one away

                            maxId = Math.max(maxId, item.id);
                            if (item instanceof WaitingItem) {
                                waitingList.add((WaitingItem) item);
                            } else if (item instanceof BlockedItem) {
                                blockedProjects.put(item.task, (BlockedItem) item);
                            } else if (item instanceof BuildableItem) {
                                buildables.add((BuildableItem) item);
                            } else {
                                throw new IllegalStateException("Unknown item type! " + item);
                            }
                        } // this conveniently ignores null
                    }
                    WaitingItem.COUNTER.set(maxId);

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
        }
    }

    /**
     * Persists the queue contents to the disk.
     */
    public synchronized void save() {
        if(BulkChange.contains(this))  return;
        
        // write out the tasks on the queue
    	ArrayList<Queue.Item> items = new ArrayList<Queue.Item>();
    	for (Item item: getItems()) {
            if(item.task instanceof TransientTask)  continue;
    	    items.add(item);
    	}

        try {
            XmlFile queueFile = new XmlFile(XSTREAM, getXMLQueueFile());
            queueFile.write(items);
            SaveableListener.fireOnChange(this, queueFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write out the queue file " + getXMLQueueFile(), e);
        }
    }

    /**
     * Wipes out all the items currently in the queue, as if all of them are cancelled at once.
     */
    @CLIMethod(name="clear-queue")
    public synchronized void clear() {
        for (WaitingItem i : waitingList)
            i.onCancelled();
        waitingList.clear();
        blockedProjects.cancelAll();
        buildables.cancelAll();
        scheduleMaintenance();
    }

    private File getQueueFile() {
        return new File(Hudson.getInstance().getRootDir(), "queue.txt");
    }

    /*package*/ File getXMLQueueFile() {
        return new File(Hudson.getInstance().getRootDir(), "queue.xml");
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(AbstractProject)}
     */
    public boolean add(AbstractProject p) {
        return schedule(p)!=null;
    }

    /**
     * Schedule a new build for this project.
     *
     * @return true if the project is actually added to the queue.
     *         false if the queue contained it and therefore the add()
     *         was noop
     */
    public WaitingItem schedule(AbstractProject p) {
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
    public boolean add(AbstractProject p, int quietPeriod) {
        return schedule(p, quietPeriod)!=null;
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
     *      null if this task is already in the queue and therefore the add operation was no-op.
     *      Otherwise indicates the {@link WaitingItem} object added, although the nature of the queue
     *      is that such {@link Item} only captures the state of the item at a particular moment,
     *      and by the time you inspect the object, some of its information can be already stale.
     *
     *      That said, one can still look at {@link WaitingItem#future}, {@link WaitingItem#id}, etc.
     */
    public synchronized WaitingItem schedule(Task p, int quietPeriod, List<Action> actions) {
        // remove nulls
        actions = new ArrayList<Action>(actions);
        for (Iterator<Action> itr = actions.iterator(); itr.hasNext();) {
            Action a =  itr.next();
            if (a==null)    itr.remove();
        }

    	for(QueueDecisionHandler h : QueueDecisionHandler.all())
    		if (!h.shouldSchedule(p, actions))
                return null;    // veto

        return scheduleInternal(p, quietPeriod, actions);
    }

    /**
     * Schedules an execution of a task.
     *
     * @since 1.311
     * @return
     *      null if this task is already in the queue and therefore the add operation was no-op.
     *      Otherwise indicates the {@link WaitingItem} object added, although the nature of the queue
     *      is that such {@link Item} only captures the state of the item at a particular moment,
     *      and by the time you inspect the object, some of its information can be already stale.
     *
     *      That said, one can still look at {@link WaitingItem#future}, {@link WaitingItem#id}, etc.
     */
    private synchronized WaitingItem scheduleInternal(Task p, int quietPeriod, List<Action> actions) {
        Calendar due = new GregorianCalendar();
    	due.add(Calendar.SECOND, quietPeriod);

        // Do we already have this task in the queue? Because if so, we won't schedule a new one.
    	List<Item> duplicatesInQueue = new ArrayList<Item>();
    	for(Item item : getItems(p)) {
    		boolean shouldScheduleItem = false;
    		for (QueueAction action: item.getActions(QueueAction.class)) {
                shouldScheduleItem |= action.shouldSchedule(actions);
    		}
    		for (QueueAction action: Util.filter(actions,QueueAction.class)) {
                shouldScheduleItem |= action.shouldSchedule(item.getActions());
    		}
    		if(!shouldScheduleItem) {
    			duplicatesInQueue.add(item);
    		}
    	}
    	if (duplicatesInQueue.isEmpty()) {
    		LOGGER.fine(p.getFullDisplayName() + " added to queue");

    		// put the item in the queue
            WaitingItem added = new WaitingItem(due,p,actions);
    		waitingList.add(added);
            scheduleMaintenance();   // let an executor know that a new item is in the queue.
            return added;
    	}

        LOGGER.fine(p.getFullDisplayName() + " is already in the queue");

        // but let the actions affect the existing stuff.
        for(Item item : duplicatesInQueue) {
            for(FoldableAction a : Util.filter(actions,FoldableAction.class)) {
                a.foldIntoExisting(item, p, actions);
            }
        }

        boolean queueUpdated = false;
        for(WaitingItem wi : Util.filter(duplicatesInQueue,WaitingItem.class)) {
            if(quietPeriod<=0) {
                // the user really wants to build now, and they mean NOW.
                // so let's pull in the timestamp if we can.
                if (wi.timestamp.before(due))
                    continue;
            } else {
                // otherwise we do the normal quiet period implementation
                if (wi.timestamp.after(due))
                    continue;
                // quiet period timer reset. start the period over again
            }

            // waitingList is sorted, so when we change a timestamp we need to maintain order
            waitingList.remove(wi);
            wi.timestamp = due;
            waitingList.add(wi);
            queueUpdated=true;
        }

        if (queueUpdated)   scheduleMaintenance();
        return null;
    }
    
    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(Task, int)} 
     */
    public synchronized boolean add(Task p, int quietPeriod) {
    	return schedule(p, quietPeriod)!=null;
    }

    public synchronized WaitingItem schedule(Task p, int quietPeriod) {
    	return schedule(p, quietPeriod, new Action[0]);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #schedule(Task, int, Action...)} 
     */
    public synchronized boolean add(Task p, int quietPeriod, Action... actions) {
    	return schedule(p, quietPeriod, actions)!=null;
    }

    /**
     * Convenience wrapper method around {@link #schedule(Task, int, List)}
     */
    public synchronized WaitingItem schedule(Task p, int quietPeriod, Action... actions) {
    	return schedule(p, quietPeriod, Arrays.asList(actions));
    }

    /**
     * Cancels the item in the queue. If the item is scheduled more than once, cancels the first occurrence.
     *
     * @return true if the project was indeed in the queue and was removed.
     *         false if this was no-op.
     */
    public synchronized boolean cancel(Task p) {
        LOGGER.fine("Cancelling " + p.getFullDisplayName());
        for (Iterator<WaitingItem> itr = waitingList.iterator(); itr.hasNext();) {
            Item item = itr.next();
            if (item.task.equals(p)) {
                itr.remove();
                item.onCancelled();
                return true;
            }
        }
        // use bitwise-OR to make sure that both branches get evaluated all the time
        return blockedProjects.cancel(p)!=null | buildables.cancel(p)!=null;
    }
    
    public synchronized boolean cancel(Item item) {
        LOGGER.fine("Cancelling " + item.task.getFullDisplayName() + " item#" + item.id);
        // use bitwise-OR to make sure that all the branches get evaluated all the time
        boolean r = (item instanceof WaitingItem && waitingList.remove(item)) | blockedProjects.remove(item) | buildables.remove(item);
        if(r)
            item.onCancelled();
        return r;
    }

    public synchronized boolean isEmpty() {
        return waitingList.isEmpty() && blockedProjects.isEmpty() && buildables.isEmpty();
    }

    private synchronized WaitingItem peek() {
        return waitingList.iterator().next();
    }

    /**
     * Gets a snapshot of items in the queue.
     *
     * Generally speaking the array is sorted such that the items that are most likely built sooner are
     * at the end.
     */
    @Exported(inline=true)
    public synchronized Item[] getItems() {
        Item[] r = new Item[waitingList.size() + blockedProjects.size() + buildables.size()];
        waitingList.toArray(r);
        int idx = waitingList.size();
        for (BlockedItem p : blockedProjects.values())
            r[idx++] = p;
        for (BuildableItem p : reverse(buildables.values()))
            r[idx++] = p;
        return r;
    }
    
    public synchronized Item getItem(int id) {
    	for (Item item: waitingList) if (item.id == id) return item;
    	for (Item item: blockedProjects) if (item.id == id) return item;
    	for (Item item: buildables) if (item.id == id) return item;
    	return null;
    }

    /**
     * Gets all the {@link BuildableItem}s that are waiting for an executor in the given {@link Computer}.
     */
    public synchronized List<BuildableItem> getBuildableItems(Computer c) {
        List<BuildableItem> result = new ArrayList<BuildableItem>();
        for (BuildableItem p : buildables.values()) {
            Label l = p.task.getAssignedLabel();
            if (l != null) {
                // if a project has assigned label, it can be only built on it
                if (!l.contains(c.getNode()))
                    continue;
            }
            result.add(p);
        }
        return result;
    }

    /**
     * Gets the snapshot of {@link #buildables}.
     */
    public synchronized List<BuildableItem> getBuildableItems() {
        return new ArrayList<BuildableItem>(buildables.values());
    }

    /**
     * How many {@link BuildableItem}s are assigned for the given label?
     */
    public synchronized int countBuildableItemsFor(Label l) {
        int r = 0;
        for (BuildableItem bi : buildables.values())
            if(bi.task.getAssignedLabel()==l)
                r++;
        return r;
    }

    /**
     * Gets the information about the queue item for the given project.
     *
     * @return null if the project is not in the queue.
     */
    public synchronized Item getItem(Task t) {
        BlockedItem bp = blockedProjects.get(t);
        if (bp!=null)
            return bp;
        BuildableItem bi = buildables.get(t);
        if(bi!=null)
            return bi;

        for (Item item : waitingList) {
            if (item.task == t)
                return item;
        }
        return null;
    }

    /**
     * Gets the information about the queue item for the given project.
     *
     * @return null if the project is not in the queue.
     */
    public synchronized List<Item> getItems(Task t) {
    	List<Item> result =new ArrayList<Item>();
    	result.addAll(blockedProjects.getAll(t));
    	result.addAll(buildables.getAll(t));
        for (Item item : waitingList) {
            if (item.task == t)
                result.add(item);
        }
        return result;
    }

    /**
     * Left for backward compatibility.
     *
     * @see #getItem(Task)
    public synchronized Item getItem(AbstractProject p) {
        return getItem((Task) p);
    }
     */

    /**
     * Returns true if this queue contains the said project.
     */
    public synchronized boolean contains(Task t) {
        if (blockedProjects.containsKey(t) || buildables.containsKey(t))
            return true;
        for (Item item : waitingList) {
            if (item.task == t)
                return true;
        }
        return false;
    }

    /**
     * Called by the executor to fetch something to build next.
     * <p>
     * This method blocks until a next project becomes buildable.
     */
    public Queue.Item pop() throws InterruptedException {
        final Executor exec = Executor.currentExecutor();

        try {
            while (true) {
                final JobOffer offer = new JobOffer(exec);
                long sleep = -1;

                synchronized (this) {
                    // consider myself parked
                    assert !parked.containsKey(exec);
                    parked.put(exec, offer);

                    // reuse executor thread to do a queue maintenance.
                    // at the end of this we get all the buildable jobs
                    // in the buildables field.
                    maintain();

                    // allocate buildable jobs to executors
                    Iterator<BuildableItem> itr = buildables.iterator();
                    while (itr.hasNext()) {
                        BuildableItem p = itr.next();

                        // one last check to make sure this build is not blocked.
                        if (isBuildBlocked(p.task)) {
                            itr.remove();
                            blockedProjects.put(p.task,new BlockedItem(p));
                            continue;
                        }

                        JobOffer runner = loadBalancer.choose(p.task, new ApplicableJobOfferList(p.task));
                        if (runner == null)
                            // if we couldn't find the executor that fits,
                            // just leave it in the buildables list and
                            // check if we can execute other projects
                            continue;

                        assert runner.canTake(p.task);
                        
                        // found a matching executor. use it.
                        runner.set(p);
                        itr.remove();
                    }

                    // we went over all the buildable projects and awaken
                    // all the executors that got work to do. now, go to sleep
                    // until this thread is awakened. If this executor assigned a job to
                    // itself above, the block method will return immediately.

                    if (!waitingList.isEmpty()) {
                        // wait until the first item in the queue is due
                        sleep = peek().timestamp.getTimeInMillis() - new GregorianCalendar().getTimeInMillis();
                        if (sleep < 100) sleep = 100;    // avoid wait(0)
                    }
                }

                // this needs to be done outside synchronized block,
                // so that executors can maintain a queue while others are sleeping
                if (sleep == -1)
                    offer.event.block();
                else
                    offer.event.block(sleep);

                synchronized (this) {
                    // retract the offer object
                    assert parked.get(exec) == offer;
                    parked.remove(exec);

                    // am I woken up because I have a project to build?
                    if (offer.item != null) {
                        // if so, just build it
                        LOGGER.fine("Pop returning " + offer.item + " for " + exec.getName());
                        offer.item.future.startExecuting(exec);
                        popped.put(offer.item, System.currentTimeMillis());
                        return offer.item;
                    }
                    // otherwise run a queue maintenance
                }
            }
        } finally {
            synchronized (this) {
                // remove myself from the parked list
                JobOffer offer = parked.remove(exec);
                if (offer != null && offer.item != null) {
                    // we are already assigned a project,
                    // ask for someone else to build it.
                    // note that while this thread is waiting for CPU
                    // someone else can schedule this build again,
                    // so check the contains method first.
                    if (!contains(offer.item.task))
                        buildables.put(offer.item.task,offer.item);
                }

                // since this executor might have been chosen for
                // maintenance, schedule another one. Worst case
                // we'll just run a pointless maintenance, and that's
                // fine.
                scheduleMaintenance();
            }
        }
    }

    /**
     * Represents a list of {@linkplain JobOffer#canTake(Task) applicable} {@link JobOffer}s
     * and provides various typical 
     */
    public final class ApplicableJobOfferList implements Iterable<JobOffer> {
        private final List<JobOffer> list;
        // laziy filled
        private Map<Node,List<JobOffer>> nodes;

        private ApplicableJobOfferList(Task task) {
            list = new ArrayList<JobOffer>(parked.size());
            for (JobOffer j : parked.values())
                if(j.canTake(task))
                    list.add(j);
        }

        /**
         * Returns all the {@linkplain JobOffer#isAvailable() available} {@link JobOffer}s.
         */
        public List<JobOffer> all() {
            return list;
        }

        public Iterator<JobOffer> iterator() {
            return list.iterator();
        }

        /**
         * List up all the {@link Node}s that have some available offers.
         */
        public Set<Node> nodes() {
            return byNodes().keySet();
        }

        /**
         * Gets a {@link JobOffer} for an executor of the given node, if any.
         * Otherwise null. 
         */
        public JobOffer _for(Node n) {
            List<JobOffer> r = byNodes().get(n);
            if(r==null) return null;
            return r.get(0);
        }

        public Map<Node,List<JobOffer>> byNodes() {
            if(nodes==null) {
                nodes = new HashMap<Node,List<JobOffer>>();
                for (JobOffer o : list) {
                    List<JobOffer> l = nodes.get(o.getNode());
                    if(l==null) nodes.put(o.getNode(),l=new ArrayList<JobOffer>());
                    l.add(o);
                }
            }
            return nodes;
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
    public synchronized void scheduleMaintenance() {
        // this code assumes that after this method is called
        // no more executors will be offered job except by
        // the pop() code.
        for (Entry<Executor, JobOffer> av : parked.entrySet()) {
            if (av.getValue().item == null) {
                av.getValue().event.signal();
                return;
            }
        }
    }

    /**
     * Checks if the given task is blocked.
     */
    private boolean isBuildBlocked(Task t) {
        return t.isBuildBlocked() || !canRun(t.getResourceList());
    }

    /**
     *  Make sure we don't queue two tasks of the same project to be built
     *  unless that project allows concurrent builds.
     */
    private boolean allowNewBuildableTask(Task t) {
        try {
            if (t.isConcurrentBuild())
                return true;
        } catch (AbstractMethodError e) {
            // earlier versions don't have the "isConcurrentBuild" method, so fall back gracefully
        }
        return !(buildables.containsKey(t) || taskIsPopped(t));
    }

    /**
     * Check if an {@link Item} of a given {@link Task} has been recently popped and
     * the {@Executor} has not yet created an {@link Executable} for it. 
     */
    private boolean taskIsPopped (Task t) {
    	long cutoff = System.currentTimeMillis() - poppedWaitTime;
    	for (Map.Entry<Item,Long> e : popped.entrySet()) {
    		if (e.getValue() < cutoff) {
    			popped.remove(e.getKey());
    		} else if (e.getKey().task == t) {
    			return true;
    		}
    	}
    	return false;
    }
    
    /**
     * Queue maintenance.
     * <p>
     * Tell the Queue that a popped item has been started, so we don't have to track
     * it in the Queue anymore.
     */
    public synchronized void completePop (Item i) {
    	popped.remove(i);
    }
        
    /**
     * Queue maintenance.
     * <p>
     * Move projects between {@link #waitingList}, {@link #blockedProjects}, and {@link #buildables}
     * appropriately.
     */
    public synchronized void maintain() {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Queue maintenance started " + this);

        Iterator<BlockedItem> itr = blockedProjects.values().iterator();
        while (itr.hasNext()) {
            BlockedItem p = itr.next();
            if (!isBuildBlocked(p.task) && allowNewBuildableTask(p.task)) {
                // ready to be executed
                LOGGER.fine(p.task.getFullDisplayName() + " no longer blocked");
                itr.remove();
                makeBuildable(new BuildableItem(p));
            }
        }

        while (!waitingList.isEmpty()) {
            WaitingItem top = peek();

            if (!top.timestamp.before(new GregorianCalendar()))
                return; // finished moving all ready items from queue

            waitingList.remove(top);
            Task p = top.task;
            if (!isBuildBlocked(p) && allowNewBuildableTask(p)) {
                // ready to be executed immediately
                LOGGER.fine(p.getFullDisplayName() + " ready to build");
                makeBuildable(new BuildableItem(top));
            } else {
                // this can't be built now because another build is in progress
                // set this project aside.
                LOGGER.fine(p.getFullDisplayName() + " is blocked");
                blockedProjects.put(p,new BlockedItem(top));
            }
        }

        final QueueSorter s = sorter;
        if (s != null)
        	s.sortBuildableItems(buildables);
    }

    private void makeBuildable(BuildableItem p) {
        if(Hudson.FLYWEIGHT_SUPPORT && p.task instanceof FlyweightTask && (!Hudson.getInstance().isQuietingDown() || p.task instanceof NonBlockingTask)) {
            ConsistentHash<Node> hash = new ConsistentHash<Node>(new Hash<Node>() {
                public String hash(Node node) {
                    return node.getNodeName();
                }
            });
            Hudson h = Hudson.getInstance();
            hash.add(h, h.getNumExecutors()*100);
            for (Node n : h.getNodes())
                hash.add(n,n.getNumExecutors()*100);

            Label lbl = p.task.getAssignedLabel();
            for (Node n : hash.list(p.task.getFullDisplayName())) {
                Computer c = n.toComputer();
                if (c==null || c.isOffline())    continue;
                if (lbl!=null && !lbl.contains(n))  continue;
                c.startFlyWeightTask(p);
                return;
            }
            // if the execution get here, it means we couldn't schedule it anywhere.
            // so do the scheduling like other normal jobs.
        }
        
        buildables.put(p.task,p);
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
     * Marks {@link Task}s that are not affected by the {@linkplain Hudson#isQuietingDown()}  quieting down},
     * because these tasks keep other tasks executing.
     *
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
     */
    public interface Task extends ModelObject, ResourceActivity {
        /**
         * If this task needs to be run on a node with a particular label,
         * return that {@link Label}. Otherwise null, indicating
         * it can run on anywhere.
         */
        Label getAssignedLabel();

        /**
         * If the previous execution of this task run on a certain node
         * and this task prefers to run on the same node, return that.
         * Otherwise null.
         */
        Node getLastBuiltOn();

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
         * Estimate of how long will it take to execute this task.
         * Measured in milliseconds.
         *
         * @return -1 if it's impossible to estimate.
         */
        long getEstimatedDuration();

        /**
         * Creates {@link Executable}, which performs the actual execution of the task.
         */
        Executable createExecutable() throws IOException;

        /**
         * Checks the permission to see if the current user can abort this executable.
         * Returns normally from this method if it's OK.
         *
         * @throws AccessDeniedException if the permission is not granted.
         */
        void checkAbortPermission();

        /**
         * Works just like {@link #checkAbortPermission()} except it indicates the status by a return value,
         * instead of exception.
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
         * True if the task allows concurrent builds
         *
         * @since 1.338
         */
        boolean isConcurrentBuild();
    }

    public interface Executable extends Runnable {
        /**
         * Task from which this executable was created.
         * Never null.
         */
        Task getParent();

        /**
         * Called by {@link Executor} to perform the task
         */
        void run();

        /**
         * Used to render the HTML. Should be a human readable text of what this executable is.
         */
        @Override String toString();
    }

    /*package*/ static final class FutureImpl extends AsyncFutureImpl<Executable> {
        private final Task task;
        /**
         * If the computation has started, set to {@link Executor} that's running the build.
         */
        private volatile Executor executor;

        private FutureImpl(Task task) {
            this.task = task;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            Queue q = Hudson.getInstance().getQueue();
            synchronized (q) {
                if(executor!=null) {
                    if(mayInterruptIfRunning)
                        executor.interrupt();
                    return mayInterruptIfRunning;
                }
                return q.cancel(task);
            }
        }

        private void startExecuting(Executor executor) {
            this.executor = executor;
        }
    }

    /**
     * Item in a queue.
     */
    @ExportedBean(defaultVisibility = 999)
    public static abstract class Item extends Actionable {
        /**
         * VM-wide unique ID that tracks the {@link Task} as it moves through different stages
         * in the queue (each represented by different subtypes of {@link Item}.
         */
    	public final int id;
    	
		/**
         * Project to be built.
         */
        @Exported
        public final Task task;

        /*package almost final*/ transient FutureImpl future;

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
         * Can be used to wait for the completion (either normal, abnormal, or cancellation) of the {@link Task}.
         * <p>
         * Just like {@link #id}, the same object tracks various stages of the queue.
         */
        public Future<Executable> getFuture() { return future; }

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

        protected Item(Task task, List<Action> actions, int id, FutureImpl future) {
            this.task = task;
            this.id = id;
            this.future = future;
            for (Action action: actions) addAction(action);
        }
        
        protected Item(Item item) {
        	this(item.task, item.getActions(), item.id, item.future);
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
        	for(Action action : getActions()) {
        		if(action instanceof ParametersAction) {
        			ParametersAction pa = (ParametersAction)action;
        			for (ParameterValue p : pa.getParameters()) {
        				s.append('\n').append(p.getShortDescription());
        			}
        		}
        	}
        	return s.toString();
        }
        
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

        /**
         * Called from queue.jelly.
         */
        public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        	Hudson.getInstance().getQueue().cancel(this);
            rsp.forwardToPreviousPage(req);
        }

        /**
         * Participates in the cancellation logic to set the {@link #future} accordingly.
         */
        /*package*/ void onCancelled() {
            future.setAsCancelled();
        }

        private Object readResolve() {
            this.future = new FutureImpl(task);
            return this;
        }

        @Override
        public String toString() {
            return getClass().getName()+':'+task.toString();
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
    	public boolean shouldSchedule(List<Action> actions);
    }

    /**
     * Extension point for deciding if particular job should be scheduled or not
     *
     * <p>
     * This extension point is still a subject to change, as we are seeking more
     * comprehensive Queue pluggability. See HUDSON-2072.
     *
     * @since 1.316
     */
    public static abstract class QueueDecisionHandler implements ExtensionPoint {
    	/**
    	 * Returns whether the new item should be scheduled. 
    	 */
    	public abstract boolean shouldSchedule(Task p, List<Action> actions);
    	    	
    	/**
    	 * All registered {@link QueueDecisionHandler}s
    	 * @return
    	 */
    	public static ExtensionList<QueueDecisionHandler> all() {
    		return Hudson.getInstance().getExtensionList(QueueDecisionHandler.class);
    	}
    }
    
    /**
     * {@link Item} in the {@link Queue#waitingList} stage.
     */
    public static final class WaitingItem extends Item implements Comparable<WaitingItem> {
    	private static final AtomicInteger COUNTER = new AtomicInteger(0);
    	
        /**
         * This item can be run after this time.
         */
        @Exported
        public Calendar timestamp;

        WaitingItem(Calendar timestamp, Task project, List<Action> actions) {
            super(project, actions, COUNTER.incrementAndGet(), new FutureImpl(project));
            this.timestamp = timestamp;
        }
        
        public int compareTo(WaitingItem that) {
            int r = this.timestamp.getTime().compareTo(that.timestamp.getTime());
            if (r != 0) return r;

            return this.id - that.id;
        }

        public CauseOfBlockage getCauseOfBlockage() {
            long diff = timestamp.getTimeInMillis() - System.currentTimeMillis();
            if (diff > 0)
                return CauseOfBlockage.fromMessage(Messages._Queue_InQuietPeriod(Util.getTimeSpanString(diff)));
            else
                return CauseOfBlockage.fromMessage(Messages._Queue_Unknown());
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
            return task.getCauseOfBlockage();
        }
    }

    /**
     * {@link Item} in the {@link Queue#buildables} stage.
     */
    public final static class BuildableItem extends NotWaitingItem {
        public BuildableItem(WaitingItem wi) {
            super(wi);
        }

        public BuildableItem(NotWaitingItem ni) {
            super(ni);
        }

        public CauseOfBlockage getCauseOfBlockage() {
            Hudson hudson = Hudson.getInstance();
            if(hudson.isQuietingDown() && !(task instanceof NonBlockingTask))
                return CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown());

            Label label = task.getAssignedLabel();
            if (hudson.getNodes().isEmpty())
                label = null;    // no master/slave. pointless to talk about nodes

            if (label != null) {
                if (label.isOffline()) {
                    Set<Node> nodes = label.getNodes();
                    if (nodes.size() != 1)      return new BecauseLabelIsOffline(label);
                    else                        return new BecauseNodeIsOffline(nodes.iterator().next());
                }
            }

            if(label==null)
                return CauseOfBlockage.fromMessage(Messages._Queue_WaitingForNextAvailableExecutor());

            Set<Node> nodes = label.getNodes();
            if (nodes.size() != 1)      return new BecauseLabelIsBusy(label);
            else                        return new BecauseNodeIsBusy(nodes.iterator().next());
        }

        @Override
        public boolean isStuck() {
            Label label = task.getAssignedLabel();
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
                Object item = Hudson.getInstance().getItemByFullName(string);
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
				Job<?,?> job = (Job<?,?>) Hudson.getInstance().getItemByFullName(projectName);
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
    }

    /**
     * Regularly invokes {@link Queue#maintain()} and clean itself up when
     * {@link Queue} gets GC-ed.
     */
    private static class MaintainTask extends SafeTimerTask {
        private final WeakReference<Queue> queue;

        MaintainTask(Queue queue) {
            this.queue = new WeakReference<Queue>(queue);

            long interval = 5 * Timer.ONE_SECOND;
            Trigger.timer.schedule(this, interval, interval);
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
    private static class ItemList<T extends Item> extends ArrayList<T> {
    	public T get(Task task) {
    		for (T item: this) {
    			if (item.task == task) {
    				return item;
    			}
    		}
    		return null;
    	}
    	
    	public List<T> getAll(Task task) {
    		List<T> result = new ArrayList<T>();
    		for (T item: this) {
    			if (item.task == task) {
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
    			if (t.task == task) {
    				it.remove();
    				return t;
    			}
    		}
    		return null;
    	}
    	
    	public void put(Task task, T item) {
    		assert item.task == task;
    		add(item);
    	}
    	
    	public ItemList<T> values() {
    		return this;
    	}

        /**
         * Works like {@link #remove(Task)} but also marks the {@link Item} as cancelled.
         */
        public T cancel(Task p) {
            T x = remove(p);
            if(x!=null) x.onCancelled();
            return x;
        }

        /**
         * Works like {@link #remove(Object)} but also marks the {@link Item} as cancelled.
         */
        public boolean cancel(Item t) {
            boolean r = remove(t);
            if(r)   t.onCancelled();
            return r;
        }

        public void cancelAll() {
            for (T t : this)
                t.onCancelled();
            clear();
        }
    }

    @CLIResolver
    public static Queue getInstance() {
        return Hudson.getInstance().getQueue();
    }

    /**
     * Restores the queue content during the start up.
     */
    @Initializer(after=JOB_LOADED)
    public static void init(Hudson h) {
        h.getQueue().load();
    }
}
