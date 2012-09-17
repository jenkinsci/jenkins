package hudson.model.labels;

import hudson.model.Action;
import hudson.model.Label;
import hudson.model.LoadBalancer;
import hudson.model.Queue;
import hudson.model.Queue.QueueDecisionHandler;
import hudson.model.Queue.Task;
import hudson.model.queue.SubTask;

/**
 * {@link Action} that can be submitted to {@link Queue} that controls where
 * the task runs.
 *
 * <h2>Where to insert {@link LabelAssignmentAction}s</h2>
 * <p>
 * If you control when the task gets submitted to the queue, you can associate this action
 * to the task by passing it as a parameter to method like {@link Queue#schedule(Task, int, Action...)}.
 *
 * <p>
 * If you want to globally affect the scheduling decision, you can do so by {@link QueueDecisionHandler}
 * and alter the list of actions that you get. Alternatively, you can implement your own {@link LoadBalancer}
 * and bypass the whole label/assignment mechanism to control the decision into your own hands.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.416
 */
public interface LabelAssignmentAction extends Action {
    /**
     * Reassigns where the task gets run.
     *
     * @param task
     *      Never null.
     * @return
     *      null to let other {@link LabelAssignmentAction}s take control, eventually to {@code SubTask#getAssignedLabel()}.
     *      If non-null value is returned, that label will be authoritative.
     */
    Label getAssignedLabel(SubTask task);
}
