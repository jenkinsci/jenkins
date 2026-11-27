package jenkins.model.menu;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Functions;
import hudson.model.Action;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Helpers for showing {@link Action}s in menus, such as the overflow menu on the app bar.
 */
@Restricted(NoExternalUse.class)
public final class MenuActions {

    private MenuActions() {}

    /**
     * Whether the action has an icon, either as an icon file name or, for an {@link IconSpec}, an icon class name.
     *
     * @param action the action to check
     * @return {@code true} if the action has an icon
     */
    public static boolean hasIcon(Action action) {
        return action.getIconFileName() != null
                || (action instanceof IconSpec iconSpec && iconSpec.getIconClassName() != null);
    }

    /**
     * The icon to show for the action in a menu.
     * Prefers the icon class name of an {@link IconSpec}, otherwise uses the icon file name,
     * translating a legacy image icon to its Jenkins Symbol when one exists.
     *
     * @param action the action to get the icon for
     * @return the icon, or {@code null} if the action has none
     */
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
