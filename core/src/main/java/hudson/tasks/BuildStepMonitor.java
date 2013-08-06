package hudson.tasks;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.CheckPoint;
import hudson.Launcher;
import hudson.model.Describable;

import java.io.IOException;

/**
 * Used by {@link BuildStep#getRequiredMonitorService()}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 */
public enum BuildStepMonitor {
    NONE {
        public boolean perform(BuildStep bs, AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            return bs.perform(build,launcher,listener);
        }
    },
    STEP {
        public boolean perform(BuildStep bs, AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            CheckPoint cp = new CheckPoint(bs.getClass().getName(),bs.getClass());
            if (bs instanceof Describable) {
                cp.block(listener, ((Describable) bs).getDescriptor().getDisplayName());
            } else {
                cp.block();
            }
            try {
                return bs.perform(build,launcher,listener);
            } finally {
                cp.report();
            }
        }
    },
    BUILD {
        public boolean perform(BuildStep bs, AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            if (bs instanceof Describable) {
                CheckPoint.COMPLETED.block(listener, ((Describable) bs).getDescriptor().getDisplayName());
            } else {
                CheckPoint.COMPLETED.block();
            }
            return bs.perform(build,launcher,listener);
        }
    };

    /**
     * Calls {@link BuildStep#perform(AbstractBuild, Launcher, BuildListener)} with the proper synchronization.
     */
    public abstract boolean perform(BuildStep bs, AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
}
