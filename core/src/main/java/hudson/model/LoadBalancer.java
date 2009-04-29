package hudson.model;

import hudson.model.Node.Mode;
import hudson.model.Queue.ApplicableJobOfferList;
import hudson.model.Queue.JobOffer;
import hudson.model.Queue.Task;
import hudson.util.ConsistentHash;
import hudson.util.ConsistentHash.Hash;

import java.util.logging.Logger;

/**
 * Strategy that decides which {@link Task} gets run on which {@link Executor}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.301
 */
public abstract class LoadBalancer /*implements ExtensionPoint*/ {
    /**
     * Chooses the executor to carry out the build for the given project.
     *
     * <p>
     * This method is invoked from different threads, but the execution is serialized by the caller.
     * The thread that invokes this method always holds a lock to {@link Queue}, so queue contents
     * can be safely introspected from this method, if that information is necessary to make
     * decisions.
     * 
     * @param applicable
     *      The list of {@link JobOffer}s that represent {@linkplain JobOffer#isAvailable() available} {@link Executor}s, from which
     *      the callee can choose. Never null.
     * @param  task
     *      The task whose execution is being considered. Never null.
     *
     * @return
     *      Pick one of the items from {@code available}, and return it. How you choose it
     *      is the crucial part of the implementation. Return null if you don't want
     *      the task to be executed right now, in which case this method will be called
     *      some time later with the same task.
     */
    protected abstract JobOffer choose(Task task, ApplicableJobOfferList applicable);

    /**
     * Traditional implementation of this.
     */
    public static final LoadBalancer DEFAULT = new LoadBalancer() {
        protected JobOffer choose(Task task, ApplicableJobOfferList applicable) {
            Label l = task.getAssignedLabel();
            if (l != null) {
                // if a project has assigned label, it can be only built on it
                for (JobOffer offer : applicable) {
                    if (l.contains(offer.getNode()))
                        return offer;
                }
                return null;
            }

            // if we are a large deployment, then we will favor slaves
            boolean isLargeHudson = Hudson.getInstance().getNodes().size() > 10;

            // otherwise let's see if the last node where this project was built is available
            // it has up-to-date workspace, so that's usually preferable.
            // (but we can't use an exclusive node)
            Node n = task.getLastBuiltOn();
            if (n != null && n.getMode() == Mode.NORMAL) {
                for (JobOffer offer : applicable) {
                    if (offer.getNode() == n) {
                        if (isLargeHudson && offer.getNode() instanceof Slave)
                            // but if we are a large Hudson, then we really do want to keep the master free from builds
                            continue;
                        return offer;
                    }
                }
            }

            // duration of a build on a slave tends not to have an impact on
            // the master/slave communication, so that means we should favor
            // running long jobs on slaves.
            // Similarly if we have many slaves, master should be made available
            // for HTTP requests and coordination as much as possible
            if (isLargeHudson || task.getEstimatedDuration() > 15 * 60 * 1000) {
                // consider a long job to be > 15 mins
                for (JobOffer offer : applicable) {
                    if (offer.getNode() instanceof Slave && offer.isNotExclusive())
                        return offer;
                }
            }

            // lastly, just look for any idle executor
            for (JobOffer offer : applicable) {
                if (offer.isNotExclusive())
                    return offer;
            }

            // nothing available
            return null;
        }
    };

    /**
     * Work in progress implementation that uses a consistent hash for scheduling.
     */
    public static final LoadBalancer CONSISTENT_HASH = new LoadBalancer() {
        protected JobOffer choose(Task task, ApplicableJobOfferList applicable) {
            // populate a consistent hash linear to the # of executors
            // TODO: there's a lot of room for innovations here
            // TODO: do this upfront and reuse the consistent hash
            ConsistentHash<Node> hash = new ConsistentHash<Node>(new Hash<Node>() {
                public String hash(Node node) {
                    return node.getNodeName();
                }
            });
            for (Node n : applicable.nodes())
                hash.add(n,n.getNumExecutors()*100);

            // TODO: add some salt as a query point so that the user can tell Hudson to hop the project to a new node
            for(Node n : hash.list(task.getFullDisplayName())) {
                JobOffer o = applicable._for(n);
                if(o!=null)
                    return o;
            }

            // nothing available
            return null;
        }
    };

    /**
     * Wraps this {@link LoadBalancer} into a decorator that tests the basic sanity of the implementation.
     * Only override this if you find some of the checks excessive, but beware that it's like driving without a seat belt.
     */
    protected LoadBalancer sanitize() {
        final LoadBalancer base = this;
        return new LoadBalancer() {
            @Override
            protected JobOffer choose(Task task, ApplicableJobOfferList applicable) {
                if (Hudson.getInstance().isQuietingDown()) {
                    // if we are quieting down, don't start anything new so that
                    // all executors will be eventually free.
                    return null;
                }

                return base.choose(task, applicable);
            }

            /**
             * Double-sanitization is pointless.
             */
            @Override
            protected LoadBalancer sanitize() {
                return this;
            }

            private final Logger LOGGER = Logger.getLogger(LoadBalancer.class.getName());
        };
    }

}
