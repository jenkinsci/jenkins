package jenkins.model;

import hudson.Functions;
import hudson.model.ModelObject;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link ModelObject} that has tTODO TODO TODO TODO TODO TODO TODO TODO
 *
 * <p>
 * The children context menu is to show all the immediate children that this model object owns,
 * thereby providing quicker navigation to ancestors' siblings in the breadcrumb.
 *
 * @author Jan Faracik
 * @see ModelObjectWithContextMenu
 * @since ????
 */
public interface ModelObjectWithPageMenu extends ModelObject {
    default ContextMenu doPageMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        ContextMenu contextMenu = new ContextMenu();

        if (Functions.isModelWithContextMenu(this)) {
            ModelObjectWithContextMenu modelObjectWithContextMenu = (ModelObjectWithContextMenu) this;
            contextMenu.items.addAll(modelObjectWithContextMenu.doContextMenu(request, response).items);
        }

        if (Functions.isModelWithChildren(this)) {
            ModelObjectWithChildren modelObjectWithChildren = (ModelObjectWithChildren) this;
            ContextMenu childrenMenu = modelObjectWithChildren.doChildrenContextMenu(request, response);

            if (!contextMenu.items.isEmpty() && !childrenMenu.items.isEmpty()) {
                contextMenu.addSeparator();
            }

            contextMenu.items.addAll(childrenMenu.items);
        }

        return contextMenu;
    }
}
