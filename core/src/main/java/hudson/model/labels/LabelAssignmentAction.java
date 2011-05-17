package hudson.model.labels;

import hudson.model.Action;
import hudson.model.Label;
import hudson.model.queue.SubTask;

/**
 * @author Kohsuke Kawaguchi
 */
public interface LabelAssignmentAction extends Action {
    Label getAssignedLabel(SubTask task);
}
