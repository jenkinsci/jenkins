package hudson.matrix;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;

import java.io.IOException;

public abstract class MatrixRunCheckoutStrategy extends
    AbstractDescribableImpl<MatrixRunCheckoutStrategy> implements ExtensionPoint {

    protected abstract boolean preCheckout(MatrixRun build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
    protected abstract boolean checkout(MatrixRun build, Launcher launcher, BuildListener listener) throws Exception;

    @Override
    public MatrixRunCheckoutStrategyDescriptor getDescriptor() {
        return (MatrixRunCheckoutStrategyDescriptor)super.getDescriptor();
    }
}
