package hudson.model;

/**
 * Partial {@link Action} implementation that doesn't have any UI presence.
 *
 * <p>
 * This class can be used as a convenient base class, when you use
 * {@link Action} for just storing data associated with a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.188
 */
public abstract class InvisibleAction implements Action {
    public final String getIconFileName() {
        return null;
    }

    public final String getDisplayName() {
        return null;
    }

    public final String getUrlName() {
        return null;
    }
}
