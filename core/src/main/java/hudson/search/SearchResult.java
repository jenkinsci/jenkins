package hudson.search;

import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface SearchResult extends List<SuggestedItem> {

    boolean hasMoreResults();

}
