package jenkins.search;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.search.SearchItem;
import java.util.Collection;

/**
 * Contributes items to the root search index, so they can be found from the command palette
 * on any page.
 * <p>
 * Item URLs are resolved relative to the Jenkins root URL.
 *
 * @since TODO
 */
public interface SearchItemProvider extends ExtensionPoint {

    @NonNull
    Collection<? extends SearchItem> getSearchItems();

    static ExtensionList<SearchItemProvider> all() {
        return ExtensionList.lookup(SearchItemProvider.class);
    }
}
