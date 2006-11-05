package hudson.model;

/**
 * Marker interface for {@link Action}s that should be displayed
 * at the top of the project page.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ProminentProjectAction extends Action {
    // TODO: do the rendering of the part from the action page
}
