package jenkins.model.menu;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Functions;
import hudson.model.Action;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public final class MenuActions {

    private MenuActions() {}

    public static boolean hasIcon(Action action) {
        return action.getIconFileName() != null
                || (action instanceof IconSpec iconSpec && iconSpec.getIconClassName() != null);
    }

    @CheckForNull
    public static String getIcon(Action action) {
        if (action instanceof IconSpec iconSpec && iconSpec.getIconClassName() != null) {
            return iconSpec.getIconClassName();
        }

        String icon = action.getIconFileName();
        Icon tangoIcon = Functions.tryGetIcon(icon);
        if (tangoIcon == null) {
            return icon;
        }
        return IconSet.tryTranslateTangoIconToSymbol(tangoIcon.getClassSpec());
    }
}
