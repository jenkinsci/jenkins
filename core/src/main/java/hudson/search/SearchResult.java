package hudson.search;

import java.util.List;

/**
 * @author <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface SearchResult extends List<SuggestedItem> {

    boolean hasMoreResults();

}
