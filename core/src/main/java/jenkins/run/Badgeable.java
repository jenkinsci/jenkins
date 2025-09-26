package jenkins.run;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.management.Badge;

public interface Badgeable {

    /**
     * A {@link Badge} shown on the action.
     *
     * @return badge or {@code null} if no badge should be shown.
     * @since TODO
     */
    default @CheckForNull Badge getBadge() {
        return null;
    }
}
