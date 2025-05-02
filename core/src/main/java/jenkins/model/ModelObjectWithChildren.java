package jenkins.model;

import hudson.model.ModelObject;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * {@link ModelObject} that has the children context menu in the breadcrumb.
 *
 * <p>
 * The children context menu is to show all the immediate children that this model object owns,
 * thereby providing quicker navigation to ancestors' siblings in the breadcrumb.
 *
 * @author Kohsuke Kawaguchi
 * @see ModelObjectWithContextMenu
 * @since 1.513
 */
public interface ModelObjectWithChildren extends ModelObject {
    /**
     * Generates the context menu to list up all the children.
     */
    ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception;
}
