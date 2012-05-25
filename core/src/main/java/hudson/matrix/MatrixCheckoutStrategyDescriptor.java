package hudson.matrix;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class MatrixCheckoutStrategyDescriptor extends Descriptor<MatrixCheckoutStrategy> {

    protected MatrixCheckoutStrategyDescriptor(Class<? extends MatrixCheckoutStrategy> clazz) {
        super(clazz);
    }

    protected MatrixCheckoutStrategyDescriptor() {
    }

    /**
     * Returns all the registered {@link MatrixExecutionStrategyDescriptor}s.
     */
    public static DescriptorExtensionList<MatrixCheckoutStrategy,MatrixCheckoutStrategyDescriptor> all() {
        return Jenkins.getInstance().<MatrixCheckoutStrategy,MatrixCheckoutStrategyDescriptor>getDescriptorList(MatrixCheckoutStrategy.class);
    }

}
