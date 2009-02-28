package hudson.model;

import hudson.model.Queue.Task;

import java.util.List;

/**
 * @author mdonohue
 * An action interface that allows action data to be folded together.
 * This is useful for combining any distinct values from a build determined to 
 * be a duplicate of a build already in the build queue.
 */
public interface FoldableAction extends Action {
	public void foldIntoExisting(Task t, List<Action> actions);
}
