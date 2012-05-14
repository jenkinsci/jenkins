package hudson.model;

import hudson.DescriptorExtensionList;
import hudson.matrix.MatrixExecutionStrategyDescriptor;
import jenkins.model.Jenkins;

public abstract class BuildCheckoutStrategyDescriptor extends Descriptor<BuildCheckoutStrategy> {
	
	protected BuildCheckoutStrategyDescriptor(Class<? extends BuildCheckoutStrategy> clazz) {
        super(clazz);
    }

    protected BuildCheckoutStrategyDescriptor() {
    }

    /**
     * Returns all the registered {@link BuildCheckoutStrategyDescriptor}s.
     */
    public static DescriptorExtensionList<BuildCheckoutStrategy,BuildCheckoutStrategyDescriptor> all() {
        return Jenkins.getInstance().<BuildCheckoutStrategy,BuildCheckoutStrategyDescriptor>getDescriptorList(BuildCheckoutStrategy.class);
    }

}
