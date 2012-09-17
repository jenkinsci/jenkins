package jenkins.plugins.ui_samples;

import hudson.Extension;
import jenkins.model.ModelObjectWithContextMenu;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NavigationContextMenu extends UISample implements ModelObjectWithContextMenu {
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
                    .add(request.getContextPath(),"/images/24x24/gear.png","top-page");
        }
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}


