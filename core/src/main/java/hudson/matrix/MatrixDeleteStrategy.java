package hudson.matrix;

import java.io.IOException;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Controls how the {@link MatrixBuild} and its sub-builds are deleted. 
 * 
 * @author vjuranek
 * @since 1.481
 *
 */
public abstract class MatrixDeleteStrategy extends AbstractDescribableImpl<MatrixDeleteStrategy> implements ExtensionPoint {
    
    public abstract void doDelete(MatrixBuild b) throws MatrixDeleteException, IOException;
    
    @Override
    public MatrixDeleteStrategyDescriptor getDescriptor() {
        return (MatrixDeleteStrategyDescriptor)super.getDescriptor();
    }

    public static abstract class MatrixDeleteStrategyDescriptor extends Descriptor<MatrixDeleteStrategy> {
        protected MatrixDeleteStrategyDescriptor(Class<? extends MatrixDeleteStrategy> clazz) {
            super(clazz);
        }

        protected MatrixDeleteStrategyDescriptor() {
        }

        /**
         * Returns all the registered {@link MatrixDeleteStrategyDescriptor}s.
         */
        public static DescriptorExtensionList<MatrixDeleteStrategy,MatrixDeleteStrategyDescriptor> all() {
            return Jenkins.getInstance().<MatrixDeleteStrategy,MatrixDeleteStrategyDescriptor>getDescriptorList(MatrixDeleteStrategy.class);
        }
    }
    
    public static class MatrixDeleteException extends RuntimeException {
        
        public MatrixDeleteException(String message) {
            super(message);
        }
        
        private static final long serialVersionUID = 1L;
    }
    
}
