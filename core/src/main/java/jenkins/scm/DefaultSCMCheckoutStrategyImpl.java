package jenkins.scm;

import hudson.Extension;
import hudson.model.AbstractProject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default {@link SCMCheckoutStrategy} implementation.
 */
public class DefaultSCMCheckoutStrategyImpl extends SCMCheckoutStrategy {

    @DataBoundConstructor
    public DefaultSCMCheckoutStrategyImpl() {}

    @Extension @Symbol("default")
    public static class DescriptorImpl extends SCMCheckoutStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Default";
        }

        @Override
        public boolean isApplicable(AbstractProject project) {
            return true;
        }
    }
}
