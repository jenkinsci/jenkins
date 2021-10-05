package jenkins.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
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

    @Extension @Symbol("standard")
    public static class DescriptorImpl extends SCMCheckoutStrategyDescriptor {
        @NonNull
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
