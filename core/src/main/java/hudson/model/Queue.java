package hudson.model;

import hudson.Util;
import hudson.model.Node.Mode;
import hudson.util.OneShotEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build queue.
 *
 * <p>
 * This class implements the core scheduling logic.
 *
 * @author Kohsuke Kawaguchi
 */
public class Queue {
    /**
     * Items in the queue ordered by {@link Item#timestamp}.
     *
     * <p>
     * This consists of {@link Item}s that cannot be run yet
     * because its time has not yet come.
     */
    private final Set<Item> queue = new TreeSet<Item>();

    /**
     * {@link Project}s that can be built immediately
     * but blocked because another build is in progress.
     */
    private final Set<AbstractProject> blockedProjects = new HashSet<AbstractProject>();

    /**
     * {@link Project}s that can be built immediately
     * that are waiting for available {@link Executor}.
     */
    private final List<AbstractProject> buildables = new LinkedList<AbstractProject>();

    /**
     * Data structure created for each idle {@link Executor}.
     * This is an offer from the queue to an executor.
     *
     * <p>
     * It eventually receives a {@link #project} to build.
     */
    private static class JobOffer {
        final Executor executor;

        /**
         * Used to wake up an executor, when it has an offered
         * {@link Project} to build.
         */
        final OneShotEvent event = new OneShotEvent();
        /**
         * The project that this {@link Executor} is going to build.
         * (Or null, in which case event is used to trigger a queue maintenance.)
         */
        AbstractProject project;

        public JobOffer(Executor executor) {
            this.executor = executor;
        }

        public void set(AbstractProject p) {
            this.project = p;
            event.signal();
        }

        public boolean isAvailable() {
            return project==null && !executor.getOwner().isOffline();
        }

        public Node getNode() {
            return executor.getOwner().getNode();
        }

        public boolean isNotExclusive() {
            return getNode().getMode()== Mode.NORMAL;
        }
    }

    private final Map<Executor,JobOffer> parked = new HashMap<Executor,JobOffer>();

    /**
     * Loads the queue contents that was {@link #save() saved}.
     */
    public synchronized void load() {
        // write out the contents of the queue
        try {
            File queueFile = getQueueFile();
            if(!queueFile.exists())
                return;

            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(queueFile)));
            String line;
            while((line=in.readLine())!=null) {
                AbstractProject j = Hudson.getInstance().getItemByFullName(line,AbstractProject.class);
                if(j!=null)
                    j.scheduleBuild();
            }
            in.close();
            // discard the queue file now that we are done
            queueFile.delete();
        } catch(IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load the queue file "+getQueueFile(),e);
        }
    }

    /**
     * Persists the queue contents to the disk.
     */
    public synchronized void save() {
        // write out the contents of the queue
        try {
            PrintWriter w = new PrintWriter(new FileOutputStream(
                getQueueFile()));
            for (Item i : getItems())
                w.println(i.project.getName());
            w.close();
        } catch(IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write out the queue file "+getQueueFile(),e);
        }
    }

    private File getQueueFile() {
        return new File(Hudson.getInstance().getRootDir(),"queue.txt");
    }

    /**
     * Schedule a new build for this project.
     *
     * @return
     *      true if the project is actually added to the queue.
     *      false if the queue contained it and therefore the add()
     *      was noop
     */
    public synchronized boolean add( AbstractProject p ) {
        if(contains(p))
            return false; // no double queueing

        // put the item in the queue
        Calendar due = new GregorianCalendar();
        due.add(Calendar.SECOND, p.getQuietPeriod());
        queue.add(new Item(due,p));

        scheduleMaintenance();   // let an executor know that a new item is in the queue.
        return true;
    }

    public synchronized void cancel( AbstractProject<?,?> p ) {
        for (Iterator itr = queue.iterator(); itr.hasNext();) {
            Item item = (Item) itr.next();
            if(item.project==p) {
                itr.remove();
                return;
            }
        }
        blockedProjects.remove(p);
        buildables.remove(p);
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty() && blockedProjects.isEmpty() && buildables.isEmpty();
    }

    private synchronized Item peek() {
        return queue.iterator().next();
    }

    /**
     * Gets a snapshot of items in the queue.
     */
    public synchronized Item[] getItems() {
        Item[] r = new Item[queue.size()+blockedProjects.size()+buildables.size()];
        queue.toArray(r);
        int idx=queue.size();
        Calendar now = new GregorianCalendar();
        for (AbstractProject p : blockedProjects) {
            r[idx++] = new Item(now, p, true, false);
        }
        for (AbstractProject p : buildables) {
            r[idx++] = new Item(now, p, false, true);
        }
        return r;
    }

    /**
     * Returns true if this queue contains the said project.
     */
    public synchronized boolean contains(AbstractProject p) {
        if(blockedProjects.contains(p) || buildables.contains(p))
            return true;
        for (Item item : queue) {
            if (item.project == p)
                return true;
        }
        return false;
    }

    /**
     * Called by the executor to fetch something to build next.
     *
     * This method blocks until a next project becomes buildable.
     */
    public AbstractProject pop() throws InterruptedException {
        final Executor exec = Executor.currentExecutor();

        // used in the finally block to check if we are returning from this method normally
        // or abnormally via an exception
        boolean successfulReturn = false;

        try {
            while(true) {
                final JobOffer offer = new JobOffer(exec);
                long sleep = -1;

                synchronized(this) {
                    // consider myself parked
                    assert !parked.containsKey(exec);
                    parked.put(exec,offer);

                    // reuse executor thread to do a queue maintenance.
                    // at the end of this we get all the buildable jobs
                    // in the buildables field.
                    maintain();

                    // allocate buildable jobs to executors
                    Iterator<AbstractProject> itr = buildables.iterator();
                    while(itr.hasNext()) {
                        AbstractProject p = itr.next();

                        // one last check to make sure this build is not blocked.
                        if(p.isBuildBlocked()) {
                            itr.remove();
                            blockedProjects.add(p);
                            continue;
                        }
                        
                        JobOffer runner = choose(p);
                        if(runner==null)
                            // if we couldn't find the executor that fits,
                            // just leave it in the buildables list and
                            // check if we can execute other projects
                            continue;

                        // found a matching executor. use it.
                        runner.set(p);
                        itr.remove();
                    }

                    // we went over all the buildable projects and awaken
                    // all the executors that got work to do. now, go to sleep
                    // until this thread is awakened. If this executor assigned a job to
                    // itself above, the block method will return immediately.

                    if(!queue.isEmpty()) {
                        // wait until the first item in the queue is due
                        sleep = peek().timestamp.getTimeInMillis()-new GregorianCalendar().getTimeInMillis();
                        if(sleep <100)    sleep =100;    // avoid wait(0)
                    }
                }

                // this needs to be done outside synchronized block,
                // so that executors can maintain a queue while others are sleeping
                if(sleep ==-1)
                    offer.event.block();
                else
                    offer.event.block(sleep);

                synchronized(this) {
                    // am I woken up because I have a project to build?
                    if(offer.project!=null) {
                        // if so, just build it
                        successfulReturn = true;
                        return offer.project;
                    }
                    // otherwise run a queue maintenance
                }
            }
        } finally {
            synchronized(this) {
                // remove myself from the parked list
                JobOffer offer = parked.get(exec);
                if(offer!=null) {
                    if(!successfulReturn && offer.project!=null) {
                        // we are already assigned a project,
                        // ask for someone else to build it.
                        // note that while this thread is waiting for CPU
                        // someone else can schedule this build again.
                        if(!contains(offer.project))
                            buildables.add(offer.project);
                    }

                    // since this executor might have been chosen for
                    // maintenance, schedule another one. Worst case
                    // we'll just run a pointless maintenance, and that's
                    // fine.
                    scheduleMaintenance();
                }
            }
        }
    }

    /**
     * Chooses the executor to carry out the build for the given project.
     *
     * @return
     *      null if no {@link Executor} can run it.
     */
    private JobOffer choose(AbstractProject<?,?> p) {
        if(Hudson.getInstance().isQuietingDown()) {
            // if we are quieting down, don't run anything so that
            // all executors will be free.
            return null;
        }

        Node n = p.getAssignedNode();
        if(n!=null) {
            // if a project has assigned node, it can be only built on it
            for (JobOffer offer : parked.values()) {
                if(offer.isAvailable() && offer.getNode()==n)
                    return offer;
            }
            return null;
        }

        // otherwise let's see if the last node where this project was built is available
        // it has up-to-date workspace, so that's usually preferable.
        // (but we can't use an exclusive node)
        n = p.getLastBuiltOn();
        if(n!=null && n.getMode()==Mode.NORMAL) {
            for (JobOffer offer : parked.values()) {
                if(offer.isAvailable() && offer.getNode()==n)
                    return offer;
            }
        }

        // duration of a build on a slave tends not to have an impact on
        // the master/slave communication, so that means we should favor
        // running long jobs on slaves.
        AbstractBuild succ = p.getLastSuccessfulBuild();
        if(succ!=null && succ.getDuration()>15*60*1000) {
            // consider a long job to be > 15 mins
            for (JobOffer offer : parked.values()) {
                if(offer.isAvailable() && offer.getNode() instanceof Slave && offer.isNotExclusive())
                    return offer;
            }
        }

        // lastly, just look for any idle executor
        for (JobOffer offer : parked.values()) {
            if(offer.isAvailable() && offer.isNotExclusive())
                return offer;
        }

        // nothing available
        return null;
    }

    /**
     * Checks the queue and runs anything that can be run.
     *
     * <p>
     * When conditions are changed, this method should be invoked.
     *
     * This wakes up one {@link Executor} so that it will maintain a queue.
     */
    public synchronized void scheduleMaintenance() {
        // this code assumes that after this method is called
        // no more executors will be offered job except by
        // the pop() code.
        for (Entry<Executor, JobOffer> av : parked.entrySet()) {
            if(av.getValue().project==null) {
                av.getValue().event.signal();
                return;
            }
        }
    }


    /**
     * Queue maintenance.
     *
     * Move projects between {@link #queue}, {@link #blockedProjects}, and {@link #buildables}
     * appropriately.
     */
    private synchronized void maintain() {
        Iterator<AbstractProject> itr = blockedProjects.iterator();
        while(itr.hasNext()) {
            AbstractProject p = itr.next();
            if(!p.isBuildBlocked()) {
                // ready to be executed
                itr.remove();
                buildables.add(p);
            }
        }

        while(!queue.isEmpty()) {
            Item top = peek();

            if(!top.timestamp.before(new GregorianCalendar()))
                return; // finished moving all ready items from queue

            AbstractProject p = top.project;
            if(!p.isBuildBlocked()) {
                // ready to be executed immediately
                queue.remove(top);
                buildables.add(p);
            } else {
                // this can't be built now because another build is in progress
                // set this project aside.
                queue.remove(top);
                blockedProjects.add(p);
            }
        }
    }

    /**
     * Item in a queue.
     */
    public class Item implements Comparable<Item> {
        /**
         * This item can be run after this time.
         */
        public final Calendar timestamp;

        /**
         * Project to be built.
         */
        public final AbstractProject<?,?> project;

        /**
         * Unique number of this {@link Item}.
         * Used to differentiate {@link Item}s with the same due date.
         */
        public final int id;

        /**
         * Build is blocked because another build is in progress.
         * This flag is only used in {@link Queue#getItems()} for
         * 'pseudo' items that are actually not really in the queue.
         */
        public final boolean isBlocked;

        /**
         * Build is waiting the executor to become available.
         * This flag is only used in {@link Queue#getItems()} for
         * 'pseudo' items that are actually not really in the queue.
         */
        public final boolean isBuildable;

        public Item(Calendar timestamp, AbstractProject project) {
            this(timestamp,project,false,false);
        }

        public Item(Calendar timestamp, AbstractProject project, boolean isBlocked, boolean isBuildable) {
            this.timestamp = timestamp;
            this.project = project;
            this.isBlocked = isBlocked;
            this.isBuildable = isBuildable;
            synchronized(Queue.this) {
                this.id = iota++;
            }
        }

        /**
         * Gets a human-readable status message describing why it's in the queu.
         */
        public String getWhy() {
            if(isBuildable) {
                Node node = project.getAssignedNode();
                Hudson hudson = Hudson.getInstance();
                if(node==hudson && hudson.getSlaves().isEmpty())
                    node = null;    // no master/slave. pointless to talk about nodes

                String name = null;
                if(node!=null) {
                    if(node==hudson)
                        name = "master";
                    else
                        name = node.getNodeName();
                }

                return "Waiting for next available executor"+(name==null?"":" on "+name);
            }

            if(isBlocked) {
                AbstractBuild<?, ?> build = project.getLastBuild();
                Executor e = build.getExecutor();
                String eta="";
                if(e!=null)
                    eta = " (ETA:"+e.getEstimatedRemainingTime()+")";
                int lbn = build.getNumber();
                return "Build #"+lbn+" is already in progress"+eta;
            }

            long diff = timestamp.getTimeInMillis() - System.currentTimeMillis();
            if(diff>0) {
                return "In the quiet period. Expires in "+ Util.getTimeSpanString(diff);
            }

            return "???";
        }

        public int compareTo(Item that) {
            int r = this.timestamp.getTime().compareTo(that.timestamp.getTime());
            if(r!=0)    return r;

            return this.id-that.id;
        }

    }

    /**
     * Unique number generator
     */
    private int iota=0;

    private static final Logger LOGGER = Logger.getLogger(Queue.class.getName());
}
