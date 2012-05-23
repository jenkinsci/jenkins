package hudson.matrix;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class MatrixRunCheckoutStrategyDescriptor extends Descriptor<MatrixRunCheckoutStrategy> {

    protected MatrixRunCheckoutStrategyDescriptor(Class<? extends MatrixRunCheckoutStrategy> clazz) {
        super(clazz);
    }

    protected MatrixRunCheckoutStrategyDescriptor() {
    }

    /**
     * Returns all the registered {@link MatrixExecutionStrategyDescriptor}s.
     */
    public static DescriptorExtensionList<MatrixRunCheckoutStrategy,MatrixRunCheckoutStrategyDescriptor> all() {
        return Jenkins.getInstance().<MatrixRunCheckoutStrategy,MatrixRunCheckoutStrategyDescriptor>getDescriptorList(MatrixRunCheckoutStrategy.class);
    }

}
