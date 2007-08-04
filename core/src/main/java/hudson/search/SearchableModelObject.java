package hudson.search;

import hudson.model.ModelObject;

/**
 * {@link ModelObject} that has {@link SearchIndex}.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface SearchableModelObject extends ModelObject {
    SearchIndex getSearchIndex();
}
