package jenkins.management;

import hudson.model.ManagementLink;
import hudson.search.SearchIndex;
import hudson.search.SearchItem;
import jenkins.search.SearchGroup;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link ManagementLink} as a search result.
 */
@Restricted(NoExternalUse.class)
public record ManagementLinkSearchItem(ManagementLink link, String url, SearchGroup group) implements SearchItem {

    public static boolean isSearchable(ManagementLink link) {
        return link.getIconFileName() != null
                && link.getUrlName() != null
                && !link.getRequiresPOST()
                && !link.getRequiresConfirmation()
                && link.hasRequiredPermission();
    }

    @Override
    public String getSearchName() {
        return link.getDisplayName();
    }

    @Override
    public String getSearchUrl() {
        return url;
    }

    @Override
    public String getSearchDescription() {
        return link.getSearchDescription();
    }

    @Override
    public String getSearchIcon() {
        return link.getIconFileName();
    }

    @Override
    public SearchGroup getSearchGroup() {
        return group;
    }

    @Override
    public SearchIndex getSearchIndex() {
        return SearchIndex.EMPTY;
    }
}
