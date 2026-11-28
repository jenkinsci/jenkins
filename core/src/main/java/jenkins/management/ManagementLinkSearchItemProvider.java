package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.search.SearchItem;
import java.util.Collection;
import java.util.Objects;
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
                .map(link -> new ManagementLinkSearchItem(link, getUrl(link), group))
                .toList();
    }

    private static String getUrl(ManagementLink link) {
        String urlName = Objects.requireNonNull(link.getUrlName());
        if (urlName.startsWith("/")) {
            return urlName;
        }
        return "manage/" + urlName;
    }
}
