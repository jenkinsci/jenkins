package hudson.security;

import org.acegisecurity.userdetails.UserDetails;

/**
 * Represents the details of a group.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.280
 * @see UserDetails
 */
public abstract class GroupDetails {
    /**
     * Returns the name of the group.
     *
     * @return never null.
     */
    public abstract String getName();

    /**
     * Returns the human-readable name used for rendering in HTML.
     *
     * <p>
     * This may contain arbitrary character, and it can change.
     *
     * @return never null.
     */
    public String getDisplayName() {
        return getName();
    }
}
