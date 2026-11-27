package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
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
        SearchGroup group = SearchGroup.get(SearchGroup.ManagementLinkSearchGroup.class);
        return ManagementLink.all().stream()
                .filter(ManagementLinkSearchItem::isSearchable)
                .map(link -> new ManagementLinkSearchItem(link, getUrl(link.getUrlName()), group))
                .toList();
    }

    private static String getUrl(String urlName) {
        if (urlName.startsWith("/")) {
            return urlName;
        }
        return "manage/" + urlName;
    }
}
