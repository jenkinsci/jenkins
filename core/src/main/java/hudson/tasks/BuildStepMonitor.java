package hudson.tasks;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.CheckPoint;
import hudson.model.Describable;
import java.io.IOException;

/**
 * Defines synchronization strategies for build steps.
 *
 * @since 1.319
 */
public enum BuildStepMonitor {
    NONE {
        @Override
        public boolean perform(BuildStep bs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            // Direct execution without synchronization
            return bs.perform(build, launcher, listener);
        }
    },
    STEP {
        @Override
        public boolean perform(BuildStep bs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            // Step-level synchronization using CheckPoint
            CheckPoint cp = new CheckPoint(bs.getClass().getName(), bs.getClass());
            if (bs instanceof Describable) {
                cp.block(listener, ((Describable<?>) bs).getDescriptor().getDisplayName());
            } else {
                cp.block();
            }
            try {
                return bs.perform(build, launcher, listener);
            } finally {
                cp.report();
            }
        }
    },
    BUILD {
        @Override
        public boolean perform(BuildStep bs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            // Build-level synchronization
            if (bs instanceof Describable) {
                CheckPoint.COMPLETED.block(listener, ((Describable<?>) bs).getDescriptor().getDisplayName());
            } else {
                CheckPoint.COMPLETED.block();
            }
            return bs.perform(build, launcher, listener);
        }
    };

    /**
     * Executes the build step with the appropriate synchronization.
     *
     * @param bs the build step to execute
     * @param build the current build
     * @param launcher the launcher
     * @param listener the build listener
     * @return true if the build can continue, false otherwise
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the execution is interrupted
     */
    public abstract boolean perform(BuildStep bs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;
}
