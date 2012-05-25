package hudson.matrix;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;

import java.io.IOException;

public abstract class MatrixCheckoutStrategy extends
    AbstractDescribableImpl<MatrixCheckoutStrategy> implements ExtensionPoint {

    public abstract void preCheckout(MatrixRun build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
    public abstract void checkout(MatrixRun build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;

    public abstract void preCheckout(MatrixBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
    public abstract void checkout(MatrixBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;

    @Override
    public MatrixCheckoutStrategyDescriptor getDescriptor() {
        return (MatrixCheckoutStrategyDescriptor)super.getDescriptor();
    }

}
