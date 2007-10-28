package hudson.model;

/**
 * {@link Action} that puts a little icon (or icons)
 * next to the build in the build history.
 *
 * <p>
 * This can be implemented by {@link Action}s that associate themselves
 * with {@link Run}. 
 *
 * <p>
 * Actions with this marker should have a view <tt>badge.jelly</tt>,
 * which will be called to render the badges. The expected visual appearance
 * of a badge is a 16x16 icon.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.150
 */
public interface BuildBadgeAction extends Action {
}
