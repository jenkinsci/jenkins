package hudson.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * Enable to add a button action to a build main page. After click on the button is called method doAction(AbstractBuild build) which performs action
 * 
 * @author Lucie Votypkova
 */
public abstract class BuildButtonAction extends AbstractDescribableImpl<BuildButtonAction> implements ExtensionPoint {   
    
    /**
     *  Perform action after click on the button
     */
    public abstract void doAction(AbstractBuild build);

    @Override
    public BuildButtonActionDescriptor getDescriptor() {
        return (BuildButtonActionDescriptor) super.getDescriptor();
    }

    
    public static abstract class BuildButtonActionDescriptor extends Descriptor<BuildButtonAction> {

        public BuildButtonActionDescriptor(Class<? extends BuildButtonAction> clazz) {
            super(clazz);
        }

        public BuildButtonActionDescriptor() {
        }

        /**
         *  Return true if the button can be used.
         */
        public abstract boolean isAvailable(AbstractBuild build);

        /**
         * Returns all the registered {@link BuildButtonActionDescriptor}s.
         */
        public static DescriptorExtensionList<BuildButtonAction, BuildButtonActionDescriptor> all() {
            return Jenkins.getInstance().<BuildButtonAction, BuildButtonActionDescriptor>getDescriptorList(BuildButtonAction.class);
        }
               
    }
}