package jenkins.views;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.management.Badge;
import org.jenkins.ui.icon.IconSpec;

public class HeaderAction implements IconSpec {

    private final @NonNull String displayName;

    private final @NonNull String iconClassName;

    private final String url;

    private final Badge badge;

    public HeaderAction(@NonNull String displayName, @NonNull String iconClassName, String url, Badge badge) {
        this.displayName = displayName;
        this.iconClassName = iconClassName;
        this.url = url;
        this.badge = badge;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    public String getUrl() {
        return url;
    }

    public Badge getBadge() {
        return badge;
    }

    @Override
    @NonNull
    public String getIconClassName() {
        return iconClassName;
    }
}
