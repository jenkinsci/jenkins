package hudson.search;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 999)
public class SearchGroup {

    private final String displayName;

    public SearchGroup(String displayName) {
        this.displayName = displayName;
    }

    @Exported
    public String getDisplayName() {
        return displayName;
    }

    public static final SearchGroup VIEW = new SearchGroup(Messages.SearchGroup_views());

    public static final SearchGroup BUILD = new SearchGroup(Messages.SearchGroup_builds());

    public static final SearchGroup COMPUTER = new SearchGroup(Messages.SearchGroup_nodes());

    public static final SearchGroup PROJECT = new SearchGroup(Messages.SearchGroup_projects());

    public static final SearchGroup PEOPLE = new SearchGroup(Messages.SearchGroup_people());

    public static final SearchGroup OTHER = new SearchGroup(Messages.SearchGroup_other());
}
