package hudson.model;

import hudson.ExtensionPoint;
import hudson.Extension;

/**
 * Marker interface for actions that are added to {@link Hudson}.
 *
 * <p>
 * Extend from this interface and put {@link Extension} on your subtype
 * to have them auto-registered to {@link Hudson}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.311
 */
public interface RootAction extends Action, ExtensionPoint {
}
