package hudson.model;

/**
 * Marker interface for {@link Action}s that should be displayed
 * at the top of the project page.
 *
 * {@link #getIconFileName()}, {@link #getUrlName()}, {@link #getDisplayName()}
 * are used to create a large, more visible icon in the top page to draw
 * users' attention.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ProminentProjectAction extends Action {
    // TODO: do the rendering of the part from the action page
}
