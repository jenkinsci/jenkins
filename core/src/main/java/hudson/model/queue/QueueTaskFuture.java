package hudson.model.queue;

import hudson.model.Queue.Executable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * {@link Future} that can be used to wait for the start and the end of the task execution
 * (such as a build.)
 *
 * <p>
 * For a historical reason, this object itself extends from {@link Future} to signal the
 * end of the task execution, and {@link #getStartCondition()} returns a separate
 * {@link Future} object that waits for the start of the task.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.469
 */
public interface QueueTaskFuture<R extends Executable> extends Future<R> {
    /**
     * Returns a {@link Future} object that can be used to wait for the start of the task execution.
     *
     * @return
     *      never return null.
     */
    Future<R> getStartCondition();

    /**
     * Short for {@code getStartCondition().get()}
     */
    R waitForStart() throws InterruptedException, ExecutionException;

}
