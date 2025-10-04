package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.management.Badge;

/**
 * Represents an entity that can display a {@link Badge}.
 * <p>
 * Implementations of this interface may provide a badge
 * to be shown on an associated action or UI element.
 * If no badge is provided, {@code null} may be returned.
 * </p>
 *
 * @since TODO
 */
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
