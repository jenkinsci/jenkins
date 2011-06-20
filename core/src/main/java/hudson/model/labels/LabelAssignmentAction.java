package hudson.model.labels;

import hudson.model.Action;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.queue.SubTask;

/**
 * {@link Action} that can be submitted to {@link Queue} that controls where
 * the task runs.
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
