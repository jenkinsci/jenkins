package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.tasks.BuildStep;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * Receives events that happen as a build executes {@link BuildStep}s.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @since 1.502
 */
public abstract class BuildStepListener implements ExtensionPoint {

    /**
     * Called when a buildStep is performed.
     */
    public abstract void started(AbstractBuild build, BuildStep bs, BuildListener listener);

    /**
     * Called when a buildStep has completed.
     */
    public abstract void finished(AbstractBuild build, BuildStep bs, BuildListener listener, boolean canContinue);

    /**
     * Returns all the registered {@link BuildStepListener}s.
     */
    public static ExtensionList<BuildStepListener> all() {
        return Jenkins.getInstance().getExtensionList(BuildStepListener.class);
    }
}
