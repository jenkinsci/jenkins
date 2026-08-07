package jenkins.model;

import hudson.Util;
import hudson.model.ModelObject;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
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
    default ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        if (Util.isOverridden(ModelObjectWithChildren.class, getClass(), "doChildrenContextMenu", StaplerRequest.class, StaplerResponse.class)) {
            return doChildrenContextMenu(StaplerRequest.fromStaplerRequest2(request), StaplerResponse.fromStaplerResponse2(response));
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + ModelObjectWithChildren.class.getSimpleName() + "." + "doChildrenContextMenu" + " methods");
        }
    }

    /**
     * @deprecated use {@link #doChildrenContextMenu(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    default ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        if (Util.isOverridden(ModelObjectWithChildren.class, getClass(), "doChildrenContextMenu", StaplerRequest2.class, StaplerResponse2.class)) {
            return doChildrenContextMenu(StaplerRequest.toStaplerRequest2(request), StaplerResponse.toStaplerResponse2(response));
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + ModelObjectWithChildren.class.getSimpleName() + "." + "doChildrenContextMenu" + " methods");
        }
    }
}
