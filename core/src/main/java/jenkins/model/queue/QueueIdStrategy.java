package jenkins.model.queue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Queue;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Pluggable strategy to generate queue item IDs as well as persist an optional opaque state whenever the queue is persisted.
 */
@Restricted(Beta.class)
public abstract class QueueIdStrategy implements ExtensionPoint {
    private static final QueueIdStrategy DEFAULT = new DefaultStrategy();

    public static QueueIdStrategy get() {
        var strategies = ExtensionList.lookup(QueueIdStrategy.class);
        if (strategies.isEmpty()) {
            return DEFAULT;
        }
        return strategies.get(0);
    }

    /**
     * Persist the state of this strategy.
     */
    public void persist(@NonNull Queue.State queueState) {}

    /**
     * Loads the state of this strategy from a persisted queue state.
     */
    public void load(@NonNull Queue.State queueState) {}

    /**
     * Generates a new ID for the given project and actions.
     * @param project The task to be queued.
     * @param actions The actions linked the task.
     * @return a new queue ID.
     */
    public abstract long generateIdFor(@NonNull Queue.Task project, @NonNull List<Action> actions);

    /**
     * Default implementation if no extension is found. Simply uses a counter.
     */
    public static final class DefaultStrategy extends QueueIdStrategy {
        private static final AtomicLong COUNTER = new AtomicLong(0);

        @Override
        public long generateIdFor(Queue.Task project, List<Action> actions) {
            return COUNTER.incrementAndGet();
        }

        @Override
        public void persist(Queue.State queueState) {
            queueState.properties.put(getClass().getName(), COUNTER.get());
        }

        @Override
        public void load(Queue.State queueState) {
            var prop = queueState.properties.get(getClass().getName());
            if (prop instanceof Long) {
                COUNTER.set((Long) prop);
            } else {
                queueState.items.stream()
                        .filter(Queue.Item.class::isInstance)
                        .map(Queue.Item.class::cast)
                        .max(Comparator.comparing(Queue.Item::getId))
                        .ifPresentOrElse(
                                maxItem -> COUNTER.set(maxItem.getId()),
                                () -> COUNTER.set(0)
                        );
            }
        }

        @Restricted(DoNotUse.class) // testing only
        public static long getCurrentCounterValue() {
            return COUNTER.get();
        }
    }
}
