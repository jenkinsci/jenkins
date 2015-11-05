package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension point for adding transient {@link Action}s to {@link View}s.
 *
 * @since 1.388
 */
public abstract class TransientViewActionFactory implements ExtensionPoint {

	/**
	 * returns a list of (transient) actions never null, may be empty
	 * 
	 * @param v
	 */
	public abstract List<Action> createFor(View v);
	
    /**
     * Returns all the registered {@link TransientViewActionFactory}s.
     */
	public static ExtensionList<TransientViewActionFactory> all() {
		return ExtensionList.lookup(TransientViewActionFactory.class);
	}
	
    /**
     * Creates {@link Action}s for a view, using all registered {@link TransientViewActionFactory}s.
     */
	public static List<Action> createAllFor(View v) {
		List<Action> result = new ArrayList<Action>();
		for (TransientViewActionFactory f: all()) {
			result.addAll(f.createFor(v));
		}
		return result;
	}

}
