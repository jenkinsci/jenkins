package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.search.SearchIndex;
import hudson.search.SearchItem;
import java.util.Collection;
import jenkins.search.SearchGroup;
import jenkins.search.SearchItemProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class ManagementLinkSearchItemProvider implements SearchItemProvider {

    @NonNull
    @Override
    public Collection<? extends SearchItem> getSearchItems() {
        return ManagementLink.all().stream()
                .filter(link -> link.getIconFileName() != null && link.getUrlName() != null)
                .filter(link -> !link.getRequiresPOST() && !link.getRequiresConfirmation())
                .filter(ManagementLink::hasRequiredPermission)
                .map(link -> new ManagementLinkSearchItem(link, link.getUrlName()))
                .toList();
    }

    private record ManagementLinkSearchItem(ManagementLink link, String urlName) implements SearchItem {

        @Override
        public String getSearchName() {
            return link.getDisplayName();
        }

        @Override
        public String getSearchUrl() {
            if (urlName.startsWith("/")) {
                return urlName;
            }
            return "manage/" + urlName;
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
            return SearchGroup.get(SearchGroup.ManagementLinkSearchGroup.class);
        }

        @Override
        public SearchIndex getSearchIndex() {
            return SearchIndex.EMPTY;
        }
    }
}
