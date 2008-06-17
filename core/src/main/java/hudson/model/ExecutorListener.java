package hudson.model;

/**
 * A listener for task related events from Executors
*
* @author Stephen Connolly
* @since 17-Jun-2008 18:58:12
*/
public interface ExecutorListener {

    /**
     * Called whenever a task is accepted by an executor.
     * @param executor The executor.
     * @param task The task.
     */
    void taskAccepted(Executor executor, Queue.Task task);

    /**
     * Called whenever a task is completed without any problems by an executor.
     * @param executor The executor.
     * @param task The task.
     * @param durationMS The number of milliseconds that the task took to complete.
     */
    void taskCompleted(Executor executor, Queue.Task task, long durationMS);

    /**
     * Called whenever a task is completed without any problems by an executor.
     * @param executor The executor.
     * @param task The task.
     * @param durationMS The number of milliseconds that the task took to complete.
     * @param problems The exception that was thrown.
     */
    void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems);
}
