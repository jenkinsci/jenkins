package hudson.matrix;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Descriptor for {@link MatrixConfigurationSorter}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.439
 */
public abstract class MatrixConfigurationSorterDescriptor extends Descriptor<MatrixConfigurationSorter> {
    protected MatrixConfigurationSorterDescriptor(Class<? extends MatrixConfigurationSorter> clazz) {
        super(clazz);
    }

    protected MatrixConfigurationSorterDescriptor() {
    }

    /**
     * Returns all the registered {@link MatrixConfigurationSorterDescriptor}s.
     */
    public static DescriptorExtensionList<MatrixConfigurationSorter,MatrixConfigurationSorterDescriptor> all() {
        return Jenkins.getInstance().<MatrixConfigurationSorter,MatrixConfigurationSorterDescriptor>getDescriptorList(MatrixConfigurationSorter.class);
    }    
}
