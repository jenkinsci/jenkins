package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import hudson.model.Run;

import java.io.IOException;

/**
 * Implementation of "Discard old build records" feature.
 *
 * <p>
 * This extension point allows plugins to implement a different strategy to decide what builds to discard
 * and what builds to keep.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.503
 */
public abstract class BuildDiscarder extends AbstractDescribableImpl<BuildDiscarder> implements ExtensionPoint {
    /**
     * Called to perform "garbage collection" on the job to discard old build records.
     *
     * <p>
     * Normally invoked automatically jobs when new builds occur.
     * The general expectation is that those marked as {@link Run#isKeepLog()} will be kept untouched.
     * To delete the build record, call {@link Run#delete()}.
     *
     * @see Job#logRotate()
     */
    public abstract void perform(Job<?,?> job) throws IOException, InterruptedException;

    @Override
    public BuildDiscarderDescriptor getDescriptor() {
        return (BuildDiscarderDescriptor)super.getDescriptor();
    }
}
