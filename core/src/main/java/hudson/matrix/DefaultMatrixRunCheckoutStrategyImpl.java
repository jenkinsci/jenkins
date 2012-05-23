package hudson.matrix;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class DefaultMatrixRunCheckoutStrategyImpl extends MatrixRunCheckoutStrategy {

    @DataBoundConstructor
    public DefaultMatrixRunCheckoutStrategyImpl() {
        }

    @Override
    protected boolean preCheckout(MatrixRun build, Launcher launcher, BuildListener listener)
        throws IOException, InterruptedException {
        return true;
    }

    @Override
    protected boolean checkout(MatrixRun build, Launcher launcher, BuildListener listener) throws Exception {
        return true;
    }

    @Extension
    public static class DescriptorImpl extends MatrixRunCheckoutStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Classic";
        }
    }
}
