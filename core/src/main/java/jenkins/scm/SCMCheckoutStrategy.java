package jenkins.scm;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractBuild.AbstractBuildExecution;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Executor;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;

import java.io.File;
import java.io.IOException;

/**
 * Controls the check out behavior in {@link AbstractBuild}.
 * 
 * <p>
 * While this can work with any {@link AbstractBuild}, the primary motivation of this extension point
 * is to control the check out behaviour in matrix projects. The intended use cases include situations like:
 * 
 * <ul>
 *     <li>Check out will only happen once in {@code MatrixBuild}, and its state will be then sent
 *         to {@code MatrixRun}s by other means such as rsync.
 *     <li>{@code MatrixBuild} does no check out of its own, and check out is only done on {@code MatrixRun}s
 * </ul>
 *
 * <h2>Hook Semantics</h2>
 * There are currently two hooks defined on this class:
 * 
 * <h3>pre checkout</h3>
 * <p>
 * The default implementation calls into {@link BuildWrapper#preCheckout(AbstractBuild, Launcher, BuildListener)} calls.
 * You can override this method to do something before/after this, but you must still call into the {@code super.preCheckout}
 * so that matrix projects can satisfy the contract with {@link BuildWrapper}s.
 *
 * <h3>checkout</h3>
 * <p>
 * The default implementation uses {@link AbstractProject#checkout(AbstractBuild, Launcher, BuildListener, File)} to
 * let {@link SCM} do check out, but your {@link SCMCheckoutStrategy} impls can substitute this call with other
 * operations that substitutes this semantics.
 * 
 * <h2>State and concurrency</h2>
 * <p>
 * An instance of this object gets created for a project for which this strategy is configured, so
 * the subtype needs to avoid using instance variables to refer to build-specific state (such as {@link BuildListener}s.)
 * Similarly, methods can be invoked concurrently. The code executes on the master, even if builds are running remotely.
 */
public abstract class SCMCheckoutStrategy extends AbstractDescribableImpl<SCMCheckoutStrategy> implements ExtensionPoint {

    /*
        Default behavior is defined in AbstractBuild.AbstractRunner, which is the common
        implementation for not just matrix projects but all sorts of other project types.
     */

    /**
     * Performs the pre checkout step.
     * 
     * This method is called by the {@link Executor} that's carrying out the build.
     * 
     * @param build
     *      Build being in progress. Never null.
     * @param launcher
     *      Allows you to launch process on the node where the build is actually running. Never null.
     * @param listener
     *      Allows you to write to console output and report errors. Never null.
     */
    public void preCheckout(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        AbstractProject<?, ?> project = build.getProject();
        if (project instanceof BuildableItemWithBuildWrappers) {
               BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) project;
               for (BuildWrapper bw : biwbw.getBuildWrappersList())
                   bw.preCheckout(build,launcher,listener);
           }
    }

    /**
     * Performs the checkout step.
     * 
     * See {@link #preCheckout(AbstractBuild, Launcher, BuildListener)} for the semantics of the parameters.
     */
    public void checkout(AbstractBuildExecution execution) throws IOException, InterruptedException {
        execution.defaultCheckout();
    }

    @Override
    public SCMCheckoutStrategyDescriptor getDescriptor() {
        return (SCMCheckoutStrategyDescriptor)super.getDescriptor();
    }

}
