package jenkins.search;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.search.SearchIndex;
import hudson.search.SearchableModelObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

@Restricted(Beta.class)
public interface SearchScope extends SearchableModelObject {

    @NonNull
    SearchIndex getScopedSearchIndex();
}
