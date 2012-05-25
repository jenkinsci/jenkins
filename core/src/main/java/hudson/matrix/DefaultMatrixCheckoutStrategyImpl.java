package hudson.matrix;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link MatrixCheckoutStrategy} implementation that falls back to the default
 * behaviours defined in {@link AbstractBuild.AbstractRunner}, which is the common
 * implementation for not just matrix projects but all sorts of other project types.
 */
public class DefaultMatrixCheckoutStrategyImpl extends MatrixCheckoutStrategy {

    @DataBoundConstructor
    public DefaultMatrixCheckoutStrategyImpl() {}

    @Override
    public void preCheckout(MatrixRun build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        build.runner.defaultPreCheckout();
    }

    @Override
    public void checkout(MatrixRun build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        build.runner.defaultCheckout();
    }

    @Override
    public void preCheckout(MatrixBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        build.runner.defaultPreCheckout();
    }

    @Override
    public void checkout(MatrixBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        build.runner.defaultCheckout();
    }

    @Extension
    public static class DescriptorImpl extends MatrixCheckoutStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Classic";
        }
    }
}
