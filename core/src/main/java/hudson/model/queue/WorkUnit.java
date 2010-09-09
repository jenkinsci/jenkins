package hudson.model.queue;

import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Queue.ExecutionUnit;
import hudson.model.Queue.FutureImpl;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;

import java.util.List;

/**
 * Represents a unit of hand-over to {@link Executor} from {@link Queue}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class WorkUnit {
    /**
     * Task to be executed.
     */
    public final ExecutionUnit work;

    /**
     * Which task does this work belong to?
     */
    public final Task task;

    /**
     * Associated parameters to the build.
     */
    public final List<Action> actions;

    /**
     * Once the execution is complete, update this future object with the outcome.
     */
    public final FutureImpl future;

    public final WorkUnitContext context;

    public WorkUnit(Item item, ExecutionUnit work) {
        this.task = item.task;
        this.future = item.future; // TODO: this is incorrect
        this.actions = item.getActions();
        this.work = work;
    }

    /**
     * Is this work unit the "main work", which is the primary {@link ExecutionUnit}
     * represented by {@link Task} itself.
     */
    public boolean isMainWork() {
        return task==work;
    }
}
