package hudson.model.queue;

import hudson.model.Action;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.model.Queue.WaitingItem;
import javax.annotation.CheckForNull;

/**
 * Result of {@link Queue#schedule2}
 *
 * @author Kohsuke Kawaguchi
 * @since 1.521
 * @see Queue#schedule(Task, int, Action...)
 */
public abstract class ScheduleResult {

    /**
     * If true, the {@link #getItem()} is newly created
     * as a result of {@link Queue#schedule2}.
     */
    public boolean isCreated() {
        return false;
    }

    /**
     * The scheduling of the task was refused and the queue didn't change.
     * If this method returns true, {@link #getItem()} will return null.
     */
    public boolean isRefused() {
        return false;
    }

    /**
     * Unless {@link #isRefused()} is true, this method either returns
     * the newly created item in the queue or the existing item that's already
     * in the queue that matched the submitted task.
     */
    public @CheckForNull Item getItem() {
        return null;
    }

    /**
     * If {@link #isCreated()} returns true, then this method returns
     * the newly created item, which is always of the type {@link WaitingItem}.
     */
    public @CheckForNull WaitingItem getCreateItem() {
        return null;
    }

    /**
     * Opposite of {@link #isRefused()}
     */
    public final boolean isAccepted() {
        return !isRefused();
    }

    public static final class Created extends ScheduleResult {
        private final WaitingItem item;
        private Created(WaitingItem item) {
            this.item = item;
        }

        @Override
        public boolean isCreated() {
            return true;
        }

        @Override
        public WaitingItem getCreateItem() {
            return item;
        }

        @Override
        public Item getItem() {
            return item;
        }
    }

    public static final class Existing extends ScheduleResult {
        private final Item item;

        private Existing(Item item) {
            this.item = item;
        }

        @Override
        public Item getItem() {
            return item;
        }
    }

    public static final class Refused extends ScheduleResult {
        @Override
        public boolean isRefused() {
            return true;
        }
    }

    public static Created created(WaitingItem i) {
        return new Created(i);
    }

    public static Existing existing(Item i) {
        return new Existing(i);
    }

    public static Refused refused() {
        return new Refused();
    }
}
