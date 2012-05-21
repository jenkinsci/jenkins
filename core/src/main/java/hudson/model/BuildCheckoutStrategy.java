package hudson.model;

import hudson.ExtensionPoint;
import hudson.Launcher;

import java.io.IOException;

public abstract class BuildCheckoutStrategy extends
		AbstractDescribableImpl<BuildCheckoutStrategy> implements ExtensionPoint {
	
	protected abstract void preCheckout(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
	protected abstract void checkout(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws Exception;
	
	@Override
    public BuildCheckoutStrategyDescriptor getDescriptor() {
        return (BuildCheckoutStrategyDescriptor)super.getDescriptor();
    }
}