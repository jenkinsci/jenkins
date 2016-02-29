package hudson.model.queue;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Queue;
import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.BuildableItem;
import hudson.model.Queue.Item;
import hudson.model.Queue.LeftItem;
import hudson.model.Queue.WaitingItem;

import java.util.concurrent.Executor;

/**
 * Listener for events in {@link Queue}.
 *
 * <p>
 * {@link Queue} is highly synchronized objects, and these callbacks are invoked synchronously.
 * To avoid the risk of deadlocks and general slow down, please minimize the amount of work callbacks
 * will perform, and push any sizable work to asynchronous execution via {@link Executor}, such as
 * {@link Computer#threadPoolForRemoting}.
 *
 * <p>
 * For the state transition of {@link hudson.model.Queue.Item} in {@link Queue}, please refer to the Queue javadoc.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
public abstract class QueueListener implements ExtensionPoint {
    /**
     * When a task is submitted to the queue, it first gets to the waiting phase,
     * where it spends until the quiet period runs out and the item becomes executable.
     *
     * @see WaitingItem#timestamp
     */
    public void onEnterWaiting(WaitingItem wi) {}

    /**
     * An item leaves the waiting phase when the current time of the system is past its
     * {@linkplain WaitingItem#timestamp due date}. The item will then enter either the blocked phase
     * or the buildable phase.
     */
    public void onLeaveWaiting(WaitingItem wi) {}

    /**
     * An item enters the blocked phase when there's someone saying "NO" to it proceeding to the buildable phase,
     * such as {@link QueueTaskDispatcher}. Note that waiting for an executor to become available is not a part of this.
     */
    public void onEnterBlocked(BlockedItem bi) {}

    /**
     * An item leaves the blocked phase and becomes buildable when there's no one vetoing.
     */
    public void onLeaveBlocked(BlockedItem bi) {}

    /**
     * An item enters the buildable phase when all signals are green (or blue!) and it's just waiting
     * for the scheduler to allocate it to the available executor. An item can spend considerable time
     * in this phase for example if all the executors are busy.
     */
    public void onEnterBuildable(BuildableItem bi) {}

    /**
     * An item leaves the buildable phase.
     *
     * It will move to the "left" state if the executors are allocated to it, or it will move to the
     * blocked phase if someone starts vetoing once again.
     */
    public void onLeaveBuildable(BuildableItem bi) {}

    /**
     * The item has left the queue, either by getting {@link Queue#cancel(Item) cancelled} or by getting
     * picked up by an executor and starts running.
     */
    public void onLeft(LeftItem li) {}

    /**
     * A task is going to run on assigned Executor.
     */
    public void onTaskExecuted(Runnable task) {}

    /**
     * Returns all the registered {@link QueueListener}s.
     */
    public static ExtensionList<QueueListener> all() {
        return ExtensionList.lookup(QueueListener.class);
    }

}
