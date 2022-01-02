package jenkins.model;

import hudson.Functions;
import hudson.model.ModelObject;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link ModelObject} that has a page menu in the breadcrumb bar or menu in its page link
 *
 * <p>
 * The page context menu is accessible to the user via the breadcrumb bar or via a link
 * to the object's page. The page menu combines the page's contextMenu and childrenContextMenu,
 * making it easier for users to navigate to all the ancestor objects this model object owns,
 * and its children.
 *
 * @author Jan Faracik, Lewis Birks
 * @see ModelObjectWithContextMenu
 * @since TODO - Provide version
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
