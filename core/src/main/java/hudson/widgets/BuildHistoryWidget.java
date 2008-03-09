package hudson.widgets;

import hudson.model.Hudson;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;

/**
 * Displays the build history on the side panel.
 *
 * <p>
 * This widget enhances {@link HistoryWidget} by groking the notion
 * that {@link #owner} can be in the queue toward the next build.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildHistoryWidget<T> extends HistoryWidget<Task,T> {
    /**
     * @param owner
     *      The parent model object that owns this widget.
     */
    public BuildHistoryWidget(Task owner, Iterable<T> baseList,Adapter<? super T> adapter) {
        super(owner,baseList, adapter);
    }

    /**
     * Returns the queue item if the owner is scheduled for execution in the queue.
     */
    public Item getQueuedItem() {
        return Hudson.getInstance().getQueue().getItem(owner);
    }
}
