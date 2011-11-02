package hudson.matrix;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class MatrixConfigurationSorterDescriptor extends Descriptor<MatrixConfigurationSorter> {
    protected MatrixConfigurationSorterDescriptor(Class<? extends MatrixConfigurationSorter> clazz) {
        super(clazz);
    }

    protected MatrixConfigurationSorterDescriptor() {
    }

    /**
     * Returns all the registered {@link AxisDescriptor}s.
     */
    public static DescriptorExtensionList<MatrixConfigurationSorter,MatrixConfigurationSorterDescriptor> all() {
        return Jenkins.getInstance().<MatrixConfigurationSorter,MatrixConfigurationSorterDescriptor>getDescriptorList(MatrixConfigurationSorter.class);
    }    
}
