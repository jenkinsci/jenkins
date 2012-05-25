package hudson.matrix;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default {@link MatrixCheckoutStrategy} implementation.
 */
public class DefaultMatrixCheckoutStrategyImpl extends MatrixCheckoutStrategy {

    @DataBoundConstructor
    public DefaultMatrixCheckoutStrategyImpl() {}

    @Extension
    public static class DescriptorImpl extends MatrixCheckoutStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Classic";
        }
    }
}
