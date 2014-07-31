package jenkins.plugins.ui_samples;

import hudson.Extension;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NavigationContextMenu extends UISample implements ModelObjectWithContextMenu, ModelObjectWithChildren {
    @Override
    public String getDescription() {
        return "Integrate with navigational context menu to provider quick access around object graph";
    }

    /**
     * This method is called via AJAX to obtain the context menu for this model object.
     */
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        if (false) {
            // this implementation is suffice for most ModelObjects. It uses sidepanel.jelly/.groovy to
            // generate the context menu
            return new ContextMenu().from(this,request,response);
        } else {
            // otherwise you can also programatically create them.
            // see the javadoc for various convenience methods to add items
            return new ContextMenu()
                    .add("http://jenkins-ci.org/","Jenkins project")
                    .add("http://www.cloudbees.com/","CloudBees")
                    .add(new MenuItem().withContextRelativeUrl("/").withStockIcon("gear.png").withDisplayName("top page"));
        }
    }

    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        // You implement this method in much the same way you do doContextMenu
        return new ContextMenu()
                .add("http://yahoo.com/","Yahoo")
                .add("http://google.com/","Google")
                .add("http://microsoft.com/","Microsoft");
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}


