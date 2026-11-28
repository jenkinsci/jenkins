package jenkins.search;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.search.SearchIndex;
import hudson.search.SearchableModelObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A {@link SearchableModelObject} the command palette can drill into, showing results from {@link #getScopedSearchIndex()}.
 */
@Restricted(Beta.class)
public interface SearchScope extends SearchableModelObject {

    @NonNull
    SearchIndex getScopedSearchIndex();
}
