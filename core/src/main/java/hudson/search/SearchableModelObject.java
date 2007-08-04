package hudson.search;

import hudson.model.ModelObject;

/**
 * {@link ModelObject} that has {@link SearchIndex}.
 *
 * <p>
 * This interface also extends {@link SearchItem} since
 * often {@link ModelObject}s form a natural tree structure,
 * and it's convenient for the model objects themselves to implement
 * the {@link SearchItem} for the edges that form this tree.
 *
 * @author Kohsuke Kawaguchi
 */
public interface SearchableModelObject extends ModelObject, SearchItem {
}
